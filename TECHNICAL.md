# PTL Trader — Technical Documentation

Developer- and operator-facing reference: toolchain, source layout, runtime
configuration, external interfaces, model parameters, engine status codes, safety
rules, testing and packaging.

For the design rationale — layering, threading model, event flows — see
[ARCHITECTURE.md](ARCHITECTURE.md). For end-user instructions see the
[PTL Trader Manual](https://wiki.pairtradinglab.com/wiki/PTL_Trader_Manual).

---

## 1. Technology stack

| Concern | Choice |
|---|---|
| Language / bytecode target | Java, `targetCompatibility = 11` (`gradle.properties`) |
| Build | Gradle 7.6.1 via the wrapper, `shadow` 4.0.2 for fat jars |
| UI | SWT 3.122.0 + JFace + Eclipse Data Binding |
| DI container | PicoContainer 2.16 |
| Event bus | Guava 19.0 `AsyncEventBus` |
| Broker API | `com.ib:ib-api-client:0.1` |
| HTTP | ning `async-http-client` 1.9.40 |
| AMQP | `com.rabbitmq:amqp-client` 5.11.0 |
| JSON | Jackson 2.8.4 |
| Time | Joda-Time 2.14.3 |
| Technical analysis | TA-Lib (`com.tictactec:ta-lib:0.4.0`) |
| Linear algebra | EJML 0.30 |
| Logging | SLF4J + log4j 1.2.17 |
| Concurrency annotations | `net.jcip:jcip-annotations` |
| Single-instance lock | `junique` 1.0.4 |
| Tests | JUnit 4.12 + Mockito 2.2.9 |

Current version: **1.7.0** (`gradle.properties`, mirrored in
`com.pairtradinglab.ptltrader.Version` — keep the two in sync when releasing; the
value is sent to the PTL API in the `X-PTL-Version` header).

---

## 2. Building

### 2.1 Prerequisites

* **JDK 11** — required for *building*. Newer JDKs are fine for running.

All dependencies resolve from public repositories: alongside Maven Central,
`build.gradle` declares an S3-hosted mirror of some packages orphaned by the
jcenter shutdown.

### 2.2 Commands

```bash
./gradlew build                       # compile + run the unit tests
./gradlew test                        # tests only
./gradlew run                         # build and launch for the current platform
./gradlew shadowJar -PforceArch=win64 # fat jar for one target
./build_all_architectures.sh          # win64 + macosx + linux64
./gradlew swtDiag                     # print the resolved SWT coordinates
```

Artifacts land in `build/libs/` as `ptltrader-<version>-<archSpec>.jar`.

### 2.3 Cross-platform SWT selection

SWT ships a different native artifact per platform, so the target must be chosen at
build time. `gradle/swt.gradle` maps `-PforceArch=<win32|win64|macosx|linux64>` to
the `swtWindowingLibrary` / `swtArch` / `swtPlatform` triple and sets `archSpec`
(the jar classifier). Without `-PforceArch`, it derives the triple from the build
host's `os.name` / `os.arch` and uses the classifier `defaultarch`.

A `dependencySubstitution` block in `build.gradle` additionally rewrites the
unresolved `org.eclipse.swt.${osgi.platform}` coordinate that some Eclipse
artifacts pull in transitively.

> 32-bit Windows (`win32`) is still selectable in the script but is no longer a
> supported target.

---

## 3. Source layout

```
build.gradle                 build config, dependency list, shadowJar setup
gradle/swt.gradle            per-platform SWT coordinate resolution
build_all_architectures.sh   convenience: build all three fat jars
launch4j/                    Windows EXE wrapper configuration
wix/                         WiX MSI installer sources
src/main/java/com/pairtradinglab/ptltrader/
    Application.java         entry point, DI wiring, SWT UI, data bindings
    AboutDialog.java         about box
    PtlApiClient.java        PTL REST client
    AmqpEngine.java          RabbitMQ connection, confined to its own thread
    AmqpProxy.java           event → JSON → SerializedEvent bridge
    Beacon.java              minute timer
    SystemMonitor.java       heartbeat / intervention tracking
    LoggerFactory(Impl).java log4j bootstrap
    ActiveCores.java         registry of running core threads
    RuntimeParams.java       command-line arguments
    Settings? → model/       (see below)
    StringXorProcessor.java  secret-key obfuscation
    SupportedFeatures.java   feature gate
    Version.java             version string sent to the API
    events/                  application-level bus events
    ib/SimpleWrapper.java    IB API adapter (EWrapper implementation)
    ib/HistoricalDataRequest.java
    model/                   observable domain beans + converters + validators
    trading/                 engine, models, data providers, activity detector
    trading/events/          trading-layer bus events
    trading/kernelfx/        numeric kernel for the Kalman models
    org/eclipse/wb/swt/      WindowBuilder resource manager
src/main/resources/…         icons, LED images
src/test/java/…              JUnit tests (103 test methods)
```

`Application.java` is largely generated/maintained by **Eclipse WindowBuilder**.
Edit `createContents()` and `initDataBindings()` with WindowBuilder where possible;
manual bindings that WindowBuilder cannot round-trip live in `finishBindings()`.

---

## 4. Running

```bash
# Linux / Windows
java --add-opens java.base/java.net=ALL-UNNAMED \
     --add-opens=java.base/sun.security.util=ALL-UNNAMED \
     -jar ptltrader-1.7.0-linux64.jar

# macOS additionally requires the SWT main-thread flag
java --add-opens java.base/java.net=ALL-UNNAMED \
     --add-opens=java.base/sun.security.util=ALL-UNNAMED \
     -XstartOnFirstThread -jar ptltrader-1.7.0-macosx.jar
```

The `--add-opens` flags are required by the ning HTTP client and the TLS stack on
Java 11+.

A running **IB Trader Workstation or IB Gateway** with the API enabled is required,
as are the relevant US market-data subscriptions.

### 4.1 Command-line arguments

`RuntimeParams` parses positional arguments only:

| Position | Value | Meaning |
|---|---|---|
| `args[0]` | profile name | default `default`. Scopes the instance lock, the preferences nodes and the log file. |
| `args[1]` | `autostart` | connect to PTL as soon as the window is first activated, then connect to IB as soon as the AMQP bus comes up. |

```bash
java … -jar ptltrader.jar accountB autostart
```

### 4.2 Single-instance enforcement

`JUnique.acquireLock("com.pairtradinglab.ptltrader.Application.<profile>")` runs
before anything else. A second launch with the same profile prints
`Error: PTL Trader is already running for profile: <profile>` and exits. Different
profiles may run concurrently.

---

## 5. Configuration and persistence

There is **no configuration file**. Everything is either fetched from PTL or stored
in `java.util.prefs` (Windows registry, `~/.java/.userPrefs` on Linux,
`~/Library/Preferences` on macOS).

| Preferences node (`Preferences.userRoot()`) | Keys |
|---|---|
| `com/pairtradinglab/ptltrader/model/Settings/<profile>` | `ptlAccessKey`, `ptlSecretKey` (obfuscated), `savePtlSecretKey`, `enableConfidentialMode` |
| `com/pairtradinglab/ptltrader/ib/SimpleWrapper/1/<profile>` | `ibClientId` (default `1`), `ibHost` (default `localhost`), `ibPort` (default `7496`), `ibFaAccount` (default empty) |

> The `1` segment in the IB node is a connection index, reserved for a future
> multi-connection build.

### 5.1 Secret-key storage

`StringXorProcessor` XORs the secret key with the hard-coded constant
`Settings.SECRET_KEY_ENC_KEY` and Base64-encodes the result. **This is obfuscation,
not encryption** — it stops the key appearing in plain text in the registry and
nothing more. Users who do not want it stored at all should clear
*Save secret key*, which removes the `ptlSecretKey` entry entirely.

### 5.2 Confidential mode

When enabled, `AmqpProxy` drops every event implementing `ConfidentialEvent`
instead of publishing it — of the events it forwards, that means `TransactionEvent`
and `HistoryEntry`. (`EquityChange` and `StrategyPlUpdated` also carry the marker
but are not forwarded to AMQP in the first place.) Trading continues normally; only
telemetry is withheld, so PTL will not be able to show trade history or results for
that instance.

### 5.3 Logging

`LoggerFactoryImpl` configures log4j once, at root level `DEBUG`, with:

* a `ConsoleAppender`, and
* a `RollingFileAppender` at
  `${user.home}/Application Data/PTLTrader/<profile>.log`, max 10 MB per file,
  5 backups,

using the layout `%d{ISO8601} [%t] %p %c %x - %m%n`. The thread name (`%t`) and
logger name (`%c`) are the useful axes: bus threads are `bus-master-N`, per-pair
core threads are `<accountCode>_<SYM1>_<SYM2>`, and per-pair loggers are named
`<SYM1>_<SYM2>`.

> Gotcha: the `Application Data` path segment is used on **every** platform, not
> just Windows. On Linux/macOS the log therefore lands in
> `~/Application Data/PTLTrader/`.

---

## 6. External interfaces

### 6.1 Pair Trading Lab REST API

Base URL `https://api.pairtradinglab.com`. Authentication is **preemptive HTTP
Basic**: access key as principal, secret key as password. Every request except
`updatePairStrategyState` also carries `X-PTL-Version: <Version.getVersion()>`.
Timeouts: 20 s connect, 30 s request.

| Method | Path | Called by | Notes |
|---|---|---|---|
| `GET` | `/portfolios` | `loadPortfolios(updateOnly)` | full portfolio + strategy tree; feeds `PortfolioList.updateFromJson()` and then `initialize()` |
| `GET` | `/transactionhistories` | `loadTransactionHistories()` | populates the Leg History table |
| `GET` | `/pairtradehistories` | `loadPairTradeHistories()` | populates the Trade History table |
| `PUT` | `/portfolios/{uid}` | `updatePortfolio(p)` | Jackson-serialised `Portfolio`; queued + retried |
| `PUT` | `/portfolios/{uid}` | `bindPortfolioToAccount(p, code)` | body `{"account_code": …}`; **not** queued — the UI needs the result |
| `PUT` | `/strategies/{uid}` | `updateStrategy(s)` | Jackson-serialised `PairStrategy`; queued + retried |
| `PUT` | `/strategies/{uid}` | `updatePairStrategyState(ps)` | body `{last_opened_equity, last_opened_datetime, last_model_state}`; queued + retried |
| `DELETE` | `/strategies/{uid}` | `deletePairStrategy(ps)` | queued + retried |

Response handling on `GET /portfolios`:

| Status | Result |
|---|---|
| `200` | parse, update model, post `PtlApiConnect` (which triggers the AMQP connect) |
| `400`, `401` | `PtlApiError.ACCESS_DENIED` → error dialog |
| `412` | `PtlApiError.UNSUPPORTED_VERSION` — this build is too old for the server |
| transport `SSLProtocolException` | `PtlApiError.SSL_NAME` |
| other throwable | `PtlApiError.UNKNOWN` |

Queued writes are drained by the `rq-worker` thread, which **retries forever** at
10 s intervals until it sees HTTP 200. This is intentional: dropping a
strategy-state update would desynchronise PTL from the live account.

### 6.2 Strategy JSON contract

Fields consumed by `PairStrategy.updateFromJson()`. Those in the upper block are
applied **only while the strategy has no running core** (i.e. before it is bound, or
after an unbind) — changing a model parameter under a live position is not
supported.

| JSON field | Model property | Notes |
|---|---|---|
| `model` | `model` | `Ratio`, `Residual`, `Kalman-grid-v2`, `Kalman-auto` |
| `entry_threshold`, `exit_threshold`, `downtick_threshold`, `max_score` | thresholds | z-score bands |
| `ratio_ma_type`, `ratio_ma_period`, `ratio_stddev_period` | Ratio model | `ratio_ma_type` is an ordinal into TA-Lib `MAType` |
| `ratio_entry_mode` | `entryMode` | `0` simple, `1` uptick, `2` downtick |
| `ratio_rsi_period`, `ratio_rsi_threshold` | Ratio RSI filter | optional, default `10` / `0` (disabled) |
| `residual_linreg_period` | Residual model | OLS window |
| `ka_ve`, `ka_usage_target` | Kalman-auto | observation covariance, usage target |
| `neutrality` | `neutrality` | `0` dollar-neutral, `1` beta-neutral |
| `ticker1margin`, `ticker2margin` | `marginPerc1/2` | per-leg margin requirement, percent |
| `last_opened_datetime`, `last_opened_equity`, `last_model_state` | resumed position state | UTC `yyyy-MM-dd HH:mm:ss`; model state is polymorphic JSON |
| `enable_max_days` / `max_days` | timeout rule | |
| `enable_min_pl` / `min_pl` | minimum P/L to close | |
| `enable_min_price` / `min_price` | minimum leg price to enter | |
| `enable_min_profit_potential` / `min_profit_potential` | minimum expectation | |
| `allow_reversals` | reversal rule | `1`/`0` |
| `entry_start_hour` … `exit_end_minute` | trading windows | |
| `timezone` | `timezoneId` | Joda zone id, default `America/New_York` |
| `allow_positions` | `0` both, `1` long only, `2` short only | |
| `status` | trading status: `0` inactive, `1` maintain, `2` active | |
| `slot_occupation` | fraction of a portfolio slot this pair consumes | |
| `features` | list of feature flags, gated by `SupportedFeatures` | |

Pairs themselves come from `ticker1` / `ticker2` (plus `trade_as_1` / `trade_as_2`)
on the strategy node; the portfolio node supplies `uid`, `name`, `account_code`,
`max_pairs_open`, `master_status`, `pdt_rules`, `account_alloc`, `features` and the
`strategies` array.

### 6.3 Interactive Brokers

| Aspect | Value |
|---|---|
| Minimum server version | `SimpleWrapper.MIN_IB_API_VERSION = 66` (connection refused below this) |
| Default endpoint | `localhost:7496`, client id `1` |
| Reconnect | every 45 s after `connectionClosed()`, until re-established |
| Market data | `reqMktData(reqId, contract, "236", false, null)` — generic tick 236 supplies the *shortable* field |
| Shortability threshold | `GenericTick` value `> 2.5` ⇒ shortable |
| Historical data | `1 Y` of `1 day` bars, close prices only, throttled to one request/second |
| Request id ranges | market data from `20 000 000` (`MarketDataProvider`), historical/other from `1 000 000` (`SimpleWrapper.nextReqId`) |
| Order ids | seeded by `nextValidId`, allocated under `SimpleWrapper.lockOrderId()` |
| Orders | always `MKT`, `m_orderRef` = `ptl open long|open short|close SYM1-SYM2` |

#### Symbol and contract mapping

Strategies use Google-style symbols `EXCHANGE:TICKER`.
`ContractExt.createFromGoogleSymbol(symbol, useCfd)`:

* splits on `:` (anything else throws `IllegalArgumentException`);
* replaces `.` with a space in the ticker (`RDS.A` → `RDS A`);
* sets `secType` to `STK` (with `primaryExch = ISLAND`) or `CFD` when `trade_as_*`
  is `TRADE_AS_CFD`;
* always routes `SMART`;
* maps the exchange to a currency — `NYSE`, `NASDAQ`, `NYSEARCA`, `NYSEAMEX`,
  `NYSEMKT` all map to `USD`; anything else throws.

Market-data subscriptions and historical requests always use the `STK` form even
for CFD-traded pairs.

#### IB error codes with special handling

| Code | Handling |
|---|---|
| `404` (`ERRC_ORDER_HELD`) | recoverable — wait 120 s, then cancel/liquidate |
| `326` | connection rejected — mark IB disconnected |
| `1101` (`ERRC_RECONNECT_DATA_LOST`) | `MarketDataProvider` re-subscribes everything |
| `2176` | suppressed (fractional-share rounding notice on historical data) |
| `≥ 1100 && < 2100` | logged as warning |
| `≥ 2100` | logged as notice; also treated as evidence the session is live |
| `≥ 1000` | ignored by `ConfinedEngine` (not order-related) |

### 6.4 PTL AMQP event bus

| Aspect | Value |
|---|---|
| Host | `amqp.pairtradinglab.com`, TLS (`factory.useSslProtocol()`), vhost `/` |
| Credentials | PTL access key / secret key |
| Outbound exchange | `ptl.clients`, routing key = simple class name of the event |
| Message properties | `content-type: application/json`, `user-id: <accessKey>`; important events additionally `deliveryMode = 2` |
| Retry | important events retry every 20 s until published; unimportant events are dropped when the connection is down |
| Reconnect throttle | at most one connect attempt per 30 s |
| Inbound | exclusive server-named queue bound to exchange `ptl.<accessKey>`; messages are consumed, logged and acked — **no dispatch is implemented yet** |

Event types published — these are exactly the events `AmqpProxy` subscribes to:
`TransactionEvent`, `HistoryEntry`, `MonitorEvent`, `TestEvent` and
`ImportantTestEvent`.

`MonitorEvent` is the heartbeat, emitted every minute with a status bitmask:

| Bit | Meaning |
|---|---|
| `1` | `STATUS_IB_NOT_CONNECTED` |
| `2` | `STATUS_INTERVENTIONS_PENDING` |

---

## 7. Trading model reference

All models extend `PairTradingModel` and are instantiated by
`PairTradingCoreFactoryImpl` from `PairStrategy.model`. An unrecognised name yields
`PairTradingModelDummy`, which never trades and reports `UNSUPPORTED_MODEL`.

Common contract:

| Method | Contract |
|---|---|
| `setPrices(double[] p1, double[] p2)` | fit the model to aligned daily closes; throws if shorter than `getLookbackRequired()` |
| `getZScore(mode)` | `ZSCORE_BID` (−1), `ZSCORE_ASK` (+1) or `ZSCORE_AUTO` (0). Must never throw |
| `entryLogic()` | `SIGNAL_LONG` (+1, long leg 1 / short leg 2), `SIGNAL_SHORT` (−1), or `SIGNAL_NONE` |
| `exitLogic(currentPosition)` | true ⇒ close now |
| `getProfitPotential(margin, coef1, coef2)` | expected currency profit if the spread reverts to the exit band. Must never throw |
| `calcLegQtys(...)` | leg sizing; default is dollar-neutral |
| `checkReversalCondition()` / `storeReversalState()` | support the "no same-direction re-entry on the same day" rule |

The bid/ask pair is always used conservatively: `MultiSpread` / `MultiRatio` compute
the *worst-case* and *best-case* spread from both legs' bid and ask, so an entry is
only signalled when even the unfavourable side of the quote clears the threshold.

### 7.1 Ratio

Spread series is `p1/p2`. TA-Lib supplies the moving average (`ratio_ma_type`,
`ratio_ma_period`) and rolling standard deviation (`ratio_stddev_period`);
`z = (ratio − MA) / stddev`. TA-Lib unstable periods are pinned at 34 (EMA, KAMA,
MAMA, T3) and 24 (RSI). Optional RSI filter: with `ratio_rsi_threshold > 0`, a long
signal additionally requires both bid- and ask-side RSI below `50 − threshold`, and
a short signal both above `50 + threshold`. Lookback is the maximum of the MA,
stddev and RSI lookbacks.

### 7.2 Residual

`OlsCalculator` fits `p1 = A·p2 + B` over the last `residual_linreg_period` samples
(twice: at lag 0 for the current fit and lag 1 for the previous bar's z-score, which
the uptick/downtick modes need) and computes the population standard deviation of
the residual. `z = spread / stdDev`. Lookback is `residual_linreg_period + 1`.

Entry modes:

| Mode | Long condition (short is symmetric) |
|---|---|
| simple | `zAsk ≤ −entry` and `zAsk ≥ −maxScore` |
| uptick | as simple, and the previous bar's z was `> −entry` (crossing in) |
| downtick | previous bar's z was `≤ −entry`, current z is above it but still below `−downtickThreshold` (crossing back out) |

### 7.3 Kalman grid (`Kalman-grid-v2`)

`SubModelKalmanGrid` instantiates a two-dimensional grid of `SubModelKalman`
filters — log₁₀ transition covariance δ from −13 to −1 (52 steps) × log₁₀
observation covariance Ve from −4 to −3 (5 steps) — and attaches one
`SimpleStrategy` to each, with a fixed entry threshold of 1 and an exit threshold of
−1 (both directions enabled) or 0 (single direction).

Every bar, each filter updates, each simulated strategy opens/closes a
`PairPosition`, and an `AbstractPerfTracker` scores it:

* `PerfTrackerOls` (default) fits an OLS trend to each strategy's cumulative log
  equity and weights by `log(1+trades) · slope / (1 + stddev)`, zeroing negative or
  inhibited slopes;
* `PerfTrackerSharpe` weights by a downside-deviation Sharpe ratio.

β, α and σ are then the weight-averaged values across all filters, and the live
z-score is computed from that merged fit. `UNSTABLE_PERIOD = 60`; the required
lookback is `grid.getLookback() + 60 − 3`.

### 7.4 Kalman auto

`SubModelKalmanAuto` grids δ only (log₁₀ −13…−1, 104 steps) with a fixed `ka_ve`,
and replaces the performance tracker with a **`UsageTracker`**: each simulated
strategy's *time-in-market* over a 240-bar window is compared against
`ka_usage_target` (percent) through a Gaussian shape function
(σ = 1.5). The weighted mean δ is computed, the single grid filter whose δ is
closest to that target is selected, and its β/α/σ drive the live score. Entry uses a
`TripleBandLogic` with in-threshold 1 and out-threshold 0.

`UNSTABLE_PERIOD = 120`; required lookback is `grid.getLookback() + 120 − 3`.

### 7.5 Model state locking

`PairTradingModelKalmanAuto` and `PairTradingModelKalmanGrid` implement
`LockableStateModel`. When a pair position is fully opened, the engine captures
`getCurrentState()` (for Kalman-auto: the selected sub-model id), locks the model to
it, stores it on the strategy and pushes it to PTL. The position is therefore always
exited on the same sub-model that entered it, even across a restart. The state is
released on close, on an externally observed close, and on `resetState()`.

### 7.6 Position sizing

Default (dollar-neutral), in `PairTradingModel.calcLegQtys`:

```
qty1 = floor( margin / (coef2·price1 + coef1·price1) )
qty2 = floor( qty1 · price1 / price2 )
```

Beta-neutral (`neutrality = 1`, Kalman-auto only), with β clamped to `[0.05, 10]`:

```
qty2 = floor( margin / (coef1·price1·β + coef2·price2) )
qty1 = floor( qty2 · β )
```

`margin` comes from `Portfolio.allocateMargin(slotOccupation)`:

```
allocateMargin(occ) = occ · equity · (accountAlloc/100) / maxPairsOpen
```

where `equity` is IB's `EquityWithLoanValue` for the bound account.

---

## 8. Engine status codes

`CoreStatus` is rendered verbatim in the UI's *Engine Status* column and is the
first thing to read when a pair is not trading.

| Constant | Displayed | Meaning |
|---|---|---|
| `NONE` | *(blank)* | core stopped |
| `PENDING` | pending | core started, waiting for first data |
| `NOT_READY` | wait for portfolio | no IB portfolio update received yet for either leg |
| `NOT_CONNECTED` | not connected | IB socket down |
| `SOCKET_DISCOVERY` | wait for socket | no wrapper resolved for the account code |
| `INACTIVE` | fully inactive | strategy or portfolio master status is inactive |
| `MAINTAIN_ONLY` | no new positions allowed | maintain mode — existing positions still managed |
| `BLOCKED` | wait for manual intervention | see §9.3 |
| `TRANSIENT` | transient | an order is in flight |
| `ONE_LEG` | one leg opened only | asymmetric position; the engine refuses to act automatically |
| `UNSUPPORTED_MODEL` | model not supported | `PairTradingModelDummy` in use |
| `UNSUPPORTED_FEATURES` | unsupported features | strategy declares a feature this build does not implement |
| `MARKET_STATUS_NOT_OK` | suspicious market data | bid/ask below 0.01 or quotes not from today |
| `EXCHANGE_DEAD` | no activity at the exchange | no `LAST` tick on the bellwether symbol for 30 min |
| `COOLDOWN` | entry: cooldown | within 120 s of the last close |
| `ENTRY_HOURS` / `EXIT_HOURS` | wait for trading hours | outside the configured window |
| `ENTRY_HIST_DATA` / `EXIT_HIST_DATA` | wait for hist data | daily bars missing or stale |
| `ENTRY_MIN_PRICE` | wait for min price | a leg quote is below `min_price` |
| `ENTRY_PROFIT_POTENTIAL` | wait for min profit | expectation below `min_profit_potential` |
| `ENTRY_SIGNAL` / `EXIT_SIGNAL` | wait for signal | model has not fired |
| `ENTRY_SHORTABILITY` | wait for shortability | IB reports the short leg as not shortable |
| `ENTRY_MANAGER` | wait for slot | `acquirePositionLock` refused: portfolio slots full |
| `ENTRY_REVERSAL_NOT_ALLOWED` | reversal not allowed | same-day, same-direction re-entry blocked |
| `ENTRY_PDT` / `EXIT_PDT` | PDT protection | pattern-day-trader rule would be violated |
| `EXIT_WAIT_PL_RULE` | wait for min P/L rule | exit signal held back by `min_pl` |

---

## 9. Safety rules

### 9.1 Constants (`ConfinedEngine`)

| Constant | Value | Purpose |
|---|---|---|
| `MIN_DAYTRADING_EQUITY` | 25 000 USD | threshold for `PDT_ENABLE_25K` |
| `RETRY_HISTORICAL_MINUTES` | 11 | minimum gap between historical retries |
| `WAIT_RECOVERABLE` | 120 s | grace period for a held order before cancel/liquidate |
| `AFTER_FILL_POS_SYNC_LOCK` | 300 s | suppress IB→engine position adoption after a fill |
| `COOLDOWN_AFTER_CLOSE` | 120 s | minimum flat time before a new entry |
| `EXCHANGE_OPEN/CLOSE` | 09:30 – 16:00 | regular session, in the strategy's timezone |
| `MAX_HIST_PRICE_AGE` | 5 days | older data ⇒ manual intervention |
| `PRICE_MOVE_RATIO_LIMIT` | 1.99 | live/last-historical price ratio guard (split detection) |
| `SLEEP_AFTER_POST` | 50 ms | pause between the two leg orders |
| `ActivityDetector.MAX_DELAY` | 1800 s | staleness limit for exchange-liveness ticks |
| `SimpleWrapper.IB_CONNECTION_RETRY_RATE` | 45 s | reconnect interval |
| message staleness | 30 s / 2 s ticks / 5 s generic ticks | queue back-pressure protection |

### 9.2 Time windows

Three independent windows, all evaluated in the strategy's timezone and all
excluding Saturday and Sunday:

* **Trading hours** — fixed 09:30–16:00, used as the outer gate for the Open/Close
  buttons and as an override that lets entry/exit logic proceed.
* **Entry window** — `entry_start_*` … `entry_end_*` (default 10:00–15:50).
* **Exit window** — `exit_start_*` … `exit_end_*` (default 10:00–15:58).

### 9.3 What triggers manual intervention

`requestManualIntervention(reason)` blocks the pair, marks it inactive and reports
the reason to PTL. Triggers:

* leg position mismatch against IB that is not explainable as a split or an external
  close;
* failure to open or to close a position (unrecoverable order error);
* historical data that is empty, has misaligned dates between legs, is older than
  5 days, or is too short for the model's lookback;
* a suspicious price move (live last price more than 1.99× or less than 1/1.99× the
  last historical close) — unless the pair was manually resumed earlier the same day.

Recovery is manual: the user presses *Resume*, which posts a `ResumeRequest`. The
engine calls `resetState()` — clearing positions, orders, executions, historical
data and error flags — and posts a `GlobalPortfolioUpdateRequest` so IB re-sends
the account state and the engine re-derives its position from the broker.

### 9.4 PDT protection

With `pdt_rules = PDT_DISABLE`, or `PDT_ENABLE_25K` while account equity is under
25 000 USD, the engine refuses to open **or close** a position on the same calendar
day the position was opened, avoiding a pattern-day-trade. With sufficient equity
the check passes.

---

## 10. Testing

```bash
./gradlew test          # or ./gradlew build
```

103 JUnit 4 test methods, Mockito for the IB socket, event bus, and logger
collaborators. Coverage is deliberately concentrated where the money is:

| Area | Tests |
|---|---|
| `ConfinedEngineTest` | 18 — order flow, fills, commission/transaction assembly, position sync, error handling |
| Model logic | `PairTradingModelRatioTest` (9), `…ResidualTest` (9), `…KalmanAutoTest` (9), `…KalmanGridTest` (10) |
| `kernelfx` | `SubModelKalman*Test`, `OlsCellTest`, `SharpeCellTest`, `SimpleCellTest`, `MemoryCellTest`, `PairPositionTest`, `PerfTracker*Test`, `UsageTrackerTest`, `SimpleStrategyTest` |
| Providers / model / utils | `PairDataProviderTest`, `HistoricalDataProviderTest`, `PortfolioTest`, `MultiRatioTest`, `StringXorProcessorTest` |

Conventions worth following when adding tests:

* Time is controlled with `DateTimeUtils.setCurrentMillisFixed(...)` in `@Before`
  and reset in `@After` — never let a test depend on the wall clock.
* `ConfinedEngine` is exercised directly (its trading methods are `protected`, and
  the test lives in the same package) rather than through `PairTradingCore`, which
  keeps tests single-threaded and deterministic.
* `PairStrategy.injectCore()` exists solely so tests can install a mock core.

Because this software trades other people's money, the project asks for a strict
review process on pull requests (see `README.md`); a change to signal generation,
order handling or position reconciliation is expected to arrive with tests.

---

## 11. Packaging and release

1. Bump the version in **both** `gradle.properties` and
   `com.pairtradinglab.ptltrader.Version`.
2. `./build_all_architectures.sh` → three fat jars in `build/libs/`.
3. **Windows EXE** (optional): run [launch4j](http://launch4j.sourceforge.net/)
   with `launch4j/ptltrader_win64_launch.cfg.xml` against the `win64` jar.
   launch4j itself is cross-platform.
4. **Windows MSI** (optional, requires Windows + [WiX Toolset](https://wixtoolset.org/)):
   copy the EXE into `wix/`, then

   ```bat
   cd wix
   build_installer.bat
   :: equivalently:
   candle installer_win64.wxs
   light -ext WixUIExtension -ext WixUtilExtension installer_win64.wixobj -out ptltrader_win64.msi
   ```

   The MSI requires a **JRE** on the target machine; a JDK will not satisfy its
   detection logic.

---

## 12. Extending the application

### Adding a trading model

1. Subclass `PairTradingModel` (or `AbstractSubModel` in `kernelfx` if the numerics
   are grid-based) and implement the abstract contract from §7.
2. Add the model name constant to `PairStrategy` and a branch in
   `PairTradingCoreFactoryImpl.createForStrategy()`.
3. Add any new parameters to `PairStrategy` (with `@JsonProperty`/`@JsonIgnore` as
   appropriate) and to `updateFromJson()`, keeping them in the
   "only while `core == null`" block.
4. If the model selects among sub-models, implement `LockableStateModel` and a
   matching `AbstractPairTradingModelState` subclass so open positions survive a
   restart.
5. Expose the parameters in the UI via WindowBuilder and bind them in
   `initDataBindings()`.
6. Add unit tests covering `setPrices`, `entryLogic`, `exitLogic` and
   `getLookbackRequired`.

### Replacing the portfolio source

The README explicitly invites forks that load portfolios from, say, CSV instead of
PTL. The seam is narrow:

* `PtlApiClient.loadPortfolios()` → `PortfolioList.updateFromJson(JsonNode)` →
  `PortfolioList.initialize()` — supply an equivalent `JsonNode` (or bypass it and
  build `Portfolio`/`PairStrategy` objects through the factories directly), then
  post `PtlApiConnect` if you still want the AMQP telemetry path;
* the write path (`PortfolioSyncOutRequest`, `StrategySyncOutRequest`,
  `PairStateUpdated`) must be re-pointed or dropped — but note that
  `last_model_state` and `last_opened_datetime` need to be persisted *somewhere*,
  otherwise Kalman strategies cannot correctly resume an open position after a
  restart.

Whatever the source, the GNU GPL v3 terms still apply to the derived work.

### Adding a bus event

Define an immutable event class in `events/` or `trading/events/`, post it with
`bus.post(...)`, and add `@Subscribe` handlers. If it should reach PTL, add a
handler in `AmqpProxy` and mark the class `ImportantEvent` (guaranteed delivery)
and/or `ConfidentialEvent` (suppressed in confidential mode) as appropriate.

---

## 13. Troubleshooting

| Symptom | Where to look |
|---|---|
| "already running for profile" on startup | another instance holds the JUnique lock; use a different profile or kill the other process |
| Build fails resolving SWT natives | wrong/absent `-PforceArch`; run `./gradlew swtDiag` |
| macOS: app exits immediately at launch | missing `-XstartOnFirstThread` |
| `NoSuchMethodError` / reflection errors from HTTP or TLS | missing `--add-opens` flags (§4) |
| IB connection refused with a version message | TWS/Gateway API older than server version 66 |
| Pair stuck at `wait for portfolio` | no `updatePortfolio` callback for those contracts yet — check the account code binding and that TWS has the account subscribed |
| Pair stuck at `no activity at the exchange` | `ActivityDetector` has no recent `LAST` tick for SPY/BAC/QQQ/SILV; usually a market-data subscription or feed problem |
| Pair stuck at `wait for slot` | portfolio slot budget exhausted — check `max_pairs_open` and each strategy's `slot_occupation` |
| Pair stuck at `wait for manual intervention` | read the log for the `pair blocked for auto execution, reason:` line, fix the underlying cause, then Resume |
| Repeated `historical data request failed` | IB pacing violations or missing historical-data permissions; retries are 11 minutes apart by design |
| Trades appear in IB but not on PTL | check the AMQP connection state and the `rq-worker` log lines; confidential mode also suppresses trade telemetry |
| Log file not where expected | it is `${user.home}/Application Data/PTLTrader/<profile>.log` on every platform (§5.3) |
