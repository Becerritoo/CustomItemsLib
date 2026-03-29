package one.lindegaard.CustomItemsLib.rewards;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import one.lindegaard.CustomItemsLib.Core;
import one.lindegaard.CustomItemsLib.Strings;
import one.lindegaard.CustomItemsLib.Tools;

public class Reward {

	public static final String MH_REWARD_DATA_NEW = "MH:HiddenRewardDataNew";

	private static final int TOKEN_VERSION = 2;
	private static final String LEGACY_0 = "Hidden(0):";
	private static final String LEGACY_1 = "Hidden(1):";
	private static final String LEGACY_2 = "Hidden(2):";
	private static final String LEGACY_4 = "Hidden(4):";
	private static final String LEGACY_5 = "Hidden(5):";

	private static final String PDC_VERSION = "reward_version";
	private static final String PDC_TYPE = "reward_type";
	private static final String PDC_VALUE = "reward_value_per_unit";
	private static final String PDC_TOKEN = "reward_token_uuid";
	private static final String PDC_SIG = "reward_signature";
	private static final String PDC_KEY_ID = "reward_key_id";
	private static final String PDC_DISPLAY = "reward_display_name";
	private static final String PDC_SKIN_UUID = "reward_skin_uuid";

	private String displayname = "";
	private double money = 0;
	private RewardType rewardType = null;
	private UUID skinUUID;
	private String encodedHash;
	private int id;

	private String tokenUuid;
	private String keyId;
	private String signature;

	public Reward() {
		this.displayname = "Skull";
		this.money = 0;
		this.rewardType = RewardType.BAGOFGOLD;
		this.skinUUID = UUID.fromString(RewardType.BAGOFGOLD.getUUID());
		rotateTokenAndSign();
		this.id = 0;
	}

	public Reward(Reward reward) {
		this.displayname = reward.getDisplayName();
		this.money = reward.getMoney();
		this.rewardType = reward.getRewardType();
		this.skinUUID = reward.getSkinUUID();
		this.encodedHash = reward.getEncodedHash();
		this.id = reward.getUniqueID();
		this.tokenUuid = reward.getTokenUUID();
		this.keyId = reward.getKeyId();
		this.signature = reward.getSignature();
	}

	public Reward(String displayName, double money, RewardType rewardType, UUID skinUUID) {
		this.displayname = displayName;
		this.money = money;
		this.rewardType = rewardType == null ? RewardType.BAGOFGOLD : rewardType;
		this.skinUUID = skinUUID;
		rotateTokenAndSign();
	}

	public Reward(List<String> lore) {
		setReward(lore);
	}

	public int getUniqueID() {
		return id;
	}

	public void setUniqueID(int id) {
		this.id = id;
	}

	private static NamespacedKey pdcKey(String path) {
		if (Core.getInstance() == null) {
			return null;
		}
		return new NamespacedKey(Core.getInstance(), path);
	}

	private String canonicalPayload() {
		String safeDisplay = displayname == null ? "" : displayname;
		String safeSkin = skinUUID == null ? "" : skinUUID.toString();
		String safeType = rewardType == null ? RewardType.BAGOFGOLD.getType() : rewardType.getType();
		String displayEncoded = Base64.getEncoder().encodeToString(safeDisplay.getBytes(StandardCharsets.UTF_8));
		return "v=" + TOKEN_VERSION + "|type=" + safeType + "|value="
				+ String.format(Locale.ENGLISH, "%.5f", money) + "|token=" + (tokenUuid == null ? "" : tokenUuid)
				+ "|skin=" + safeSkin + "|display=" + displayEncoded + "|key=" + (keyId == null ? "" : keyId);
	}

	private String makeDecodedHashOld() {
		return String.format(Locale.ENGLISH, "%.5f", money)
				+ (rewardType == null ? RewardType.BAGOFGOLD.getUUID() : rewardType.getUUID());
	}

