package one.lindegaard.CustomItemsLib.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import one.lindegaard.CustomItemsLib.Core;
import one.lindegaard.CustomItemsLib.rewards.RewardSecurity;

/**
 * Signed, non-monetary metadata for consumer-owned item templates.
 */
public final class SignedItemData {

	private static final int FORMAT_VERSION = 1;
	private static final int MAX_PAYLOAD_LENGTH = 4096;
	private static final Pattern PURPOSE_PATTERN = Pattern.compile("[a-z0-9._-]{1,64}");

	private static final String KEY_VERSION = "signed_item_version";
	private static final String KEY_PURPOSE = "signed_item_purpose";
	private static final String KEY_PAYLOAD = "signed_item_payload";
	private static final String KEY_TOKEN = "signed_item_token";
	private static final String KEY_KEY_ID = "signed_item_key_id";
	private static final String KEY_SIGNATURE = "signed_item_signature";

	private final String purpose;
	private final String payload;
	private final String token;
	private final String keyId;

	private SignedItemData(String purpose, String payload, String token, String keyId) {
		this.purpose = purpose;
		this.payload = payload;
		this.token = token;
		this.keyId = keyId;
	}

	public String getPurpose() {
		return purpose;
	}

	public String getPayload() {
		return payload;
	}

	public String getToken() {
		return token;
	}

	public String getKeyId() {
		return keyId;
	}

	/**
	 * Returns a clone with signed metadata attached.
	 */
	public static ItemStack sign(ItemStack source, String purpose, String payload) {
		validateInput(source, purpose, payload);
		RewardSecurity security = requireSecurity();

		ItemStack item = source.clone();
		ItemMeta meta = item.getItemMeta();
		if (meta == null)
			throw new IllegalArgumentException("Item does not support metadata");

		String token = UUID.randomUUID().toString();
		String keyId = security.getActiveKeyId();
		String signature = security.sign(keyId, canonical(item, purpose, payload, token));
		if (signature == null || signature.isEmpty())
			throw new IllegalStateException("Could not sign item metadata");

		PersistentDataContainer data = meta.getPersistentDataContainer();
		data.set(key(KEY_VERSION), PersistentDataType.INTEGER, FORMAT_VERSION);
		data.set(key(KEY_PURPOSE), PersistentDataType.STRING, purpose);
		data.set(key(KEY_PAYLOAD), PersistentDataType.STRING, payload);
		data.set(key(KEY_TOKEN), PersistentDataType.STRING, token);
		data.set(key(KEY_KEY_ID), PersistentDataType.STRING, keyId);
		data.set(key(KEY_SIGNATURE), PersistentDataType.STRING, signature);
		item.setItemMeta(meta);
		return item;
	}

	/**
	 * Reads metadata only when every field and its signature are valid.
	 */
	public static SignedItemData readVerified(ItemStack item) {
		if (item == null || !item.hasItemMeta())
			return null;
		ItemMeta meta = item.getItemMeta();
		PersistentDataContainer data = meta.getPersistentDataContainer();
		Integer version = data.get(key(KEY_VERSION), PersistentDataType.INTEGER);
		String purpose = data.get(key(KEY_PURPOSE), PersistentDataType.STRING);
		String payload = data.get(key(KEY_PAYLOAD), PersistentDataType.STRING);
		String token = data.get(key(KEY_TOKEN), PersistentDataType.STRING);
		String keyId = data.get(key(KEY_KEY_ID), PersistentDataType.STRING);
		String signature = data.get(key(KEY_SIGNATURE), PersistentDataType.STRING);
		if (version == null || version != FORMAT_VERSION || purpose == null || payload == null || token == null
				|| keyId == null || signature == null || !PURPOSE_PATTERN.matcher(purpose).matches()
				|| payload.length() > MAX_PAYLOAD_LENGTH)
			return null;
		try {
			UUID.fromString(token);
		} catch (IllegalArgumentException exception) {
			return null;
		}

		RewardSecurity security = requireSecurity();
		if (!security.verify(keyId, canonical(item, purpose, payload, token), signature))
			return null;
		return new SignedItemData(purpose, payload, token, keyId);
	}

	public static SignedItemData readVerified(ItemStack item, String expectedPurpose) {
		SignedItemData data = readVerified(item);
		return data != null && data.purpose.equals(expectedPurpose) ? data : null;
	}

	private static void validateInput(ItemStack item, String purpose, String payload) {
		if (item == null || item.getType().isAir())
			throw new IllegalArgumentException("Item is missing");
		if (purpose == null || !PURPOSE_PATTERN.matcher(purpose).matches())
			throw new IllegalArgumentException("Invalid signed item purpose");
		if (payload == null || payload.length() > MAX_PAYLOAD_LENGTH)
			throw new IllegalArgumentException("Invalid signed item payload");
	}

	private static RewardSecurity requireSecurity() {
		RewardSecurity security = Core.getRewardSecurity();
		if (security == null || !security.isLoaded())
			throw new IllegalStateException("CustomItemsLib security is not available");
		return security;
	}

	private static String canonical(ItemStack item, String purpose, String payload, String token) {
		Base64.Encoder encoder = Base64.getEncoder();
		return "signed-item-v" + FORMAT_VERSION + "\n"
				+ item.getType().getKey() + "\n"
				+ encoder.encodeToString(purpose.getBytes(StandardCharsets.UTF_8)) + "\n"
				+ encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "\n"
				+ token;
	}

	private static NamespacedKey key(String name) {
		Core core = Core.getInstance();
		if (core == null)
			throw new IllegalStateException("CustomItemsLib is not enabled");
		return new NamespacedKey(core, name);
	}
}
