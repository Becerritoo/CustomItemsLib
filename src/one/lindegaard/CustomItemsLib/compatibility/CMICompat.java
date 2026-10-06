package one.lindegaard.CustomItemsLib.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import com.Zrips.CMI.CMI;
import com.Zrips.CMI.Modules.Holograms.HologramManager;

import one.lindegaard.CustomItemsLib.Core;

public class CMICompat {

	private static Plugin mPlugin;
	private static boolean supported = false;

	// https://www.spigotmc.org/resources/cmi-ranks-kits-portals-essentials-mysql-sqlite-bungeecord.3742/
	public CMICompat() {
		if (!isEnabledInConfig()) {
			Bukkit.getConsoleSender().sendMessage(Core.PREFIX + "Compatibility with CMI is disabled in config.yml");
		} else {
			mPlugin = Bukkit.getPluginManager().getPlugin(CompatPlugin.CMI.getName());

			if (mPlugin.getDescription().getVersion().compareTo("9.0") >= 0) {
				Bukkit.getConsoleSender().sendMessage(Core.PREFIX + "Enabling compatibility with CMI ("
						+ mPlugin.getDescription().getVersion() + ").");

				supported = true;

			} else
				Bukkit.getConsoleSender().sendMessage(Core.PREFIX_ERROR + "Your current version of CMI ("
						+ mPlugin.getDescription().getVersion()
						+ ") is not supported by CustomItemsLib. CustomItemsLib does only support version 9.0 or newer.");

		}
	}

	// **************************************************************************
	// OTHER
	// **************************************************************************

	public static CMI getCMIPlugin() {
		return (CMI) mPlugin;
	}

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isEnabledInConfig() {
		return Core.getConfigManager().enableIntegrationCMI;
	}

	public static HologramManager getHologramManager() {
		return getCMIPlugin().getHologramManager();
	}

}
