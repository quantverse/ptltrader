# Decoupling PTL Trader from Pair Trading Lab

**Date:** 2026-09-07
**Status:** Approved design, ready for implementation planning

## 1. Motivation

The Pair Trading Lab (PTL) service is being shut down. PTL Trader currently cannot
run without it: portfolios, strategy parameters, strategy runtime state and trade
history all live on PTL servers, and the application holds only an in-memory
working copy that it synchronises back.

This design removes the PTL integration entirely and replaces it with local
storage. Users migrate by exporting a portfolio from the PTL website while it is
still up and importing the file into the trader. From then on the trader owns its
own data.

The trading engine itself — `PairTradingCore`, `ConfinedEngine`, the
`PairTradingModel` family, the IB adapter — is not affected. The change is
confined to the persistence seam that `TECHNICAL.md` §12 already identifies as
"Replacing the portfolio source".

## 2. What PTL provides today

Four separable responsibilities, all of which must be resolved:

1. **Portfolio and strategy configuration** — `GET /portfolios` returns a tree of
   portfolios each containing a `strategies` array.
2. **Strategy runtime state** — `last_opened_datetime`, `last_opened_equity` and
   `last_model_state` are pushed back via `PUT /strategies/{uid}` and retried
   forever. Without them, Kalman strategies cannot correctly resume an open
   position after a restart.
3. **History tables** — `GET /transactionhistories` and `GET /pairtradehistories`
   backfill the Leg History and Trade History tabs at startup. Both tables are
   also fed live at runtime by `TransactionEvent` / `HistoryEntry`.
4. **AMQP telemetry** — trades, transactions and a minute heartbeat published to
   the `ptl.clients` exchange.

## 3. Decisions

| Question | Decision |
|---|---|
| Import format | JSON, identical in shape to `GET /portfolios` |
| Role of the database | SQLite is the master store; the app loads from it at startup |
| Persistence shape | Portfolio config as a JSON document per row; normalized tables for history and strategy runtime state |
| Import semantics | Import always creates a new portfolio with fresh UUIDs; never reconciles |
| Local management | Create, delete and export portfolios; add pairs by hand |
| History tabs | Kept, backed by SQLite |
| AMQP telemetry | Removed entirely |
| Confidential mode | Removed |
| Feature flags | Removed entirely |
| Database location | Platform-correct data directory; the log file moves there too |
| PTL-side work | In scope: add a JSON export to the pairtradinglab repo |
| Transitional REST migration | Out of scope: file import only |

### 3.1 Why a JSON document rather than a normalized config schema

Portfolio configuration is only ever read and written *wholesale* — the
application loads every portfolio at startup and saves a whole bean when its dirty
flag fires. It is never queried by field. A normalized schema would therefore earn
nothing at the query level while costing a hand-written mapping of roughly sixty
columns in both directions, duplicating what the existing Jackson annotations and
`updateFromJson()` already express, and taxing every future model parameter with a
column, a migration and two mapping edits.

History is different: it is appended, sorted and paged, so it gets real columns.

## 4. Architecture

### 4.1 The replacement component

`PortfolioStore` (interface) and `SqlitePortfolioStore` (implementation), a
PicoContainer singleton implementing `org.picocontainer.Startable`, injected
wherever `PtlApiClient` is today and subscribing to the same bus events.

| `PtlApiClient` today | `PortfolioStore` |
|---|---|
| `loadPortfolios(false)` on connect | `load()` in `start()` → `PortfolioList.updateFromJson()` + `initialize()` |
| `loadTransactionHistories()` / `loadPairTradeHistories()` | `SELECT` from `leg_history` / `trade_history` |
| `@Subscribe onPortfolioSyncOutRequest` → `PUT /portfolios/{uid}` | upsert portfolio document |
| `@Subscribe onStrategySyncOutRequest` → `PUT /strategies/{uid}` | upsert strategy node inside its portfolio document |
| `@Subscribe onPairStateUpdated` → `PUT` state | upsert row in `strategy_state` |
| `deletePairStrategy` → `DELETE /strategies/{uid}` | remove strategy node from the document |
| `bindPortfolioToAccount` → `PUT` + server-side 409 | enforce "one portfolio per account" in-process, then `p.bind(code)` |
| (was AMQP telemetry) | `@Subscribe` on `TransactionEvent` / `HistoryEntry` → insert history rows |

