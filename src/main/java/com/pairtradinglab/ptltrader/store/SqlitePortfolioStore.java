/**
 * 	This file is part of PTL Trader.
 *
 * 	Copyright © 2011-2021 Quantverse OÜ. All Rights Reserved.
 *
 *  PTL Trader is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  PTL Trader is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with PTL Trader. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pairtradinglab.ptltrader.store;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.picocontainer.Startable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.DataDirectory;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.events.GlobalPortfolioUpdateRequest;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.events.PortfolioSyncOutRequest;
import com.pairtradinglab.ptltrader.events.StoreProblem;
import com.pairtradinglab.ptltrader.events.StrategySyncOutRequest;
import com.pairtradinglab.ptltrader.model.LegHistory;
import com.pairtradinglab.ptltrader.model.LegHistoryEntry;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.PortfolioList;
import com.pairtradinglab.ptltrader.model.Status;
import com.pairtradinglab.ptltrader.model.TradeHistory;
import com.pairtradinglab.ptltrader.model.TradeHistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.PairStateUpdated;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;

/**
 * SQLite-backed portfolio store.
 *
 * Threading mirrors the PtlApiClient rq-worker pattern it replaces: a single
 * db-worker thread owns the connection and drains a queue of writes, so bus
 * threads never touch JDBC. Unlike rq-worker it does not retry forever; against
 * a local file, endless retry would only hide a failing disk.
 *
 * Every use of the JDBC Connection - writes and reads alike - is confined to
 * db-worker. Callers on other threads only ever enqueue a unit of work; where
 * they need a result back (load()), they block on a latch for db-worker to
 * finish it rather than touching the Connection themselves. That confinement
 * is what makes Database's documented lack of thread-safety safe to rely on.
 */
public class SqlitePortfolioStore implements PortfolioStore, Startable {

	static final int WRITE_ATTEMPTS = 3;

	/** How long a caller blocks in flush()/load() waiting for db-worker before giving up. */
	static final long WORKER_AWAIT_TIMEOUT_MS = 30000L;

	/** How long stop() waits for db-worker to terminate after being interrupted. */
	static final long WORKER_JOIN_TIMEOUT_MS = 5000L;

	private final EventBus bus;
	private final Logger logger;
	private final RuntimeParams runtimeParams;
	private final PortfolioList portfolioList;
	private final Status status;
	private final LegHistory legHistory;
	private final TradeHistory tradeHistory;

	private final ObjectMapper mapper = new ObjectMapper();
	private final BlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<Runnable>(4096);

	private volatile Database database;
	private volatile boolean busRegistered = false;

	private final Thread writeQueueWorker = new Thread(new Runnable() {
		@Override
		public void run() {
			while (true) {
				try {
					runWithRetry(writeQueue.take());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				} catch (Throwable t) {
					// A single bug or Error escaping runWithRetry must not silently kill
					// this thread: that would turn every later write into the same silent
					// loss that reportLostWork() exists to prevent, for the rest of the
					// process lifetime.
					logger.error("db-worker loop caught an unexpected throwable", t);
					bus.post(new StoreProblem("write", StoreError.IO_FAILURE, t.getMessage()));
				}
			}
		}
	}, "db-worker");

	public SqlitePortfolioStore(EventBus bus, LoggerFactory loggerFactory, RuntimeParams runtimeParams,
			PortfolioList portfolioList, Status status, LegHistory legHistory, TradeHistory tradeHistory) {
		super();
		this.bus = bus;
		this.logger = loggerFactory.createLogger(this.getClass().getSimpleName());
		this.runtimeParams = runtimeParams;
		this.portfolioList = portfolioList;
		this.status = status;
		this.legHistory = legHistory;
		this.tradeHistory = tradeHistory;
	}

