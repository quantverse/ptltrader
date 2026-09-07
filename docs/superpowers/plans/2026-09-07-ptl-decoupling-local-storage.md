# PTL Trader Local Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the Pair Trading Lab integration from PTL Trader and replace it with JSON file import plus a local SQLite database that owns portfolios, strategy runtime state and trade history.

**Architecture:** A new `PortfolioStore` component replaces `PtlApiClient` at the persistence seam, subscribing to the same bus events so the dirty-flag sync-out mechanism, the trading engine, the models and the IB adapter are untouched. Portfolio configuration is stored as a JSON document per portfolio, in the exact shape `GET /portfolios` returned, so `PortfolioList.updateFromJson()` keeps working unchanged. Strategy runtime state and history get normalized tables.

**Tech Stack:** Java 11, Gradle 4.x, SWT + JFace databinding, PicoContainer 2.16, Guava EventBus, Jackson 2.8.4, Joda-Time, JUnit 4.12, Mockito 2.2.9, sqlite-jdbc 3.49.1.0.

**Spec:** `docs/superpowers/specs/2026-09-07-ptl-decoupling-local-storage-design.md`

## Global Constraints

- **Build and test with JDK 11 at `/home/karlos/JDK/jdk-11.0.32.1+1`.** Every gradle command must be prefixed `JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1`. This is not optional: on the system default JDK 21, 61 of the 103 existing tests fail and `shadowJar` dies with `Unsupported class file major version 65`.
- **Baseline is 102 passing tests** on JDK 11 (verified by summing `tests=` across `build/test-results/test/TEST-*.xml`: 102 tests, 0 failures, 0 errors, 0 skipped). Verify before starting: `JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test` must succeed. No task may reduce that number except where this plan explicitly deletes a test — Task 9 deletes `StringXorProcessorTest` (2 tests), and that is the only sanctioned reduction.
  > Do not use the count printed by a JDK 21 run. On JDK 21 gradle reports "103 tests completed, 61 failed": the extra one is a synthetic `initializationError` from a class that fails to load, not a real test.
- **Do not pass `--offline` on the first build after adding a dependency.** sqlite-jdbc is not in the local gradle cache and must be downloaded once.
- **Dependency version is exactly `org.xerial:sqlite-jdbc:3.49.1.0`.** Verified working on JDK 11 with WAL, `synchronous=FULL`, foreign keys and `ON CONFLICT ... DO UPDATE` upsert syntax.
- **Work on branch `v2`.** Do not merge to `master`.
- **Every new `.java` file starts with the project's GPL v3 header**, copied verbatim from any existing source file (the 18-line block beginning `This file is part of PTL Trader.` and ending with the GNU licence URL). Referred to below as "the standard GPL header".
- **The trading layer never touches SWT, and the store never touches SWT.** Existing layering rule; keep it.
- **`PicoContainer` matches constructor parameters by name** for components registered with `Characteristics.USE_NAMES`. Parameter names in those constructors are load-bearing — do not rename them casually.
- **Never modify `updateFromJson()` on `Portfolio` or `PairStrategy`.** The whole design depends on the stored document matching what those methods already parse. If a task seems to need a change there, stop and re-read the spec.

## File Structure

| File | Responsibility |
|---|---|
| `…/ptltrader/DataDirectory.java` | Create: resolve the platform per-user data directory; own the db and log paths |
| `…/ptltrader/store/Database.java` | Create: JDBC connection, pragmas, schema creation and versioned migration |
| `…/ptltrader/store/PortfolioStore.java` | Create: the interface `Application` depends on |
| `…/ptltrader/store/SqlitePortfolioStore.java` | Create: the implementation, bus subscriber, `db-worker` thread |
| `…/ptltrader/store/PortfolioDocuments.java` | Create: build documents from beans; splice runtime state in and out |
| `…/ptltrader/store/StrategyState.java` | Create: value object for one strategy's runtime state |
| `…/ptltrader/store/StoreError.java` | Create: local error enum replacing `PtlApiError` |
| `…/ptltrader/store/PortfolioImporter.java` | Create: validate an import file and re-uid it |
| `…/ptltrader/events/StoreProblem.java` | Create: bus event replacing `PtlApiProblem` |
| `…/ptltrader/NewPortfolioDialog.java` | Create: name prompt for a new portfolio |
| `…/ptltrader/AddPairDialog.java` | Create: symbol/instrument/model prompt for a new pair |
| `…/ptltrader/model/PairStrategy.java` | Modify: symmetric Jackson annotations; drop `features` |
| `…/ptltrader/model/Portfolio.java` | Modify: symmetric Jackson annotations; drop `features` |
| `…/ptltrader/Application.java` | Modify: DI wiring, startup flow, File menu, remove PTL/AMQP UI |
| `…/ptltrader/LoggerFactoryImpl.java` | Modify: log to the platform data directory |
| `…/ptltrader/model/Settings.java` | Modify: remove PTL credentials and confidential mode |
| `build.gradle` | Modify: add sqlite-jdbc, remove rabbitmq and async-http-client |

All package paths abbreviate `src/main/java/com/pairtradinglab/ptltrader`.

---

### Task 1: Platform data directory

**Files:**
- Create: `src/main/java/com/pairtradinglab/ptltrader/DataDirectory.java`
- Modify: `src/main/java/com/pairtradinglab/ptltrader/LoggerFactoryImpl.java:46-48`
- Test: `src/test/java/com/pairtradinglab/ptltrader/DataDirectoryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `DataDirectory.resolve(String osName, String userHome, String localAppData): File` — pure, for testing.
  - `DataDirectory.current(): File` — reads real system properties and the `LOCALAPPDATA` environment variable.
  - `DataDirectory.databaseFile(String profile): File` — `current()/<profile>.db`.
  - `DataDirectory.logFile(String profile): File` — `current()/<profile>.log`.
  - `DataDirectory.ensureExists(File dir): void` — creates the directory tree, throws `IOException` if it cannot.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/DataDirectoryTest.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader;

import static org.junit.Assert.*;

import java.io.File;
import org.junit.Test;

public class DataDirectoryTest {

	@Test
	public void testResolveWindowsUsesLocalAppData() {
		File d = DataDirectory.resolve("Windows 10", "C:\\Users\\joe", "C:\\Users\\joe\\AppData\\Local");
		assertEquals(new File("C:\\Users\\joe\\AppData\\Local", "PTLTrader"), d);
	}

	@Test
	public void testResolveWindowsFallsBackWhenLocalAppDataMissing() {
		// LOCALAPPDATA is normally set, but a stripped service environment may not have it.
		File d = DataDirectory.resolve("Windows 10", "C:\\Users\\joe", null);
		assertEquals(new File(new File("C:\\Users\\joe", "AppData"), "Local").getPath(),
				d.getParentFile().getPath());
		assertEquals("PTLTrader", d.getName());
	}

	@Test
	public void testResolveLinux() {
		File d = DataDirectory.resolve("Linux", "/home/joe", null);
		assertEquals(new File("/home/joe/.local/share", "ptltrader"), d);
	}

	@Test
	public void testResolveMac() {
		File d = DataDirectory.resolve("Mac OS X", "/Users/joe", null);
		assertEquals(new File("/Users/joe/Library/Application Support", "PTLTrader"), d);
	}

	@Test
	public void testResolveUnknownOsFallsBackToLinuxLayout() {
		File d = DataDirectory.resolve("SunOS", "/export/home/joe", null);
		assertEquals(new File("/export/home/joe/.local/share", "ptltrader"), d);
	}

	@Test
	public void testDatabaseAndLogFileNamesUseProfile() {
		assertEquals("live.db", DataDirectory.databaseFile("live").getName());
		assertEquals("live.log", DataDirectory.logFile("live").getName());
		assertEquals(DataDirectory.current(), DataDirectory.databaseFile("live").getParentFile());
	}

	@Test
	public void testEnsureExistsCreatesNestedDirectories() throws Exception {
		File base = File.createTempFile("ptltrader-dd", "");
		assertTrue(base.delete());
		File nested = new File(new File(base, "a"), "b");
		DataDirectory.ensureExists(nested);
		assertTrue(nested.isDirectory());
		// Idempotent: a second call on an existing directory must not throw.
		DataDirectory.ensureExists(nested);
		assertTrue(nested.isDirectory());
		nested.delete();
		new File(base, "a").delete();
		base.delete();
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*DataDirectoryTest*'
```

Expected: compilation failure — `cannot find symbol: class DataDirectory`.

- [ ] **Step 3: Write the implementation**

Create `src/main/java/com/pairtradinglab/ptltrader/DataDirectory.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader;

import java.io.File;
import java.io.IOException;

/**
 * Resolves the per-user directory where this application keeps its database and log.
 *
 * Historically the log was written to "$HOME/Application Data/PTLTrader" on every
 * platform, which is a Windows-only convention and wrong on Linux and macOS. The
 * database and the log now live together in the correct per-platform location.
 */
public class DataDirectory {

	private DataDirectory() {
	}

	/**
	 * Pure resolution, parameterised for testing.
	 *
	 * @param osName        value of the "os.name" system property
	 * @param userHome      value of the "user.home" system property
	 * @param localAppData  value of the LOCALAPPDATA environment variable, may be null
	 */
	public static File resolve(String osName, String userHome, String localAppData) {
		String os = osName == null ? "" : osName.toLowerCase();
		if (os.contains("windows")) {
			File base = (localAppData == null || localAppData.isEmpty())
					? new File(new File(userHome, "AppData"), "Local")
					: new File(localAppData);
			return new File(base, "PTLTrader");
		}
		if (os.contains("mac")) {
			return new File(new File(new File(userHome, "Library"), "Application Support"), "PTLTrader");
		}
		// Linux and anything else: the XDG default layout.
		return new File(new File(new File(userHome, ".local"), "share"), "ptltrader");
	}

	public static File current() {
		return resolve(System.getProperty("os.name"), System.getProperty("user.home"), System.getenv("LOCALAPPDATA"));
	}

	public static File databaseFile(String profile) {
		return new File(current(), profile + ".db");
	}

	public static File logFile(String profile) {
		return new File(current(), profile + ".log");
	}

	/**
	 * Creates the directory and any missing parents. Does nothing if it already exists.
	 */
	public static void ensureExists(File dir) throws IOException {
		if (dir.isDirectory()) return;
		if (!dir.mkdirs() && !dir.isDirectory()) {
			throw new IOException("unable to create data directory: " + dir.getAbsolutePath());
		}
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*DataDirectoryTest*'
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Point the logger at the new directory**

In `LoggerFactoryImpl.createLogger()`, replace these two lines:

```java
					String home = System.getProperty("user.home")+ File.separator + "Application Data";
					String logfile = home + File.separator + "PTLTrader" + File.separator + runtimeParams.getProfile() + ".log";
```

with:

```java
					File logTarget = DataDirectory.logFile(runtimeParams.getProfile());
					try {
						DataDirectory.ensureExists(logTarget.getParentFile());
					} catch (IOException e) {
						// Nothing is logged yet, so report on stderr and continue with
						// the console appender only.
						System.err.println("unable to create log directory: " + e.getMessage());
					}
					String logfile = logTarget.getAbsolutePath();
```

- [ ] **Step 6: Run the whole suite to confirm nothing regressed**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
```

Expected: BUILD SUCCESSFUL, the original 103 tests plus the 7 new ones.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/pairtradinglab/ptltrader/DataDirectory.java \
        src/main/java/com/pairtradinglab/ptltrader/LoggerFactoryImpl.java \
        src/test/java/com/pairtradinglab/ptltrader/DataDirectoryTest.java
git commit -m "Add platform data directory and move the log into it

The log was written to \$HOME/Application Data/PTLTrader on every
platform, a Windows convention that is wrong on Linux and macOS. The
database added in the following commits lives beside it, so both now
resolve through DataDirectory."
```

---

### Task 2: Database connection, schema and migration

**Files:**
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/Database.java`
- Modify: `build.gradle` (dependencies block)
- Test: `src/test/java/com/pairtradinglab/ptltrader/store/DatabaseTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces:
  - `Database.SCHEMA_VERSION` — `int`, currently `1`.
  - `new Database(File file, Logger logger)`
  - `Database.open(): void` — connects, applies pragmas, creates or migrates the schema. Throws `SQLException`.
  - `Database.getConnection(): Connection`
  - `Database.close(): void`
  - `Database.readSchemaVersion(): int`
  - Tables `portfolios`, `strategy_state`, `trade_history`, `leg_history` as defined in spec §5.2. Tasks 5 and 7 read and write them.

- [ ] **Step 1: Add the dependency**

In `build.gradle`, inside the `dependencies` block, add this line immediately before
the `testImplementation 'org.mockito:mockito-core:2.2.9'` line:

```groovy
    implementation 'org.xerial:sqlite-jdbc:3.49.1.0'
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/store/DatabaseTest.java` with the standard GPL header, then:

```java
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
```

- [ ] **Step 3: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --tests '*DatabaseTest*'
```

