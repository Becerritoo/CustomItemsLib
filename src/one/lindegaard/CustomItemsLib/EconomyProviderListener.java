package one.lindegaard.CustomItemsLib;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServerLoadEvent;

public class EconomyProviderListener implements Listener {

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPluginEnable(PluginEnableEvent event) {
		if (!"BagOfGold".equalsIgnoreCase(event.getPlugin().getName()))
			return;

		if (Core.getEconomyManager() != null)
			Core.getEconomyManager().setupEconomy();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onServerLoad(ServerLoadEvent event) {
		if (Core.getEconomyManager() != null)
			Core.getEconomyManager().setupEconomy();
	}
}