Unchanged by this design: the dirty-flag / beacon sync-out mechanism
(`Portfolio.onBeaconFlash`, `PairStrategy.onBeaconFlash`), the
`PortfolioSyncOutRequest`, `StrategySyncOutRequest` and `PairStateUpdated` events,
and `updateFromJson()` on both beans.

`PtlApiConnect`, `PtlApiProblem` and `PtlApiError` are deleted. In their place a
single `events/StoreProblem` event carries a `StoreError` enum with the locally
meaningful cases — `BIND_DENIED` (another portfolio is already bound to that
account), `IO_FAILURE` (the database could not be read or written) and
`INVALID_IMPORT` (a rejected import file). `Application` shows these in the same
`MessageDialog` slots that previously handled `PtlApiProblem`, so the UI error
paths keep their existing shape.

### 4.2 Threading

The store mirrors the existing `rq-worker` pattern: a single `db-worker` thread
owns the one JDBC connection and is fed by a `LinkedBlockingQueue`, so bus threads
never touch the database directly. The database is opened in WAL mode with
`synchronous=FULL`.

Unlike `rq-worker`, the store does **not** retry indefinitely. A write is attempted
three times; on continued failure it logs at `ERROR` and posts a `LogEvent` that
appears in the Log tab. Infinite retry was correct against a flaky network but
would only hide a failing disk.

Startup reads happen on the same thread before the SWT shell opens.

### 4.3 Startup flow

```
main()
 ├ JUnique.acquireLock("…Application.<profile>")
 ├ Realm.runWithDefault(SWT realm)
 ├ build Pico container, resolve Application, pico.start()
 │   └ SqlitePortfolioStore.start(): open + migrate DB,
 │       load portfolios → PortfolioList.updateFromJson() + initialize(),
 │       load histories
 ├ window.open()
 └ on shell activation, if -autostart: connectToIb()      (was connectToPtl())
```

## 5. Database

### 5.1 Location

A platform-correct per-user data directory holding both `<profile>.db` and
`<profile>.log`:

| Platform | Path |
|---|---|
| Windows | `%LOCALAPPDATA%\PTLTrader` |
| Linux | `~/.local/share/ptltrader` |
| macOS | `~/Library/Application Support/PTLTrader` |

This also fixes the documented gotcha that `LoggerFactoryImpl` writes to
`~/Application Data/PTLTrader/` on every platform including Linux and macOS. The
log moves with the database so the two do not diverge; a single resolver class
owns the path decision.

One profile means one database file and one JUnique lock, so the existing
single-instance-per-profile rule already guarantees a single writer.

### 5.2 Schema

```sql
CREATE TABLE schema_version (version INTEGER NOT NULL);

CREATE TABLE portfolios (
  uid        TEXT PRIMARY KEY,
  name       TEXT NOT NULL,          -- denormalized so the list can be ordered
  document   TEXT NOT NULL,          -- GET /portfolios object, incl. strategies[]
  updated_at TEXT NOT NULL
);

CREATE TABLE strategy_state (
  strategy_uid         TEXT PRIMARY KEY,
  portfolio_uid        TEXT NOT NULL REFERENCES portfolios(uid) ON DELETE CASCADE,
  last_opened_datetime TEXT,         -- 'yyyy-MM-dd HH:mm:ss', UTC
  last_opened_equity   REAL,
  last_model_state     TEXT,         -- polymorphic JSON, as PTL stored it
  updated_at           TEXT NOT NULL
);

CREATE TABLE trade_history (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  datetime        TEXT NOT NULL,
  account         TEXT NOT NULL,
  stock1          TEXT NOT NULL,
  stock2          TEXT NOT NULL,
  action          TEXT NOT NULL,
  realized_pl     REAL NOT NULL,
  realized_pl_pct REAL NOT NULL,
  commissions     REAL NOT NULL,
  zscore          REAL NOT NULL,
  comment         TEXT
);

CREATE TABLE leg_history (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  datetime     TEXT NOT NULL,
  account      TEXT NOT NULL,
  symbol       TEXT NOT NULL,
  action       TEXT NOT NULL,
  qty          INTEGER NOT NULL,
  price        REAL NOT NULL,
  value        REAL NOT NULL,
  realized_pl  REAL NOT NULL,
  commissions  REAL NOT NULL,
  slippage     REAL NOT NULL,
  fill_time_ms INTEGER NOT NULL
);

CREATE INDEX ix_trade_history_dt ON trade_history (datetime DESC);
CREATE INDEX ix_leg_history_dt   ON leg_history (datetime DESC);
```