	private String makeDecodedHash() {
		return String.format(Locale.ENGLISH, "%.5f", money)
				+ (rewardType == null ? RewardType.BAGOFGOLD.getType() : rewardType.getType());
	}

	private void ensureType() {
		if (rewardType == null) {
			rewardType = RewardType.BAGOFGOLD;
		}
	}

	private void ensureKeyId() {
		if (keyId != null && !keyId.isEmpty()) {
			return;
		}
		if (Core.getRewardSecurity() != null && Core.getRewardSecurity().isLoaded()) {
			keyId = Core.getRewardSecurity().getActiveKeyId();
		} else {
			keyId = "k1";
		}
	}

	private void ensureTokenUuid() {
		if (tokenUuid == null || tokenUuid.isEmpty()) {
			tokenUuid = UUID.randomUUID().toString();
		}
	}

	private void updateSignature() {
		ensureType();
		ensureTokenUuid();
		ensureKeyId();
		if (Core.getRewardSecurity() != null && Core.getRewardSecurity().isLoaded()) {
			signature = Core.getRewardSecurity().sign(keyId, canonicalPayload());
			encodedHash = signature;
		} else {
			signature = "";
			encodedHash = Strings.encode(makeDecodedHash());
		}
	}

	public void rotateTokenAndSign() {
		tokenUuid = UUID.randomUUID().toString();
		updateSignature();
	}

	public boolean checkHash() {
		if (tokenUuid != null && !tokenUuid.isEmpty() && signature != null && !signature.isEmpty()) {
			if (Core.getRewardSecurity() == null || !Core.getRewardSecurity().isLoaded()) {
				return false;
			}
			return Core.getRewardSecurity().verify(keyId, canonicalPayload(), signature);
		}
		if (this.encodedHash != null) {
			return makeDecodedHash().equals(Strings.decode(this.encodedHash))
					|| makeDecodedHashOld().equals(Strings.decode(this.encodedHash));
		}
		return true;
	}

	public void updateEncodedHash() {
		updateSignature();
	}

	private static RewardType parseRewardType(String rewardTypeStr) {
		if (rewardTypeStr == null || rewardTypeStr.isEmpty()) {
			return RewardType.BAGOFGOLD;
		}
		try {
			return RewardType.valueOf(rewardTypeStr);
		} catch (Exception ignored) {
		}

		if (RewardType.BAGOFGOLD.getUUID().equals(rewardTypeStr))
			return RewardType.BAGOFGOLD;
		if (RewardType.ITEM.getUUID().equals(rewardTypeStr))
			return RewardType.ITEM;
		if (RewardType.KILLED.getUUID().equals(rewardTypeStr))
			return RewardType.KILLED;
		if (RewardType.KILLER.getUUID().equals(rewardTypeStr))
			return RewardType.KILLER;

		return RewardType.BAGOFGOLD;
	}

	public void setReward(List<String> lore) {
		String moneyStr = "", rewardTypeStr = "";
		tokenUuid = null;
		keyId = null;
		signature = null;
		encodedHash = null;
		if (lore == null) {
			rewardType = RewardType.BAGOFGOLD;
			money = 0;
			displayname = Core.getConfigManager() != null ? Core.getConfigManager().bagOfGoldName : "BagOfGold";
			rotateTokenAndSign();
			return;
		}

		for (int n = 0; n < lore.size(); n++) {
			String str = lore.get(n);

			if (str.startsWith(LEGACY_0)) {
				this.displayname = str.substring(LEGACY_0.length());
			} else if (str.startsWith(LEGACY_1)) {
				moneyStr = str.substring(LEGACY_1.length());
				this.money = Double.valueOf(moneyStr);
			} else if (str.startsWith(LEGACY_2)) {
				rewardTypeStr = str.substring(LEGACY_2.length());
				this.rewardType = parseRewardType(rewardTypeStr);
			} else if (str.startsWith(LEGACY_4)) {
				this.skinUUID = (str.length() > LEGACY_4.length()) ? UUID.fromString(str.substring(LEGACY_4.length()))
						: null;
			} else if (str.startsWith(LEGACY_5)) {
				this.encodedHash = str.substring(LEGACY_5.length());
				String compareHash = Strings.encode(moneyStr + rewardTypeStr);
				if (!encodedHash.equalsIgnoreCase(compareHash)) {
					Bukkit.getConsoleSender().sendMessage(Core.PREFIX + ChatColor.RED
							+ "[Warning] A player has tried to change the value of a BagOfGold Item. Value set to 0!");
					money = 0;
					encodedHash = Strings.encode(makeDecodedHash());
				}
			}
		}
		if (displayname == null || displayname.isEmpty()) {
			displayname = Core.getConfigManager() != null ? Core.getConfigManager().bagOfGoldName : "BagOfGold";
		}
		ensureType();
		rotateTokenAndSign();
	}

