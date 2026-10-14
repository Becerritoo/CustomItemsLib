package one.lindegaard.CustomItemsLib.storage;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import one.lindegaard.CustomItemsLib.Core;
import one.lindegaard.CustomItemsLib.PlayerSettings;
import one.lindegaard.CustomItemsLib.storage.async.IDataStoreTask;
import one.lindegaard.CustomItemsLib.storage.async.PlayerSettingsRetrieverTask;
import one.lindegaard.CustomItemsLib.storage.async.StoreTask;

public class DataStoreManager {

	private static final long DRAIN_TIMEOUT_MILLIS = 20000;
	private static final long THREAD_JOIN_TIMEOUT_MILLIS = 2000;

	private Plugin plugin;

	// Accessed on multiple threads
	private final LinkedHashSet<Object> mWaiting = new LinkedHashSet<Object>();

	// Accessed only from these threads
	private IDataStore mStore;
	private volatile boolean mExit = false;

	// Accessed only from store thread
	private StoreThread mStoreThread;

	// Accessed only from retrieve thread
	private TaskThread mTaskThread;

	public DataStoreManager(Plugin plugin, IDataStore store) {
		this.plugin = plugin;
		mStore = store;
		mTaskThread = new TaskThread();
		int savePeriod = Core.getConfigManager().savePeriod;
		if (savePeriod < 1200) {
			savePeriod = 1200;
			Bukkit.getConsoleSender().sendMessage(Core.PREFIX_WARNING
					+ "save-period in your config.yml is too low. Please raise it to 1200 or higher");
		}
		mStoreThread = new StoreThread(savePeriod);
	}

	public boolean isRunning() {
		return mTaskThread.isAlive() && mStoreThread.isAlive();
	}

	// *****************************************************************************
	// PlayerSettings
	// *****************************************************************************
	public void requestPlayerSettings(OfflinePlayer player, IDataCallback<PlayerSettings> callback) {
		mTaskThread.addTask(new PlayerSettingsRetrieverTask(player, mWaiting), callback);
	}

	/**
	 * Update the playerSettings in the Database
	 * 
	 * @param offlinePlayer
	 * @param playerSetting
	 */
	public void insertPlayerSettings(PlayerSettings ps) {
		synchronized (mWaiting) {
			mWaiting.add(ps);
		}
	}