**`last_opened_datetime` format.** It is `yyyy-MM-dd HH:mm:ss` in UTC — the
format `PairStrategy.updateFromJson()` parses, not ISO-8601. `StrategyState`
writes it with that pattern and `updateFromJson` reads it back with
`DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZoneUTC()`. The two must
match exactly and neither side may be changed alone: writing ISO-8601 here, or
switching the writer to it to match an earlier draft of this section, would make
every stored instant reparse as a different one and shift every `max_days` exit
by the local UTC offset.

History columns mirror the immutable fields of `TradeHistoryEntry` and
`LegHistoryEntry`; the derived `*S` display strings are recomputed by the
constructors and are not stored.

**Writing history.** PTL persisted history as a side effect of receiving
telemetry, so with AMQP gone nothing would write it. The store therefore
subscribes to `TransactionEvent` and `HistoryEntry` — the same events that
`LegHistory.onTransactionEvent` and `TradeHistory.onHistoryEntry` already consume
to update the tables in memory — and inserts a row for each. The model beans keep
their existing subscriptions and are not modified; the store is an additional,
independent subscriber, so the in-memory tables and the database are written from
the same events without either depending on the other.

**Reading history.** At startup the store loads the most recent 1000 rows of each
table, ordered by `datetime` descending, which is the same order the tabs display.
This bounds both startup cost and memory for an installation that has been running
for years. The tables are append-only and are never pruned by the application:
history is small (a few hundred bytes per row, a handful of rows per trade) and
silently discarding a user's trade record would be worse than the disk cost.

### 5.3 Why `strategy_state` is separate from the document

Runtime state is written on a different cadence from configuration — on every
position open and close, from bus threads rather than from the beacon — and it is
the one write whose loss actually breaks correct resumption of an open position.
Keeping it in its own table means a configuration save and a state save can never
race over the same row, and a config upsert can never clobber state.

On load the store reads each `document`, splices the matching `strategy_state`
values into each strategy node as `last_opened_datetime`, `last_opened_equity` and
`last_model_state`, and hands the assembled `JsonNode` to `updateFromJson()`,
which already reads exactly those field names. `updateFromJson()` does not change.

### 5.4 Migrations

`schema_version` holds a single integer. The store applies numbered migration
steps in a transaction at startup. Version 1 is the schema above. Opening a
database whose version is *higher* than the build understands is a fatal startup
error with a clear message, not a silent downgrade.

## 6. Making serialization symmetric

This is the substantive model-layer work and the part most likely to fail
silently, because a missed annotation loses a strategy parameter only on the next
restart.

Today the model parameters on `PairStrategy` are annotated `@JsonIgnore`, because
PTL owned them and the trader wrote back only rules, hours, margins and status.
With the trader as master they must persist.

**Flip from `@JsonIgnore` to `@JsonProperty`** on `PairStrategy`: `model`,
`entry_threshold`, `exit_threshold`, `downtick_threshold`, `max_score`,
`ratio_ma_period`, `ratio_stddev_period`, `ratio_entry_mode`, `ratio_rsi_period`,
`ratio_rsi_threshold`, `residual_linreg_period`, `neutrality`, `ka_ve`,
`ka_usage_target`, `timezone`, and the constructor-set `uid`, `ticker1`,
`ticker2`, `trade_as_1`, `trade_as_2`. On `Portfolio`: `uid`, `name`.

**Needs an explicit `@JsonGetter`:** `ratio_ma_type` is a TA-Lib `MAType` in
memory and would otherwise serialize as an enum name, but `updateFromJson` reads
it as an ordinal (`MAType.values()[n.get("ratio_ma_type").asInt()]`). The getter
must emit the ordinal.

**Already round-trips correctly:** the `enable_*` booleans. Jackson writes
`true`/`false` and `BooleanNode.asInt()` returns 1/0, which is what
`updateFromJson` compares against.

