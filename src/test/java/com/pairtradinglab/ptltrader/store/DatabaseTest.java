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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabaseTest {
	private File dbFile;
	private Database db;

	@Before
	public void setUp() throws Exception {
		// A real file, not :memory: — WAL and reopen semantics differ.
		dbFile = File.createTempFile("ptltrader-test", ".db");
		assertTrue(dbFile.delete());
		db = new Database(dbFile, mock(Logger.class));
	}

	@After
	public void tearDown() throws Exception {
		if (db != null) db.close();
		dbFile.delete();
		new File(dbFile.getAbsolutePath() + "-wal").delete();
		new File(dbFile.getAbsolutePath() + "-shm").delete();
	}

	private Set<String> tableNames() throws SQLException {
		Set<String> names = new HashSet<String>();
		try (Statement st = db.getConnection().createStatement();
			 ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
			while (rs.next()) names.add(rs.getString(1));
		}
		return names;
	}

	@Test
	public void testOpenCreatesSchema() throws Exception {
		db.open();
		Set<String> tables = tableNames();
		assertTrue(tables.contains("schema_version"));
		assertTrue(tables.contains("portfolios"));
		assertTrue(tables.contains("strategy_state"));
		assertTrue(tables.contains("trade_history"));
		assertTrue(tables.contains("leg_history"));
		assertEquals(Database.SCHEMA_VERSION, db.readSchemaVersion());
	}

	@Test
	public void testOpenIsIdempotent() throws Exception {
		db.open();
		db.close();
		Database again = new Database(dbFile, mock(Logger.class));
		again.open();
		assertEquals(Database.SCHEMA_VERSION, again.readSchemaVersion());
		again.close();
	}

	@Test
	public void testPragmasApplied() throws Exception {
		db.open();
		try (Statement st = db.getConnection().createStatement()) {
			try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
				assertTrue(rs.next());
				assertEquals("wal", rs.getString(1).toLowerCase());
			}
			try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
			}
		}
	}

	@Test
	public void testStrategyStateCascadesOnPortfolioDelete() throws Exception {
		db.open();
		Connection c = db.getConnection();
		try (Statement st = c.createStatement()) {
			st.executeUpdate("INSERT INTO portfolios (uid, name, document, updated_at) "
					+ "VALUES ('P1', 'n', '{}', '2026-01-01T00:00:00Z')");
			st.executeUpdate("INSERT INTO strategy_state (strategy_uid, portfolio_uid, updated_at) "
					+ "VALUES ('S1', 'P1', '2026-01-01T00:00:00Z')");
			st.executeUpdate("DELETE FROM portfolios WHERE uid='P1'");
			try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM strategy_state")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	public void testFutureSchemaVersionIsRejected() throws Exception {
		db.open();
		try (Statement st = db.getConnection().createStatement()) {
			st.executeUpdate("UPDATE schema_version SET version=" + (Database.SCHEMA_VERSION + 1));
		}
		db.close();

		Database newer = new Database(dbFile, mock(Logger.class));
		try {
			newer.open();
			fail("expected SQLException for a database written by a newer build");
		} catch (SQLException expected) {
			assertTrue(expected.getMessage().contains("newer version"));
		} finally {
			newer.close();
		}
	}
}
