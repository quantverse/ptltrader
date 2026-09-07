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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

/**
 * Owns the SQLite connection and the schema.
 *
 * Not thread safe by design: SqlitePortfolioStore confines every use of this
 * class to its single db-worker thread, exactly as PtlApiClient confined its
 * HTTP writes to rq-worker.
 */
public class Database {
	public static final int SCHEMA_VERSION = 1;

	private final File file;
	private final Logger logger;
	private Connection connection;

	public Database(File file, Logger logger) {
		super();
		this.file = file;
		this.logger = logger;
	}

	public Connection getConnection() {
		return connection;
	}

	public void open() throws SQLException {
		connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
		try (Statement st = connection.createStatement()) {
			st.execute("PRAGMA journal_mode=WAL");
			st.execute("PRAGMA synchronous=FULL");
			st.execute("PRAGMA foreign_keys=ON");
		}
		migrate();
	}

	public int readSchemaVersion() throws SQLException {
		try (Statement st = connection.createStatement()) {
			try (ResultSet rs = st.executeQuery(
					"SELECT name FROM sqlite_master WHERE type='table' AND name='schema_version'")) {
				if (!rs.next()) return 0;
			}
			try (ResultSet rs = st.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
				if (!rs.next()) return 0;
				return rs.getInt(1);
			}
		}
	}

	private void migrate() throws SQLException {
		int current = readSchemaVersion();
		if (current > SCHEMA_VERSION) {
			throw new SQLException(String.format(
					"database %s was written by a newer version of PTL Trader (schema %d, this build understands %d)",
					file.getAbsolutePath(), current, SCHEMA_VERSION));
		}
		if (current == SCHEMA_VERSION) return;

		connection.setAutoCommit(false);
		try {
			if (current < 1) {
				createSchemaV1();
			}
			try (Statement st = connection.createStatement()) {
				st.executeUpdate("DELETE FROM schema_version");
				st.executeUpdate("INSERT INTO schema_version (version) VALUES (" + SCHEMA_VERSION + ")");
			}
			connection.commit();
			logger.info("database schema migrated from version " + current + " to " + SCHEMA_VERSION);
		} catch (SQLException e) {
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(true);
		}
	}

	private void createSchemaV1() throws SQLException {
		try (Statement st = connection.createStatement()) {
			st.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");

			st.executeUpdate("CREATE TABLE IF NOT EXISTS portfolios ("
					+ "uid TEXT PRIMARY KEY,"
					+ "name TEXT NOT NULL,"
					+ "document TEXT NOT NULL,"
					+ "updated_at TEXT NOT NULL)");

			st.executeUpdate("CREATE TABLE IF NOT EXISTS strategy_state ("
					+ "strategy_uid TEXT PRIMARY KEY,"
					+ "portfolio_uid TEXT NOT NULL REFERENCES portfolios(uid) ON DELETE CASCADE,"
					+ "last_opened_datetime TEXT,"
					+ "last_opened_equity REAL,"
					+ "last_model_state TEXT,"
					+ "updated_at TEXT NOT NULL)");

			st.executeUpdate("CREATE TABLE IF NOT EXISTS trade_history ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ "datetime TEXT NOT NULL,"
					+ "account TEXT NOT NULL,"
					+ "stock1 TEXT NOT NULL,"
					+ "stock2 TEXT NOT NULL,"
					+ "action TEXT NOT NULL,"
					+ "realized_pl REAL NOT NULL,"
					+ "realized_pl_pct REAL NOT NULL,"
					+ "commissions REAL NOT NULL,"
					+ "zscore REAL NOT NULL,"
					+ "comment TEXT)");

			st.executeUpdate("CREATE TABLE IF NOT EXISTS leg_history ("
					+ "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					+ "datetime TEXT NOT NULL,"
					+ "account TEXT NOT NULL,"
					+ "symbol TEXT NOT NULL,"
					+ "action TEXT NOT NULL,"
					+ "qty INTEGER NOT NULL,"
					+ "price REAL NOT NULL,"
					+ "value REAL NOT NULL,"
					+ "realized_pl REAL NOT NULL,"
					+ "commissions REAL NOT NULL,"
					+ "slippage REAL NOT NULL,"
					+ "fill_time_ms INTEGER NOT NULL)");

			st.executeUpdate("CREATE INDEX IF NOT EXISTS ix_trade_history_dt ON trade_history (datetime DESC)");
			st.executeUpdate("CREATE INDEX IF NOT EXISTS ix_leg_history_dt ON leg_history (datetime DESC)");
		}
	}

	public void close() {
		if (connection == null) return;
		try {
			connection.close();
		} catch (SQLException e) {
			logger.warn("error closing database: " + e.getMessage());
		}
		connection = null;
	}
}