**Stays `@JsonIgnore`**, being derived or runtime-only — on `PairStrategy`:
`positions`, `zscoreBid`/`zscoreAsk` and their display strings, `rsi`, `pl`,
`status`, `coreStatus`, `daysRemaining`, `profitPotential`, `closeable`,
`openable`, `deletable`, `resumable`, `core`, `parent`, `initialized`, `dirty`,
`stockDisp1`/`stockDisp2`; on `Portfolio`: `pairCount`, `totalPl`, `slotUsage`,
`equity`, `bindEnabled`, `unbindEnabled`, `lastUpdate`.

**Deliberately excluded from the document:** `last_opened_datetime`,
`last_opened_equity`, `last_model_state`, which belong to `strategy_state`.

**Removed from both beans:** `features` (see §8).

## 7. Import, export and local management

All new dialogs are separate classes alongside the existing `AboutDialog`, not
additions to `Application.java`. That file is already 131 KB and is the worst
available place to put new UI.

### 7.1 Import

`File › Import Portfolio…` opens a `FileDialog` filtered to `*.json`. The importer
accepts either the API's array form or a single portfolio object.

Import **always creates a new portfolio**. It mints a fresh UUID for the portfolio
and for every strategy, discarding the uids in the file, and it **clears
`account_code`**. An imported account binding is meaningless on a different
machine, and binding to a live account must be a deliberate act by the user. As a
consequence an import can never modify or clobber an existing portfolio, and in
particular can never disturb the runtime state of a strategy holding an open
position.

Importing the same file twice therefore yields two independent portfolios. Users
remove the unwanted one with `File › Delete Portfolio`.

The importer validates the document before inserting anything: required fields
present, `model` recognised, and both tickers parseable by
`ContractExt.createFromGoogleSymbol()`. A malformed file is rejected whole, with a
message naming the offending strategy. On success it reports how many pairs were
imported.

### 7.2 Export

`File › Export Portfolio…` writes the selected portfolio's configuration document
as a one-element array, so an exported file is directly importable. Runtime state
is not exported: it describes a position in one specific account and must not
travel with the configuration.

### 7.3 Creating and deleting

`File › New Portfolio…` prompts for a name. `PortfolioFactoryImpl.create()`
already mints a UUID, so no factory changes are needed.

`File › Delete Portfolio` refuses if the portfolio is bound to an account or if
any of its strategies holds an open position, and otherwise asks for
confirmation before deleting the row (cascading `strategy_state`).

`Portfolio › Add Pair…` prompts for two Google-style symbols, the instrument type
for each leg (STK or CFD) and the model. `PairStrategyFactory.createForPortfolio(
p, stock1, stock2, tradeAs1, tradeAs2)` already exists and already mints a UUID.
Symbols are validated through `ContractExt.createFromGoogleSymbol()` in the
dialog, so an unsupported exchange fails at entry rather than at the first trade
attempt. New strategies are created with `status` inactive so that adding a pair
can never start trading it by surprise. After `Portfolio.addPairStrategy(s)` — which
only appends to the list and fires a property change — the flow calls
`s.initialize()`, which registers the strategy's two `Position` objects on the bus
and calls `bind()`; on a portfolio already bound to an account that is what creates
and starts the trading core. Without it the new pair sits inert, with no z-score and
no engine status, until the application is restarted.

## 8. Removal inventory

Deleted outright:

* `PtlApiClient`, `PtlApiError`, `events/PtlApiConnect`, `events/PtlApiProblem`
* `AmqpEngine`, `AmqpProxy`, `AmqpControlMessage`, `AmqpError`,
  `events/AmqpConnect`, `events/AmqpProblem`, `events/SerializedEvent`,
  `events/ConfidentialEvent`, `events/ImportantEvent`, `events/TestEvent`,
  `events/ImportantTestEvent`, `events/MonitorEvent`
* `SystemMonitor`
* `SupportedFeatures`, the bind-time feature gate, and the `features` list on
  `Portfolio` and `PairStrategy`. A local build always supports its own feature
  set, so the check could only ever produce false refusals. A `features` array
  present in an imported file is ignored.
* `StringXorProcessor` and `StringXorProcessorTest`
* On `Settings`: `ptlAccessKey`, `ptlSecretKey`, `savePtlSecretKey`,
  `enableConfidentialMode`, `ptlConnectEnabled`, along with the credentials
  section of the Settings UI
