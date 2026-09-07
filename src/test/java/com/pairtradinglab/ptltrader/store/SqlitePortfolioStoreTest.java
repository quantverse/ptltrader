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
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

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
}
