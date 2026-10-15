package one.lindegaard.CustomItemsLib.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Handles one-time imports from the data directories used before
 * CustomItemsLib owned shared configuration and reward persistence.
 */
public final class LegacyDataMigrator {

	private static final String CORE_CONFIG = "bagofgold-core-config";
	private static final String CORE_DATABASE = "bagofgold-core-database";
	private static final String WORLD_GROUPS = "consumer-world-groups";
	private static final String BAGOFGOLD_REWARDS = "bagofgold-reward-blocks";
	private static final String MOBHUNTING_REWARDS = "mobhunting-reward-blocks";

	private final Plugin plugin;
	private final File pluginsFolder;
	private final File stateFile;
	private final YamlConfiguration state;

	public LegacyDataMigrator(Plugin plugin) {
		this.plugin = plugin;
		this.pluginsFolder = plugin.getDataFolder().getParentFile();
		this.stateFile = new File(plugin.getDataFolder(), "legacy-migrations.yml");
		this.state = YamlConfiguration.loadConfiguration(stateFile);
	}

	public void migrateCoreFiles() {
		migrateFile(CORE_CONFIG, new File(pluginsFolder, "BagOfGold/bagofgoldcore.yml"),
				new File(plugin.getDataFolder(), "config.yml"), true);
		migrateFile(CORE_DATABASE, new File(pluginsFolder, "BagOfGold/bagofgoldcore.db"),
				new File(plugin.getDataFolder(), "bagofgoldcore.db"), false);
	}

	public void migrateWorldGroups(File destination) {
		if (isComplete(WORLD_GROUPS))
			return;

		if (destination.exists()) {
			complete(WORLD_GROUPS, "destination-present", null, 0);
			return;
		}

		File bagOfGold = new File(pluginsFolder, "BagOfGold/worldgroups.yml");
		File mobHunting = new File(pluginsFolder, "MobHunting/worldgroups.yml");
		File source = bagOfGold.exists() ? bagOfGold : mobHunting.exists() ? mobHunting : null;
		if (source == null) {
			complete(WORLD_GROUPS, "source-absent", null, 0);
			return;
		}

		migrateFile(WORLD_GROUPS, source, destination, true);
	}

	public int migrateRewardFiles(LegacyRewardImporter importer, RewardMigrationSaver saver) {
		int imported = 0;
		imported += migrateRewardFile(BAGOFGOLD_REWARDS, new File(pluginsFolder, "BagOfGold/rewards.yml"),
				importer, saver);
		imported += migrateRewardFile(MOBHUNTING_REWARDS, new File(pluginsFolder, "MobHunting/rewards.yml"),
				importer, saver);
		return imported;
	}

	private void migrateFile(String id, File source, File destination, boolean validateYaml) {
		if (isComplete(id))
			return;

		if (destination.exists()) {
			complete(id, "destination-present", source, 0);
			return;
		}
		if (!source.exists()) {
			complete(id, "source-absent", source, 0);
			return;
		}

		try {
			if (validateYaml) {
				YamlConfiguration validation = new YamlConfiguration();
				validation.load(source);
			}
			destination.getParentFile().mkdirs();
			Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
			complete(id, "copied", source, 1);
			plugin.getLogger().info("Imported legacy data from " + source.getPath() + "; the source file was retained.");
		} catch (IOException | InvalidConfigurationException ex) {
			plugin.getLogger().warning("Could not import legacy data from " + source.getPath() + ": "
					+ ex.getMessage());
		}
	}

	private int migrateRewardFile(String id, File source, LegacyRewardImporter importer,
			RewardMigrationSaver saver) {
		if (isComplete(id))
			return 0;
		if (!source.exists()) {
			complete(id, "source-absent", source, 0);
			return 0;
		}

		try {
			YamlConfiguration legacyRewards = new YamlConfiguration();
			legacyRewards.load(source);
			int imported = importer.importRewards(legacyRewards);
			if (imported > 0 && !saver.save()) {
				plugin.getLogger().warning("Legacy rewards from " + source.getPath()
						+ " were loaded but could not be persisted; the migration will be retried.");
				return 0;
			}
			complete(id, "imported", source, imported);
			plugin.getLogger().info("Imported " + imported + " legacy reward blocks from " + source.getPath()
					+ "; the source file was retained.");
			return imported;
		} catch (IOException | InvalidConfigurationException ex) {
			plugin.getLogger().warning("Could not import legacy rewards from " + source.getPath() + ": "
					+ ex.getMessage());
			return 0;
		}
	}

	private boolean isComplete(String id) {
		return state.getBoolean("migrations." + id + ".complete", false);
	}

	private void complete(String id, String result, File source, int imported) {
		String path = "migrations." + id;
		state.set(path + ".complete", true);
		state.set(path + ".result", result);
		state.set(path + ".completed-at", Instant.now().toString());
		state.set(path + ".source", source == null ? null : source.getPath());
		state.set(path + ".imported", imported);
		try {
			stateFile.getParentFile().mkdirs();
			state.save(stateFile);
		} catch (IOException ex) {
			plugin.getLogger().warning("Could not save legacy migration state: " + ex.getMessage());
		}
	}

	@FunctionalInterface
	public interface LegacyRewardImporter {
		int importRewards(YamlConfiguration legacyRewards) throws InvalidConfigurationException;
	}

	@FunctionalInterface
	public interface RewardMigrationSaver {
		boolean save();
	}
}
