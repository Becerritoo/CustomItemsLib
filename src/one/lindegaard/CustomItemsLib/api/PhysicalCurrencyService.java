package one.lindegaard.CustomItemsLib.api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType.SlotType;

/**
 * Connects CustomItemsLib reward-item operations to a plugin that accounts for
 * physical currency.
 * <p>
 * Implementations are registered through Bukkit's {@code ServicesManager} by
 * the plugin that owns the balance and currency policy. CustomItemsLib does not
 * require an implementation in order to start.
 */
public interface PhysicalCurrencyService {

	/**
	 * Adds physical currency to the tracked balance without creating reward items.
	 * The provider may apply its own debt or deposit policy.
	 *
	 * @param player account owner
	 * @param amount positive amount to add
	 */
	void addToBalance(OfflinePlayer player, double amount);

	/**
	 * Removes physical currency from the tracked balance without removing reward
	 * items.
	 *
	 * @param player account owner
	 * @param amount positive amount to remove
	 */
	void removeFromBalance(OfflinePlayer player, double amount);

	/**
	 * Records currency that CustomItemsLib has already placed in a player's
	 * inventory. Implementations must not create or remove inventory items here.
	 *
	 * @param player account owner
	 * @param amount amount successfully placed in the inventory
	 */
	void recordInventoryCredit(Player player, double amount);

	/**
	 * Allows a provider integration to opt into reward slots that CustomItemsLib
	 * does not normally use, such as merchant result or crafting slots.
	 *
	 * @param slotType slot being considered
	 * @return true when the provider supports reward items in the slot
	 */
	default boolean isAdditionalRewardSlotAllowed(SlotType slotType) {
		return false;
	}
}