Note: no `--offline` here — sqlite-jdbc must be downloaded.
Expected: compilation failure — `cannot find symbol: class Database`.

- [ ] **Step 4: Write the implementation**

Create `src/main/java/com/pairtradinglab/ptltrader/store/Database.java` with the standard GPL header, then:

```java
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
```

- [ ] **Step 5: Run test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --tests '*DatabaseTest*'
```

Expected: BUILD SUCCESSFUL, 5 tests.

- [ ] **Step 6: Run the whole suite**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add build.gradle \
        src/main/java/com/pairtradinglab/ptltrader/store/Database.java \
        src/test/java/com/pairtradinglab/ptltrader/store/DatabaseTest.java
git commit -m "Add SQLite database with versioned schema

Schema version 1: portfolios holding a JSON config document, plus
normalized strategy_state, trade_history and leg_history. Opening a
database written by a newer build fails loudly rather than silently
downgrading it."
```

---

### Task 3: Symmetric serialization on the model beans

This is the highest-risk task in the plan. `PairStrategy` currently annotates its
model parameters `@JsonIgnore`, because PTL owned them and the trader only ever
wrote back rules, hours, margins and status. With the trader as master they must
persist, and a single missed annotation silently loses a parameter on the next
restart. The round-trip test below is what prevents that.

`features` is removed from both beans here rather than in Task 9, because it is the
same edit to the same fields and splitting it would mean touching them twice.

**Files:**
- Modify: `src/main/java/com/pairtradinglab/ptltrader/model/PairStrategy.java`
- Modify: `src/main/java/com/pairtradinglab/ptltrader/model/Portfolio.java`
- Test: `src/test/java/com/pairtradinglab/ptltrader/model/SerializationRoundTripTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `mapper.valueToTree(pairStrategy)` emits every field `PairStrategy.updateFromJson()` reads except `last_opened_datetime`, `last_opened_equity` and `last_model_state`. `mapper.valueToTree(portfolio)` emits every field `Portfolio.updateFromJson()` reads except `strategies`. Task 4 relies on both.
- Also produces: `PairStrategy.getFeatures()` and `Portfolio.getFeatures()` no longer exist; `SupportedFeatures` still exists until Task 9.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/model/SerializationRoundTripTest.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;
import com.pairtradinglab.ptltrader.RuntimeParams;

/**
 * The trader is now the master store for strategy configuration, so every field
 * updateFromJson() reads must survive a serialize/parse round trip. A field that
 * is parsed but not written is lost on the next restart, with no error.
 */
public class SerializationRoundTripTest {

	/** Field names compared as text. */
	private static final String[] TEXT_FIELDS = {
		"uid", "model", "ticker1", "ticker2", "timezone"
	};

	/**
	 * Field names compared numerically. asDouble() coerces booleans to 1.0/0.0,
	 * so this also covers the enable_* flags, which are ints in the document and
	 * booleans on the bean.
	 */
	private static final String[] NUMERIC_FIELDS = {
		"trade_as_1", "trade_as_2",
		"entry_threshold", "exit_threshold", "downtick_threshold", "max_score",
		"ratio_ma_type", "ratio_ma_period", "ratio_stddev_period", "ratio_entry_mode",
		"ratio_rsi_period", "ratio_rsi_threshold", "residual_linreg_period",
		"neutrality", "ka_ve", "ka_usage_target",
		"ticker1margin", "ticker2margin",
		"allow_reversals",
		"enable_max_days", "max_days",
		"enable_min_pl", "min_pl",
		"enable_min_price", "min_price",
		"enable_min_profit_potential", "min_profit_potential",
		"entry_start_hour", "entry_start_minute", "entry_end_hour", "entry_end_minute",
		"exit_start_hour", "exit_start_minute", "exit_end_hour", "exit_end_minute",
		"allow_positions", "status", "slot_occupation"
	};

	private static final String STRATEGY_JSON = "{"
		+ "\"uid\":\"Sssfqqf1f11l2Jk4\",\"model\":\"Kalman-auto\","
		+ "\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\",\"trade_as_1\":0,\"trade_as_2\":1,"
		+ "\"entry_threshold\":2.5,\"exit_threshold\":0.25,\"downtick_threshold\":0.75,\"max_score\":9.0,"
		+ "\"ratio_ma_type\":1,\"ratio_ma_period\":17,\"ratio_stddev_period\":13,\"ratio_entry_mode\":2,"
		+ "\"ratio_rsi_period\":11,\"ratio_rsi_threshold\":25.0,\"residual_linreg_period\":33,"
		+ "\"neutrality\":1,\"ka_ve\":0.002,\"ka_usage_target\":55.0,"
		+ "\"ticker1margin\":40.0,\"ticker2margin\":60.0,"
		+ "\"last_opened_datetime\":null,\"last_opened_equity\":null,\"last_model_state\":null,"
		+ "\"allow_reversals\":1,\"enable_max_days\":1,\"max_days\":22,"
		+ "\"enable_min_pl\":0,\"min_pl\":1.5,\"enable_min_price\":1,\"min_price\":6.0,"
		+ "\"enable_min_profit_potential\":1,\"min_profit_potential\":45.0,"
		+ "\"entry_start_hour\":9,\"entry_start_minute\":45,\"entry_end_hour\":15,\"entry_end_minute\":55,"
		+ "\"exit_start_hour\":10,\"exit_start_minute\":5,\"exit_end_hour\":15,\"exit_end_minute\":57,"
		+ "\"timezone\":\"America/Chicago\",\"allow_positions\":1,\"status\":1,\"slot_occupation\":0.5"
		+ "}";

	private static final String PORTFOLIO_JSON = "{"
		+ "\"uid\":\"Uuzfqqf1f11l2Jk4\",\"name\":\"My Portfolio\",\"account_code\":\"DU123456\","
		+ "\"max_pairs_open\":7,\"master_status\":1,\"pdt_rules\":0,\"account_alloc\":80,"
		+ "\"strategies\":[]}";

	private ObjectMapper mapper;
	private Portfolio portfolio;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		portfolio = new Portfolio(null, null, lf, "Uuzfqqf1f11l2Jk4");
	}

	private PairStrategy buildStrategy(JsonNode n) {
		PairStrategy s = new PairStrategy(n.get("uid").asText(), portfolio,
				n.get("ticker1").asText(), n.get("ticker2").asText(),
				n.get("trade_as_1").asInt(), n.get("trade_as_2").asInt(), null, null);
		s.updateFromJson(n);
		return s;
	}

	@Test
	public void testStrategyConfigSurvivesRoundTrip() throws Exception {
		JsonNode original = mapper.readTree(STRATEGY_JSON);
		JsonNode out = mapper.valueToTree(buildStrategy(original));

		for (String f : TEXT_FIELDS) {
			assertTrue("serialized strategy is missing field: " + f, out.has(f));
			assertEquals("field " + f, original.get(f).asText(), out.get(f).asText());
		}
		for (String f : NUMERIC_FIELDS) {
			assertTrue("serialized strategy is missing field: " + f, out.has(f));
			assertEquals("field " + f, original.get(f).asDouble(), out.get(f).asDouble(), 1e-9);
		}
	}

	@Test
	public void testStrategyReparsesToAnIdenticalBean() throws Exception {
		JsonNode original = mapper.readTree(STRATEGY_JSON);
		PairStrategy first = buildStrategy(original);

		// Serialize, re-parse, and confirm the observable configuration matches.
		ObjectNode serialized = mapper.valueToTree(first);
		serialized.putNull("last_opened_datetime");
		serialized.putNull("last_opened_equity");
		serialized.putNull("last_model_state");
		PairStrategy second = buildStrategy(serialized);

		assertEquals(first.getModel(), second.getModel());
		assertEquals(first.getEntryThreshold(), second.getEntryThreshold(), 1e-9);
		assertEquals(first.getExitThreshold(), second.getExitThreshold(), 1e-9);
		assertEquals(first.getRatioMaType(), second.getRatioMaType());
		assertEquals(first.getRatioMaPeriod(), second.getRatioMaPeriod());
		assertEquals(first.getNeutrality(), second.getNeutrality());
		assertEquals(first.getKalmanAutoVe(), second.getKalmanAutoVe(), 1e-9);
		assertEquals(first.getTimezoneId(), second.getTimezoneId());
		assertEquals(first.getSlotOccupation(), second.getSlotOccupation(), 1e-9);
		assertEquals(first.getTradingStatus(), second.getTradingStatus());
		assertEquals(first.isAllowReversals(), second.isAllowReversals());
		assertEquals(first.getStock1(), second.getStock1());
		assertEquals(first.getTradeAs2(), second.getTradeAs2());
	}

	@Test
	public void testRatioMaTypeIsSerializedAsOrdinalNotName() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		assertTrue("ratio_ma_type must serialize as a numeric ordinal, because "
				+ "updateFromJson reads it with MAType.values()[node.asInt()]",
				out.get("ratio_ma_type").isNumber());
		assertEquals(1, out.get("ratio_ma_type").asInt());
	}

	@Test
	public void testRuntimeStateIsNotPartOfTheDocument() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		assertFalse("runtime state belongs to strategy_state, not the document",
				out.has("last_opened_datetime"));
		assertFalse(out.has("last_opened_equity"));
		assertFalse(out.has("last_model_state"));
	}

	@Test
	public void testDerivedFieldsAreNotSerialized() throws Exception {
		JsonNode out = mapper.valueToTree(buildStrategy(mapper.readTree(STRATEGY_JSON)));
		for (String f : new String[] { "positions", "core", "parent", "portfolio", "dirty",
				"initialized", "coreStatus", "daysRemaining", "profitPotential",
				"zscoreBid", "zscoreAsk", "closeable", "openable", "deletable", "resumable",
				"features", "syncOutEnabled", "modelState", "lastOpened" }) {
			assertFalse("derived/runtime field must not be serialized: " + f, out.has(f));
		}
	}

	@Test
	public void testPortfolioConfigSurvivesRoundTrip() throws Exception {
		JsonNode original = mapper.readTree(PORTFOLIO_JSON);
		portfolio.updateFromJson(original);
		JsonNode out = mapper.valueToTree(portfolio);

		assertEquals("Uuzfqqf1f11l2Jk4", out.get("uid").asText());
		assertEquals("My Portfolio", out.get("name").asText());
		assertEquals("DU123456", out.get("account_code").asText());
		assertEquals(7, out.get("max_pairs_open").asInt());
		assertEquals(1, out.get("master_status").asInt());
		assertEquals(0, out.get("pdt_rules").asInt());
		assertEquals(80, out.get("account_alloc").asInt());
		assertFalse("strategies are added by PortfolioDocuments, not by bean serialization",
				out.has("strategies"));
		assertFalse(out.has("features"));
		assertFalse(out.has("pairStrategies"));
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*SerializationRoundTripTest*'
```

Expected: FAIL. `testStrategyConfigSurvivesRoundTrip` reports
`serialized strategy is missing field: uid` — the current bean serializes only the
rules, hours, margins and status subset.

- [ ] **Step 3: Annotate `PairStrategy`**

In `src/main/java/com/pairtradinglab/ptltrader/model/PairStrategy.java`:

a. Add the import:

```java
import com.fasterxml.jackson.annotation.JsonGetter;
```

b. Change these field annotations from `@JsonIgnore` to the named property. Match on
the field declaration rather than the line number:

```java
	@JsonProperty("uid")
	private final String uid;
	@JsonProperty("ticker1")
	private volatile String stock1;
	@JsonProperty("ticker2")
	private volatile String stock2;
	@JsonProperty("trade_as_1")
	private volatile int tradeAs1;
	@JsonProperty("trade_as_2")
	private volatile int tradeAs2;
	@JsonProperty("model")
	private volatile String model=MODEL_RATIO;
	@JsonProperty("entry_threshold")
	private volatile double entryThreshold=2;
	@JsonProperty("exit_threshold")
	private volatile double exitThreshold=0;
	@JsonProperty("downtick_threshold")
	private volatile double downtickThreshold=0;
	@JsonProperty("max_score")
	private volatile double maxEntryScore=6;
	@JsonProperty("ratio_ma_period")
	private volatile int ratioMaPeriod=15;
	@JsonProperty("ratio_stddev_period")
	private volatile int ratioStdDevPeriod=15;
	@JsonProperty("residual_linreg_period")
	private volatile int residualLinRegPeriod=30;
	@JsonProperty("ratio_entry_mode")
	private volatile int entryMode=PairTradingModel.ENTRY_MODE_SIMPLE;
	@JsonProperty("ratio_rsi_period")
	private volatile int ratioRsiPeriod=10;
	@JsonProperty("ratio_rsi_threshold")
	private volatile double ratioRsiThreshold=0;
	@JsonProperty("ka_ve")
	private volatile double kalmanAutoVe = 0.001;
	@JsonProperty("ka_usage_target")
	private volatile double kalmanAutoUsageTarget = 60;
	@JsonProperty("neutrality")
	private volatile int neutrality = NEUTRALITY_DOLLAR;
	@JsonProperty("timezone")
	private volatile String timezoneId = "America/New_York";
```