	public ArrayList<String> getHiddenLore() {
		ArrayList<String> lores = new ArrayList<String>();
		if (rewardType != RewardType.BAGOFGOLD)
			lores.add(Core.getMessages().getString("core.reward.lore"));
		return lores;
	}

	public String getDisplayName() {
		return displayname;
	}

	public double getMoney() {
		return money;
	}

	public RewardType getRewardType() {
		return rewardType;
	}

	public String getTokenUUID() {
		return tokenUuid;
	}

	public String getKeyId() {
		return keyId;
	}

	public String getSignature() {
		return signature;
	}

	public void setDisplayname(String displayName) {
		this.displayname = displayName;
		updateSignature();
	}

	public void setMoney(double money) {
		this.money = money;
		updateSignature();
	}

	public void setRewardType(RewardType rewardType) {
		this.rewardType = rewardType;
		updateSignature();
	}

	public UUID getSkinUUID() {
		return skinUUID;
	}

	public void setSkinUUID(UUID skinUUID) {
		this.skinUUID = skinUUID;
		updateSignature();
	}

	public String getEncodedHash() {
		return encodedHash;
	}

	public void setHash(String hash) {
		this.encodedHash = hash;
		this.signature = hash;
	}

	public String toString() {
		return "{Description=" + displayname + ", money=" + String.format(Locale.ENGLISH, "%.5f", money) + ", type="
				+ rewardType.getType() + ", Skin=" + skinUUID + ", token=" + tokenUuid + ", id=" + id + "}";
	}

	public boolean equals(Reward reward) {
		if (reward == null)
			return false;
		if (skinUUID == null && reward.getSkinUUID() != null)
			return false;
		if (skinUUID != null && !skinUUID.equals(reward.getSkinUUID()))
			return false;
		return displayname.equalsIgnoreCase(reward.getDisplayName()) && money == reward.money
				&& rewardType == reward.getRewardType() && checkHash();
	}

	public void save(ConfigurationSection section) {
		section.set("displayname", displayname);
		section.set("money", String.format(Locale.ENGLISH, "%.5f", money));
		section.set("type", rewardType.getType());
		section.set("skinuuid", skinUUID == null ? "" : skinUUID.toString());
		section.set("hash", encodedHash == null ? "" : Strings.decode(encodedHash));
		section.set("token_uuid", tokenUuid == null ? "" : tokenUuid);
		section.set("token_key_id", keyId == null ? "" : keyId);
		section.set("token_sig", signature == null ? "" : signature);
	}

