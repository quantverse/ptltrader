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

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
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
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.PortfolioList;
import com.pairtradinglab.ptltrader.model.Status;
import com.pairtradinglab.ptltrader.model.TradeHistory;
import com.pairtradinglab.ptltrader.trading.events.PairStateUpdated;

/**
 * SQLite-backed portfolio store.
 *
 * Threading mirrors the PtlApiClient rq-worker pattern it replaces: a single
 * db-worker thread owns the connection and drains a queue of writes, so bus
 * threads never touch JDBC. Unlike rq-worker it does not retry forever; against
 * a local file, endless retry would only hide a failing disk.
 */
public class SqlitePortfolioStore implements PortfolioStore, Startable {

	static final int WRITE_ATTEMPTS = 3;

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

	private final Thread writeQueueWorker = new Thread(new Runnable() {
		@Override
		public void run() {
			while (true) {
				try {
					runWithRetry(writeQueue.take());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
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
			} catch (RuntimeException e) {
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

	/** Queues a unit of work that may throw SQLException. */
	private void enqueue(final SqlTask task) {
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
	}

	interface SqlTask {
		void run(Connection c) throws SQLException;
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
		bus.register(this);
		load();
	}

	@Override
	public void stop() {
		bus.unregister(this);
		flush();
		writeQueueWorker.interrupt();
		if (database != null) database.close();
	}

	@Override
	public void load() {
		if (database == null) return;
		try {
			List<String> documents = readDocuments(database.getConnection());
			ArrayNode root = mapper.createArrayNode();
			for (String doc : documents) {
				JsonNode node = mapper.readTree(doc);
				Map<String, StrategyState> states =
						readStrategyStates(database.getConnection(), node.path("uid").asText());
				root.add(PortfolioDocuments.splice(node, states, mapper));
			}
			portfolioList.updateFromJson(root);
			portfolioList.initialize();
			status.setPtlConnected(true);
			logger.info("loaded " + documents.size() + " portfolios from the database");
		} catch (Exception e) {
			logger.error("unable to load portfolios", e);
			bus.post(new LogEvent("unable to load portfolios: " + e.getMessage()));
			bus.post(new StoreProblem("load", StoreError.IO_FAILURE, e.getMessage()));
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
		if (!writeQueueWorker.isAlive()) return;
		final CountDownLatch latch = new CountDownLatch(1);
		writeQueue.add(new Runnable() {
			@Override
			public void run() {
				latch.countDown();
			}
		});
		try {
			latch.await();
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
}
