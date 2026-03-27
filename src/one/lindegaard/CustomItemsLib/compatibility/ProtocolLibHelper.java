package one.lindegaard.CustomItemsLib.compatibility;

import java.util.Iterator;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import com.comphenix.packetwrapper.WrapperPlayServerCollect;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

import one.lindegaard.CustomItemsLib.Core;

public class ProtocolLibHelper {

	private static ProtocolManager protocolManager;

	public static void enableProtocolLib() {
		protocolManager = ProtocolLibrary.getProtocolManager();

		ProtocolLibrary.getProtocolManager()
				.addPacketListener(new PacketAdapter(Core.getInstance(), ListenerPriority.HIGHEST,
						PacketType.Play.Server.getInstance().values().toArray(new PacketType[0])) {
					@Override
					public void onPacketSending(PacketEvent event) {
						boolean hideInternalLore = shouldHideInternalLore(event);
						if (!hideInternalLore) {
							return;
						}

						PacketContainer packet = event.getPacket().deepClone();
						boolean changed = false;

						StructureModifier<ItemStack> itemModifier = packet.getItemModifier();
						for (int i = 0; i < itemModifier.size(); i++) {
							ItemStack itemStack = itemModifier.read(i);
							ItemStack sanitized = sanitizeHiddenLore(itemStack);
							if (sanitized != itemStack) {
								itemModifier.write(i, sanitized);
								changed = true;
							}
						}

						StructureModifier<List<ItemStack>> listModifier = packet.getItemListModifier();
						for (int i = 0; i < listModifier.size(); i++) {
							List<ItemStack> itemList = listModifier.read(i);
							if (itemList == null || itemList.isEmpty()) {
								continue;
							}

							boolean listChanged = false;
							for (int j = 0; j < itemList.size(); j++) {
								ItemStack original = itemList.get(j);
								ItemStack sanitized = sanitizeHiddenLore(original);
								if (sanitized != original) {
									itemList.set(j, sanitized);
									listChanged = true;
								}
							}

							if (listChanged) {
								listModifier.write(i, itemList);
								changed = true;
							}
						}

						if (changed) {
							event.setPacket(packet);
						}
					}

					
				});
	}

	private static boolean shouldHideInternalLore(PacketEvent event) {
		if (event == null) {
			return false;
		}

		Player player = event.getPlayer();
		if (player == null) {
			return false;
		}

		// Hidden(...) lore is internal metadata and must never be visible to clients.
		// Always strip it for packet-rendered items (Java + Bedrock via Geyser/Floodgate).
		return true;
	}

	private static boolean isInternalHiddenLoreLine(String line) {
		if (line == null || line.isEmpty()) {
			return false;
		}

		String plain = ChatColor.stripColor(line);
		if (plain == null) {
			return false;
		}

		return plain.trim().startsWith("Hidden(");
	}

	private static ItemStack sanitizeHiddenLore(ItemStack itemStack) {
		if (itemStack == null || !itemStack.hasItemMeta()) {
			return itemStack;
		}

		ItemMeta itemMeta = itemStack.getItemMeta();
		if (itemMeta == null || !itemMeta.hasLore()) {
			return itemStack;
		}

		List<String> lore = itemMeta.getLore();
		if (lore == null || lore.isEmpty()) {
			return itemStack;
		}

		List<String> filtered = new java.util.ArrayList<>(lore.size());
		boolean removed = false;
		for (String line : lore) {
			if (isInternalHiddenLoreLine(line)) {
				removed = true;
				continue;
			}
			filtered.add(line);
		}

		if (!removed) {
			return itemStack;
		}

		ItemStack cloned = itemStack.clone();
		ItemMeta clonedMeta = cloned.getItemMeta();
		if (clonedMeta == null) {
			return itemStack;
		}
		clonedMeta.setLore(filtered.isEmpty() ? null : filtered);
		cloned.setItemMeta(clonedMeta);
		return cloned;
	}

	public static ProtocolManager getProtocolmanager() {
		return protocolManager;
	}

	public static void pickupMoney(Player player, Entity ent) {
		WrapperPlayServerCollect wpsc = new WrapperPlayServerCollect();
		wpsc.setCollectedEntityId(ent.getEntityId());
		wpsc.setCollectorEntityId(player.getEntityId());
		wpsc.sendPacket(player);
	}

}
