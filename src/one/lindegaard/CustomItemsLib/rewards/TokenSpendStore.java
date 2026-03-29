package one.lindegaard.CustomItemsLib.rewards;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import com.mysql.cj.jdbc.MysqlDataSource;

import one.lindegaard.CustomItemsLib.Core;

public class TokenSpendStore {

	public enum MarkResult {
		MARKED,
		DUPLICATE,
		ERROR
	}

	private final Core plugin;

	public TokenSpendStore(Core plugin) {
		this.plugin = plugin;
	}

	public void initialize() throws Exception {
		try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate("CREATE TABLE IF NOT EXISTS mh_spent_tokens ("
					+ " token_uuid VARCHAR(64) NOT NULL PRIMARY KEY,"
					+ " player_uuid VARCHAR(36),"
					+ " source VARCHAR(64),"
					+ " amount DOUBLE,"
					+ " spent_at BIGINT NOT NULL"
					+ ")");
		}
	}

	public MarkResult markTokenSpent(String tokenUuid, UUID playerUuid, String source, double amount) {
		if (tokenUuid == null || tokenUuid.isEmpty()) {
			return MarkResult.ERROR;
		}

		String sql;
		boolean mysql = isMySQL();
		if (mysql) {
			sql = "INSERT IGNORE INTO mh_spent_tokens(token_uuid, player_uuid, source, amount, spent_at) VALUES(?,?,?,?,?)";
		} else {
			sql = "INSERT OR IGNORE INTO mh_spent_tokens(token_uuid, player_uuid, source, amount, spent_at) VALUES(?,?,?,?,?)";
		}

		try (Connection connection = openConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, tokenUuid);
			statement.setString(2, playerUuid == null ? null : playerUuid.toString());
			statement.setString(3, source == null ? "unknown" : source);
			statement.setDouble(4, amount);
			statement.setLong(5, System.currentTimeMillis());

			int updated = statement.executeUpdate();
			return updated > 0 ? MarkResult.MARKED : MarkResult.DUPLICATE;
		} catch (SQLException ex) {
			if (mysql && isDuplicateKey(ex)) {
				return MarkResult.DUPLICATE;
			}
			Core.getMessages().debug("TokenSpendStore markTokenSpent failed for %s: %s", tokenUuid, ex.getMessage());
			return MarkResult.ERROR;
		} catch (Exception ex) {
			Core.getMessages().debug("TokenSpendStore markTokenSpent failed for %s: %s", tokenUuid, ex.getMessage());
			return MarkResult.ERROR;
		}
	}

	public boolean isTokenSpent(String tokenUuid) {
		if (tokenUuid == null || tokenUuid.isEmpty()) {
			return false;
		}

		try (Connection connection = openConnection();
				PreparedStatement statement = connection
						.prepareStatement("SELECT 1 FROM mh_spent_tokens WHERE token_uuid=? LIMIT 1")) {
			statement.setString(1, tokenUuid);
			try (ResultSet rs = statement.executeQuery()) {
				return rs.next();
			}
		} catch (Exception ex) {
			Core.getMessages().debug("TokenSpendStore isTokenSpent failed for %s: %s", tokenUuid, ex.getMessage());
			return false;
		}
	}

	public boolean revokeToken(String tokenUuid, String source) {
		return markTokenSpent(tokenUuid, null, source == null ? "manual-revoke" : source, 0D) == MarkResult.MARKED;
	}

	public int countTokens() {
		try (Connection connection = openConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM mh_spent_tokens")) {
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} catch (Exception ex) {
			Core.getMessages().debug("TokenSpendStore countTokens failed: %s", ex.getMessage());
			return 0;
		}
	}

	private boolean isMySQL() {
		return Core.getConfigManager().databaseType != null
				&& Core.getConfigManager().databaseType.equalsIgnoreCase("mysql");
	}

	private boolean isDuplicateKey(SQLException ex) {
		return ex.getErrorCode() == 1062 || (ex.getSQLState() != null && ex.getSQLState().startsWith("23"));
	}

	private Connection openConnection() throws Exception {
		if (isMySQL()) {
			Class.forName("com.mysql.cj.jdbc.Driver");
			MysqlDataSource dataSource = new MysqlDataSource();
			dataSource.setUser(Core.getConfigManager().databaseUsername);
			dataSource.setPassword(Core.getConfigManager().databasePassword);
			if (Core.getConfigManager().databaseHost.contains(":")) {
				dataSource.setServerName(Core.getConfigManager().databaseHost.split(":")[0]);
				dataSource.setPort(Integer.parseInt(Core.getConfigManager().databaseHost.split(":")[1]));
			} else {
				dataSource.setServerName(Core.getConfigManager().databaseHost);
			}
			dataSource.setDatabaseName(Core.getConfigManager().databaseName);
			Connection connection = dataSource.getConnection();
			connection.setAutoCommit(true);
			return connection;
		}

		Connection connection = DriverManager
				.getConnection("jdbc:sqlite:" + plugin.getDataFolder().getPath() + "/" + Core.getConfigManager().databaseName
						+ ".db");
		connection.setAutoCommit(true);
		return connection;
	}
}