c. `ratioMaType` keeps `@JsonIgnore` on the field, because the enum must be written
as its ordinal:

```java
	@JsonIgnore
	private volatile MAType ratioMaType=MAType.Sma;
```

Add this method next to `getRatioMaType()`:

```java
	/**
	 * updateFromJson reads this with MAType.values()[node.asInt()], so it must be
	 * written as the ordinal and never as the enum name.
	 */
	@JsonGetter("ratio_ma_type")
	public int getRatioMaTypeOrdinal() {
		return ratioMaType.ordinal();
	}
```

d. Leave `@JsonIgnore` in place on `positions`, `stockDisp1`, `stockDisp2`,
`zscoreBid`, `zscoreBidS`, `zscoreAsk`, `zscoreAskS`, `rsi`, `rsiS`, `pl`, `plS`,
`lastOpened`, `modelState`, `lastOpenedS`, `daysRemaining`, `status`, `coreStatus`,
`profitPotential`, `profitPotentialS`, `lastOpenEquity`, `closeable`, `openable`,
`deletable`, `resumable`, `parent`, `core`, `initialized` and `dirty`.

e. Delete the `features` field:

```java
	@JsonIgnore
	private final List<String> features = new CopyOnWriteArrayList<String>();
```

together with any `getFeatures()` / `hasFeature()` methods, and the block at the end
of `updateFromJson()` that populates it:

```java
		Iterator<JsonNode> ite = n.path("features").elements();
		features.clear();
		while (ite.hasNext()) {
			String fea = ite.next().asText();
			features.add(fea);
		}
```

f. `syncOutEnabled` currently carries no annotation and would serialize. Mark it:

```java
	@JsonIgnore
	private volatile boolean syncOutEnabled=true;
```

g. `getPortfolio()` would serialize the parent and recurse. Confirm it carries
`@JsonIgnore`; add it if not:

```java
	@JsonIgnore
	public Portfolio getPortfolio() {
```

- [ ] **Step 4: Annotate `Portfolio`**

In `src/main/java/com/pairtradinglab/ptltrader/model/Portfolio.java`:

a. Change:

```java
	@JsonProperty("uid")
	private final String uid;
```

b. `name` already serializes as `name`; leave it.

c. Mark `syncOutEnabled` so it cannot leak into the document:

```java
	@JsonIgnore
	private volatile boolean syncOutEnabled=true;
```

d. Confirm `pairStrategies` keeps its `@JsonIgnore`, and add the same to the getter
so the property cannot reappear through it:

```java
	@JsonIgnore
	public List<PairStrategy> getPairStrategies() {
```

e. Delete the `features` field, its accessors, and the block in `updateFromJson()`
that fills it:

```java
		Iterator<JsonNode> ite = r.path("features").elements();
		features.clear();
		while (ite.hasNext()) {
			String fea = ite.next().asText();
			features.add(fea);
		}
```

f. Remove imports the compiler now reports as unused. Do not remove imports the file
still uses.

- [ ] **Step 5: Run the test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*SerializationRoundTripTest*'
```

Expected: BUILD SUCCESSFUL, 6 tests.

If `testDerivedFieldsAreNotSerialized` fails naming a field this plan did not
mention, that field is newly leaking — add `@JsonIgnore` to it rather than removing
it from the assertion list.

- [ ] **Step 6: Fix the fallout from removing `features`**

Removing `features` breaks the bind-time gate in `Application.java` (the
"This portfolio uses features not supported in this PTL Trader version." dialog near
line 734) and any `SupportedFeatures` references in the model layer. Delete the
gate's condition and its dialog branch; leave `SupportedFeatures.java` itself for
Task 9.

Compile to find every call site:

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew compileJava --offline
```

Fix each reported error, then re-run until the compile is clean.

- [ ] **Step 7: Run the whole suite**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
```

Expected: BUILD SUCCESSFUL, no regression in the pre-existing tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/pairtradinglab/ptltrader/model/PairStrategy.java \
        src/main/java/com/pairtradinglab/ptltrader/model/Portfolio.java \
        src/main/java/com/pairtradinglab/ptltrader/Application.java \
        src/test/java/com/pairtradinglab/ptltrader/model/SerializationRoundTripTest.java
git commit -m "Make model bean serialization symmetric with updateFromJson

Strategy model parameters were @JsonIgnore because PTL owned them and
the trader only wrote back rules, hours and status. With the trader as
master they must persist, so a parsed-but-unwritten field would be lost
on the next restart. The round-trip test asserts every field
updateFromJson reads is also written.

ratio_ma_type needs an explicit ordinal getter: updateFromJson resolves
it with MAType.values()[n.asInt()], so the enum name would not parse.

Also drops the features flag lists, which only ever gated binding
against a server-supplied capability set."
```

---

### Task 4: Document assembly and runtime state splicing

**Files:**
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/StrategyState.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioDocuments.java`
- Test: `src/test/java/com/pairtradinglab/ptltrader/store/PortfolioDocumentsTest.java`

**Interfaces:**
- Consumes: symmetric serialization from Task 3.
- Produces:
  - `StrategyState` — public final fields `strategyUid` (String), `lastOpenedDatetime` (String, or null), `lastOpenedEquity` (Double, or null), `lastModelState` (String of JSON, or null); constructor in that order; `static StrategyState fromStrategy(PairStrategy s, ObjectMapper mapper)`; `DATETIME_FORMAT` constant.
  - `PortfolioDocuments.build(Portfolio p, ObjectMapper mapper): ObjectNode` — a config document with a `strategies` array and no runtime state.
  - `PortfolioDocuments.splice(JsonNode document, Map<String,StrategyState> states, ObjectMapper mapper): ObjectNode` — a copy with the three runtime fields set on every strategy node, null-filled where no state row exists.
  - `PortfolioDocuments.STATE_FIELDS: String[]` — the three runtime field names.
  - Tasks 5 and 8 call all of these.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/store/PortfolioDocumentsTest.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.LoggerFactory;
import com.pairtradinglab.ptltrader.LoggerFactoryImpl;
import com.pairtradinglab.ptltrader.RuntimeParams;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;

public class PortfolioDocumentsTest {

	private static final String STRATEGY_JSON = "{"
		+ "\"uid\":\"S1\",\"model\":\"Ratio\",\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\","
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
		+ "\"strategies\":[]}";

	private ObjectMapper mapper;
	private Portfolio portfolio;

	@Before
	public void setUp() throws Exception {
		mapper = new ObjectMapper();
		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		portfolio = new Portfolio(null, null, lf, "P1");
		portfolio.updateFromJson(mapper.readTree(PORTFOLIO_JSON));

		JsonNode sn = mapper.readTree(STRATEGY_JSON);
		PairStrategy s = new PairStrategy("S1", portfolio, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		s.updateFromJson(sn);
		portfolio.addPairStrategy(s);
	}

	@Test
	public void testBuildProducesADocumentWithNestedStrategies() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);

		assertEquals("P1", doc.get("uid").asText());
		assertEquals("My Portfolio", doc.get("name").asText());
		assertEquals(10, doc.get("max_pairs_open").asInt());
		assertTrue(doc.get("strategies").isArray());
		assertEquals(1, doc.get("strategies").size());
		assertEquals("S1", doc.get("strategies").get(0).get("uid").asText());
		assertEquals("NYSE:V", doc.get("strategies").get(0).get("ticker1").asText());
	}

	@Test
	public void testBuildOmitsRuntimeState() throws Exception {
		JsonNode s = PortfolioDocuments.build(portfolio, mapper).get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertFalse("build() must not emit runtime state: " + f, s.has(f));
		}
	}

	@Test
	public void testSpliceNullFillsWhenNoStateRowExists() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		ObjectNode spliced = PortfolioDocuments.splice(doc, new HashMap<String, StrategyState>(), mapper);

		JsonNode s = spliced.get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertTrue("updateFromJson calls n.get(\"" + f + "\") and would NPE if absent", s.has(f));
			assertTrue("absent state must splice as null, not be omitted", s.get(f).isNull());
		}
	}

	@Test
	public void testSpliceInjectsState() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", "2026-03-01 14:30:00", Double.valueOf(12345.75),
				"{\"@class\":\".PairTradingModelKalmanAutoState\",\"ve\":0.001}"));

		JsonNode s = PortfolioDocuments.splice(doc, states, mapper).get("strategies").get(0);
		assertEquals("2026-03-01 14:30:00", s.get("last_opened_datetime").asText());
		assertEquals(12345.75, s.get("last_opened_equity").asDouble(), 1e-9);
		assertTrue("model state must splice as an object, not a JSON string",
				s.get("last_model_state").isObject());
		assertEquals(".PairTradingModelKalmanAutoState",
				s.get("last_model_state").get("@class").asText());
	}

	@Test
	public void testSpliceDoesNotMutateTheInputDocument() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", "2026-03-01 14:30:00", Double.valueOf(1.0), null));

		PortfolioDocuments.splice(doc, states, mapper);
		assertFalse("splice must return a copy",
				doc.get("strategies").get(0).has("last_opened_datetime"));
	}

	@Test
	public void testCorruptModelStateSplicesAsNullRatherThanFailing() throws Exception {
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		Map<String, StrategyState> states = new HashMap<String, StrategyState>();
		states.put("S1", new StrategyState("S1", null, null, "{ this is not json"));

		JsonNode s = PortfolioDocuments.splice(doc, states, mapper).get("strategies").get(0);
		assertTrue("a corrupt blob must not prevent the portfolio loading",
				s.get("last_model_state").isNull());
	}

	@Test
	public void testSplicedDocumentIsParseableByUpdateFromJson() throws Exception {
		// The whole point: a stored document plus its state must feed updateFromJson.
		ObjectNode doc = PortfolioDocuments.build(portfolio, mapper);
		ObjectNode spliced = PortfolioDocuments.splice(doc, new HashMap<String, StrategyState>(), mapper);

		LoggerFactory lf = new LoggerFactoryImpl(new RuntimeParams(new String[] { "unittest" }));
		Portfolio reloaded = new Portfolio(null, null, lf, "P1");
		reloaded.updateFromJson(spliced);
		assertEquals("My Portfolio", reloaded.getName());
		assertEquals(10, reloaded.getMaxPairs());
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*PortfolioDocumentsTest*'
```

Expected: compilation failure — `cannot find symbol: class PortfolioDocuments`.

- [ ] **Step 3: Write `StrategyState`**

Create `src/main/java/com/pairtradinglab/ptltrader/store/StrategyState.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairtradinglab.ptltrader.model.PairStrategy;

/**
 * One strategy's runtime state: what it needs in order to resume an open position
 * after a restart. Stored in its own table because it is written on a different
 * cadence from configuration, and losing it is the one persistence failure that
 * has a monetary cost.
 */
public class StrategyState {
	/**
	 * The format PairStrategy.updateFromJson() parses, in UTC. Must match exactly.
	 */
	public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

	public final String strategyUid;
	public final String lastOpenedDatetime;
	public final Double lastOpenedEquity;
	public final String lastModelState;

	public StrategyState(String strategyUid, String lastOpenedDatetime,
			Double lastOpenedEquity, String lastModelState) {
		super();
		this.strategyUid = strategyUid;
		this.lastOpenedDatetime = lastOpenedDatetime;
		this.lastOpenedEquity = lastOpenedEquity;
		this.lastModelState = lastModelState;
	}

	public static StrategyState fromStrategy(PairStrategy s, ObjectMapper mapper) {
		String dt = null;
		if (s.getLastOpened() != null) {
			DateTimeFormatter fmt = DateTimeFormat.forPattern(DATETIME_FORMAT);
			dt = s.getLastOpened().withZone(DateTimeZone.UTC).toString(fmt);
		}
		String state = null;
		if (s.getModelState() != null) {
			try {
				state = mapper.writeValueAsString(s.getModelState());
			} catch (JsonProcessingException e) {
				throw new IllegalStateException(
						"unable to serialize model state for strategy " + s.getUid(), e);
			}
		}
		return new StrategyState(s.getUid(), dt, Double.valueOf(s.getLastOpenEquity()), state);
	}
}
```

