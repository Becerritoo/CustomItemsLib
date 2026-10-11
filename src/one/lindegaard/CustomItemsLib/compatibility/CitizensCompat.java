package one.lindegaard.CustomItemsLib.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.CitizensPlugin;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;

/**
 * Legacy general-purpose Citizens bridge.
 * <p>
 * Consumer-specific traits belong to the consumer plugin. This class remains
 * available for source and binary compatibility during the 1.x line.
 */
@Deprecated
public class CitizensCompat implements Listener {

	public CitizensCompat() {
	}

	public void setVillagerSkin(Entity entity) {
		NPC npc = getNPC(entity);
		if (npc != null)
			npc.setBukkitEntityType(EntityType.VILLAGER);
	}

	/**
	 * Retained for compatibility. CustomItemsLib does not register Citizens traits.
	 */
	public void shutdown() {
	}

	public static CitizensPlugin getCitizensPlugin() {
		Plugin plugin = Bukkit.getPluginManager().getPlugin(CompatPlugin.Citizens.getName());
		return plugin instanceof CitizensPlugin ? (CitizensPlugin) plugin : null;
	}

	public static boolean isSupported() {
		return Bukkit.getPluginManager().isPluginEnabled(CompatPlugin.Citizens.getName())
				&& getCitizensPlugin() != null && CitizensAPI.hasImplementation();
	}

	public static boolean isNPC(Entity entity) {
		CitizensPlugin citizens = getCitizensPlugin();
		return entity != null && isSupported() && citizens.getNPCRegistry().isNPC(entity);
	}

	public boolean isNPC(Integer id) {
		CitizensPlugin citizens = getCitizensPlugin();
		return id != null && isSupported() && citizens.getNPCRegistry().getById(id) != null;
	}

	public int getNPCId(Entity entity) {
		NPC npc = getNPC(entity);
		return npc == null ? -1 : npc.getId();
	}

	public String getNPCName(Entity entity) {
		NPC npc = getNPC(entity);
		return npc == null ? null : npc.getName();
	}

	public NPC getNPC(Entity entity) {
		CitizensPlugin citizens = getCitizensPlugin();
		return entity == null || !isSupported() ? null : citizens.getNPCRegistry().getNPC(entity);
	}

	public boolean isSentryOrSentinelOrSentries(Entity entity) {
		NPC npc = getNPC(entity);
		return npc != null && hasTrait(npc, "Sentry", "Sentinel", "Sentries");
	}

	public boolean isSentryOrSentinelOrSentries(String mobtype) {
		if (mobtype == null)
			return false;
		try {
			CitizensPlugin citizens = getCitizensPlugin();
			if (!isSupported())
				return false;
			NPC npc = citizens.getNPCRegistry().getById(Integer.parseInt(mobtype));
			return npc != null && hasTrait(npc, "Sentry", "Sentinel", "Sentries");
		} catch (NumberFormatException ex) {
			return false;
		}
	}

	public boolean isEnabledInConfig() {
		return Bukkit.getPluginManager().isPluginEnabled(CompatPlugin.Citizens.getName());
	}

	private boolean hasTrait(NPC npc, String... names) {
		CitizensPlugin citizens = getCitizensPlugin();
		if (citizens == null)
			return false;
		for (String name : names) {
			Class<? extends Trait> traitClass = citizens.getTraitFactory().getTraitClass(name);
			if (traitClass != null && npc.hasTrait(traitClass))
				return true;
		}
		return false;
	}
}
