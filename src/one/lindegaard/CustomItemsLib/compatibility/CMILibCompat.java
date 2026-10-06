package one.lindegaard.CustomItemsLib.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.Zrips.CMILib.CMILib;
import net.Zrips.CMILib.ActionBar.CMIActionBar;
import net.Zrips.CMILib.BossBar.BossBarInfo;
import one.lindegaard.CustomItemsLib.Core;

public class CMILibCompat {

	private static boolean supported = false;

	public CMILibCompat() {
		supported = false;
		if (!isEnabledInConfig()) {
			Bukkit.getConsoleSender().sendMessage(Core.PREFIX + "Compatibility with CMILib is disabled in config.yml");
			return;
		}

		Plugin plugin = Bukkit.getPluginManager().getPlugin(CompatPlugin.CMILib.getName());
		if (plugin == null || !plugin.isEnabled())
			return;

		if (plugin.getDescription().getVersion().compareTo("1.0") < 0) {
			Bukkit.getConsoleSender().sendMessage(Core.PREFIX_ERROR + "Your current version of CMILib ("
					+ plugin.getDescription().getVersion()
					+ ") is not supported by CustomItemsLib. CustomItemsLib supports version 1.0 or newer.");
			return;
		}

		Bukkit.getConsoleSender().sendMessage(Core.PREFIX + "Enabling compatibility with CMILib ("
				+ plugin.getDescription().getVersion() + ").");
		supported = true;
	}

	public static boolean isSupported() {
		return supported;
	}

	public static boolean isEnabledInConfig() {
		return Core.getConfigManager().enableIntegrationCMILib;
	}

	public static void sendActionBarMessage(Player player, String text) {
		CMIActionBar.send(player, text);
	}

	public static void sendBossBarMessage(Player player, String text) {
		BossBarInfo bossBar = new BossBarInfo(player, "...");
		bossBar.setSeconds(10);
		bossBar.setTitleOfBar(text);
		bossBar.setKeepForTicks(0);
		CMILib.getInstance().getBossBarManager().addBossBar(player, bossBar);
	}
}