- [ ] **Step 4: Write `PortfolioDocuments`**

Create `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioDocuments.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;

/**
 * Converts between the observable model beans and the stored JSON document.
 *
 * The document has the same shape the PTL REST API used to return, which is what
 * Portfolio.updateFromJson() and PairStrategy.updateFromJson() parse. Runtime state
 * is deliberately absent from the stored document and is spliced back in on load
 * from the strategy_state table.
 */
public class PortfolioDocuments {

	/** Runtime fields that live in strategy_state, never in the document. */
	public static final String[] STATE_FIELDS = {
		"last_opened_datetime", "last_opened_equity", "last_model_state"
	};

	private PortfolioDocuments() {
	}

	/**
	 * Serializes a portfolio and its strategies into a configuration document.
	 * The result contains no runtime state.
	 */
	public static ObjectNode build(Portfolio p, ObjectMapper mapper) {
		ObjectNode doc = mapper.valueToTree(p);
		ArrayNode strategies = doc.putArray("strategies");
		for (PairStrategy s : p.getPairStrategies()) {
			strategies.add(mapper.<ObjectNode>valueToTree(s));
		}
		return doc;
	}

	/**
	 * Returns a copy of the document with runtime state applied to each strategy.
	 *
	 * Every strategy node gets all three state fields, explicitly null where no
	 * state row exists: PairStrategy.updateFromJson() reads them with n.get(...),
	 * which returns null for an absent field and would throw.
	 */
	public static ObjectNode splice(JsonNode document, Map<String, StrategyState> states, ObjectMapper mapper) {
		ObjectNode copy = document.deepCopy();
		JsonNode strategies = copy.get("strategies");
		if (strategies == null || !strategies.isArray()) return copy;

		for (JsonNode node : strategies) {
			ObjectNode s = (ObjectNode) node;
			StrategyState st = states.get(s.path("uid").asText());
			if (st == null) {
				s.putNull("last_opened_datetime");
				s.putNull("last_opened_equity");
				s.putNull("last_model_state");
				continue;
			}
			if (st.lastOpenedDatetime == null) s.putNull("last_opened_datetime");
			else s.put("last_opened_datetime", st.lastOpenedDatetime);

			if (st.lastOpenedEquity == null) s.putNull("last_opened_equity");
			else s.put("last_opened_equity", st.lastOpenedEquity.doubleValue());

			if (st.lastModelState == null) {
				s.putNull("last_model_state");
			} else {
				try {
					// Stored as JSON text; updateFromJson expects a nested object.
					s.set("last_model_state", mapper.readTree(st.lastModelState));
				} catch (IOException e) {
					// A corrupt state blob must not prevent the portfolio loading.
					// The strategy resumes as if it had no stored state.
					s.putNull("last_model_state");
				}
			}
		}
		return copy;
	}
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*PortfolioDocumentsTest*'
```

Expected: BUILD SUCCESSFUL, 7 tests.

- [ ] **Step 6: Run the whole suite and commit**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
git add src/main/java/com/pairtradinglab/ptltrader/store/StrategyState.java \
        src/main/java/com/pairtradinglab/ptltrader/store/PortfolioDocuments.java \
        src/test/java/com/pairtradinglab/ptltrader/store/PortfolioDocumentsTest.java
git commit -m "Add portfolio document assembly and runtime state splicing

Configuration is stored without runtime state; state is spliced back
in on load. Absent state splices as explicit nulls because
updateFromJson reads those fields unconditionally, and a corrupt state
blob degrades to null rather than blocking the whole portfolio."
```

---

### Task 5: The SQLite portfolio store

**Files:**
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/StoreError.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/events/StoreProblem.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioStore.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStore.java`
- Test: `src/test/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStoreTest.java`

**Interfaces:**
- Consumes: `Database` (Task 2), `PortfolioDocuments` and `StrategyState` (Task 4).
- Produces:
  - `StoreError` — enum `BIND_DENIED`, `IO_FAILURE`, `INVALID_IMPORT`, each with a human-readable `toString()`.
  - `StoreProblem` — public final fields `origin` (String), `error` (StoreError), `detail` (String).
  - `PortfolioStore` interface: `load()`, `savePortfolio(Portfolio)`, `saveStrategyState(PairStrategy)`, `deleteStrategy(PairStrategy)`, `deletePortfolio(Portfolio)`, `insertPortfolioDocument(JsonNode)`, `bindPortfolioToAccount(Portfolio, String)`, `flush()`.
  - `SqlitePortfolioStore implements PortfolioStore, Startable`; constructor `(EventBus bus, LoggerFactory loggerFactory, RuntimeParams runtimeParams, PortfolioList portfolioList, Status status, LegHistory legHistory, TradeHistory tradeHistory)` — parameter names are load-bearing for PicoContainer `USE_NAMES`.
  - Package-visible statics used by tests and Task 7: `upsertDocument`, `upsertStrategyState`, `readStrategyStates`, `readDocuments`, `deletePortfolioRow`, `deleteStrategyStateRow`.
  - Task 6 wires it; Task 7 adds history; Task 8 calls `insertPortfolioDocument`, `deletePortfolio` and `savePortfolio`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStoreTest.java` with the standard GPL header, then:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*SqlitePortfolioStoreTest*'
```

Expected: compilation failure — `cannot find symbol: class SqlitePortfolioStore`.

- [ ] **Step 3: Write `StoreError` and `StoreProblem`**

Create `src/main/java/com/pairtradinglab/ptltrader/store/StoreError.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

/**
 * Local persistence failures, replacing the former PtlApiError.
 */
public enum StoreError {
	BIND_DENIED("There is another portfolio already bound to this account."),
	IO_FAILURE("Unable to read or write the local database."),
	INVALID_IMPORT("The selected file is not a valid portfolio export.");

	private final String message;

	private StoreError(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return message;
	}
}
```

Create `src/main/java/com/pairtradinglab/ptltrader/events/StoreProblem.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.events;

import com.pairtradinglab.ptltrader.store.StoreError;

public class StoreProblem {
	public final String origin;
	public final StoreError error;
	public final String detail;

	public StoreProblem(String origin, StoreError error, String detail) {
		super();
		this.origin = origin;
		this.error = error;
		this.detail = detail;
	}
}
```

- [ ] **Step 4: Write the `PortfolioStore` interface**

Create `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioStore.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.model.Portfolio;

/**
 * Local replacement for PtlApiClient: owns loading and persisting portfolios,
 * strategy runtime state and history.
 */
public interface PortfolioStore {

	/** Loads every portfolio into the PortfolioList and initializes it. */
	void load();

	/** Queues a configuration save for one portfolio and its strategies. */
	void savePortfolio(Portfolio p);

	/** Queues a runtime state save for one strategy. */
	void saveStrategyState(PairStrategy s);

	/** Queues removal of one strategy's state and rewrites its portfolio document. */
	void deleteStrategy(PairStrategy s);

	/** Queues removal of a whole portfolio and its strategy state. */
	void deletePortfolio(Portfolio p);

	/**
	 * Inserts a validated, freshly re-uid'd portfolio document and reloads the
	 * PortfolioList so the new portfolio appears in the UI.
	 */
	void insertPortfolioDocument(JsonNode document);

	/**
	 * Binds a portfolio to an IB account, enforcing that no other portfolio holds
	 * the same account. Posts StoreProblem(BIND_DENIED) and does nothing on conflict.
	 */
	void bindPortfolioToAccount(Portfolio p, String accountCode);

	/** Blocks until every queued write has been applied. For shutdown and tests. */
	void flush();
}
```

- [ ] **Step 5: Write `SqlitePortfolioStore`**

Create `src/main/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStore.java` with the standard GPL header, then:

```java
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
 * threads never touch JDBC. Unlike rq-worker it does not retry forever — against
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
		flush();
		load();
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
```

> If `PortfolioSyncOutRequest` or `StrategySyncOutRequest` name their field
> something other than `p` / `s`, correct the two subscriber bodies. Check with
> `grep -n "public final" src/main/java/com/pairtradinglab/ptltrader/events/PortfolioSyncOutRequest.java src/main/java/com/pairtradinglab/ptltrader/events/StrategySyncOutRequest.java`

- [ ] **Step 6: Run the test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*SqlitePortfolioStoreTest*'
```

Expected: BUILD SUCCESSFUL, 7 tests.

- [ ] **Step 7: Run the whole suite and commit**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
git add src/main/java/com/pairtradinglab/ptltrader/store/ \
        src/main/java/com/pairtradinglab/ptltrader/events/StoreProblem.java \
        src/test/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStoreTest.java
git commit -m "Add SqlitePortfolioStore

Replaces PtlApiClient's persistence responsibilities. A single
db-worker thread owns the connection, mirroring the rq-worker pattern,
but retries three times rather than forever: against a local file
endless retry would hide a failing disk instead of surfacing it."
```

---

### Task 6: Wire the store in and delete `PtlApiClient`

**Files:**
- Modify: `src/main/java/com/pairtradinglab/ptltrader/Application.java`
- Delete: `src/main/java/com/pairtradinglab/ptltrader/PtlApiClient.java`
- Delete: `src/main/java/com/pairtradinglab/ptltrader/PtlApiError.java`
- Delete: `src/main/java/com/pairtradinglab/ptltrader/events/PtlApiConnect.java`
- Delete: `src/main/java/com/pairtradinglab/ptltrader/events/PtlApiProblem.java`
- Modify: `src/main/java/com/pairtradinglab/ptltrader/model/PortfolioFactoryImpl.java` (unused `PtlApiClient` import)

**Interfaces:**
- Consumes: `SqlitePortfolioStore` and `PortfolioStore` from Task 5.
- Produces: an application that starts from the local database with no network calls to PTL. Task 8 adds the File menu actions on top of this.

- [ ] **Step 1: Replace the injected dependency**

In `Application.java`, replace the field:

```java
	private final PtlApiClient apiClient;
```

with:

```java
	private final PortfolioStore portfolioStore;
```

In the constructor signature replace the parameter `PtlApiClient apiClient` with
`PortfolioStore portfolioStore`, and the assignment `this.apiClient = apiClient;`
with `this.portfolioStore = portfolioStore;`.

Add the imports:

```java
import com.pairtradinglab.ptltrader.store.PortfolioStore;
import com.pairtradinglab.ptltrader.store.SqlitePortfolioStore;
import com.pairtradinglab.ptltrader.events.StoreProblem;
```

and remove the `PtlApiProblem` import.

- [ ] **Step 2: Rewire the container**

In `main()`, replace:

```java
					pico.as(Characteristics.USE_NAMES).addComponent(PtlApiClient.class);
```

with:

```java
					pico.as(Characteristics.USE_NAMES).addComponent(PortfolioStore.class, SqlitePortfolioStore.class);
```

`SqlitePortfolioStore` needs `PortfolioList`, registered on the following line.
PicoContainer resolves by type regardless of registration order, so no reordering
is required.

- [ ] **Step 3: Remove the PTL connect flow**

Delete the `connectToPtl()` method entirely:

```java
	private void connectToPtl() {
		mSettings.setPtlConnectEnabled(false);
		apiClient.loadPortfolios(false);
		apiClient.loadTransactionHistories();
		apiClient.loadPairTradeHistories();
	}
```

Portfolios are now loaded by `SqlitePortfolioStore.start()` during `pico.start()`,
before the shell opens.

At its two call sites, remove the calls:
- in the shell-activated handler (near line 469), the autostart branch keeps only its existing `connectToIb()` path;
- near line 1615, the "Connect to PTL" button handler — delete the `connectToPtl()` call. The button widget itself is removed in Task 9; leaving an empty handler for now keeps this task compiling.

In `open()`, delete:

```java
		apiClient.closeAll();
		PtlApiClient.getExecutor().shutdownNow();
```

`pico.stop()` already stops the store through `Startable`.

- [ ] **Step 4: Replace the error handler**

Replace the whole `onPtlApiProblem` handler with:

```java
	@Subscribe
	public void onStoreProblem(final StoreProblem p) {
		final String title = "bindPortfolioToAccount".equals(p.origin)
				? "Bind Operation Failed" : "Local Database Error";
		Display.getDefault().syncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog.openError(shlPtlTrader, title, p.error.toString());
			}
		});
	}
```

- [ ] **Step 5: Repoint the remaining `apiClient` call sites**

| Old call | Replacement |
|---|---|
| `apiClient.updatePortfolio((Portfolio) portfolio);` (near line 674) | `portfolioStore.savePortfolio((Portfolio) portfolio);` |
| `apiClient.bindPortfolioToAccount((Portfolio) portfolio, ((Account) account).getCode());` (near line 754) | `portfolioStore.bindPortfolioToAccount((Portfolio) portfolio, ((Account) account).getCode());` |
| `apiClient.deletePairStrategy((PairStrategy) ps);` (near line 1320) | `portfolioStore.deleteStrategy((PairStrategy) ps);` |
| `apiClient.loadPortfolios(true);` (the *Update Portfolios From PTL* menu handler, near line 1765) | `portfolioStore.load();` |

