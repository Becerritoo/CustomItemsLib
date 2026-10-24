package one.lindegaard.CustomItemsLib.rewards;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mysql.cj.jdbc.MysqlDataSource;

import one.lindegaard.CustomItemsLib.Core;

public class TokenSpendStore {

	public static final class TokenSpend {
		private final String tokenUuid;
		private final UUID playerUuid;
		private final String source;
		private final double amount;

		public TokenSpend(String tokenUuid, UUID playerUuid, String source, double amount) {
			this.tokenUuid = tokenUuid;
			this.playerUuid = playerUuid;
			this.source = source;
			this.amount = amount;
		}

		public String getTokenUuid() {
			return tokenUuid;
		}

		public UUID getPlayerUuid() {
			return playerUuid;
		}

		public String getSource() {
			return source;
		}

		public double getAmount() {
			return amount;
		}
	}

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
		return markTokensSpent(java.util.Collections.singletonList(
				new TokenSpend(tokenUuid, playerUuid, source, amount)));
	}

	/**
	 * Atomically claims every token in a physical-currency transaction. Either all
	 * token UUIDs are recorded, or none of them are. The method deliberately uses
	 * one connection and one transaction so a purchase never partially consumes a
	 * set of bags and does not open a database connection per bag.
	 */
	public MarkResult markTokensSpent(Collection<TokenSpend> tokenSpends) {
		if (tokenSpends == null || tokenSpends.isEmpty() || hasInvalidTokenIds(tokenSpends)) {
			return MarkResult.ERROR;
		}
		if (hasDuplicateTokenIds(tokenSpends))
			return MarkResult.DUPLICATE;

		String sql;
		boolean mysql = isMySQL();
		if (mysql) {
			sql = "INSERT IGNORE INTO mh_spent_tokens(token_uuid, player_uuid, source, amount, spent_at) VALUES(?,?,?,?,?)";
		} else {
			sql = "INSERT OR IGNORE INTO mh_spent_tokens(token_uuid, player_uuid, source, amount, spent_at) VALUES(?,?,?,?,?)";
		}

		try (Connection connection = openConnection()) {
			boolean previousAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql)) {
				long spentAt = System.currentTimeMillis();
				for (TokenSpend tokenSpend : tokenSpends) {
					statement.setString(1, tokenSpend.getTokenUuid());
					statement.setString(2, tokenSpend.getPlayerUuid() == null ? null
							: tokenSpend.getPlayerUuid().toString());
					statement.setString(3, tokenSpend.getSource() == null ? "unknown" : tokenSpend.getSource());
					statement.setDouble(4, tokenSpend.getAmount());
					statement.setLong(5, spentAt);
					if (statement.executeUpdate() != 1) {
						connection.rollback();
						connection.setAutoCommit(previousAutoCommit);
						return MarkResult.DUPLICATE;
					}
				}
				connection.commit();
				connection.setAutoCommit(previousAutoCommit);
				return MarkResult.MARKED;
			} catch (Exception ex) {
				connection.rollback();
				throw ex;
			}
		} catch (SQLException ex) {
			if (mysql && isDuplicateKey(ex)) {
				return MarkResult.DUPLICATE;
			}
			logFailure(tokenSpends, ex);
			return MarkResult.ERROR;
		} catch (Exception ex) {
			logFailure(tokenSpends, ex);
			return MarkResult.ERROR;
		}
	}

	static boolean hasInvalidTokenIds(Collection<TokenSpend> tokenSpends) {
		for (TokenSpend tokenSpend : tokenSpends) {
			if (tokenSpend == null || tokenSpend.getTokenUuid() == null || tokenSpend.getTokenUuid().isEmpty()) {
				return true;
			}
		}
		return false;
	}

	static boolean hasDuplicateTokenIds(Collection<TokenSpend> tokenSpends) {
		Set<String> tokenIds = new HashSet<String>();
		for (TokenSpend tokenSpend : tokenSpends) {
			if (!tokenIds.add(tokenSpend.getTokenUuid()))
				return true;
		}
		return false;
	}

	private void logFailure(Collection<TokenSpend> tokenSpends, Exception ex) {
		if (Core.getMessages() != null) {
			Core.getMessages().debug("TokenSpendStore could not atomically mark %s token(s): %s", tokenSpends.size(),
					ex.getMessage());
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

	protected boolean isMySQL() {
		return Core.getConfigManager().databaseType != null
				&& Core.getConfigManager().databaseType.equalsIgnoreCase("mysql");
	}

	private boolean isDuplicateKey(SQLException ex) {
		return ex.getErrorCode() == 1062 || (ex.getSQLState() != null && ex.getSQLState().startsWith("23"));
	}

	protected Connection openConnection() throws Exception {
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