	private void runWithRetry(Runnable task) {
		for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
			try {
				task.run();
				return;
			} catch (Throwable e) {
				logger.warn("database write failed, attempt " + attempt + " of " + WRITE_ATTEMPTS
						+ ": " + e.getMessage());
				if (attempt == WRITE_ATTEMPTS) {
					logger.error("database write permanently failed", e);
					bus.post(new LogEvent("database write failed: " + e.getMessage()));
					bus.post(new StoreProblem("write", StoreError.IO_FAILURE, e.getMessage()));
					return;
				}
				try {
					Thread.sleep(200L * attempt);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	/**
	 * Reports a write or read that never made it onto (or through) the queue at
	 * all: the worker was not running, or the bounded queue was full. Unlike a
	 * failure inside a queued task - which runWithRetry already logs and reports
	 * - this loss would otherwise be completely silent.
	 */
	private void reportLostWork(String origin, String detail) {
		String message = origin + " failed: " + detail;
		logger.error("database " + message);
		bus.post(new LogEvent("database " + message));
		bus.post(new StoreProblem(origin, StoreError.IO_FAILURE, detail));
	}

	/** Queues a unit of work that may throw SQLException. Never runs it inline. */
	private void enqueue(final SqlTask task) {
		if (!writeQueueWorker.isAlive()) {
			reportLostWork("write", "db-worker is not running");
			return;
		}
		try {
			writeQueue.add(new Runnable() {
				@Override
				public void run() {
					try {
						task.run(database.getConnection());
					} catch (SQLException e) {
						throw new RuntimeException(e);
					}
				}
			});
		} catch (IllegalStateException e) {
			// LinkedBlockingQueue(4096).add() throws rather than blocking when full,
			// and that exception would otherwise be swallowed by Guava's default
			// SubscriberExceptionHandler when this runs on an AsyncEventBus thread.
			reportLostWork("write", "write queue is full");
		}
	}

	interface SqlTask {
		void run(Connection c) throws SQLException;
	}

	/** A unit of work run on db-worker that produces a result for the caller. */
	private interface ReadTask<T> {
		T run(Connection c) throws Exception;
	}

	/**
	 * Runs a read on db-worker and blocks the calling thread for its result, so
	 * that every use of the Connection made by load() stays confined to
	 * db-worker exactly like the writes are, instead of racing them.
	 */
	private <T> T runOnWorker(final ReadTask<T> task) throws Exception {
		if (!writeQueueWorker.isAlive()) {
			throw new IllegalStateException("db-worker is not running");
		}
		final CountDownLatch latch = new CountDownLatch(1);
		final Object[] result = new Object[1];
		final Exception[] failure = new Exception[1];
		try {
			writeQueue.add(new Runnable() {
				@Override
				public void run() {
					try {
						result[0] = task.run(database.getConnection());
					} catch (Exception e) {
						failure[0] = e;
					} finally {
						latch.countDown();
					}
				}
			});
		} catch (IllegalStateException e) {
			throw new IllegalStateException("write queue is full", e);
		}
		if (!latch.await(WORKER_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
			throw new IllegalStateException(
					"timed out waiting for db-worker after " + WORKER_AWAIT_TIMEOUT_MS + "ms");
		}
		if (failure[0] != null) throw failure[0];
		@SuppressWarnings("unchecked")
		T typed = (T) result[0];
		return typed;
	}

	// ---- static SQL helpers, package-visible so they can be tested directly ----

	static void upsertDocument(Connection c, String uid, String name, String document) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO portfolios (uid, name, document, updated_at) VALUES (?, ?, ?, ?) "
				+ "ON CONFLICT(uid) DO UPDATE SET name=excluded.name, document=excluded.document, "
				+ "updated_at=excluded.updated_at")) {
			ps.setString(1, uid);
			ps.setString(2, name);
			ps.setString(3, document);
			ps.setString(4, nowIso());
			ps.executeUpdate();
		}
	}

	static void upsertStrategyState(Connection c, String portfolioUid, StrategyState st) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO strategy_state (strategy_uid, portfolio_uid, last_opened_datetime, "
				+ "last_opened_equity, last_model_state, updated_at) VALUES (?, ?, ?, ?, ?, ?) "
				+ "ON CONFLICT(strategy_uid) DO UPDATE SET portfolio_uid=excluded.portfolio_uid, "
				+ "last_opened_datetime=excluded.last_opened_datetime, "
				+ "last_opened_equity=excluded.last_opened_equity, "
				+ "last_model_state=excluded.last_model_state, updated_at=excluded.updated_at")) {
			ps.setString(1, st.strategyUid);
			ps.setString(2, portfolioUid);
			ps.setString(3, st.lastOpenedDatetime);
			if (st.lastOpenedEquity == null) ps.setNull(4, java.sql.Types.REAL);
			else ps.setDouble(4, st.lastOpenedEquity.doubleValue());
			ps.setString(5, st.lastModelState);
			ps.setString(6, nowIso());
			ps.executeUpdate();
		}
	}

	static Map<String, StrategyState> readStrategyStates(Connection c, String portfolioUid) throws SQLException {
		Map<String, StrategyState> out = new HashMap<String, StrategyState>();
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT strategy_uid, last_opened_datetime, last_opened_equity, last_model_state "
				+ "FROM strategy_state WHERE portfolio_uid=?")) {
			ps.setString(1, portfolioUid);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					double equity = rs.getDouble("last_opened_equity");
					Double boxed = rs.wasNull() ? null : Double.valueOf(equity);
					out.put(rs.getString("strategy_uid"), new StrategyState(
							rs.getString("strategy_uid"),
							rs.getString("last_opened_datetime"),
							boxed,
							rs.getString("last_model_state")));
				}
			}
		}
		return out;
	}

	static List<String> readDocuments(Connection c) throws SQLException {
		List<String> out = new ArrayList<String>();
		try (Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT document FROM portfolios ORDER BY name, uid")) {
			while (rs.next()) out.add(rs.getString("document"));
		}
		return out;
	}

	static void deletePortfolioRow(Connection c, String uid) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("DELETE FROM portfolios WHERE uid=?")) {
			ps.setString(1, uid);
			ps.executeUpdate();
		}
	}

	static void deleteStrategyStateRow(Connection c, String strategyUid) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement("DELETE FROM strategy_state WHERE strategy_uid=?")) {
			ps.setString(1, strategyUid);
			ps.executeUpdate();
		}
	}

	static String nowIso() {
		return new DateTime(DateTimeZone.UTC).toString();
	}

	/** How much history is loaded into the UI tables at startup. */
	public static final int HISTORY_LOAD_LIMIT = 1000;

	static void insertTradeHistory(Connection c, HistoryEntry he) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO trade_history (datetime, account, stock1, stock2, action, "
				+ "realized_pl, realized_pl_pct, commissions, zscore, comment) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			ps.setString(1, he.datetime.withZone(DateTimeZone.UTC).toString());
			ps.setString(2, he.account);
			ps.setString(3, he.stock1);
			ps.setString(4, he.stock2);
			ps.setString(5, he.action);
			ps.setDouble(6, he.realizedPl);
			ps.setDouble(7, he.realizedPlPerc);
			ps.setDouble(8, he.commissions);
			ps.setDouble(9, he.zscore);
			ps.setString(10, he.comment);
			ps.executeUpdate();
		}
	}

	static List<TradeHistoryEntry> readTradeHistory(Connection c, int limit) throws SQLException {
		List<TradeHistoryEntry> out = new ArrayList<TradeHistoryEntry>();
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT datetime, account, stock1, stock2, action, realized_pl, realized_pl_pct, "
				+ "commissions, zscore, comment FROM trade_history ORDER BY datetime DESC, id DESC LIMIT ?")) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new TradeHistoryEntry(
							new DateTime(rs.getString("datetime")).withZone(DateTimeZone.getDefault()),
							rs.getString("stock1"), rs.getString("stock2"), rs.getString("action"),
							rs.getDouble("realized_pl"), rs.getDouble("realized_pl_pct"),
							rs.getDouble("commissions"), rs.getDouble("zscore"),
							rs.getString("comment"), rs.getString("account")));
				}
			}
		}
		return out;
	}

	static void insertLegHistory(Connection c, TransactionEvent te) throws SQLException {
		try (PreparedStatement ps = c.prepareStatement(
				"INSERT INTO leg_history (datetime, account, symbol, action, qty, price, value, "
				+ "realized_pl, commissions, slippage, fill_time_ms) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			ps.setString(1, te.getDatetime().withZone(DateTimeZone.UTC).toString());
			ps.setString(2, te.getAccount());
			ps.setString(3, te.symbol);
			ps.setString(4, te.direction == TransactionEvent.DIRECTION_LONG
					? LegHistoryEntry.ACTION_BUY : LegHistoryEntry.ACTION_SELL);
			ps.setInt(5, te.qty);
			ps.setDouble(6, te.price);
			ps.setDouble(7, te.value);
			ps.setDouble(8, te.getRealizedPl());
			ps.setDouble(9, te.getCommissions());
			ps.setDouble(10, 0.0);
			ps.setLong(11, te.getFillTime() == null ? 0L : te.getFillTime().getMillis());
			ps.executeUpdate();
		}
	}

	static List<LegHistoryEntry> readLegHistory(Connection c, int limit) throws SQLException {
		List<LegHistoryEntry> out = new ArrayList<LegHistoryEntry>();
		try (PreparedStatement ps = c.prepareStatement(
				"SELECT datetime, account, symbol, action, qty, price, value, realized_pl, "
				+ "commissions, slippage, fill_time_ms FROM leg_history "
				+ "ORDER BY datetime DESC, id DESC LIMIT ?")) {
			ps.setInt(1, limit);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new LegHistoryEntry(
							new DateTime(rs.getString("datetime")).withZone(DateTimeZone.getDefault()),
							rs.getString("symbol"), rs.getString("action"), rs.getInt("qty"),
							rs.getDouble("realized_pl"), rs.getDouble("commissions"),
							rs.getDouble("price"), rs.getDouble("value"), rs.getDouble("slippage"),
							Duration.millis(rs.getLong("fill_time_ms")), rs.getString("account")));
				}
			}
		}
		return out;
	}

	// ---- lifecycle ----

	@Override
	public void start() {
		File dbPath = DataDirectory.databaseFile(runtimeParams.getProfile());
		try {
			DataDirectory.ensureExists(dbPath.getParentFile());
			Database d = new Database(dbPath, logger);
			d.open();
			database = d;
			logger.info("database opened: " + dbPath.getAbsolutePath());
		} catch (Exception e) {
			logger.error("unable to open database", e);
			bus.post(new StoreProblem("open", StoreError.IO_FAILURE, e.getMessage()));
			return;
		}
		writeQueueWorker.start();
		// load() reads through db-worker itself (see runOnWorker), so it only needs
		// the worker running, not the bus subscription. Registering after it keeps
		// a PairStateUpdated/*SyncOutRequest arriving during startup from racing the
		// initial load's own writes to the PortfolioList.
		load();
		// loadHistories() must also run before bus.register(this): a live
		// TransactionEvent/HistoryEntry arriving during startup would otherwise be
		// written to the database AND appended to the in-memory table by the beans'
		// own subscriptions, and then read back again by this backfill - showing up
		// twice. It is deliberately not folded into load(): Task 8's import flow
		// calls flush() then load() to refresh the portfolio list, and TradeHistory/
		// LegHistory.addEntryLast() do not deduplicate, so every import would
		// re-append up to HISTORY_LOAD_LIMIT rows onto the UI tables.
		loadHistories();
		bus.register(this);
		busRegistered = true;
	}

	@Override
	public void stop() {
		if (busRegistered) {
			bus.unregister(this);
			busRegistered = false;
		}
		flush();
		writeQueueWorker.interrupt();
		try {
			writeQueueWorker.join(WORKER_JOIN_TIMEOUT_MS);
			if (writeQueueWorker.isAlive()) {
				logger.error("db-worker did not stop within " + WORKER_JOIN_TIMEOUT_MS + "ms");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (database != null) database.close();
	}

	@Override
	public void load() {
		if (database == null) return;
		try {
			ArrayNode root = runOnWorker(new ReadTask<ArrayNode>() {
				@Override
				public ArrayNode run(Connection c) throws Exception {
					List<String> documents = readDocuments(c);
					ArrayNode r = mapper.createArrayNode();
					for (String doc : documents) {
						JsonNode node = mapper.readTree(doc);
						Map<String, StrategyState> states = readStrategyStates(c, node.path("uid").asText());
						r.add(PortfolioDocuments.splice(node, states, mapper));
					}
					return r;
				}
			});
			portfolioList.updateFromJson(root);
			portfolioList.initialize();
			status.setPtlConnected(true);
			logger.info("loaded " + root.size() + " portfolios from the database");
		} catch (Exception e) {
			logger.error("unable to load portfolios", e);
			bus.post(new LogEvent("unable to load portfolios: " + e.getMessage()));
			bus.post(new StoreProblem("load", StoreError.IO_FAILURE, e.getMessage()));
		}
	}

	/**
	 * Backfills the Trade History and Leg History UI tables from the database.
	 * Called once from start(), after load() and before bus.register(this).
	 *
	 * It is deliberately its own method rather than folded into load(): Task 8's
	 * import flow calls flush() then load() to refresh the portfolio list, and
	 * TradeHistory/LegHistory.addEntryLast() do not deduplicate, so an import
	 * would otherwise re-append up to HISTORY_LOAD_LIMIT rows every time. And it
	 * must finish before bus.register(this), or a live TransactionEvent/
	 * HistoryEntry arriving during startup would be written to the database AND
	 * appended to the in-memory table by the beans' own subscriptions, then read
	 * back again here - showing up twice.
	 *
	 * Reads through runOnWorker(), exactly like load(), so every use of the
	 * Connection stays confined to db-worker.
	 */
	private void loadHistories() {
		if (database == null) return;
		try {
			List<TradeHistoryEntry> trades = runOnWorker(new ReadTask<List<TradeHistoryEntry>>() {
				@Override
				public List<TradeHistoryEntry> run(Connection c) throws Exception {
					return readTradeHistory(c, HISTORY_LOAD_LIMIT);
				}
			});
			for (TradeHistoryEntry e : trades) {
				tradeHistory.addEntryLast(e);
			}
			List<LegHistoryEntry> legs = runOnWorker(new ReadTask<List<LegHistoryEntry>>() {
				@Override
				public List<LegHistoryEntry> run(Connection c) throws Exception {
					return readLegHistory(c, HISTORY_LOAD_LIMIT);
				}
			});
			for (LegHistoryEntry e : legs) {
				legHistory.addEntryLast(e);
			}
			logger.info("loaded " + trades.size() + " trade history and " + legs.size() + " leg history rows");
		} catch (Exception e) {
			logger.error("unable to load history", e);
			bus.post(new LogEvent("unable to load history: " + e.getMessage()));
			bus.post(new StoreProblem("loadHistories", StoreError.IO_FAILURE, e.getMessage()));
		}
	}

	@Override
	public void savePortfolio(Portfolio p) {
		final ObjectNode doc = PortfolioDocuments.build(p, mapper);
		final String uid = p.getUid();
		final String name = p.getName();
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				upsertDocument(c, uid, name, doc.toString());
			}
		});
	}

	@Override
	public void saveStrategyState(PairStrategy s) {
		final StrategyState st = StrategyState.fromStrategy(s, mapper);
		final String portfolioUid = s.getPortfolio().getUid();
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				upsertStrategyState(c, portfolioUid, st);
			}
		});
	}

	@Override
	public void deleteStrategy(PairStrategy s) {
		final String strategyUid = s.getUid();
		Portfolio p = s.getPortfolio();
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				deleteStrategyStateRow(c, strategyUid);
			}
		});
		// The strategy is already detached from the portfolio in memory, so
		// rewriting the document drops it from storage too.
		savePortfolio(p);
	}

	@Override
	public void deletePortfolio(Portfolio p) {
		final String uid = p.getUid();
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				deletePortfolioRow(c, uid);
			}
		});
		portfolioList.removePortfolio(p);
	}

	@Override
	public void insertPortfolioDocument(final JsonNode document) {
		final String uid = document.path("uid").asText();
		final String name = document.path("name").asText();
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				upsertDocument(c, uid, name, document.toString());
			}
		});
	}

	@Override
	public void bindPortfolioToAccount(Portfolio p, String accountCode) {
		if (accountCode == null || accountCode.isEmpty()) {
			// Portfolio.accountCode defaults to "", so an empty code would otherwise
			// match every other unbound portfolio below (a false BIND_DENIED), and
			// falling through with no other unbound portfolio would reach
			// Portfolio.bind(""), which throws IllegalArgumentException uncaught on
			// the caller's thread. A null code would NPE at the equals() call below.
			logger.warn("bind rejected: account code must not be null or empty");
			bus.post(new LogEvent("not allowed to bind portfolio: account code must not be empty"));
			bus.post(new StoreProblem("bindPortfolioToAccount", StoreError.BIND_DENIED,
					"account code must not be null or empty"));
			return;
		}
		for (Portfolio other : portfolioList.getPortfolios()) {
			if (other != p && accountCode.equals(other.getAccountCode())) {
				logger.warn("bind rejected: account " + accountCode + " already bound to " + other.getUid());
				bus.post(new LogEvent("not allowed to bind portfolio to the account specified"));
				bus.post(new StoreProblem("bindPortfolioToAccount", StoreError.BIND_DENIED, null));
				return;
			}
		}
		p.bind(accountCode);
		savePortfolio(p);
		bus.post(new GlobalPortfolioUpdateRequest());
	}

	@Override
	public void flush() {
		if (!writeQueueWorker.isAlive()) {
			reportLostWork("flush", "db-worker is not running; nothing to flush");
			return;
		}
		final CountDownLatch latch = new CountDownLatch(1);
		try {
			writeQueue.add(new Runnable() {
				@Override
				public void run() {
					latch.countDown();
				}
			});
		} catch (IllegalStateException e) {
			reportLostWork("flush", "write queue is full");
			return;
		}
		try {
			if (!latch.await(WORKER_AWAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
				logger.error("flush() timed out after " + WORKER_AWAIT_TIMEOUT_MS + "ms waiting for db-worker");
				bus.post(new StoreProblem("flush", StoreError.IO_FAILURE,
						"timed out waiting for db-worker"));
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// ---- bus subscriptions, mirroring the ones PtlApiClient had ----

	@Subscribe
	public void onPortfolioSyncOutRequest(PortfolioSyncOutRequest event) {
		savePortfolio(event.p);
	}

	@Subscribe
	public void onStrategySyncOutRequest(StrategySyncOutRequest event) {
		savePortfolio(event.s.getPortfolio());
	}

	@Subscribe
	public void onPairStateUpdated(PairStateUpdated event) {
		saveStrategyState(event.strategy);
	}

	// The retired web service used to store history as a side effect of
	// receiving telemetry. LegHistory and TradeHistory keep their own @Subscribe
	// handlers for the live in-memory tables, unchanged; the store is an
	// independent second subscriber to the same events, so neither depends on
	// the other.

	@Subscribe
	public void onHistoryEntry(final HistoryEntry he) {
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				insertTradeHistory(c, he);
			}
		});
	}

	@Subscribe
	public void onTransactionEvent(final TransactionEvent te) {
		enqueue(new SqlTask() {
			@Override
			public void run(Connection c) throws SQLException {
				insertLegHistory(c, te);
			}
		});
	}
}