The bind handler previously waited for a server round trip before calling
`p.bind(...)`; `bindPortfolioToAccount` now does the bind itself, so remove any
local `p.bind(...)` call that would otherwise run twice.

The menu item is replaced wholesale in Task 8; repointing it here only keeps this
task compiling.

- [ ] **Step 6: Delete the PTL client classes**

```bash
git rm src/main/java/com/pairtradinglab/ptltrader/PtlApiClient.java \
       src/main/java/com/pairtradinglab/ptltrader/PtlApiError.java \
       src/main/java/com/pairtradinglab/ptltrader/events/PtlApiConnect.java \
       src/main/java/com/pairtradinglab/ptltrader/events/PtlApiProblem.java
```

Remove `import com.pairtradinglab.ptltrader.PtlApiClient;` from
`PortfolioFactoryImpl.java`.

- [ ] **Step 7: Compile and fix every remaining reference**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew compileJava --offline
```

`AmqpEngine` subscribes to `PtlApiConnect`. Delete that `@Subscribe` method — Task 9
deletes the class entirely, so it only needs to compile until then. Repeat until the
compile is clean.

- [ ] **Step 8: Run the whole suite**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Verify the application starts from the database**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew run
```

Expected: the window opens with an empty portfolio list and no PTL connection
attempt. Then confirm the files were created:

```bash
ls -la ~/.local/share/ptltrader/
```

Expected: `default.db`, `default.log`, and `default.db-wal`.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Replace PtlApiClient with the local portfolio store

Portfolios now load from SQLite during pico.start(), before the shell
opens, so there is no connect step and no network dependency. The
dirty-flag sync-out events are unchanged; only their subscriber moved."
```

---

### Task 7: History persistence

**Files:**
- Modify: `src/main/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStore.java`
- Modify: `src/main/java/com/pairtradinglab/ptltrader/trading/events/TransactionEvent.java` (add `getAccount()` if absent)
- Test: `src/test/java/com/pairtradinglab/ptltrader/store/HistoryPersistenceTest.java`

**Interfaces:**
- Consumes: `Database` (Task 2), `SqlitePortfolioStore` (Task 5).
- Produces: `SqlitePortfolioStore.insertTradeHistory(Connection, HistoryEntry)`, `insertLegHistory(Connection, TransactionEvent)`, `readTradeHistory(Connection, int limit): List<TradeHistoryEntry>`, `readLegHistory(Connection, int limit): List<LegHistoryEntry>`, and `HISTORY_LOAD_LIMIT = 1000`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/store/HistoryPersistenceTest.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.pairtradinglab.ptltrader.model.LegHistoryEntry;
import com.pairtradinglab.ptltrader.model.TradeHistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;

public class HistoryPersistenceTest {
	private File dbFile;
	private Database db;

	@Before
	public void setUp() throws Exception {
		dbFile = File.createTempFile("ptltrader-hist", ".db");
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

	@Test
	public void testTradeHistoryRoundTrip() throws Exception {
		DateTime dt = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		HistoryEntry he = new HistoryEntry("S1", "DU123456", dt, "NYSE:V", "NYSE:MA",
				HistoryEntry.ACTION_CLOSED, 250.5, 1.75, 4.0, 2.15, "ok");
		SqlitePortfolioStore.insertTradeHistory(db.getConnection(), he);

		List<TradeHistoryEntry> out = SqlitePortfolioStore.readTradeHistory(db.getConnection(), 100);
		assertEquals(1, out.size());
		TradeHistoryEntry e = out.get(0);
		assertEquals("NYSE:V", e.getStock1());
		assertEquals("NYSE:MA", e.getStock2());
		assertEquals(HistoryEntry.ACTION_CLOSED, e.getAction());
		assertEquals("DU123456", e.getAccount());
		assertEquals(250.5, e.getRealizedPl(), 1e-9);
		assertEquals(1.75, e.getRealizedPlPercent(), 1e-9);
		assertEquals(4.0, e.getCommissions(), 1e-9);
		assertEquals(2.15, e.getZscore(), 1e-9);
		assertEquals("ok", e.getComment());
		assertEquals(dt.getMillis(), e.getDatetime().getMillis());
	}

	@Test
	public void testLegHistoryRoundTrip() throws Exception {
		DateTime dt = new DateTime(2026, 3, 1, 14, 30, 0, DateTimeZone.UTC);
		TransactionEvent te = new TransactionEvent("S1", "DU123456", "NYSE:V",
				TransactionEvent.DIRECTION_LONG, 100, 12.5);
		te.setDatetime(dt);
		te.setRealizedPl(33.25);
		te.setCommissions(1.5);
		te.setFillTime(Duration.millis(1234));
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);

		List<LegHistoryEntry> out = SqlitePortfolioStore.readLegHistory(db.getConnection(), 100);
		assertEquals(1, out.size());
		LegHistoryEntry e = out.get(0);
		assertEquals("NYSE:V", e.getSymbol());
		assertEquals(LegHistoryEntry.ACTION_BUY, e.getAction());
		assertEquals(100, e.getQty());
		assertEquals(12.5, e.getPrice(), 1e-9);
		assertEquals(1250.0, e.getValue(), 1e-9);
		assertEquals(33.25, e.getRealizedPl(), 1e-9);
		assertEquals(1.5, e.getCommissions(), 1e-9);
		assertEquals(1234, e.getFillTime().getMillis());
		assertEquals("DU123456", e.getAccount());
	}

	@Test
	public void testShortDirectionMapsToSell() throws Exception {
		TransactionEvent te = new TransactionEvent("S1", "A", "NYSE:MA",
				TransactionEvent.DIRECTION_SHORT, 50, 20.0);
		te.setDatetime(new DateTime(DateTimeZone.UTC));
		te.setFillTime(Duration.ZERO);
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);
		assertEquals(LegHistoryEntry.ACTION_SELL,
				SqlitePortfolioStore.readLegHistory(db.getConnection(), 10).get(0).getAction());
	}

	@Test
	public void testNullFillTimeIsTolerated() throws Exception {
		TransactionEvent te = new TransactionEvent("S1", "A", "NYSE:MA",
				TransactionEvent.DIRECTION_LONG, 10, 5.0);
		te.setDatetime(new DateTime(DateTimeZone.UTC));
		// fillTime deliberately left null: a transaction can be recorded before
		// the fill duration is known.
		SqlitePortfolioStore.insertLegHistory(db.getConnection(), te);
		assertEquals(0, SqlitePortfolioStore.readLegHistory(db.getConnection(), 10)
				.get(0).getFillTime().getMillis());
	}

	@Test
	public void testReadsNewestFirstAndRespectsLimit() throws Exception {
		for (int i = 0; i < 5; i++) {
			HistoryEntry he = new HistoryEntry("S" + i, "A",
					new DateTime(2026, 1, 1 + i, 0, 0, 0, DateTimeZone.UTC),
					"NYSE:A", "NYSE:B", HistoryEntry.ACTION_CLOSED, 0, 0, 0, 0, "c" + i);
			SqlitePortfolioStore.insertTradeHistory(db.getConnection(), he);
		}
		List<TradeHistoryEntry> out = SqlitePortfolioStore.readTradeHistory(db.getConnection(), 3);
		assertEquals(3, out.size());
		assertEquals("newest row must come first", "c4", out.get(0).getComment());
		assertEquals("c3", out.get(1).getComment());
		assertEquals("c2", out.get(2).getComment());
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*HistoryPersistenceTest*'
```

Expected: compilation failure — `cannot find symbol: method insertTradeHistory`.

- [ ] **Step 3: Add the history methods**

In `SqlitePortfolioStore`, add the imports:

```java
import org.joda.time.Duration;
import com.pairtradinglab.ptltrader.model.LegHistoryEntry;
import com.pairtradinglab.ptltrader.model.TradeHistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.HistoryEntry;
import com.pairtradinglab.ptltrader.trading.events.TransactionEvent;
```

and the constant and methods:

```java
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
```

If `TransactionEvent` has no public `getAccount()`, add one — the field exists but is
private. Do not change its Jackson annotations.

- [ ] **Step 4: Subscribe to the history events and backfill on load**

Add these subscriptions:

```java
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
```

In `load()`, immediately after `logger.info("loaded " + documents.size() + ...)`,
backfill the tables:

```java
			for (TradeHistoryEntry e : readTradeHistory(database.getConnection(), HISTORY_LOAD_LIMIT)) {
				tradeHistory.addEntryLast(e);
			}
			for (LegHistoryEntry e : readLegHistory(database.getConnection(), HISTORY_LOAD_LIMIT)) {
				legHistory.addEntryLast(e);
			}
```

> `LegHistory` and `TradeHistory` keep their own `@Subscribe` handlers for the live
> in-memory update. The store is an independent second subscriber, so neither
> depends on the other.

- [ ] **Step 5: Run test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*HistoryPersistenceTest*'
```

Expected: BUILD SUCCESSFUL, 5 tests.

- [ ] **Step 6: Run the whole suite and commit**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
git add src/main/java/com/pairtradinglab/ptltrader/store/SqlitePortfolioStore.java \
        src/main/java/com/pairtradinglab/ptltrader/trading/events/TransactionEvent.java \
        src/test/java/com/pairtradinglab/ptltrader/store/HistoryPersistenceTest.java
git commit -m "Persist trade and leg history locally

PTL stored history as a side effect of receiving telemetry, so with
AMQP going away nothing would write it. The store subscribes to the
same TransactionEvent and HistoryEntry the model beans already consume
and inserts rows independently, then backfills the newest 1000 on load."
```

---

### Task 8: Import, export, create, delete and add-pair

**Files:**
- Create: `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioImporter.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/NewPortfolioDialog.java`
- Create: `src/main/java/com/pairtradinglab/ptltrader/AddPairDialog.java`
- Modify: `src/main/java/com/pairtradinglab/ptltrader/Application.java` (File menu, near lines 1755-1795)
- Test: `src/test/java/com/pairtradinglab/ptltrader/store/PortfolioImporterTest.java`

**Interfaces:**
- Consumes: `PortfolioStore` and `StoreError` (Task 5), `PortfolioDocuments` (Task 4).
- Produces:
  - `PortfolioImporter.parse(String json, ObjectMapper mapper): List<ObjectNode>` — validates and returns re-uid'd documents ready for `insertPortfolioDocument`. Throws `PortfolioImporter.InvalidImportException` naming the offending strategy and field.
  - `PortfolioImporter.REQUIRED_PORTFOLIO_FIELDS`, `PortfolioImporter.REQUIRED_STRATEGY_FIELDS` — `String[]`.
  - `NewPortfolioDialog.open(): String` — the entered name, or null if cancelled.
  - `AddPairDialog.open(): int` (JFace `Window.OK`/`CANCEL`) and `AddPairDialog.getResult(): Result` with public final `stock1`, `stock2` (String), `tradeAs1`, `tradeAs2` (int), `model` (String).

