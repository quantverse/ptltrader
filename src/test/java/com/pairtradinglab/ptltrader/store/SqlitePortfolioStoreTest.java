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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.eventbus.EventBus;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.events.StoreProblem;
import com.pairtradinglab.ptltrader.model.LegHistory;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.PortfolioList;
import com.pairtradinglab.ptltrader.model.Status;
import com.pairtradinglab.ptltrader.model.TradeHistory;
import com.pairtradinglab.ptltrader.trading.PairTradingModelKalmanAutoState;

public class SqlitePortfolioStoreTest {

	private File dbFile;
	private Database db;
	private ObjectMapper mapper;

	@Before
	public void setUp() throws Exception {
		mapper = new ObjectMapper();
		dbFile = File.createTempFile("ptltrader-store", ".db");
		assertTrue(dbFile.delete());
		db = new Database(dbFile, mock(Logger.class));
		db.open();
	}

	@After
	public void tearDown() throws Exception {
		if (db != null) db.close();
		dbFile.delete();
		new File(dbFile.getAbsolutePath() + "-wal").delete();
		new File(dbFile.getAbsolutePath() + "-shm").delete();
	}

	private int countRows(String table) throws Exception {
		try (Statement st = db.getConnection().createStatement();
			 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
			rs.next();
			return rs.getInt(1);
		}
	}

	@Test
	public void testUpsertAndReadBackDocument() throws Exception {
		String doc = "{\"uid\":\"P1\",\"name\":\"My Portfolio\"}";
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "My Portfolio", doc);
		assertEquals(1, countRows("portfolios"));