	/**
	 * Gets an offline player using the last known name. WARNING: This does a
	 * database lookup directly. This will block waiting for a reply
	 */
	public OfflinePlayer getPlayerByName(String name) {
		try {
			return mStore.getPlayerByName(name);
		} catch (UserNotFoundException e) {
			return null;
		} catch (DataStoreException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Get the playerId from the database
	 * 
	 * @param offlinePlayer
	 * @return
	 * @throws UserNotFoundException
	 */
	public int getPlayerId(OfflinePlayer offlinePlayer) throws UserNotFoundException {
		if (offlinePlayer == null)
			return 0;
		if (RANDOM_PLAYER_UUID.equals(offlinePlayer.getUniqueId().toString()))
			return 0;

		try {
			int playerId = mStore.getPlayerId(offlinePlayer);
			if (playerId > 0)
				return playerId;
		} catch (DataStoreException e) {
			if (Core.getConfigManager().debug)
				e.printStackTrace();
		}

		try {
			PlayerSettings playerSettings;
			try {
				playerSettings = mStore.loadPlayerSettings(offlinePlayer);
			} catch (UserNotFoundException e) {
				String worldgroup = offlinePlayer.isOnline()
						? Core.getWorldGroupManager().getCurrentWorldGroup(offlinePlayer)
						: Core.getWorldGroupManager().getDefaultWorldgroup();
				playerSettings = new PlayerSettings(offlinePlayer, worldgroup, Core.getConfigManager().learningMode, false,
						null, null, System.currentTimeMillis(), System.currentTimeMillis());
			}

			Set<PlayerSettings> playerDataSet = new LinkedHashSet<PlayerSettings>();
			playerDataSet.add(playerSettings);
			mStore.savePlayerSettings(playerDataSet, false);

			int recoveredPlayerId = mStore.getPlayerId(offlinePlayer);
			if (recoveredPlayerId > 0)
				return recoveredPlayerId;
		} catch (DataStoreException e) {
			if (Core.getConfigManager().debug)
				e.printStackTrace();
		}

		throw new UserNotFoundException(
				Core.PREFIX + " User " + offlinePlayer.getName() + " (" + offlinePlayer.getUniqueId()
						+ ") is not present in Core database");
	}

	public OfflinePlayer getPlayerByPlayerId(int playerId) throws UserNotFoundException {
		try {
			return mStore.getPlayerByPlayerId(playerId);
		} catch (DataStoreException e) {
			if (Core.getConfigManager().debug)
				e.printStackTrace();
		}
		throw new UserNotFoundException(Core.PREFIX + " User " + playerId + " is not present in Core database");
	}

	public final static String RANDOM_PLAYER_UUID = "bb3be6f2-b457-11ea-b3de-0242ac130004";

	public void createRandomBountyPlayer() {
		try {
			mStore.createRandomBountyPlayer();
		} catch (DataStoreException e) {
			if (Core.getConfigManager().debug)
				e.printStackTrace();
		}
	}

	// *****************************************************************************
	// Common
	// *****************************************************************************
	/**
	 * Flush all waiting data to the database
	 */
	public void flush() {
		StoreTask storeTask;
		int waitingSize;
		synchronized (mWaiting) {
			waitingSize = mWaiting.size();
			if (waitingSize == 0)
				return;
			storeTask = new StoreTask(mWaiting);
		}
		Core.getMessages().debug("Force saving waiting %s data to database...", waitingSize);
		if (!mTaskThread.addTask(storeTask, null)) {
			plugin.getLogger().warning("Storage worker is unavailable; saving pending data on the current thread.");
			try {
				storeTask.run(mStore);
			} catch (DataStoreException | RuntimeException e) {
				plugin.getLogger().log(Level.SEVERE, "Unable to save pending data after storage worker failure.", e);
			}
		}
	}

	/**
	 * Shutdown the DataStoreManager
	 */
	public void shutdown() {
		mExit = true;
		mStoreThread.interrupt();
		joinThread(mStoreThread, "periodic storage");
		mTaskThread.setWriteOnlyMode(true);
		flush();
		try {
			if (!mTaskThread.waitForEmptyQueue(DRAIN_TIMEOUT_MILLIS))
				plugin.getLogger().warning("Storage tasks did not finish before shutdown; continuing without waiting indefinitely.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			plugin.getLogger().warning("Interrupted while waiting for storage tasks during shutdown.");
		} finally {
			mTaskThread.interrupt();
			joinThread(mTaskThread, "storage task");
		}
	}

	private void joinThread(Thread thread, String description) {
		try {
			thread.join(THREAD_JOIN_TIMEOUT_MILLIS);
			if (thread.isAlive())
				plugin.getLogger().warning("The " + description + " thread did not stop within "
						+ THREAD_JOIN_TIMEOUT_MILLIS + " ms.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			plugin.getLogger().warning("Interrupted while stopping the " + description + " thread.");
		}
	}

	/**
	 * Wait until all data has been updated
	 */
	public void waitForUpdates() {
		flush();
		try {
			if (!mTaskThread.waitForEmptyQueue(DRAIN_TIMEOUT_MILLIS))
				plugin.getLogger().warning("Timed out while waiting for storage updates.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			plugin.getLogger().warning("Interrupted while waiting for storage updates.");
		}
	}

	/**
	 * Constructor for the StoreThread
	 * 
	 * @author Rocologo
	 *
	 */
	private class StoreThread extends Thread {
		private int mSaveInterval;

		public StoreThread(int interval) {
			super("CIL StoreThread");
			mSaveInterval = interval;
			start();
		}

		@Override
		public void run() {

			try {
				while (!mExit) {
					mTaskThread.addTask(new StoreTask(mWaiting), null);

					Thread.sleep(mSaveInterval * 50);
				}
			} catch (InterruptedException e) {
				Core.getMessages().debug("StoreThread was interrupted");
			}
		}
	}

	private class Task {
		public Task(IDataStoreTask<?> task, IDataCallback<?> callback) {
			this.task = task;
			this.callback = callback;
		}

		public IDataStoreTask<?> task;

		public IDataCallback<?> callback;
	}

	private class CallbackCaller implements Runnable {
		private IDataCallback<Object> mCallback;
		private Object mObj;
		private boolean mSuccess;

		public CallbackCaller(IDataCallback<Object> callback, Object obj, boolean success) {
			mCallback = callback;
			mObj = obj;
			mSuccess = success;
		}

		@Override
		public void run() {
			if (mSuccess)
				mCallback.onCompleted(mObj);
			else
				mCallback.onError((Throwable) mObj);
		}

	}

	private class TaskThread extends Thread {
		private final BlockingQueue<Task> mQueue;
		private final AtomicInteger mOutstandingTasks = new AtomicInteger();
		private volatile boolean mWritesOnly = false;

		private final Object mSignal = new Object();

		public TaskThread() {
			super("BGC TaskThread");

			mQueue = new LinkedBlockingQueue<Task>();

			start();

		}

		public boolean waitForEmptyQueue(long timeoutMillis) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
			synchronized (mSignal) {
				if (mOutstandingTasks.get() > 0)
					Core.getMessages().debug("Waiting for %s storage tasks to finish before closing connections.",
							mOutstandingTasks.get());
				while (mOutstandingTasks.get() > 0) {
					if (!isAlive())
						return false;
					long remainingNanos = deadline - System.nanoTime();
					if (remainingNanos <= 0)
						return false;
					long waitMillis = Math.min(500, Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
					mSignal.wait(waitMillis);
				}
			}
			return true;
		}

		public void setWriteOnlyMode(boolean writes) {
			mWritesOnly = writes;
		}

		public <T> boolean addTask(IDataStoreTask<T> storeTask, IDataCallback<T> callback) {
			if (!isAlive()) {
				plugin.getLogger().warning("Rejected storage task because the storage worker is not running.");
				return false;
			}
			mOutstandingTasks.incrementAndGet();
			try {
				mQueue.add(new Task(storeTask, callback));
				return true;
			} catch (RuntimeException e) {
				completeTask();
				throw e;
			}
		}

		private void completeTask() {
			int remaining = mOutstandingTasks.decrementAndGet();
			if (remaining == 0)
				synchronized (mSignal) {
					mSignal.notifyAll();
				}
		}

		private void handleTaskFailure(Task task, Throwable failure) {
			plugin.getLogger().log(Level.SEVERE, "Storage task failed; the storage worker will continue.", failure);
			if (task.callback != null && !mExit) {
				try {
					Bukkit.getScheduler().runTask(plugin,
							new CallbackCaller((IDataCallback<Object>) task.callback, failure, false));
				} catch (RuntimeException callbackFailure) {
					plugin.getLogger().log(Level.SEVERE, "Unable to schedule the storage failure callback.",
							callbackFailure);
				}
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			try {
				while (!isInterrupted()) {
					Task task = mQueue.take();
					try {
						if (mWritesOnly && task.task.readOnly())
							continue;
						Object result = task.task.run(mStore);

						if (task.callback != null && !mExit)
							Bukkit.getScheduler().runTask(plugin,
									new CallbackCaller((IDataCallback<Object>) task.callback, result, true));
					} catch (DataStoreException | RuntimeException e) {
						handleTaskFailure(task, e);
					} finally {
						completeTask();
					}
				}
			} catch (InterruptedException e) {
				Core.getMessages().debug("TaskThread was interrupted");
				Thread.currentThread().interrupt();
			} finally {
				synchronized (mSignal) {
					mSignal.notifyAll();
				}
			}
		}
	}

}