> The model constants used below are verified as: `PairStrategy.MODEL_RATIO` =
> `"Ratio"`, `MODEL_RESIDUAL` = `"Residual"`, `MODEL_KALMAN_GRID` =
> `"Kalman-grid-v2"`, `MODEL_KALMAN_AUTO` = `"Kalman-auto"`; `STATUS_NONE` = `""`;
> `TRADING_STATUS_INACTIVE` = `0`; `Portfolio.MASTER_STATUS_ACTIVE` = `2`;
> `Portfolio.PDT_ENABLE_25K` = `1`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/pairtradinglab/ptltrader/store/PortfolioImporterTest.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class PortfolioImporterTest {

	private static final String STRATEGY = "{"
		+ "\"uid\":\"S1\",\"model\":\"Ratio\",\"ticker1\":\"NYSE:V\",\"ticker2\":\"NYSE:MA\","
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

	private static final String PORTFOLIO = "{"
		+ "\"uid\":\"P1\",\"name\":\"My Portfolio\",\"account_code\":\"DU123456\","
		+ "\"max_pairs_open\":10,\"master_status\":2,\"pdt_rules\":1,\"account_alloc\":100,"
		+ "\"strategies\":[" + STRATEGY + "]}";

	private ObjectMapper mapper;

	@Before
	public void setUp() {
		mapper = new ObjectMapper();
	}

	@Test
	public void testAcceptsArrayForm() throws Exception {
		List<ObjectNode> out = PortfolioImporter.parse("[" + PORTFOLIO + "]", mapper);
		assertEquals(1, out.size());
		assertEquals("My Portfolio", out.get(0).get("name").asText());
	}

	@Test
	public void testAcceptsSingleObjectForm() throws Exception {
		assertEquals(1, PortfolioImporter.parse(PORTFOLIO, mapper).size());
	}

	@Test
	public void testAssignsFreshUids() throws Exception {
		ObjectNode p = PortfolioImporter.parse(PORTFOLIO, mapper).get(0);
		assertNotEquals("portfolio uid must be regenerated", "P1", p.get("uid").asText());
		assertNotEquals("strategy uid must be regenerated", "S1",
				p.get("strategies").get(0).get("uid").asText());
	}

	@Test
	public void testTwoImportsOfTheSameFileDoNotCollide() throws Exception {
		String a = PortfolioImporter.parse(PORTFOLIO, mapper).get(0).get("uid").asText();
		String b = PortfolioImporter.parse(PORTFOLIO, mapper).get(0).get("uid").asText();
		assertNotEquals(a, b);
	}

	@Test
	public void testClearsAccountCode() throws Exception {
		ObjectNode p = PortfolioImporter.parse(PORTFOLIO, mapper).get(0);
		assertEquals("an imported binding is meaningless locally", "", p.get("account_code").asText());
	}

	@Test
	public void testStripsRuntimeState() throws Exception {
		ObjectNode s = (ObjectNode) PortfolioImporter.parse(PORTFOLIO, mapper).get(0)
				.get("strategies").get(0);
		for (String f : PortfolioDocuments.STATE_FIELDS) {
			assertFalse("imported state belongs to no account: " + f, s.has(f));
		}
	}

	@Test
	public void testRejectsMissingStrategyField() throws Exception {
		String broken = PORTFOLIO.replace("\"entry_threshold\":2.0,", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue("message must name the field", e.getMessage().contains("entry_threshold"));
			assertTrue("message must name the strategy", e.getMessage().contains("NYSE:V"));
		}
	}

	@Test
	public void testRejectsMissingPortfolioField() throws Exception {
		String broken = PORTFOLIO.replace("\"max_pairs_open\":10,", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("max_pairs_open"));
		}
	}

	@Test
	public void testRejectsUnparseableSymbol() throws Exception {
		String broken = PORTFOLIO.replace("\"ticker1\":\"NYSE:V\"", "\"ticker1\":\"NOTASYMBOL\"");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("NOTASYMBOL"));
		}
	}

	@Test
	public void testRejectsUnknownExchange() throws Exception {
		String broken = PORTFOLIO.replace("\"ticker2\":\"NYSE:MA\"", "\"ticker2\":\"XETRA:BMW\"");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException for an unsupported exchange");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("XETRA:BMW"));
		}
	}

	@Test
	public void testRejectsGarbage() throws Exception {
		try {
			PortfolioImporter.parse("not json at all", mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			// expected
		}
	}

	@Test
	public void testRejectsMissingStrategiesArray() throws Exception {
		String broken = PORTFOLIO.replace(",\"strategies\":[" + STRATEGY + "]", "");
		try {
			PortfolioImporter.parse(broken, mapper);
			fail("expected InvalidImportException");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("strategies"));
		}
	}

	@Test
	public void testAnExportedDocumentReImportsCleanly() throws Exception {
		// Closes the loop with PortfolioDocuments.build: whatever export writes,
		// import must accept. A field added to the bean but missing from
		// REQUIRED_STRATEGY_FIELDS, or vice versa, breaks here.
		com.pairtradinglab.ptltrader.LoggerFactory lf =
				new com.pairtradinglab.ptltrader.LoggerFactoryImpl(
						new com.pairtradinglab.ptltrader.RuntimeParams(new String[] { "unittest" }));
		com.pairtradinglab.ptltrader.model.Portfolio pf =
				new com.pairtradinglab.ptltrader.model.Portfolio(null, null, lf, "P1");
		pf.updateFromJson(mapper.readTree(PORTFOLIO));

		com.pairtradinglab.ptltrader.model.PairStrategy st =
				new com.pairtradinglab.ptltrader.model.PairStrategy(
						"S1", pf, "NYSE:V", "NYSE:MA", 0, 0, null, null);
		st.updateFromJson(mapper.readTree(STRATEGY));
		pf.addPairStrategy(st);

		ObjectNode exported = PortfolioDocuments.build(pf, mapper);
		List<ObjectNode> reimported = PortfolioImporter.parse(exported.toString(), mapper);

		assertEquals(1, reimported.size());
		assertEquals(1, reimported.get(0).get("strategies").size());
		assertEquals("NYSE:V", reimported.get(0).get("strategies").get(0).get("ticker1").asText());
		assertEquals(2.0, reimported.get(0).get("strategies").get(0).get("entry_threshold").asDouble(), 1e-9);
	}

	@Test
	public void testAMalformedFileIsRejectedWhole() throws Exception {
		String second = STRATEGY.replace("\"timezone\":\"America/New_York\",", "");
		String twoStrategies = PORTFOLIO.replace(
				"\"strategies\":[" + STRATEGY + "]",
				"\"strategies\":[" + STRATEGY + "," + second + "]");
		try {
			PortfolioImporter.parse(twoStrategies, mapper);
			fail("a file with one bad strategy must be rejected entirely");
		} catch (PortfolioImporter.InvalidImportException e) {
			assertTrue(e.getMessage().contains("timezone"));
		}
	}
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*PortfolioImporterTest*'
```

Expected: compilation failure — `cannot find symbol: class PortfolioImporter`.

- [ ] **Step 3: Write `PortfolioImporter`**

Create `src/main/java/com/pairtradinglab/ptltrader/store/PortfolioImporter.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader.store;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.trading.ContractExt;

/**
 * Validates a portfolio export file and prepares it for insertion.
 *
 * Import always creates a new portfolio: every uid is regenerated and the account
 * binding is cleared. That makes an import incapable of modifying an existing
 * portfolio, and in particular incapable of disturbing the runtime state of a
 * strategy that currently holds an open position.
 */
public class PortfolioImporter {

	public static class InvalidImportException extends Exception {
		private static final long serialVersionUID = 1L;

		public InvalidImportException(String message) {
			super(message);
		}
	}

	/** Fields Portfolio.updateFromJson() reads with get(), which throws if absent. */
	public static final String[] REQUIRED_PORTFOLIO_FIELDS = {
		"name", "max_pairs_open", "account_code", "account_alloc", "master_status", "pdt_rules"
	};

	/** Fields PairStrategy.updateFromJson() reads with get(), which throws if absent. */
	public static final String[] REQUIRED_STRATEGY_FIELDS = {
		"model", "ticker1", "ticker2", "trade_as_1", "trade_as_2",
		"entry_threshold", "exit_threshold", "downtick_threshold", "max_score",
		"ratio_ma_type", "ratio_ma_period", "ratio_stddev_period", "ratio_entry_mode",
		"residual_linreg_period", "neutrality", "ka_ve", "ka_usage_target",
		"ticker1margin", "ticker2margin",
		"allow_reversals", "enable_max_days", "max_days", "enable_min_pl", "min_pl",
		"enable_min_price", "min_price", "enable_min_profit_potential", "min_profit_potential",
		"entry_start_hour", "entry_start_minute", "entry_end_hour", "entry_end_minute",
		"exit_start_hour", "exit_start_minute", "exit_end_hour", "exit_end_minute",
		"timezone", "allow_positions", "status", "slot_occupation"
	};

	private PortfolioImporter() {
	}

	public static List<ObjectNode> parse(String json, ObjectMapper mapper) throws InvalidImportException {
		JsonNode root;
		try {
			root = mapper.readTree(json);
		} catch (Exception e) {
			throw new InvalidImportException("The file is not valid JSON: " + e.getMessage());
		}
		if (root == null || root.isNull()) {
			throw new InvalidImportException("The file is empty.");
		}

		List<JsonNode> portfolios = new ArrayList<JsonNode>();
		if (root.isArray()) {
			for (JsonNode n : root) portfolios.add(n);
		} else if (root.isObject()) {
			portfolios.add(root);
		} else {
			throw new InvalidImportException("Expected a portfolio object or an array of them.");
		}
		if (portfolios.isEmpty()) {
			throw new InvalidImportException("The file contains no portfolios.");
		}

		// Validate everything before returning anything: a bad file is rejected whole.
		List<ObjectNode> out = new ArrayList<ObjectNode>();
		for (JsonNode p : portfolios) {
			out.add(prepare(p));
		}
		return out;
	}

	private static ObjectNode prepare(JsonNode source) throws InvalidImportException {
		if (!source.isObject()) {
			throw new InvalidImportException("Expected a portfolio object.");
		}
		ObjectNode p = source.deepCopy();
		String pname = p.path("name").asText("(unnamed)");

		for (String f : REQUIRED_PORTFOLIO_FIELDS) {
			if (!p.has(f)) {
				throw new InvalidImportException(String.format(
						"Portfolio \"%s\" is missing the required field \"%s\".", pname, f));
			}
		}

		JsonNode strategies = p.get("strategies");
		if (strategies == null || !strategies.isArray()) {
			throw new InvalidImportException(String.format(
					"Portfolio \"%s\" has no strategies array.", pname));
		}

		p.put("uid", UUID.randomUUID().toString());
		// An imported account binding is meaningless on this machine, and binding
		// to a live account must be a deliberate act by the user.
		p.put("account_code", "");
		p.remove("features");

		for (JsonNode node : strategies) {
			if (!node.isObject()) {
				throw new InvalidImportException("Expected a strategy object.");
			}
			ObjectNode s = (ObjectNode) node;
			String label = s.path("ticker1").asText("?") + "/" + s.path("ticker2").asText("?");

			for (String f : REQUIRED_STRATEGY_FIELDS) {
				if (!s.has(f)) {
					throw new InvalidImportException(String.format(
							"Strategy %s is missing the required field \"%s\".", label, f));
				}
			}
			validateSymbol(s.get("ticker1").asText());
			validateSymbol(s.get("ticker2").asText());

			s.put("uid", UUID.randomUUID().toString());
			s.remove("features");
			// Runtime state describes a position in an account this import is not
			// bound to, so it must not travel with the configuration.
			for (String f : PortfolioDocuments.STATE_FIELDS) {
				s.remove(f);
			}
		}
		return p;
	}

	private static void validateSymbol(String symbol) throws InvalidImportException {
		try {
			ContractExt.createFromGoogleSymbol(symbol, false);
		} catch (RuntimeException e) {
			throw new InvalidImportException(String.format(
					"Symbol \"%s\" is not a supported EXCHANGE:TICKER symbol.", symbol));
		}
	}
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline --tests '*PortfolioImporterTest*'
```

Expected: BUILD SUCCESSFUL, 14 tests.

- [ ] **Step 5: Write `NewPortfolioDialog`**

Create `src/main/java/com/pairtradinglab/ptltrader/NewPortfolioDialog.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;

/**
 * Prompts for the name of a new portfolio.
 */
public class NewPortfolioDialog {
	private final Shell parent;

	public NewPortfolioDialog(Shell parent) {
		this.parent = parent;
	}

	/**
	 * @return the entered name, or null if the user cancelled
	 */
	public String open() {
		InputDialog dlg = new InputDialog(parent, "New Portfolio", "Portfolio name:", "",
				new IInputValidator() {
					@Override
					public String isValid(String newText) {
						if (newText == null || newText.trim().isEmpty()) return "The name must not be empty.";
						if (newText.length() > 255) return "The name is too long.";
						return null;
					}
				});
		if (dlg.open() != Window.OK) return null;
		return dlg.getValue().trim();
	}
}
```

- [ ] **Step 6: Write `AddPairDialog`**

Create `src/main/java/com/pairtradinglab/ptltrader/AddPairDialog.java` with the standard GPL header, then:

```java
package com.pairtradinglab.ptltrader;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import com.pairtradinglab.ptltrader.model.PairStrategy;
import com.pairtradinglab.ptltrader.trading.ContractExt;

/**
 * Prompts for the two legs of a new pair, their instrument types and the model.
 *
 * Symbols are validated here rather than at first trade, so an unsupported
 * exchange is rejected while the user can still fix it.
 */
public class AddPairDialog extends Dialog {

	public static class Result {
		public final String stock1;
		public final String stock2;
		public final int tradeAs1;
		public final int tradeAs2;
		public final String model;

		public Result(String stock1, String stock2, int tradeAs1, int tradeAs2, String model) {
			this.stock1 = stock1;
			this.stock2 = stock2;
			this.tradeAs1 = tradeAs1;
			this.tradeAs2 = tradeAs2;
			this.model = model;
		}
	}