		// A second upsert of the same uid replaces rather than duplicating.
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "Renamed", doc);
		assertEquals(1, countRows("portfolios"));

		try (Statement st = db.getConnection().createStatement();
			 ResultSet rs = st.executeQuery("SELECT name, document FROM portfolios WHERE uid='P1'")) {
			assertTrue(rs.next());
			assertEquals("Renamed", rs.getString("name"));
			assertEquals("P1", mapper.readTree(rs.getString("document")).get("uid").asText());
		}
	}

	@Test
	public void testStrategyStateUpsertAndRead() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "n", "{}");
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", "2026-03-01 14:30:00", Double.valueOf(12345.75),
						"{\"@class\":\".PairTradingModelKalmanAutoState\"}"));

		Map<String, StrategyState> states =
				SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1");
		assertEquals(1, states.size());
		StrategyState st = states.get("S1");
		assertEquals("2026-03-01 14:30:00", st.lastOpenedDatetime);
		assertEquals(12345.75, st.lastOpenedEquity.doubleValue(), 1e-9);
		assertTrue(st.lastModelState.contains("KalmanAutoState"));

		// Overwriting the state must not create a second row.
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", "2026-04-01 10:00:00", Double.valueOf(1.0), null));
		assertEquals(1, countRows("strategy_state"));
		states = SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1");
		assertEquals("2026-04-01 10:00:00", states.get("S1").lastOpenedDatetime);
		assertNull(states.get("S1").lastModelState);
	}

	@Test
	public void testNullStateFieldsRoundTripAsNull() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "n", "{}");
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", null, null, null));

		StrategyState st = SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1").get("S1");
		assertNull(st.lastOpenedDatetime);
		assertNull("a missing equity must read back as null, not 0.0", st.lastOpenedEquity);
		assertNull(st.lastModelState);
	}

	@Test
	public void testStatesAreScopedToTheirPortfolio() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "a", "{}");
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P2", "b", "{}");
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", null, null, null));
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P2",
				new StrategyState("S2", null, null, null));

		assertEquals(1, SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1").size());
		assertTrue(SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1").containsKey("S1"));
		assertTrue(SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P2").containsKey("S2"));
	}

	@Test
	public void testDeletePortfolioCascadesToState() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "n", "{}");
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", null, null, null));
		SqlitePortfolioStore.deletePortfolioRow(db.getConnection(), "P1");
		assertEquals(0, countRows("portfolios"));
		assertEquals(0, countRows("strategy_state"));
	}

	@Test
	public void testDeleteStrategyStateRow() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "n", "{}");
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1",
				new StrategyState("S1", null, null, null));
		SqlitePortfolioStore.deleteStrategyStateRow(db.getConnection(), "S1");
		assertEquals(0, countRows("strategy_state"));
		assertEquals("deleting a strategy must not delete its portfolio", 1, countRows("portfolios"));
	}

	@Test
	public void testReadDocumentsOrderedByName() throws Exception {
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P2", "Beta", "{\"uid\":\"P2\"}");
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", "Alpha", "{\"uid\":\"P1\"}");

		List<String> docs = SqlitePortfolioStore.readDocuments(db.getConnection());
		assertEquals(2, docs.size());
		assertEquals("P1", mapper.readTree(docs.get(0)).get("uid").asText());
		assertEquals("P2", mapper.readTree(docs.get(1)).get("uid").asText());
	}

	// ---- behavior of the instance itself, with mocked collaborators and no
	// ---- db-worker thread started, covering the write-path fixes from review ----

	private static final String PORTFOLIO_JSON = "{"
		+ "\"uid\":\"P1\",\"name\":\"n\",\"account_code\":\"\","
		+ "\"max_pairs_open\":10,\"master_status\":2,\"pdt_rules\":1,\"account_alloc\":100,"
		+ "\"strategies\":[]}";

	private SqlitePortfolioStore newUnstartedStore(EventBus bus, PortfolioList portfolioList) {
		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		RuntimeParams rp = new RuntimeParams(new String[] { "unittest" });
		Status status = mock(Status.class);
		LegHistory legHistory = new LegHistory();
		TradeHistory tradeHistory = new TradeHistory();
		// start() is deliberately never called: the db-worker thread never runs,
		// exactly like the case where start() bailed out after a failed database open.
		return new SqlitePortfolioStore(bus, lf, rp, portfolioList, status, legHistory, tradeHistory);
	}

	@Test
	public void testEnqueueReportsFailureWhenWorkerNotRunning() throws Exception {
		EventBus bus = mock(EventBus.class);
		PortfolioList portfolioList = mock(PortfolioList.class);
		SqlitePortfolioStore store = newUnstartedStore(bus, portfolioList);

		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		Portfolio p = new Portfolio(null, null, lf, "P1");
		p.updateFromJson(mapper.readTree(PORTFOLIO_JSON));

		// Previously this silently vanished: enqueue() just added to a queue that
		// nobody was ever going to drain, with no log and no event.
		store.savePortfolio(p);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(bus, atLeastOnce()).post(captor.capture());
		assertTrue("expected an IO_FAILURE StoreProblem when the db-worker is not running",
				containsIoFailure(captor.getAllValues()));

		// flush() must report the not-running case too, rather than pretending
		// every queued write was applied.
		reset(bus);
		store.flush();
		ArgumentCaptor<Object> flushCaptor = ArgumentCaptor.forClass(Object.class);
		verify(bus, atLeastOnce()).post(flushCaptor.capture());
		assertTrue("expected flush() to report failure instead of returning silently",
				containsIoFailure(flushCaptor.getAllValues()));
	}

	/**
	 * Import and New Portfolio used to fire-and-forget their insert and then report
	 * success. With the db-worker not running (start() bailed after a failed open),
	 * nothing was saved while the user was told it had been.
	 */
	@Test
	public void testInsertPortfolioDocumentsFailsWhenWorkerNotRunning() throws Exception {
		SqlitePortfolioStore store = newUnstartedStore(mock(EventBus.class), mock(PortfolioList.class));
		try {
			store.insertPortfolioDocuments(Collections.singletonList(mapper.readTree(PORTFOLIO_JSON)));
			fail("expected StoreException when nothing can be written");
		} catch (StoreException expected) {
			// the caller must be told the portfolio was not saved
		}
	}

	@Test
	public void testLoadReportsFailureWhenDatabaseNeverOpened() throws Exception {
		SqlitePortfolioStore store = newUnstartedStore(mock(EventBus.class), mock(PortfolioList.class));
		assertFalse("load() must not report success when nothing could be read", store.load());
	}

	@Test
	public void testBindPortfolioToAccountRejectsEmptyOrNullCode() throws Exception {
		EventBus bus = mock(EventBus.class);
		PortfolioList portfolioList = mock(PortfolioList.class);
		when(portfolioList.getPortfolios()).thenReturn(Collections.<Portfolio>emptyList());
		SqlitePortfolioStore store = newUnstartedStore(bus, portfolioList);

		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		Portfolio p = new Portfolio(null, null, lf, "P1");
		p.updateFromJson(mapper.readTree(PORTFOLIO_JSON));

		// Portfolio.accountCode defaults to "". Before the fix, an empty code
		// matched every other unbound portfolio (a false BIND_DENIED against an
		// empty list this is moot, but with no conflict it fell through to
		// Portfolio.bind(""), which throws IllegalArgumentException uncaught).
		store.bindPortfolioToAccount(p, "");
		assertEquals("empty account code must not be bound", "", p.getAccountCode());

		// A null code must not NPE at the equals() comparison either.
		store.bindPortfolioToAccount(p, null);
		assertEquals("null account code must not be bound", "", p.getAccountCode());

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(bus, atLeastOnce()).post(captor.capture());
		int bindDeniedCount = 0;
		for (Object o : captor.getAllValues()) {
			if (o instanceof StoreProblem && ((StoreProblem) o).error == StoreError.BIND_DENIED) {
				bindDeniedCount++;
			}
		}
		assertEquals("both the empty and the null code must be rejected as BIND_DENIED", 2, bindDeniedCount);
	}

	@Test
	public void testDeletePortfolioDetachesItFromTheBusBeforeRemovingIt() throws Exception {
		EventBus bus = mock(EventBus.class);
		PortfolioList portfolioList = mock(PortfolioList.class);
		SqlitePortfolioStore store = newUnstartedStore(bus, portfolioList);

		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		EventBus portfolioBus = mock(EventBus.class);
		Portfolio p = new Portfolio(portfolioBus, null, lf, "P1");
		p.updateFromJson(mapper.readTree(PORTFOLIO_JSON));
		PairStrategy s = new PairStrategy("S1", p, "NYSE:V", "NYSE:MA", 0, 0, mock(EventBus.class), null);
		p.addPairStrategy(s);
		p.initialize();
		verify(portfolioBus).register(p);

		store.deletePortfolio(p);

		// Without this the deleted portfolio keeps receiving BeaconFlash and its
		// onBeaconFlash() re-inserts the row that was just deleted, so the portfolio
		// comes back on the next restart with all its pairs.
		verify(portfolioBus).unregister(p);
		verify(portfolioList).removePortfolio(p);
	}

	// ---- runtime state survival, required by section 11 of the design spec ----

	private static final String KALMAN_STRATEGY_JSON = "{"
		+ "\"uid\":\"S1\",\"model\":\"Kalman-auto\",\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\","
		+ "\"trade_as_1\":0,\"trade_as_2\":0,"
		+ "\"entry_threshold\":2.0,\"exit_threshold\":0.0,\"downtick_threshold\":0.0,\"max_score\":10.0,"
		+ "\"ratio_ma_type\":1,\"ratio_ma_period\":15,\"ratio_stddev_period\":15,\"ratio_entry_mode\":0,"
		+ "\"ratio_rsi_period\":10,\"ratio_rsi_threshold\":0.0,\"residual_linreg_period\":30,"
		+ "\"neutrality\":0,\"ka_ve\":0.001,\"ka_usage_target\":60.0,"
		+ "\"ticker1margin\":50.0,\"ticker2margin\":50.0,"
		+ "\"last_opened_datetime\":null,\"last_opened_equity\":null,\"last_model_state\":null,"
		+ "\"allow_reversals\":1,\"enable_max_days\":1,\"max_days\":20,"
		+ "\"enable_min_pl\":0,\"min_pl\":0.0,\"enable_min_price\":1,\"min_price\":5.0,"
		+ "\"enable_min_profit_potential\":1,\"min_profit_potential\":40.0,"
		+ "\"entry_start_hour\":9,\"entry_start_minute\":30,\"entry_end_hour\":15,\"entry_end_minute\":55,"
		+ "\"exit_start_hour\":9,\"exit_start_minute\":30,\"exit_end_hour\":15,\"exit_end_minute\":55,"
		+ "\"timezone\":\"America/New_York\",\"allow_positions\":0,\"status\":2,\"slot_occupation\":1.0"
		+ "}";

	/**
	 * The full path a Kalman strategy's runtime state takes across a restart:
	 * StrategyState.fromStrategy -> upsertStrategyState -> readStrategyStates ->
	 * PortfolioDocuments.splice -> PairStrategy.updateFromJson. Nothing else in the
	 * suite produces a last_model_state through the real serializer or consumes one
	 * through the real deserializer, so nothing else would notice if the polymorphic
	 * @class type id stopped round-tripping - for instance after a package move,
	 * which JsonTypeInfo.Id.MINIMAL_CLASS ids would silently break.
	 *
	 * This is the one piece of data whose loss has a monetary cost: a Kalman
	 * strategy that resumes an open position without its state resumes wrong.
	 */
	@Test
	public void testKalmanModelStateAndLastOpenedSurviveAStoreRoundTrip() throws Exception {
		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		Portfolio p = new Portfolio(null, null, lf, "P1");
		p.updateFromJson(mapper.readTree(PORTFOLIO_JSON));

		PairStrategy s = new PairStrategy("S1", p, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		s.updateFromJson(mapper.readTree(KALMAN_STRATEGY_JSON));
		p.addPairStrategy(s);

		// A real state object, as a running Kalman-auto core would leave behind.
		PairTradingModelKalmanAutoState original = new PairTradingModelKalmanAutoState(7);
		s.setModelState(original);
		DateTime opened = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		s.setLastOpened(opened);
		s.setLastOpenEquity(12345.75);

		// out: exactly what onPairStateUpdated() -> saveStrategyState() writes.
		StrategyState written = StrategyState.fromStrategy(s, mapper);
		SqlitePortfolioStore.upsertDocument(db.getConnection(), "P1", p.getName(),
				PortfolioDocuments.build(p, mapper).toString());
		SqlitePortfolioStore.upsertStrategyState(db.getConnection(), "P1", written);

		// in: exactly what load() reads back, including the polymorphic type id.
		Map<String, StrategyState> states =
				SqlitePortfolioStore.readStrategyStates(db.getConnection(), "P1");
		assertEquals(".PairTradingModelKalmanAutoState",
				mapper.readTree(states.get("S1").lastModelState).get("@class").asText());

		String document = SqlitePortfolioStore.readDocuments(db.getConnection()).get(0);
		List<String> corrupt = new ArrayList<String>();
		ObjectNode spliced = PortfolioDocuments.splice(mapper.readTree(document), states, mapper, corrupt);
		assertTrue("the stored state must be readable", corrupt.isEmpty());

		Portfolio reloadedPortfolio = new Portfolio(null, null, lf, "P1");
		reloadedPortfolio.updateFromJson(mapper.readTree(PORTFOLIO_JSON));
		PairStrategy reloaded = new PairStrategy("S1", reloadedPortfolio, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		reloaded.updateFromJson(spliced.get("strategies").get(0));

		assertNotNull("the model state must survive the round trip", reloaded.getModelState());
		assertSame("the concrete subclass must survive, not just the field values",
				PairTradingModelKalmanAutoState.class, reloaded.getModelState().getClass());
		assertEquals(7, ((PairTradingModelKalmanAutoState) reloaded.getModelState()).subModelId);

		assertNotNull(reloaded.getLastOpened());
		assertEquals("last_opened_datetime must round trip as the same instant, not shift "
				+ "by the local UTC offset - max_days exits depend on it",
				opened.getMillis(), reloaded.getLastOpened().getMillis());
		assertEquals(12345.75, reloaded.getLastOpenEquity(), 1e-9);
	}

	private static boolean containsIoFailure(List<Object> posted) {
		for (Object o : posted) {
			if (o instanceof StoreProblem && ((StoreProblem) o).error == StoreError.IO_FAILURE) return true;
		}
		return false;
	}
}
