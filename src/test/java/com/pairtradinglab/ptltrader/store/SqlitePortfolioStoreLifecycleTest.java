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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.DataDirectory;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.StoreProblem;
import com.pairtradinglab.ptltrader.model.LegHistory;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.PairStrategyFactoryImpl;
import com.pairtradinglab.ptltrader.model.Portfolio;
import com.pairtradinglab.ptltrader.model.PortfolioFactoryImpl;
import com.pairtradinglab.ptltrader.model.PortfolioList;
import com.pairtradinglab.ptltrader.model.Status;
import com.pairtradinglab.ptltrader.model.TradeHistory;
import com.pairtradinglab.ptltrader.trading.PairTradingCoreFactory;
import com.pairtradinglab.ptltrader.trading.PairTradingModelKalmanAutoState;
import com.pairtradinglab.ptltrader.trading.events.PairStateUpdated;

/**
 * Exercises a real start()/stop() cycle of the store against a throwaway data
 * directory. Every other instance-level test deliberately never calls start(), so
 * without this class start(), load(), loadHistories(), runOnWorker, the db-worker
 * thread, runWithRetry and stop() have no automated coverage at all.
 *
 * The database path is not injectable - SqlitePortfolioStore's constructor must not
 * change, PicoContainer matches its parameters by name - so the test redirects
 * DataDirectory by overriding "user.home" for the duration, and restores it after.
 * The suite runs single-forked and sequentially, so nothing else observes the swap.
 */
public class SqlitePortfolioStoreLifecycleTest {

	private static final String PROFILE = "lifecycletest";

	private static final String STRATEGY_JSON = "{"
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

	private static final String PORTFOLIO_JSON = "{"
		+ "\"uid\":\"P1\",\"name\":\"My Portfolio\",\"account_code\":\"\","
		+ "\"max_pairs_open\":10,\"master_status\":2,\"pdt_rules\":1,\"account_alloc\":100,"
		+ "\"strategies\":[" + STRATEGY_JSON + "]}";

	private ObjectMapper mapper;
	private File home;
	private String savedUserHome;

	@Before
	public void setUp() throws Exception {
		mapper = new ObjectMapper();
		home = File.createTempFile("ptltrader-home", "");
		assertTrue(home.delete());
		assertTrue(home.mkdirs());
		savedUserHome = System.getProperty("user.home");
		System.setProperty("user.home", home.getAbsolutePath());
	}

	@After
	public void tearDown() throws Exception {
		if (savedUserHome != null) System.setProperty("user.home", savedUserHome);
		deleteRecursively(home);
	}

	private static void deleteRecursively(File f) {
		if (f == null) return;
		File[] children = f.listFiles();
		if (children != null) {
			for (File c : children) deleteRecursively(c);
		}
		f.delete();
	}

	private SqlitePortfolioStore newStore(EventBus bus, PortfolioList portfolioList, Status status) {
		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		return new SqlitePortfolioStore(bus, lf, new RuntimeParams(new String[] { PROFILE }),
				portfolioList, status, new LegHistory(), new TradeHistory());
	}

	private PortfolioList newPortfolioList(EventBus bus) {
		LoggerFactory lf = mock(LoggerFactory.class);
		when(lf.createLogger(anyString())).thenReturn(mock(Logger.class));
		PairTradingCoreFactory coreFactory = mock(PairTradingCoreFactory.class);
		PairStrategyFactoryImpl strategyFactory = new PairStrategyFactoryImpl(bus, coreFactory);
		return new PortfolioList(new PortfolioFactoryImpl(bus, strategyFactory, lf));
	}