	private static final String[] MODELS = {
		PairStrategy.MODEL_RATIO, PairStrategy.MODEL_RESIDUAL,
		PairStrategy.MODEL_KALMAN_GRID, PairStrategy.MODEL_KALMAN_AUTO
	};
	/** Index matches PairStrategy's trade_as values: 0 = stock, 1 = CFD. */
	private static final String[] INSTRUMENTS = { "Stock", "CFD" };

	private Text textStock1;
	private Text textStock2;
	private Combo comboTradeAs1;
	private Combo comboTradeAs2;
	private Combo comboModel;
	private Result result;

	public AddPairDialog(Shell parentShell) {
		super(parentShell);
	}

	public Result getResult() {
		return result;
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText("Add Pair");
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite c = new Composite(area, SWT.NONE);
		c.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		c.setLayout(new GridLayout(3, false));

		new Label(c, SWT.NONE).setText("Stock 1:");
		textStock1 = new Text(c, SWT.BORDER);
		textStock1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		textStock1.setMessage("EXCHANGE:TICKER, e.g. NYSE:V");
		comboTradeAs1 = new Combo(c, SWT.READ_ONLY);
		comboTradeAs1.setItems(INSTRUMENTS);
		comboTradeAs1.select(0);

		new Label(c, SWT.NONE).setText("Stock 2:");
		textStock2 = new Text(c, SWT.BORDER);
		textStock2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		textStock2.setMessage("EXCHANGE:TICKER, e.g. NYSE:MA");
		comboTradeAs2 = new Combo(c, SWT.READ_ONLY);
		comboTradeAs2.setItems(INSTRUMENTS);
		comboTradeAs2.select(0);

		new Label(c, SWT.NONE).setText("Model:");
		comboModel = new Combo(c, SWT.READ_ONLY);
		comboModel.setItems(MODELS);
		comboModel.select(0);
		comboModel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

		return area;
	}

	@Override
	protected void okPressed() {
		String s1 = textStock1.getText().trim().toUpperCase();
		String s2 = textStock2.getText().trim().toUpperCase();

		String bad = validate(s1);
		if (bad == null) bad = validate(s2);
		if (bad != null) {
			MessageDialog.openError(getShell(), "Invalid Symbol", bad);
			return;
		}
		if (s1.equals(s2)) {
			MessageDialog.openError(getShell(), "Invalid Pair", "The two legs must be different symbols.");
			return;
		}

		result = new Result(s1, s2, comboTradeAs1.getSelectionIndex(),
				comboTradeAs2.getSelectionIndex(), comboModel.getText());
		super.okPressed();
	}

	private String validate(String symbol) {
		if (symbol.isEmpty()) return "Both symbols are required.";
		try {
			ContractExt.createFromGoogleSymbol(symbol, false);
			return null;
		} catch (RuntimeException e) {
			return String.format("\"%s\" is not a supported EXCHANGE:TICKER symbol.", symbol);
		}
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, "Add", true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}
}
```

- [ ] **Step 7: Replace the File menu**

In `Application.java`, replace the `mntmReloadPortfolios` item and its listener with
the new actions, inserted before the existing `mntmExit`:

```java
		MenuItem mntmImportPortfolio = new MenuItem(menuFile, SWT.NONE);
		mntmImportPortfolio.setText("Import Portfolio...");
		mntmImportPortfolio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				importPortfolio();
			}
		});

		MenuItem mntmExportPortfolio = new MenuItem(menuFile, SWT.NONE);
		mntmExportPortfolio.setText("Export Portfolio...");
		mntmExportPortfolio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				exportSelectedPortfolio();
			}
		});

		new MenuItem(menuFile, SWT.SEPARATOR);

		MenuItem mntmNewPortfolio = new MenuItem(menuFile, SWT.NONE);
		mntmNewPortfolio.setText("New Portfolio...");
		mntmNewPortfolio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				createPortfolio();
			}
		});

		MenuItem mntmDeletePortfolio = new MenuItem(menuFile, SWT.NONE);
		mntmDeletePortfolio.setText("Delete Portfolio");
		mntmDeletePortfolio.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				deleteSelectedPortfolio();
			}
		});

		MenuItem mntmAddPair = new MenuItem(menuFile, SWT.NONE);
		mntmAddPair.setText("Add Pair...");
		mntmAddPair.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				addPairToSelectedPortfolio();
			}
		});

		new MenuItem(menuFile, SWT.SEPARATOR);
```

Delete the `MenuItem mntmReloadPortfolios;` field and the binding that references it
in `finishBindings()` (the `observeEnabledMntmReloadPortfolios` block).

- [ ] **Step 8: Implement the five actions**

Add these methods to `Application`:

```java
	private Portfolio getSelectedPortfolio() {
		Object sel = ((org.eclipse.jface.viewers.IStructuredSelection)
				tableViewerPortfolios.getSelection()).getFirstElement();
		return (sel instanceof Portfolio) ? (Portfolio) sel : null;
	}

	private void importPortfolio() {
		FileDialog fd = new FileDialog(shlPtlTrader, SWT.OPEN);
		fd.setText("Import Portfolio");
		fd.setFilterExtensions(new String[] { "*.json", "*.*" });
		fd.setFilterNames(new String[] { "Portfolio export (*.json)", "All files" });
		String path = fd.open();
		if (path == null) return;

		try {
			String json = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
			List<ObjectNode> docs = PortfolioImporter.parse(json, new ObjectMapper());

			int pairs = 0;
			for (ObjectNode doc : docs) {
				pairs += doc.get("strategies").size();
				portfolioStore.insertPortfolioDocument(doc);
			}
			bus.post(new LogEvent(String.format("imported %d portfolio(s), %d pair(s)", docs.size(), pairs)));
			MessageDialog.openInformation(shlPtlTrader, "Import Complete",
					String.format("Imported %d portfolio(s) containing %d pair(s).", docs.size(), pairs));
		} catch (PortfolioImporter.InvalidImportException e) {
			MessageDialog.openError(shlPtlTrader, "Import Failed", e.getMessage());
		} catch (IOException e) {
			MessageDialog.openError(shlPtlTrader, "Import Failed", "Unable to read the file: " + e.getMessage());
		}
	}

	private void exportSelectedPortfolio() {
		Portfolio p = getSelectedPortfolio();
		if (p == null) {
			MessageDialog.openError(shlPtlTrader, "Error", "Please select a portfolio first.");
			return;
		}
		FileDialog fd = new FileDialog(shlPtlTrader, SWT.SAVE);
		fd.setText("Export Portfolio");
		fd.setFilterExtensions(new String[] { "*.json" });
		fd.setFileName(p.getName().replaceAll("[^A-Za-z0-9._-]", "_") + ".json");
		fd.setOverwrite(true);
		String path = fd.open();
		if (path == null) return;

		try {
			ObjectMapper mapper = new ObjectMapper();
			ArrayNode root = mapper.createArrayNode();
			// Config only: runtime state describes a position in one account and
			// must not travel with the configuration.
			root.add(PortfolioDocuments.build(p, mapper));
			Files.write(Paths.get(path), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root));
			bus.post(new LogEvent("exported portfolio to " + path));
		} catch (IOException e) {
			MessageDialog.openError(shlPtlTrader, "Export Failed", e.getMessage());
		}
	}

	private void createPortfolio() {
		String name = new NewPortfolioDialog(shlPtlTrader).open();
		if (name == null) return;

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode doc = mapper.createObjectNode();
		doc.put("uid", UUID.randomUUID().toString());
		doc.put("name", name);
		doc.put("account_code", "");
		doc.put("max_pairs_open", 10);
		doc.put("account_alloc", 100);
		doc.put("master_status", Portfolio.MASTER_STATUS_ACTIVE);
		doc.put("pdt_rules", Portfolio.PDT_ENABLE_25K);
		doc.putArray("strategies");
		portfolioStore.insertPortfolioDocument(doc);
		bus.post(new LogEvent("created portfolio " + name));
	}

	private void deleteSelectedPortfolio() {
		Portfolio p = getSelectedPortfolio();
		if (p == null) {
			MessageDialog.openError(shlPtlTrader, "Error", "Please select a portfolio first.");
			return;
		}
		if (!p.getAccountCode().isEmpty()) {
			MessageDialog.openError(shlPtlTrader, "Error",
					"Unbind this portfolio from its account before deleting it.");
			return;
		}
		for (PairStrategy s : p.getPairStrategies()) {
			if (!PairStrategy.STATUS_NONE.equals(s.getStatus())) {
				MessageDialog.openError(shlPtlTrader, "Error",
						"This portfolio has a pair with an open position and cannot be deleted.");
				return;
			}
		}
		if (!MessageDialog.openConfirm(shlPtlTrader, "Confirm Operation", String.format(
				"Are you sure you want to delete the portfolio \"%s\" and all %d of its pairs? "
				+ "This cannot be undone.", p.getName(), p.getPairStrategies().size()))) {
			return;
		}
		String name = p.getName();
		p.stopStrategyCores();
		portfolioStore.deletePortfolio(p);
		bus.post(new LogEvent("deleted portfolio " + name));
	}

	private void addPairToSelectedPortfolio() {
		Portfolio p = getSelectedPortfolio();
		if (p == null) {
			MessageDialog.openError(shlPtlTrader, "Error", "Please select a portfolio first.");
			return;
		}
		AddPairDialog dlg = new AddPairDialog(shlPtlTrader);
		if (dlg.open() != org.eclipse.jface.window.Window.OK) return;
		AddPairDialog.Result r = dlg.getResult();
		if (r == null) return;

		for (PairStrategy existing : p.getPairStrategies()) {
			if (r.stock1.equals(existing.getStock1()) && r.stock2.equals(existing.getStock2())) {
				MessageDialog.openError(shlPtlTrader, "Error", "This pair is already in the portfolio.");
				return;
			}
		}

		PairStrategy s = pairStrategyFactory.createForPortfolio(p, r.stock1, r.stock2, r.tradeAs1, r.tradeAs2);
		s.setModel(r.model);
		// New pairs start inactive so that adding one can never begin trading it
		// by surprise.
		s.setTradingStatus(PairStrategy.TRADING_STATUS_INACTIVE);
		p.addPairStrategy(s);
		portfolioStore.savePortfolio(p);
		bus.post(new LogEvent(String.format("added pair %s / %s", r.stock1, r.stock2)));
	}
```

Add `PairStrategyFactory pairStrategyFactory` to the `Application` constructor
parameter list, the field list and the assignments — `PairStrategyFactoryImpl` is
already registered as a component, so no container change is needed.

Add the imports:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import org.eclipse.swt.widgets.FileDialog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pairtradinglab.ptltrader.model.PairStrategyFactory;
import com.pairtradinglab.ptltrader.store.PortfolioDocuments;
import com.pairtradinglab.ptltrader.store.PortfolioImporter;
```

`java.util.List` is very likely already imported; do not duplicate it.

- [ ] **Step 9: Add the first-run hint**

Spec §9 requires that a fresh install points the user at the import action, because
with PTL gone an empty database is otherwise a dead end with no visible next step.

In `Application.setDefaultValues()`, after `bus.post(new LogEvent("application started"));`,
add:

```java
		if (mPortfolioList.getPortfolios().isEmpty()) {
			bus.post(new LogEvent(
					"no portfolios yet - use File > Import Portfolio... to load a portfolio "
					+ "exported from Pair Trading Lab, or File > New Portfolio... to start one"));
		}
```

The store has already run `load()` during `pico.start()` by this point, so the list
is populated if anything was stored.

- [ ] **Step 10: Compile and run the suite**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Verify by hand — this is the end-to-end check**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew run
```

Confirm each of these in the running application:

1. **File › New Portfolio…** creates a portfolio that appears in the list.
2. **File › Add Pair…** with `NYSE:V` / `NYSE:MA` adds a pair; `XETRA:BMW` is rejected in the dialog.
3. Change several strategy parameters in the Settings tabs — entry threshold, MA period, timezone, allow-positions.
4. **File › Export Portfolio…** writes a file. Open it: one array element, a `strategies` array, no `last_model_state`, and the parameters from step 3 present.
5. **File › Import Portfolio…** on that same file adds a *second*, independent portfolio with a different uid.
6. **Quit and relaunch.** Both portfolios and all their pairs are still there, **with the parameters from step 3 intact**.
7. **File › Delete Portfolio** removes one; after another relaunch it is still gone.

Item 6 is the real test of Task 3. If any parameter has reverted to its default, a
`@JsonProperty` is missing — go back and add it to the round-trip test's field list
first, watch it fail, then fix the annotation.

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "Add portfolio import, export, creation, deletion and add-pair

Import always creates a new portfolio with fresh uids and a cleared
account binding, so it can never modify an existing portfolio or
disturb the runtime state of one holding an open position. A malformed
file is rejected whole, naming the offending strategy and field.

New pairs are created inactive so adding one cannot start trading by
surprise."
```

