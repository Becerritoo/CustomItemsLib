package one.lindegaard.CustomItemsLib.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Shared Hangar release checker and verified update downloader.
 */
public abstract class HangarUpdateService {

	private static final long MAX_DOWNLOAD_BYTES = 100L * 1024L * 1024L;
	private static final String HANGAR_HOST = "hangar.papermc.io";
	private static final String HANGAR_CDN_HOST = "hangarcdn.papermc.io";

	private final JavaPlugin plugin;
	private final String owner;
	private final String projectSlug;
	private final HttpClient httpClient;
	private final AtomicBoolean requestInProgress = new AtomicBoolean(false);

	private volatile UpdateStatus updateAvailable = UpdateStatus.UNKNOWN;
	private volatile String currentJarFile = "";
	private volatile String newDownloadVersion = "";
	private volatile Release latestRelease;

	protected HangarUpdateService(JavaPlugin plugin, String owner, String projectSlug) {
		this.plugin = plugin;
		this.owner = owner;
		this.projectSlug = projectSlug;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL).build();
	}

	protected abstract long getCheckIntervalSeconds();

	protected abstract boolean isAutoUpdateEnabled();

	protected abstract String getPrefix();

	protected abstract String getCheckingMessage();

	protected abstract String getVersionFoundMessage(String version);

	protected abstract String getNoUpdateMessage();

	protected abstract String getUpdateCompleteMessage();

	protected abstract String getCouldNotUpdateMessage();

	protected abstract String getUpdateHelpMessage(String projectUrl);

	public UpdateStatus getUpdateAvailable() {
		return updateAvailable;
	}

	public String getCurrentJarFile() {
		return currentJarFile;
	}

	public void setCurrentJarFile(String name) {
		currentJarFile = name == null ? "" : name;
	}

	public String getNewDownloadVersion() {
		return newDownloadVersion;
	}

	public String getProjectUrl() {
		return "https://hangar.papermc.io/" + owner + "/" + projectSlug;
	}

	public void hourlyUpdateCheck(final CommandSender sender, boolean updateCheck, final boolean silent) {
		long seconds = getCheckIntervalSeconds();
		if (seconds < 900) {
			Bukkit.getConsoleSender().sendMessage(getPrefix() + ChatColor.RED
					+ "check_every is too low. It has been raised to 900 seconds.");
			seconds = 900;
		}
		if (!updateCheck)
			return;

		new BukkitRunnable() {
			@Override
			public void run() {
				checkForUpdate(sender, silent, false);
			}
		}.runTaskTimer(plugin, 1200L, seconds * 20L);
	}

	public void checkForUpdate(final CommandSender sender, final boolean silent, final boolean forceDownload) {
		if (updateAvailable == UpdateStatus.RESTART_NEEDED) {
			if (!silent)
				sender.sendMessage(getPrefix() + ChatColor.GREEN + getUpdateCompleteMessage());
			return;
		}
		if (!requestInProgress.compareAndSet(false, true))
			return;

		if (!silent)
			sender.sendMessage(getPrefix() + getCheckingMessage());

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				Release release = fetchLatestCompatibleRelease();
				latestRelease = release;
				if (release == null || isUpdateNewerVersion(release.version) != UpdateStatus.AVAILABLE) {
					updateAvailable = UpdateStatus.NOT_AVAILABLE;
					runSync(() -> {
						if (!silent)
							sender.sendMessage(getPrefix() + getNoUpdateMessage());
					});
					return;
				}

				newDownloadVersion = release.version;
				updateAvailable = UpdateStatus.AVAILABLE;
				runSync(() -> sender.sendMessage(
						getPrefix() + ChatColor.GREEN + getVersionFoundMessage(release.version)));

				if (isAutoUpdateEnabled() || forceDownload) {
					downloadAndStage(release);
					updateAvailable = UpdateStatus.RESTART_NEEDED;
					runSync(() -> sender.sendMessage(
							getPrefix() + ChatColor.GREEN + getUpdateCompleteMessage()));
				} else {
					runSync(() -> sender.sendMessage(getPrefix() + getUpdateHelpMessage(getProjectUrl())));
				}
			} catch (Exception exception) {
				updateAvailable = UpdateStatus.NO_RESPONSE;
				plugin.getLogger().warning("Could not check or download the Hangar update: " + exception.getMessage());
				runSync(() -> {
					if (!silent)
						sender.sendMessage(getPrefix() + ChatColor.RED + getCouldNotUpdateMessage());
				});
			} finally {
				requestInProgress.set(false);
			}
		});
	}

	public UpdateStatus isUpdateNewerVersion(String newVersion) {
		try {
			SemanticVersion installed = SemanticVersion.parse(plugin.getDescription().getVersion());
			SemanticVersion available = SemanticVersion.parse(newVersion);
			return available.compareTo(installed) > 0 ? UpdateStatus.AVAILABLE : UpdateStatus.NOT_AVAILABLE;
		} catch (IllegalArgumentException exception) {
			plugin.getLogger().warning(exception.getMessage());
			return UpdateStatus.UNKNOWN;
		}
	}

	private Release fetchLatestCompatibleRelease() throws IOException, InterruptedException {
		String encodedOwner = URLEncoder.encode(owner, StandardCharsets.UTF_8);
		String encodedProject = URLEncoder.encode(projectSlug, StandardCharsets.UTF_8);
		URI uri = URI.create("https://" + HANGAR_HOST + "/api/v1/projects/" + encodedOwner + "/"
				+ encodedProject + "/versions?limit=25&offset=0&platform=PAPER");
		HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
				.header("Accept", "application/json")
				.header("User-Agent", projectSlug + "/" + plugin.getDescription().getVersion()).GET().build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200)
			throw new IOException("Hangar returned HTTP " + response.statusCode());

		JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
		JsonArray results = root.getAsJsonArray("result");
		if (results == null)
			throw new IOException("Hangar response did not contain versions");

		String minecraftVersion = Bukkit.getBukkitVersion().split("-", 2)[0];
		Release selected = null;
		SemanticVersion selectedVersion = null;
		for (JsonElement element : results) {
			if (!element.isJsonObject())
				continue;
			JsonObject versionObject = element.getAsJsonObject();
			if (!isReviewedRelease(versionObject) || !supportsMinecraft(versionObject, minecraftVersion))
				continue;

			String version = getString(versionObject, "name");
			SemanticVersion semanticVersion;
			try {
				semanticVersion = SemanticVersion.parse(version);
			} catch (IllegalArgumentException ignored) {
				continue;
			}
			if (semanticVersion.isPrerelease())
				continue;

			JsonObject paperDownload = getObject(getObject(versionObject, "downloads"), "PAPER");
			JsonObject fileInfo = getObject(paperDownload, "fileInfo");
			String downloadUrl = getString(paperDownload, "downloadUrl");
			String fileName = getString(fileInfo, "name");
			String sha256 = getString(fileInfo, "sha256Hash");
			long size = fileInfo != null && fileInfo.has("sizeBytes") ? fileInfo.get("sizeBytes").getAsLong() : -1;
			if (!isValidDownload(downloadUrl, fileName, sha256, size))
				continue;

			if (selectedVersion == null || semanticVersion.compareTo(selectedVersion) > 0) {
				selectedVersion = semanticVersion;
				selected = new Release(version, downloadUrl, fileName, sha256, size);
			}
		}
		return selected;
	}

	private boolean isReviewedRelease(JsonObject versionObject) {
		if (!"public".equalsIgnoreCase(getString(versionObject, "visibility")))
			return false;
		if (!"reviewed".equalsIgnoreCase(getString(versionObject, "reviewState")))
			return false;
		JsonObject channel = getObject(versionObject, "channel");
		return "Release".equalsIgnoreCase(getString(channel, "name"));
	}

	private boolean supportsMinecraft(JsonObject versionObject, String minecraftVersion) {
		JsonObject dependencies = getObject(versionObject, "platformDependencies");
		JsonArray paperVersions = dependencies == null ? null : dependencies.getAsJsonArray("PAPER");
		if (paperVersions == null)
			return false;
		for (JsonElement version : paperVersions) {
			if (minecraftVersion.equals(version.getAsString()))
				return true;
		}
		return false;
	}

	private boolean isValidDownload(String downloadUrl, String fileName, String sha256, long size) {
		if (downloadUrl == null || fileName == null || sha256 == null || !fileName.endsWith(".jar"))
			return false;
		if (!fileName.equals(Path.of(fileName).getFileName().toString()))
			return false;
		if (!sha256.matches("[0-9a-fA-F]{64}") || size <= 0 || size > MAX_DOWNLOAD_BYTES)
			return false;
		URI uri = URI.create(downloadUrl);
		return "https".equalsIgnoreCase(uri.getScheme()) && (HANGAR_HOST.equalsIgnoreCase(uri.getHost())
				|| HANGAR_CDN_HOST.equalsIgnoreCase(uri.getHost()));
	}

	private void downloadAndStage(Release release)
			throws IOException, InterruptedException, NoSuchAlgorithmException {
		Path updateDirectory = Bukkit.getUpdateFolderFile().toPath();
		Files.createDirectories(updateDirectory);
		String targetName = currentJarFile.isEmpty() ? release.fileName : Path.of(currentJarFile).getFileName().toString();
		Path target = updateDirectory.resolve(targetName);
		Path temporary = updateDirectory.resolve(targetName + ".part");

		HttpRequest request = HttpRequest.newBuilder(URI.create(release.downloadUrl)).timeout(Duration.ofMinutes(2))
				.header("User-Agent", projectSlug + "/" + plugin.getDescription().getVersion()).GET().build();
		HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200)
			throw new IOException("Hangar download returned HTTP " + response.statusCode());

		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		long bytes = 0;
		try (InputStream input = response.body(); java.io.OutputStream output = Files.newOutputStream(temporary)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = input.read(buffer)) != -1) {
				bytes += read;
				if (bytes > MAX_DOWNLOAD_BYTES)
					throw new IOException("Downloaded file exceeds the size limit");
				digest.update(buffer, 0, read);
				output.write(buffer, 0, read);
			}
		} catch (IOException | RuntimeException exception) {
			Files.deleteIfExists(temporary);
			throw exception;
		}

		String actualHash = HexFormat.of().formatHex(digest.digest());
		if (bytes != release.size || !actualHash.equalsIgnoreCase(release.sha256)) {
			Files.deleteIfExists(temporary);
			throw new IOException("Downloaded JAR failed size or SHA-256 verification");
		}

		try {
			Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException exception) {
			Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void runSync(Runnable runnable) {
		if (plugin.isEnabled())
			Bukkit.getScheduler().runTask(plugin, runnable);
	}

	private static JsonObject getObject(JsonObject object, String key) {
		if (object == null || !object.has(key) || !object.get(key).isJsonObject())
			return null;
		return object.getAsJsonObject(key);
	}

	private static String getString(JsonObject object, String key) {
		if (object == null || !object.has(key) || object.get(key).isJsonNull())
			return null;
		return object.get(key).getAsString();
	}

	private static final class Release {
		private final String version;
		private final String downloadUrl;
		private final String fileName;
		private final String sha256;
		private final long size;

		private Release(String version, String downloadUrl, String fileName, String sha256, long size) {
			this.version = version;
			this.downloadUrl = downloadUrl;
			this.fileName = fileName;
			this.sha256 = sha256;
			this.size = size;
		}
	}
}