	public void read(ConfigurationSection section) throws InvalidConfigurationException {
		if (section.contains("displayname"))
			displayname = section.getString("displayname");
		else
			displayname = section.getString("description");
		money = Double.valueOf(section.getString("money").replace(",", "."));
		if (section.contains("type"))
			rewardType = parseRewardType(section.getString("type"));
		else {
			String uuid = section.getString("uuid");
			rewardType = parseRewardType(uuid);
		}

		skinUUID = null;
		String skin = section.getString("skinuuid", "");
		if (skin != null && !skin.isEmpty()) {
			skinUUID = UUID.fromString(skin);
		}

		tokenUuid = section.getString("token_uuid", "");
		keyId = section.getString("token_key_id", "");
		signature = section.getString("token_sig", "");
		encodedHash = Strings.encode(section.getString("hash", makeDecodedHash()));
		if (tokenUuid == null || tokenUuid.isEmpty() || keyId == null || keyId.isEmpty() || signature == null
				|| signature.isEmpty()) {
			rotateTokenAndSign();
		}
	}

	public boolean isMoney() {
		return isBagOfGoldReward() || isItemReward();
	}

	public boolean isBagOfGoldReward() {
		return rewardType == RewardType.BAGOFGOLD;
	}

	public boolean isKilledHeadReward() {
		return rewardType == RewardType.KILLED;
	}

	public boolean isKillerHeadReward() {
		return rewardType == RewardType.KILLER;
	}

	public boolean isItemReward() {
		return rewardType == RewardType.ITEM;
	}

	public static boolean isReward(Item item) {
		return item != null && (item.hasMetadata(MH_REWARD_DATA_NEW) || isReward(item.getItemStack()));
	}

	public static Reward getReward(Item item) {
		if (item != null && item.hasMetadata(MH_REWARD_DATA_NEW)) {
			for (MetadataValue mv : item.getMetadata(MH_REWARD_DATA_NEW)) {
				if (mv.value() instanceof Reward)
					return (Reward) mv.value();
			}
		}
		return item == null ? null : getReward(item.getItemStack());
	}

	private static boolean hasPdcReward(ItemMeta itemMeta) {
		if (itemMeta == null)
			return false;
		NamespacedKey versionKey = pdcKey(PDC_VERSION);
		NamespacedKey typeKey = pdcKey(PDC_TYPE);
		NamespacedKey valueKey = pdcKey(PDC_VALUE);
		NamespacedKey tokenKey = pdcKey(PDC_TOKEN);
		NamespacedKey sigKey = pdcKey(PDC_SIG);
		NamespacedKey keyIdKey = pdcKey(PDC_KEY_ID);
		if (versionKey == null || typeKey == null || valueKey == null || tokenKey == null || sigKey == null
				|| keyIdKey == null) {
			return false;
		}
		PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
		return pdc.has(versionKey, PersistentDataType.INTEGER) && pdc.has(typeKey, PersistentDataType.STRING)
				&& pdc.has(valueKey, PersistentDataType.DOUBLE) && pdc.has(tokenKey, PersistentDataType.STRING)
				&& pdc.has(sigKey, PersistentDataType.STRING) && pdc.has(keyIdKey, PersistentDataType.STRING);
	}

	public static boolean isReward(ItemStack itemStack) {
		if (itemStack == null || !itemStack.hasItemMeta())
			return false;
		return hasPdcReward(itemStack.getItemMeta());
	}

	public static boolean isLegacyReward(ItemStack itemStack) {
		if (itemStack == null || !itemStack.hasItemMeta())
			return false;
		ItemMeta meta = itemStack.getItemMeta();
		if (!meta.hasLore() || meta.getLore() == null || meta.getLore().size() < 3)
			return false;
		String lore = meta.getLore().get(2);
		if (!lore.startsWith(LEGACY_2))
			return false;
		String type = lore.substring(LEGACY_2.length());
		return type.equals(RewardType.BAGOFGOLD.getType()) || type.equals(RewardType.KILLED.getType())
				|| type.equals(RewardType.KILLER.getType()) || type.equals(RewardType.ITEM.getType())
				|| type.equals(RewardType.BAGOFGOLD.getUUID()) || type.equals(RewardType.KILLED.getUUID())
				|| type.equals(RewardType.KILLER.getUUID()) || type.equals(RewardType.ITEM.getUUID());
	}

