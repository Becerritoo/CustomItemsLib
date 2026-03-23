package one.lindegaard.CustomItemsLib.server;

import org.bukkit.Bukkit; 

public class Servers {

	private static final VersionInfo SERVER_VERSION = detectServerVersion();

	private static final class VersionInfo {
		private final int major;
		private final int minor;
		private final int patch;
		private final boolean valid;

		private VersionInfo(int major, int minor, int patch, boolean valid) {
			this.major = major;
			this.minor = minor;
			this.patch = patch;
			this.valid = valid;
		}
	}

	private static VersionInfo detectServerVersion() {
		try {
			String bukkitVersion = Bukkit.getBukkitVersion();
			String coreVersion = bukkitVersion.split("-", 2)[0];
			String[] numbers = coreVersion.split("\\.");
			if (numbers.length < 2) {
				return new VersionInfo(0, 0, 0, false);
			}

			int major = Integer.parseInt(numbers[0]);
			int minor = Integer.parseInt(numbers[1]);
			int patch = numbers.length >= 3 ? Integer.parseInt(numbers[2]) : 0;
			return new VersionInfo(major, minor, patch, true);
		} catch (Exception ignored) {
			return new VersionInfo(0, 0, 0, false);
		}
	}

	private static boolean isVersion(int major, int minor) {
		return SERVER_VERSION.valid && SERVER_VERSION.major == major && SERVER_VERSION.minor == minor;
	}

	private static boolean isVersion(int major, int minor, int patch) {
		return SERVER_VERSION.valid && SERVER_VERSION.major == major && SERVER_VERSION.minor == minor
				&& SERVER_VERSION.patch == patch;
	}

	private static boolean isAtLeast(int major, int minor) {
		return isAtLeast(major, minor, 0);
	}

	private static boolean isAtLeast(int major, int minor, int patch) {
		// Fail-open for unknown version formats to preserve legacy behavior.
		if (!SERVER_VERSION.valid)
			return true;

		if (SERVER_VERSION.major != major)
			return SERVER_VERSION.major > major;
		if (SERVER_VERSION.minor != minor)
			return SERVER_VERSION.minor > minor;
		return SERVER_VERSION.patch >= patch;
	}

	// *******************************************************************
	// Version detection
	// *******************************************************************

	public static boolean isMC121() {
		return isVersion(1, 21);
	}

	public static boolean isMC120() {
		return isVersion(1, 20);
	}
	
	public static boolean isMC119() {
		return isVersion(1, 19);
	}
	
	public static boolean isMC118() {
		return isVersion(1, 18);
	}
	
	public static boolean isMC117() {
		return isVersion(1, 17);
	}
	
	public static boolean isMC1162() {
		return isVersion(1, 16, 2);
	}
	
	public static boolean isMC116() {
		return isVersion(1, 16);
	}
	
	public static boolean isMC115() {
		return isVersion(1, 15);
	}

	public static boolean isMC114() {
		return isVersion(1, 14);
	}

	public static boolean isMC113() {
		return isVersion(1, 13);
	}

	public static boolean isMC112() {
		return isVersion(1, 12);
	}

	public static boolean isMC111() {
		return isVersion(1, 11);
	}

	public static boolean isMC110() {
		return isVersion(1, 10);
	}

	public static boolean isMC19() {
		return isVersion(1, 9);
	}

	public static boolean isMC18() {
		return isVersion(1, 8);
	}

	public static boolean isMC121OrNewer() {
		return isAtLeast(1, 21);
	}

	public static boolean isMC120OrNewer() {
		return isAtLeast(1, 20);
	}

	public static boolean isMC119OrNewer() {
		return isAtLeast(1, 19);
	}
	
	public static boolean isMC118OrNewer() {
		return isAtLeast(1, 18);
	}
	
	public static boolean isMC117OrNewer() {
		return isAtLeast(1, 17);
	}
	
	public static boolean isMC1162OrNewer() {
		return isAtLeast(1, 16, 2);
	}
	
	public static boolean isMC116OrNewer() {
		return isAtLeast(1, 16);
	}

	public static boolean isMC115OrNewer() {
		return isAtLeast(1, 15);
	}

	public static boolean isMC114OrNewer() {
		return isAtLeast(1, 14);
	}

	public static boolean isMC113OrNewer() {
		return isAtLeast(1, 13);
	}

	public static boolean isMC112OrNewer() {
		return isAtLeast(1, 12);
	}

	public static boolean isMC111OrNewer() {
		return isAtLeast(1, 11);
	}

	public static boolean isMC110OrNewer() {
		return isAtLeast(1, 10);
	}

	public static boolean isMC19OrNewer() {
		return isAtLeast(1, 9);
	}

	// *******************************************************************
	// Version detection
	// *******************************************************************
	public static boolean isGlowstoneServer() {
		return Bukkit.getServer().getName().equalsIgnoreCase("Glowstone");
	}

	private static String serverNameLower() {
		return Bukkit.getServer().getName().toLowerCase();
	}

	private static String serverVersionLower() {
		return Bukkit.getServer().getVersion().toLowerCase();
	}

	private static boolean classExists(String className) {
		try {
			Class.forName(className, false, Bukkit.getServer().getClass().getClassLoader());
			return true;
		} catch (Throwable ignored) {
			return false;
		}
	}

	public static boolean isPaperServer() {
		String name = serverNameLower();
		String version = serverVersionLower();

		if (name.contains("paper") || version.contains("paper"))
			return true;

		// Support both legacy and modern Paper package names.
		return classExists("com.destroystokyo.paper.PaperConfig")
				|| classExists("io.papermc.paper.configuration.GlobalConfiguration");
	}

	public static boolean isPurpurServer() {
		String name = serverNameLower();
		String version = serverVersionLower();

		if (name.contains("purpur") || version.contains("purpur"))
			return true;

		return classExists("org.purpurmc.purpur.PurpurConfig");
	}

	public static boolean isSpigotServer() {
		if (isPaperServer() || isPurpurServer())
			return false;

		String name = serverNameLower();
		String version = serverVersionLower();
		return name.contains("spigot") || (name.contains("craftbukkit") && version.contains("spigot"));
	}

	public static boolean isCraftBukkitServer() {
		if (isPaperServer() || isPurpurServer() || isSpigotServer())
			return false;

		return Bukkit.getServer().getName().equalsIgnoreCase("CraftBukkit")
				&& Bukkit.getServer().getVersion().toLowerCase().contains("bukkit");
	}

}
