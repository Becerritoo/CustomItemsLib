package one.lindegaard.CustomItemsLib.rewards;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;

import one.lindegaard.CustomItemsLib.rewards.TokenSpendStore.MarkResult;
import one.lindegaard.CustomItemsLib.rewards.TokenSpendStore.TokenSpend;

public class TokenSpendStoreTest {

	@Test
	public void batchClaimsEveryTokenInOneTransaction() {
		FakeStore store = new FakeStore(-1);
		MarkResult result = store.markTokensSpent(Arrays.asList(spend("one"), spend("two"), spend("three")));

		assertEquals(MarkResult.MARKED, result);
		assertEquals(1, store.connections);
		assertEquals(3, store.updates);
		assertEquals(1, store.commits);
		assertEquals(0, store.rollbacks);
	}

	@Test
	public void databaseDuplicateRollsBackWholeBatch() {
		FakeStore store = new FakeStore(2);
		MarkResult result = store.markTokensSpent(Arrays.asList(spend("one"), spend("two"), spend("three")));

		assertEquals(MarkResult.DUPLICATE, result);
		assertEquals(1, store.connections);
		assertEquals(2, store.updates);
		assertEquals(0, store.commits);
		assertEquals(1, store.rollbacks);
	}

	@Test
	public void duplicateUuidIsRejectedBeforeOpeningConnection() {
		FakeStore store = new FakeStore(-1);
		MarkResult result = store.markTokensSpent(Arrays.asList(spend("same"), spend("same")));

		assertEquals(MarkResult.DUPLICATE, result);
		assertEquals(0, store.connections);
	}

	private static TokenSpend spend(String token) {
		return new TokenSpend(token, UUID.fromString("00000000-0000-0000-0000-000000000001"), "test", 10D);
	}

	private static final class FakeStore extends TokenSpendStore {
		private final int duplicateAt;
		private int connections;
		private int updates;
		private int commits;
		private int rollbacks;

		private FakeStore(int duplicateAt) {
			super(null);
			this.duplicateAt = duplicateAt;
		}

		@Override
		protected boolean isMySQL() {
			return false;
		}

		@Override
		protected Connection openConnection() {
			connections++;
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
					PreparedStatement.class.getClassLoader(), new Class<?>[] { PreparedStatement.class },
					(proxy, method, args) -> {
						if (method.getName().equals("executeUpdate")) {
							updates++;
							return updates == duplicateAt ? 0 : 1;
						}
						return defaultValue(method.getReturnType());
					});

			return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
					new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("getAutoCommit"))
							return true;
						if (method.getName().equals("prepareStatement"))
							return statement;
						if (method.getName().equals("commit"))
							commits++;
						if (method.getName().equals("rollback"))
							rollbacks++;
						return defaultValue(method.getReturnType());
					});
		}
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive())
			return null;
		if (type == boolean.class)
			return false;
		if (type == byte.class)
			return (byte) 0;
		if (type == short.class)
			return (short) 0;
		if (type == int.class)
			return 0;
		if (type == long.class)
			return 0L;
		if (type == float.class)
			return 0F;
		if (type == double.class)
			return 0D;
		if (type == char.class)
			return '\0';
		return null;
	}
}