* The *Update Portfolios From PTL* menu item and the *Connect to PTL* action
* The `X-PTL-Version` header usage. `Version` itself stays, for the About box.

Build changes: remove `com.rabbitmq:amqp-client` and `com.ning:async-http-client`;
add `org.xerial:sqlite-jdbc`.

**Note on `SystemMonitor`.** It is the only consumer of
`ManualInterventionRequested` other than the emitter in `ConfinedEngine`, so
deleting it removes the only aggregated view of pending interventions. The UI
never displayed that aggregate — it shows per-pair `coreStatus` and `resumable` —
so nothing regresses visibly, but the capability is dropped rather than re-homed.
`ManualInterventionRequested` itself stays, since `ConfinedEngine` still posts it.

## 9. Migration path for users

There is no in-application migration from PTL. Users export a JSON file from the
PTL website while the service is still running and import it into the trader.

This makes the PTL-side export (§10) a hard prerequisite that must ship, and be
announced, *before* the service closes. The trader release should carry a
first-run message pointing at `File › Import Portfolio…` when it starts with an
empty database, and the README should state the deadline.

Existing preference keys under `com/pairtradinglab/ptltrader/model/Settings/` are
left in place rather than actively deleted; they are simply no longer read. The IB
preferences node is untouched.

## 10. PTL-side JSON export

In the `pairtradinglab` repository, add `cmd_export_json()` to
`PortManagerController`, taking the same `puid` parameter as the existing
`cmd_export()`. It reuses the portfolio row, the `FeatureListGenerator` output and
the strategies query from `Api\Portfolios::index()`, decodes `last_model_state`,
and emits the result wrapped in an array as `Content-Type: application/json` with
`Content-Disposition: attachment; filename=portfolio.json`.

The port manager UI gains a JSON download link beside the existing CSV export.

The existing CSV export is left in place. It is lossy for this purpose — it omits
`uid`, `status`, `downtick_threshold`, `allow_reversals`, `trade_as_1`/
`trade_as_2` and every portfolio-level field — but it remains useful for
spreadsheet analysis.

## 11. Testing

* **Serialization round-trip** — parse a representative portfolio document, run
  `updateFromJson()`, serialize the beans back, and assert deep equality on the
  configuration subset. This is the highest-value test in the change: it is what
  catches a forgotten annotation before it silently loses a parameter.
* **Runtime state survival** — post a `PairStateUpdated` for a Kalman strategy,
  reopen the store, and assert `last_model_state` deserializes to the same
  `AbstractPairTradingModelState` subclass with the same values. This protects the
  one piece of data whose loss has a monetary cost.
* **Store CRUD** — `SqlitePortfolioStoreTest` against a temporary *file* database,
  not `:memory:`, because WAL behaviour differs.
* **Import** — fresh uids assigned, `account_code` cleared, malformed documents
  rejected whole, the same file imported twice producing two independent
  portfolios.
* **Export** — an exported file re-imports to an equivalent portfolio.
* **Schema migration** — v0 to v1 on an empty file; a future-versioned database
  fails startup with a clear error.
* **Regression** — the existing engine and model test suites must continue to pass
  untouched. If any of them needs changing, that is a signal the change has leaked
  out of the persistence seam.

## 12. Documentation to update

`README.md` (the PTL account requirement, the migration deadline, the new import
workflow), `ARCHITECTURE.md` (§1 external systems, §8.4, §8.5, §9.1, §9.2) and
`TECHNICAL.md` (§5 configuration and persistence, §6.1, §6.2, §6.4, §12).

## 13. Suggested implementation order

1. Data directory resolver; move the log file. Small, independent, testable.
2. Schema, migrations and `SqlitePortfolioStore` with load and upsert, behind the
   `PortfolioStore` interface. No UI yet.
3. Symmetric serialization on `Portfolio` and `PairStrategy`, with the round-trip
   test. Do this before wiring the store in, so persistence is provably lossless
   first.
4. Swap `PtlApiClient` for `PortfolioStore` in the DI wiring and the startup flow.
   At this point the application runs entirely from local storage.
5. History persistence and backfill.
6. Import, export, new portfolio, delete portfolio, add pair — dialogs and menu.
7. Removal of AMQP, `SystemMonitor`, credentials, feature flags and dependencies.
8. Documentation.
9. PTL-side JSON export (independent of 1–8; can proceed in parallel and should
   ship first).