---

### Task 9: Remove AMQP, SystemMonitor, credentials and feature flags

**Files:**
- Delete: `AmqpEngine.java`, `AmqpProxy.java`, `AmqpControlMessage.java`, `AmqpError.java`, `SystemMonitor.java`, `SupportedFeatures.java`, `StringXorProcessor.java`
- Delete: `events/AmqpConnect.java`, `events/AmqpProblem.java`, `events/SerializedEvent.java`, `events/ConfidentialEvent.java`, `events/ImportantEvent.java`, `events/TestEvent.java`, `events/ImportantTestEvent.java`, `events/MonitorEvent.java`
- Delete: `src/test/java/com/pairtradinglab/ptltrader/StringXorProcessorTest.java`
- Modify: `model/Settings.java`, `Application.java`, `build.gradle`, `trading/events/TransactionEvent.java`, `trading/events/HistoryEntry.java`

**Interfaces:**
- Consumes: everything from Tasks 1-8.
- Produces: a build with no rabbitmq or async-http-client dependency and no PTL credentials.

- [ ] **Step 1: Delete the classes**

```bash
git rm src/main/java/com/pairtradinglab/ptltrader/AmqpEngine.java \
       src/main/java/com/pairtradinglab/ptltrader/AmqpProxy.java \
       src/main/java/com/pairtradinglab/ptltrader/AmqpControlMessage.java \
       src/main/java/com/pairtradinglab/ptltrader/AmqpError.java \
       src/main/java/com/pairtradinglab/ptltrader/SystemMonitor.java \
       src/main/java/com/pairtradinglab/ptltrader/SupportedFeatures.java \
       src/main/java/com/pairtradinglab/ptltrader/StringXorProcessor.java \
       src/main/java/com/pairtradinglab/ptltrader/events/AmqpConnect.java \
       src/main/java/com/pairtradinglab/ptltrader/events/AmqpProblem.java \
       src/main/java/com/pairtradinglab/ptltrader/events/SerializedEvent.java \
       src/main/java/com/pairtradinglab/ptltrader/events/ConfidentialEvent.java \
       src/main/java/com/pairtradinglab/ptltrader/events/ImportantEvent.java \
       src/main/java/com/pairtradinglab/ptltrader/events/TestEvent.java \
       src/main/java/com/pairtradinglab/ptltrader/events/ImportantTestEvent.java \
       src/main/java/com/pairtradinglab/ptltrader/events/MonitorEvent.java \
       src/test/java/com/pairtradinglab/ptltrader/StringXorProcessorTest.java
```

> Deleting `StringXorProcessorTest` is the one sanctioned reduction in the test
> count: the class it tests no longer exists.

- [ ] **Step 2: Strip the marker interfaces from the surviving events**

`TransactionEvent` and `HistoryEntry` implement the deleted markers. Change:

```java
public class TransactionEvent implements ImportantEvent, ConfidentialEvent {
```

to:

```java
public class TransactionEvent {
```

and:

```java
public class HistoryEntry implements ImportantEvent, ConfidentialEvent {
```

to:

```java
public class HistoryEntry {
```

Remove the now-unused imports of `ImportantEvent` and `ConfidentialEvent` from both.
Do the same for `EquityChange` and `StrategyPlUpdated`, which also carry the
`ConfidentialEvent` marker.

Leave the `@JsonSerialize` annotations and `CustomDatetimeUtcSerializer` /
`CustomDurationSerializer` in place — they are unrelated to telemetry.

- [ ] **Step 3: Strip `Settings`**

In `model/Settings.java`, delete the fields `ptlAccessKey`, `ptlSecretKey`,
`savePtlSecretKey`, `enableConfidentialMode` and `ptlConnectEnabled`, the
`SECRET_KEY_ENC_KEY` constant, the `xorProcessor` constructor parameter and field,
the `storePtlSecretKey()` method, every accessor for the deleted fields, and the
corresponding `Preferences` reads and writes in `start()` and `stop()`.

If nothing remains, keep the class as an empty `Startable` rather than deleting it —
`Application` and the container both reference it, and removing it widens this task
beyond its purpose.

- [ ] **Step 4: Strip the container wiring and the UI**

In `Application.main()`, delete these registrations:

```java
					pico.as(Characteristics.USE_NAMES).addComponent(SystemMonitor.class);
					pico.addComponent(StringXorProcessor.class);
					pico.as(Characteristics.USE_NAMES).addComponent(AmqpEngine.class);
					pico.as(Characteristics.USE_NAMES).addComponent(AmqpProxy.class);
```

and the `amqpBusExecutor.shutdownNow();` call together with the `amqpBusExecutor`
field.

Remove `AmqpEngine amqpEngine`, `AmqpProxy amqpProxy` and `SystemMonitor systemMonitor`
from the `Application` constructor, fields and assignments.

Delete the `onAmqpProblem` handler; the PTL credentials widgets `textPTLAccessKey`
and `textPTLAccessToken` with their labels, the save-secret-key and
confidential-mode checkboxes, and the "Connect to PTL" button left over from Task 6;
and every data binding that references them (the `strategy_11` / `strategy_12`
blocks near lines 2378-2383 and the PTL status LED bindings near lines 2041-2048).

`Status.ptlConnected` is still called by `SqlitePortfolioStore.load()` as a
"storage is ready" flag. Either rename it to `storeReady` with its accessors and
update that call, or leave the name as it is — but do not delete it without
repointing the store.

- [ ] **Step 5: Drop the dependencies**

In `build.gradle`, delete:

```groovy
    implementation 'com.rabbitmq:amqp-client:5.11.0'
    implementation group: 'com.ning', name: 'async-http-client', version: '1.9.40'
```

- [ ] **Step 6: Compile repeatedly until clean**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew compileJava --offline
```

Fix each error. Expect fallout in `Application.java` and anywhere a marker interface
was referenced.

- [ ] **Step 7: Confirm nothing references the removed systems**

```bash
grep -rn "Amqp\|SystemMonitor\|SupportedFeatures\|StringXorProcessor\|ptlAccessKey\|ptlSecretKey\|MonitorEvent\|ConfidentialEvent\|ImportantEvent" src/main src/test || echo "SOURCE CLEAN"
grep -n "rabbitmq\|async-http-client" build.gradle || echo "DEPS CLEAN"
```

Expected: `SOURCE CLEAN` and `DEPS CLEAN`.

- [ ] **Step 8: Run the full suite and build the JAR**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew test --offline
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew shadowJar
ls -la build/libs/
```

Expected: tests pass and a `ptltrader-*.jar` is produced. `shadowJar` is the step
that fails on JDK 21, so a success here also confirms the toolchain is right.

- [ ] **Step 9: Verify the application still runs**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew run
```

Confirm the window opens, the Settings tab no longer shows PTL credentials, and the
portfolios imported in Task 8 still load with their parameters intact.

- [ ] **Step 10: Commit**

```bash
git add -A
git commit -m "Remove AMQP telemetry, SystemMonitor, PTL credentials and feature flags

With the service gone there is no telemetry consumer, so AmqpEngine,
AmqpProxy and the event marker interfaces go with it, along with the
rabbitmq and async-http-client dependencies.

SystemMonitor existed only to feed MonitorEvent to AMQP. It was also
the only aggregator of ManualInterventionRequested; the UI never showed
that aggregate, so nothing regresses visibly, but the capability is
dropped rather than re-homed.

The secret-key obfuscation goes too: with no credentials to store,
StringXorProcessor has nothing to protect."
```

---

### Task 10: Documentation

**Files:**
- Modify: `README.md`, `ARCHITECTURE.md`, `TECHNICAL.md`

**Interfaces:**
- Consumes: the finished implementation.
- Produces: documentation that matches the code.

- [ ] **Step 1: Update `README.md`**

- Remove "This software is tightly coupled with Pair Trading Lab" and the whole "You need a valid Pair Trading Lab account to use this software" paragraph, including the invitation to fork for CSV loading — that is now what the software does.
- Add a **Migrating from Pair Trading Lab** section: export each portfolio as JSON from the PTL Portfolio Manager *before the service closes*, then **File › Import Portfolio…**.
- Add a **Data storage** section giving the three platform paths, noting that the database holds portfolios, strategy state and trade history and belongs in the user's backups.
- Note that one profile is one database, and that the existing single-instance-per-profile rule guarantees a single writer.

- [ ] **Step 2: Update `ARCHITECTURE.md`**

- §1: drop the PTL REST API and PTL event bus rows from the external-systems table, leaving IB. Reverse the "All persistent strategy state lives on the Pair Trading Lab servers" sentence. In the mermaid diagram remove `API`, `AMQP`, `PTLAPI` and `PTLMQ`; add `STORE["SqlitePortfolioStore"]` and `DB[("SQLite")]`.
- §2: replace the `PtlApiClient` / `AmqpEngine` mentions in the package table with the new `…​.store` package.
- §3: update the `Startable` list — `SystemMonitor`, `AmqpEngine` and `AmqpProxy` are gone; `SqlitePortfolioStore` is added.
- §8.4 and §8.5: replace both with one section describing the store, its `db-worker` thread and its three-attempt write policy.
- §9.1: update the startup sequence to the one in spec §4.3.
- §9.2: retitle from "Going live" — the sequence no longer begins with a PTL connect. Update its mermaid diagram.
- §9.3: `PairStateUpdated → PtlApiClient PUT` becomes `PairStateUpdated → SqlitePortfolioStore upsert`; delete the `AmqpProxy → ptl.clients` line.
- §10: replace the secret-key paragraph — there are no secrets to store.

- [ ] **Step 3: Update `TECHNICAL.md`**

- §5: rewrite. There is now a database as well as `java.util.prefs`. Keep the IB preferences node row; drop the `Settings` row. Give the `DataDirectory` paths and the schema from spec §5.2.
- §5.1 and §5.2 (secret-key storage, confidential mode): delete both.
- §5.3: correct the log path and remove the "Application Data on every platform" gotcha, which this work fixes.
- §6.1 and §6.4: delete.
- §6.2: retitle to "Portfolio document format" and keep the field table — it is now the import file contract rather than a REST contract. Add that the document omits `last_opened_*` and `last_model_state`, which live in `strategy_state`.
- §12 "Replacing the portfolio source": rewrite as "Adding an import format", pointing at `PortfolioImporter`.
- §12 "Adding a bus event": drop the `AmqpProxy` / `ImportantEvent` / `ConfidentialEvent` paragraph.
- §12 "Adding a trading model": add a step — new parameters need a `@JsonProperty` carrying the document field name or they will not persist, and they must also be added to `PortfolioImporter.REQUIRED_STRATEGY_FIELDS` and to the field lists in `SerializationRoundTripTest`.
- §13: replace the PTL troubleshooting rows with database ones — database locked by another instance, unreadable database, a database written by a newer build.

- [ ] **Step 4: Verify the docs match the code**

```bash
grep -rn "pairtradinglab.com\|PTL account\|Pair Trading Lab server\|AmqpEngine\|PtlApiClient\|Application Data" README.md ARCHITECTURE.md TECHNICAL.md
```

Every remaining hit must be deliberate. Historical context in the README's opening
paragraph is fine; an instruction telling users to connect to PTL is not.

- [ ] **Step 5: Final full verification**

```bash
JAVA_HOME=/home/karlos/JDK/jdk-11.0.32.1+1 ./gradlew clean test shadowJar
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add README.md ARCHITECTURE.md TECHNICAL.md
git commit -m "Update documentation for local storage

The PTL account requirement, the REST and AMQP interface sections and
the secret-key storage notes are gone; the database, the import
workflow and the corrected log path replace them."
```

---

## Notes for whoever runs this

**The one thing most likely to go wrong is Task 3.** A missing `@JsonProperty`
produces no error, no warning and no test failure unless the field is named in the
round-trip test's lists — it simply reverts to its default the next time the
application starts, silently changing how a strategy trades. If you ever add a field
to `PairStrategy`, add it to `TEXT_FIELDS` or `NUMERIC_FIELDS` in
`SerializationRoundTripTest` in the same commit.

**Task 8 Step 11.6** (change parameters, quit, relaunch, confirm they survived) is
the only check that exercises the whole persistence path against a real database.
Do not skip it.

**Order matters between Tasks 3 and 4-6.** Serialization is made provably lossless
*before* the store depends on it, so a gap surfaces as a unit test failure rather
than as silently corrupted portfolios.

**The PTL-side export is a hard prerequisite for users**, not for this code. It has
its own plan (`2026-09-07-ptl-json-export.md`) and should ship first, so that people
can export their portfolios while the service is still running.
