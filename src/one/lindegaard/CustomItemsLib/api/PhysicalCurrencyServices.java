package one.lindegaard.CustomItemsLib.api;

import org.bukkit.Bukkit;

/**
 * Looks up the active physical-currency provider without coupling the library to
 * a consumer plugin.
 */
public final class PhysicalCurrencyServices {

	private PhysicalCurrencyServices() {
	}

	/**
	 * Gets the currently registered provider.
	 *
	 * @return active provider, or {@code null} when physical-currency accounting is
	 *         not installed
	 */
	public static PhysicalCurrencyService get() {
		return Bukkit.getServicesManager().load(PhysicalCurrencyService.class);
	}

	public static boolean isAvailable() {
		return get() != null;
	}
}