	private static Reward fromPdc(ItemStack itemStack) {
		if (itemStack == null || !itemStack.hasItemMeta())
			return null;
		ItemMeta itemMeta = itemStack.getItemMeta();
		if (!hasPdcReward(itemMeta))
			return null;

		PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
		Reward reward = new Reward();
		reward.displayname = pdc.getOrDefault(pdcKey(PDC_DISPLAY), PersistentDataType.STRING,
				(itemMeta.hasDisplayName() ? ChatColor.stripColor(itemMeta.getDisplayName()) : "BagOfGold"));
		reward.money = pdc.getOrDefault(pdcKey(PDC_VALUE), PersistentDataType.DOUBLE, 0D);
		reward.rewardType = parseRewardType(pdc.getOrDefault(pdcKey(PDC_TYPE), PersistentDataType.STRING,
				RewardType.BAGOFGOLD.getType()));
		String skin = pdc.getOrDefault(pdcKey(PDC_SKIN_UUID), PersistentDataType.STRING, "");
		reward.skinUUID = skin.isEmpty() ? null : UUID.fromString(skin);
		reward.tokenUuid = pdc.getOrDefault(pdcKey(PDC_TOKEN), PersistentDataType.STRING, "");
		reward.keyId = pdc.getOrDefault(pdcKey(PDC_KEY_ID), PersistentDataType.STRING, "");
		reward.signature = pdc.getOrDefault(pdcKey(PDC_SIG), PersistentDataType.STRING, "");
		reward.encodedHash = reward.signature;
		return reward;
	}

	public static Reward getReward(ItemStack itemStack) {
		return fromPdc(itemStack);
	}

	public static Reward getLegacyReward(ItemStack itemStack) {
		if (!isLegacyReward(itemStack))
			return null;
		return new Reward(itemStack.getItemMeta().getLore());
	}

	public static ItemStack migrateLegacyReward(ItemStack itemStack) {
		Reward legacy = getLegacyReward(itemStack);
		if (legacy == null)
			return itemStack;
		return setDisplayNameAndHiddenLores(itemStack, legacy);
	}

	public static boolean isReward(Block block) {
		return block != null && block.hasMetadata(MH_REWARD_DATA_NEW);
	}

	public static Reward getReward(Block block) {
		return (Reward) block.getMetadata(MH_REWARD_DATA_NEW).get(0).value();
	}

	public static boolean isReward(Entity entity) {
		return entity != null && entity.hasMetadata(MH_REWARD_DATA_NEW);
	}

	public static Reward getReward(Entity entity) {
		return (Reward) entity.getMetadata(MH_REWARD_DATA_NEW).get(0).value();
	}

	private static void writeRewardPdc(ItemMeta itemMeta, Reward reward) {
		PersistentDataContainer pdc = itemMeta.getPersistentDataContainer();
		pdc.set(pdcKey(PDC_VERSION), PersistentDataType.INTEGER, TOKEN_VERSION);
		pdc.set(pdcKey(PDC_TYPE), PersistentDataType.STRING,
				reward.getRewardType() == null ? RewardType.BAGOFGOLD.getType() : reward.getRewardType().getType());
		pdc.set(pdcKey(PDC_VALUE), PersistentDataType.DOUBLE, reward.getMoney());
		pdc.set(pdcKey(PDC_TOKEN), PersistentDataType.STRING, reward.getTokenUUID());
		pdc.set(pdcKey(PDC_SIG), PersistentDataType.STRING, reward.getSignature());
		pdc.set(pdcKey(PDC_KEY_ID), PersistentDataType.STRING, reward.getKeyId());
		pdc.set(pdcKey(PDC_DISPLAY), PersistentDataType.STRING,
				reward.getDisplayName() == null ? "" : reward.getDisplayName());
		pdc.set(pdcKey(PDC_SKIN_UUID), PersistentDataType.STRING,
				reward.getSkinUUID() == null ? "" : reward.getSkinUUID().toString());
	}

