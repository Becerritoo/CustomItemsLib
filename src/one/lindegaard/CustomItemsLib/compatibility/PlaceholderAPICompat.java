package one.lindegaard.CustomItemsLib.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.clip.placeholderapi.PlaceholderAPI;
import one.lindegaard.CustomItemsLib.Core;

public class PlaceholderAPICompat {

	private static boolean supported;

	public PlaceholderAPICompat() {
		supported = false;
		if (!isEnabledInConfig()) {
			Bukkit.getConsoleSender()
					.sendMessage(Core.PREFIX + "Compatibility with PlaceholderAPI is disabled in config.yml");
			return;
		}

		Plugin plugin = Bukkit.getPluginManager().getPlugin(CompatPlugin.PlaceholderAPI.getName());
		if (plugin == null || !plugin.isEnabled())
			return;

		Bukkit.getConsoleSender().sendMessage(Core.PREFIX + "Enabling compatibility with PlaceholderAPI ("
				+ plugin.getDescription().getVersion() + ").");
		supported = true;
	}

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isEnabledInConfig() {
		return Core.getConfigManager().enableIntegrationPlaceholderAPI;
	}

	public static String setPlaceholders(Player player, String message) {
		if (supported && player != null)
			return PlaceholderAPI.setPlaceholders(player, message);
		return message;
	}
}
