package one.lindegaard.CustomItemsLib.rewards;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import one.lindegaard.CustomItemsLib.Core;

public class RewardSecurity {

	private static final int SECRET_BYTES = 32;
	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private final Core plugin;
	private final File file;

	private final Map<String, byte[]> keys = new HashMap<>();
	private String activeKeyId = "k1";
	private boolean loaded = false;

	public RewardSecurity(Core plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "security.yml");
	}

	public synchronized void initialize() throws IOException {
		if (!file.exists()) {
			createDefaultConfig();
		}

		YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
		activeKeyId = config.getString("hmac.active-key-id", "k1");

		keys.clear();
		ConfigurationSection sec = config.getConfigurationSection("hmac.keys");
		if (sec != null) {
			Set<String> ids = sec.getKeys(false);
			for (String id : ids) {
				String encoded = sec.getString(id, "").trim();
				if (!encoded.isEmpty()) {
					keys.put(id, Base64.getDecoder().decode(encoded));
				}
			}
		}

		if (keys.isEmpty()) {
			createDefaultConfig();
			config = YamlConfiguration.loadConfiguration(file);
			activeKeyId = config.getString("hmac.active-key-id", "k1");
			sec = config.getConfigurationSection("hmac.keys");
			if (sec != null) {
				for (String id : sec.getKeys(false)) {
					String encoded = sec.getString(id, "").trim();
					if (!encoded.isEmpty()) {
						keys.put(id, Base64.getDecoder().decode(encoded));
					}
				}
			}
		}

		if (!keys.containsKey(activeKeyId)) {
			byte[] randomSecret = randomSecret();
			keys.put(activeKeyId, randomSecret);
			config.set("hmac.keys." + activeKeyId, Base64.getEncoder().encodeToString(randomSecret));
			config.save(file);
		}

		loaded = true;
	}

	private void createDefaultConfig() throws IOException {
		YamlConfiguration config = new YamlConfiguration();
		config.set("security-format-version", 1);
		config.set("hmac.active-key-id", "k1");
		config.set("hmac.accept-legacy-keys", true);
		config.set("hmac.keys.k1", Base64.getEncoder().encodeToString(randomSecret()));
		config.save(file);
	}

	private byte[] randomSecret() {
		SecureRandom secureRandom = new SecureRandom();
		byte[] bytes = new byte[SECRET_BYTES];
		secureRandom.nextBytes(bytes);
		return bytes;
	}

	public synchronized boolean isLoaded() {
		return loaded;
	}

	public synchronized String getActiveKeyId() {
		return activeKeyId;
	}

	public synchronized String sign(String keyId, String payload) {
		if (!loaded || payload == null || keyId == null) {
			return "";
		}
		byte[] key = keys.get(keyId);
		if (key == null) {
			return "";
		}

		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
			byte[] signed = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(signed);
		} catch (GeneralSecurityException ex) {
			return "";
		}
	}

	public synchronized boolean verify(String keyId, String payload, String signature) {
		if (!loaded || keyId == null || payload == null || signature == null || signature.isEmpty()) {
			return false;
		}

		String expected = sign(keyId, payload);
		if (expected.isEmpty()) {
			return false;
		}

		return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