	/**
	 * setDisplayNameAndHiddenLores: add the Display name and secure metadata.
	 */
	public static ItemStack setDisplayNameAndHiddenLores(ItemStack skull, Reward reward) {
		ItemMeta skullMeta = skull.getItemMeta();
		if (skullMeta == null) {
			return skull;
		}

		reward.rotateTokenAndSign();
		skullMeta.setLore(null);

		if (reward.getRewardType() == RewardType.BAGOFGOLD) {
			skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
					Core.getConfigManager().bagOfGoldDisplayNameFormat.replace("{name}", reward.getDisplayName())
							.replace("{value}", Tools.format(reward.getMoney()))));

		} else if (reward.getRewardType() == RewardType.ITEM)
			if (reward.getMoney() == 0)
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().itemDisplayNameFormatNoValue.replace("{name}", reward.getDisplayName())));
			else
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().itemDisplayNameFormat.replace("{name}", reward.getDisplayName())
								.replace("{value}", Tools.format(reward.getMoney()))));

		else if (reward.getRewardType() == RewardType.KILLED)
			if (reward.getMoney() == 0)
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().killedHeadDisplayNameFormatNoValue.replace("{name}",
								reward.getDisplayName())));
			else
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().killedHeadDisplayNameFormat.replace("{name}", reward.getDisplayName())
								.replace("{value}", Tools.format(reward.getMoney()))));

		else if (reward.getRewardType() == RewardType.KILLER)
			if (reward.getMoney() == 0)
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().killerHeadDisplayNameFormatNoValue.replace("{name}",
								reward.getDisplayName())));
			else
				skullMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
						Core.getConfigManager().killerHeadDisplayNameFormat.replace("{name}", reward.getDisplayName())
								.replace("{value}", Tools.format(reward.getMoney()))));

		writeRewardPdc(skullMeta, reward);
		skull.setItemMeta(skullMeta);
		return skull;
	}

	public static ItemStack setDisplayNameAndHiddenLores(ItemStack skull, String name, double value,
			List<String> lores) {
		RewardType rewardType = RewardType.BAGOFGOLD;
		UUID skinUuid = null;
		List<String> visibleLore = new ArrayList<String>();
		if (lores != null) {
			for (String line : lores) {
				if (line == null)
					continue;
				if (line.startsWith(LEGACY_2)) {
					rewardType = parseRewardType(line.substring(LEGACY_2.length()));
				} else if (line.startsWith(LEGACY_4)) {
					String raw = line.substring(LEGACY_4.length());
					if (!raw.isEmpty()) {
						skinUuid = UUID.fromString(raw);
					}
				} else if (!line.trim().startsWith("Hidden(")) {
					visibleLore.add(line);
				}
			}
		}

		Reward reward = new Reward(name, value, rewardType, skinUuid);
		ItemStack updated = setDisplayNameAndHiddenLores(skull, reward);
		if (!visibleLore.isEmpty()) {
			ItemMeta skullMeta = updated.getItemMeta();
			if (skullMeta != null) {
				skullMeta.setLore(visibleLore);
				updated.setItemMeta(skullMeta);
			}
		}
		return updated;
	}

	public static boolean isFakeReward(Item item) {
		ItemStack itemStack = item == null ? null : item.getItemStack();
		return isFakeReward(itemStack);
	}

	public static boolean isFakeReward(ItemStack itemStack) {
		if (itemStack != null && itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()
				&& itemStack.getItemMeta().getDisplayName().contains(Core.getConfigManager().bagOfGoldName)) {
			return !isReward(itemStack);
		}
		return false;
	}

	public static boolean isHead(ItemStack itemStack) {
		if (!isReward(itemStack)) {
			return false;
		}
		Reward reward = getReward(itemStack);
		if (reward == null) {
			return false;
		}
		return reward.isKilledHeadReward() || reward.isKillerHeadReward();
	}
}