	/**
	 * A full restart: start() opens the database and loads nothing, a write goes
	 * through the db-worker, stop() flushes and closes it, and a second store started
	 * on the same file loads what the first one wrote - including the Kalman model
	 * state, which is the datum whose loss has a monetary cost.
	 */
	@Test
	public void testStartWriteStopAndReloadAcrossStoreInstances() throws Exception {
		EventBus bus = mock(EventBus.class);
		Status status = new Status();
		PortfolioList first = newPortfolioList(bus);
		SqlitePortfolioStore store = newStore(bus, first, status);

		store.start();
		assertTrue("start() must open the database and complete load()", status.isStoreReady());
		assertTrue("nothing has been stored yet", first.getPortfolios().isEmpty());
		assertTrue("the database file must live under the redirected data directory",
				DataDirectory.databaseFile(PROFILE).getAbsolutePath()
						.startsWith(home.getAbsolutePath()));

		// Insert a portfolio the way the New Portfolio / import flows do, then reload
		// it through the real load() path so the store owns a live bean to save state for.
		store.insertPortfolioDocument(mapper.readTree(PORTFOLIO_JSON));
		store.flush();
		store.load();
		assertEquals(1, first.getPortfolios().size());
		PairStrategy s = first.getPortfolios().get(0).getPairStrategies().get(0);

		DateTime opened = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		s.setLastOpened(opened);
		s.setLastOpenEquity(12345.75);
		s.setModelState(new PairTradingModelKalmanAutoState(7));
		store.saveStrategyState(s);

		store.stop();

		// A second store on the same file: this is the restart.
		PortfolioList second = newPortfolioList(bus);
		SqlitePortfolioStore reopened = newStore(bus, second, new Status());
		reopened.start();
		try {
			assertEquals(1, second.getPortfolios().size());
			Portfolio p = second.getPortfolios().get(0);
			assertEquals("My Portfolio", p.getName());
			assertEquals(1, p.getPairStrategies().size());

			PairStrategy recovered = p.getPairStrategies().get(0);
			assertNotNull("stop() must have flushed the pending state write before closing",
					recovered.getModelState());
			assertSame(PairTradingModelKalmanAutoState.class, recovered.getModelState().getClass());
			assertEquals(7, ((PairTradingModelKalmanAutoState) recovered.getModelState()).subModelId);
			assertEquals(opened.getMillis(), recovered.getLastOpened().getMillis());
			assertEquals(12345.75, recovered.getLastOpenEquity(), 1e-9);
		} finally {
			reopened.stop();
		}
	}

	/**
	 * Registers a subscriber that collects every StoreProblem the store posts, so a
	 * test can assert on none. Deliberately an anonymous class: Gradle 4's test
	 * detector runs named nested classes of a test class as tests when it cannot
	 * read their superclass (as on JDK 17+), and they then fail with "No runnable
	 * methods". Anonymous classes are never picked up.
	 */
	private static List<StoreProblem> collectStoreProblems(EventBus bus) {
		final List<StoreProblem> problems = Collections.synchronizedList(new ArrayList<StoreProblem>());
		bus.register(new Object() {
			@Subscribe
			public void onStoreProblem(StoreProblem p) {
				problems.add(p);
			}
		});
		return problems;
	}

	/**
	 * A deleted portfolio must stay deleted across a restart.
	 *
	 * This uses a real synchronous EventBus, because that is the whole mechanism:
	 * Portfolio.initialize() registers the bean on the bus and, before the fix,
	 * nothing ever unregistered it. The deleted bean kept receiving BeaconFlash and
	 * its onBeaconFlash() posted a PortfolioSyncOutRequest on the next dirty flag,
	 * which the store turned back into an upsertDocument - re-inserting the very row
	 * the delete had just removed, so the portfolio reappeared on the next restart
	 * with all of its pairs.
	 */
	@Test
	public void testDeletedPortfolioDoesNotComeBackAfterRestart() throws Exception {
		EventBus bus = new EventBus("lifecycletest");
		List<StoreProblem> problems = collectStoreProblems(bus);
		// Prove the collector can hear before relying on it hearing nothing below.
		bus.post(new StoreProblem("probe", StoreError.IO_FAILURE, null));
		assertEquals("the StoreProblem collector must receive events", 1, problems.size());
		problems.clear();

		PortfolioList first = newPortfolioList(bus);
		SqlitePortfolioStore store = newStore(bus, first, new Status());
		store.start();
		store.insertPortfolioDocument(mapper.readTree(PORTFOLIO_JSON));
		store.flush();
		store.load();
		Portfolio p = first.getPortfolios().get(0);
		PairStrategy s = p.getPairStrategies().get(0);

		store.deletePortfolio(p);

		// The beacon keeps flashing after the delete. A still-registered portfolio
		// would answer this one with a PortfolioSyncOutRequest and resurrect itself.
		p.setName("renamed after deletion");
		bus.post(new BeaconFlash(DateTime.now()));

		// And a last PairStateUpdated from a still-stopping core: writing it would
		// violate strategy_state's foreign key on a portfolio that no longer exists.
		bus.post(new PairStateUpdated(s, DateTime.now()));

		store.stop();

		PortfolioList second = newPortfolioList(bus);
		SqlitePortfolioStore reopened = newStore(bus, second, new Status());
		reopened.start();
		try {
			assertTrue("a deleted portfolio must not reappear", second.getPortfolios().isEmpty());
		} finally {
			reopened.stop();
		}

		assertEquals("no StoreProblem may be posted by this sequence: " + problems,
				0, problems.size());
	}
}
