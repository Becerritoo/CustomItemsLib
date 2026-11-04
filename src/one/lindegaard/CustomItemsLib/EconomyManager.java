package one.lindegaard.CustomItemsLib;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.milkbowl.vault.economy.Economy;

/**
 * Economy handler for Vault-compatible providers.
 *
 * @author Rocologo
 */
public class EconomyManager {

	private final Core plugin;
	private Economy vaultEconomy;
	private EcoType type = EcoType.NONE;
	private String version = "";

	public enum EcoType {
		NONE, VAULT
	}

	public EconomyManager(Core plugin) {
		this.plugin = plugin;
		setupEconomy();
	}

	public EcoType getType() {
		return type;
	}

	public boolean isActive() {
		return type == EcoType.VAULT && vaultEconomy != null;
	}

	public String getVersion() {
		return version;
	}

	private RegisteredServiceProvider<Economy> getPreferredVaultProvider() {
		Collection<RegisteredServiceProvider<Economy>> providers = plugin.getServer().getServicesManager()
				.getRegistrations(Economy.class);

		RegisteredServiceProvider<Economy> bagOfGoldProvider = null;
		for (RegisteredServiceProvider<Economy> provider : providers) {
			if (provider == null || provider.getProvider() == null)
				continue;
			if ("BagOfGold".equalsIgnoreCase(provider.getProvider().getName())) {
				if (bagOfGoldProvider == null
						|| provider.getPriority().compareTo(bagOfGoldProvider.getPriority()) > 0)
					bagOfGoldProvider = provider;
			}
		}

		return bagOfGoldProvider != null ? bagOfGoldProvider
				: plugin.getServer().getServicesManager().getRegistration(Economy.class);
	}

	/**
	 * Finds the preferred Vault-compatible economy provider.
	 *
	 * @return true when an economy provider is available
	 */
	public Boolean setupEconomy() {
		EcoType previousType = type;
		String previousVersion = version;

		try {
			RegisteredServiceProvider<Economy> provider = getPreferredVaultProvider();
			if (provider != null && provider.getProvider() != null) {
				vaultEconomy = provider.getProvider();
				version = String.format("%s via Vault", vaultEconomy.getName());
				type = EcoType.VAULT;
				if (previousType != type || !previousVersion.equals(version))
					Bukkit.getConsoleSender().sendMessage(
							Core.PREFIX + "CustomItemsLib is using " + version + " as Economy Provider");
				return true;
			}
		} catch (NoClassDefFoundError ignored) {
			// Vault is an optional dependency.
		}

		vaultEconomy = null;
		version = "";
		type = EcoType.NONE;
		return false;
	}

	public String getName() {
		return isActive() ? vaultEconomy.getName() : "Vault was not found";
	}

	public String format(Double amount) {
		return isActive() ? vaultEconomy.format(amount) : "Vault was not found";
	}

	public boolean hasEconomyAccount(OfflinePlayer offlinePlayer) {
		return isActive() && vaultEconomy.hasAccount(offlinePlayer);
	}

	/**
	 * Attempts to zero the account because Vault has no account deletion method.
	 */
	public void removeAccount(OfflinePlayer offlinePlayer) {
		if (!isActive())
			return;
		try {
			if (!vaultEconomy.hasAccount(offlinePlayer))
				vaultEconomy.createPlayerAccount(offlinePlayer);
			vaultEconomy.withdrawPlayer(offlinePlayer, vaultEconomy.getBalance(offlinePlayer));
		} catch (NoClassDefFoundError ignored) {
			// The optional Vault bridge was removed while the server was running.
		}
	}

	public boolean hasMoney(OfflinePlayer offlinePlayer, double amount) {
		return isActive() && vaultEconomy.has(offlinePlayer, amount);
	}

	public double getBalance(OfflinePlayer offlinePlayer) {
		if (!isActive())
			return 0.0;
		ensureAccount(offlinePlayer);
		return vaultEconomy.getBalance(offlinePlayer);
	}

	public boolean hasEnough(OfflinePlayer offlinePlayer, Double amount) {
		return getBalance(offlinePlayer) >= amount;
	}

	public boolean withdrawPlayer(OfflinePlayer offlinePlayer, Double amount) {
		if (!isActive())
			return false;
		ensureAccount(offlinePlayer);
		return vaultEconomy.withdrawPlayer(offlinePlayer, amount).transactionSuccess();
	}

	public boolean depositPlayer(OfflinePlayer offlinePlayer, Double amount) {
		if (!isActive())
			return false;
		ensureAccount(offlinePlayer);
		return vaultEconomy.depositPlayer(offlinePlayer, amount).transactionSuccess();
	}

	public boolean setBalance(OfflinePlayer offlinePlayer, Double amount, World world) {
		if (!isActive())
			return false;
		ensureAccount(offlinePlayer);
		return vaultEconomy.depositPlayer(offlinePlayer, amount - vaultEconomy.getBalance(offlinePlayer))
				.transactionSuccess();
	}

	public String getFormattedBalance(double balance) {
		try {
			if (isActive())
				return vaultEconomy.format(balance);
		} catch (Exception ignored) {
		}
		return String.format("%.2f", balance);
	}

	public double getSpaceForMoney(OfflinePlayer offlinePlayer) {
		return getBalance(offlinePlayer);
	}

	public boolean isBankOwner(String account, OfflinePlayer offlinePlayer) {
		return isActive() && vaultEconomy.isBankOwner(account, offlinePlayer).transactionSuccess();
	}

	public double getBankBalance(OfflinePlayer offlinePlayer) {
		if (!isActive())
			return 0.0;
		ensureAccount(offlinePlayer);
		return vaultEconomy.bankBalance(offlinePlayer.getUniqueId().toString()).balance;
	}

	private void ensureAccount(OfflinePlayer offlinePlayer) {
		if (!vaultEconomy.hasAccount(offlinePlayer))
			vaultEconomy.createPlayerAccount(offlinePlayer);
	}
}
