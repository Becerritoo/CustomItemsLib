package one.lindegaard.CustomItemsLib.compatibility;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.lang.reflect.Method;

import org.bukkit.GameMode;
import org.bukkit.Bukkit;
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
						PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {
					@Override
					public void onPacketSending(PacketEvent event) {
						boolean hideInternalLore = shouldHideInternalLore(event);
						if (!hideInternalLore) {
							return;
						}

						if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
							PacketContainer packet = event.getPacket().deepClone();
							StructureModifier<ItemStack> sm = packet.getItemModifier();
							for (int i = 0; i < sm.size(); i++) {
								ItemStack is = sm.getValues().get(i);
								if (is != null && is.hasItemMeta()) {
									ItemMeta itemMeta = is.getItemMeta();
									if (itemMeta != null && itemMeta.hasLore()) {
										List<String> lore = itemMeta.getLore();
										if (lore == null)
											continue;
										Iterator<String> itr = lore.iterator();
										while (itr.hasNext()) {
											String str = itr.next();
											if (str.startsWith("Hidden("))
												itr.remove();
										}
										itemMeta.setLore(lore);
										is.setItemMeta(itemMeta);
									}
								}
							}
							event.setPacket(packet);
						}

				else if (event.getPacketType() == PacketType.Play.Server.WINDOW_ITEMS) {
							PacketContainer packet = event.getPacket().deepClone();
							StructureModifier<List<ItemStack>> modifiers = packet.getItemListModifier();
							for (int j = 0; j < modifiers.size(); j++) {
								List<ItemStack> itemStackList = modifiers.getValues().get(j);
								if (itemStackList == null)
									continue;
								for (int i = 0; i < itemStackList.size(); i++) {
									ItemStack is = itemStackList.get(i);
									if (is != null && is.hasItemMeta()) {
										ItemMeta itemMeta = is.getItemMeta();
										if (itemMeta != null && itemMeta.hasLore()) {
											List<String> lore = itemMeta.getLore();
											if (lore == null)
												continue;
											Iterator<String> itr = lore.iterator();
											while (itr.hasNext()) {
												String str = itr.next();
												if (str.startsWith("Hidden("))
//													BagOfGold.getInstance().getMessages().debug("ProtocolLibHelper:ItemSlots=%s", event.getPacket().getItemSlots().toString());
													itr.remove();
											}
											itemMeta.setLore(lore);
											is.setItemMeta(itemMeta);
										}
									}
								}
							}
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

		if (isTemporaryPlayer(event)) {
			Player resolved = resolveOnlinePlayer(player);
			return resolved != null && resolved.getGameMode() == GameMode.SURVIVAL;
		}

		try {
			return player.getGameMode() == GameMode.SURVIVAL;
		} catch (UnsupportedOperationException ex) {
			Player resolved = resolveOnlinePlayer(player);
			return resolved != null && resolved.getGameMode() == GameMode.SURVIVAL;
		}
	}

	private static boolean isTemporaryPlayer(PacketEvent event) {
		try {
			Method method = event.getClass().getMethod("isPlayerTemporary");
			Object value = method.invoke(event);
			if (value instanceof Boolean) {
				return (Boolean) value;
			}
		} catch (Exception ignored) {
		}
		return false;
	}

	private static Player resolveOnlinePlayer(Player player) {
		try {
			UUID uuid = player.getUniqueId();
			if (uuid != null) {
				Player online = Bukkit.getPlayer(uuid);
				if (online != null && online.isOnline()) {
					return online;
				}
			}
		} catch (Exception ignored) {
		}

		try {
			String name = player.getName();
			if (name != null && !name.isEmpty()) {
				Player online = Bukkit.getPlayerExact(name);
				if (online != null && online.isOnline()) {
					return online;
				}
			}
		} catch (Exception ignored) {
		}

		return null;
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
