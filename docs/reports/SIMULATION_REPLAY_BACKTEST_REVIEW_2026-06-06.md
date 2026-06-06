# P1 Simulation / Replay / Backtest Readiness Review
**Date:** 2026-06-06
**Scope:** Hidden coupling, untestable complexity, and readiness gaps in `trading/simulation`, `replay/engine`, `data/persistence/replay`, and the three competing backtest implementations.
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.

---

## TL;DR (verdict)

You have **three different "backtest" implementations** with **three different `BacktestResult` record types**, none of which agree on the schema. One of them (`BacktestServiceImpl`) is **literally a stub that returns hardcoded arithmetic-series prices** and `MatchingEngine` instances that are **constructed but never invoked**. The simulation runtime (`SimulatedOrderService` + `MatchingEngine` + `PnLLedger`) is **the only piece that is real**, but it has no virtual clock and no way to deterministically replay a historical range.

The replay layer is **mostly real** but **stateful in ways that will bite under load**: `HistoricalRangeService` is a 1077-line god class with five responsibilities; `IsolatedReplayStateManager.afterReplay` is a no-op the second time; `ReplayClock` does not enforce monotonicity; and the `RuntimeModeHolder` is a single `volatile` field shared across the entire JVM.

The core concern: **you cannot backtest a strategy against historical data with this code base today.** You'd find that out the first time a researcher tries to reproduce a live result in REPLAY mode and gets a different number for a reason that's not in the data.

Fix the P0s below. They are concentrated in `trading/simulation/BacktestServiceImpl` and the missing unification of the three backtest surfaces.

---

## P0 — Fix or delete this sprint

### P0-1 — `BacktestServiceImpl` is theatre. It will mislead every researcher who uses it.

**Where:** `trading/simulation/src/main/java/com/tradej/simulation/service/BacktestServiceImpl.java:18-117`

Read this:

```java
private BacktestResult runSmaCrossover(BacktestConfig config) {
    int fastPeriod = 5;
    int slowPeriod = 20;
    MatchingEngine engine = new MatchingEngine(...);   // <-- constructed, never used

    ...
    long capital = 10_000_000L; // 1L default capital (10L in paisa)
    boolean inPosition = false;
    long entryPrice = 0L;
    long positionSize = 0L;
    int trades = 0;

    // Run crossover on close prices
    for (int i = slowPeriod; i < 100; i++) {            // <-- hardcoded 100-iteration loop
        double fastSma = 0;
        double slowSma = 0;
        for (int j = 0; j < fastPeriod; j++) {
            fastSma += 100_000 + (j * 100);              // <-- arithmetic-series "prices"
        }
        fastSma /= fastPeriod;
        for (int j = 0; j < slowPeriod; j++) {
            slowSma += 100_000 + (j * 50);               // <-- different arithmetic series
        }
        slowSma /= slowPeriod;
        ...
    }

    long totalPnl = capital - 10_000_000L;
    return new BacktestResult(
            "BT-" + UUID.randomUUID().toString().substring(0, 8),
            "sma-crossover", config.symbol(),             // <-- config.symbol() never used in loop
            trades, trades > 0 ? trades / 2 : 0, trades > 0 ? trades / 2 : 0,   // <-- wins == losses always
            (double) totalPnl, 0.0, 0.0,
            List.<Order>of(), "COMPLETED");
}
```

The defects are not subtle. This function:

1. **Does not read the config.** `config.fromMs` and `config.toMs` are ignored. The loop runs 80 times regardless of the requested range.
2. **Uses hardcoded prices.** `100_000 + (j * 100)` and `100_000 + (j * 50)` are arithmetic series. The fast SMA is always larger than the slow SMA, so a buy signal is emitted on iteration 20 and a sell signal on iteration 21. The strategy is **trivially profitable in simulation** with no data.
3. **Constructs `MatchingEngine` and never calls it.** Line 57-61 builds the engine with config-derived slippage parameters and then forgets about it. The `match()` method is never called. There is no slippage, no partial fill, no matching. The PnL is computed as `(exitPrice - entryPrice) * positionSize` against the same fake prices.
4. **Forces `wins == losses`.** Line 104: `trades > 0 ? trades / 2 : 0, trades > 0 ? trades / 2 : 0` — wins and losses are always equal integers. This makes every backtest's win rate exactly 50%, no matter what the strategy or data.
5. **Ignores the slippage config entirely** for PnL. The slippage values are passed into the engine constructor, but the engine is never used. `slippageConfig.spreadBps`, `volatilitySlippageBps`, `partialFillEnabled`, `partialFillRatio`, `minFillSize`, `maxSlippageBps` — all 6 parameters — are dead.
6. **`runBuyAndHold` is also a stub** (lines 109-116): returns `1, 1, 0, 0.0, 0.0, 0.0` — one trade, one win, zero PnL. For any symbol, any range, any capital.
7. **`status(runId)` is a lie** (lines 45-47): returns `100.0` percent complete, "COMPLETED" for any `runId`, including ones that don't exist.
8. **`listResults(int limit)` returns empty list** (lines 50-52). No persistence.

If a researcher runs `BacktestService.run("sma-crossover", "RELIANCE", from, to)`, they will get a `BacktestResult` that **looks correct** (runId, strategy, symbol, trades, PnL) and is **mathematically deterministic but unrelated to either the strategy logic or the symbol**. This is the worst kind of bug: it produces a number, the number is reproducible, the number is wrong, and the user has no way to know.

**Required fix — pick one of two paths:**

**Path A: Delete `BacktestServiceImpl` and have `BacktestService` default to delegating to `BacktestExecutionService`** (in `replay/engine/`) which uses the real pipeline. This is the right answer because `BacktestExecutionService` already exists and already does the right thing — it runs `PipelineRuntime.backtestSequence` on a DAG with `DefaultBacktestFillModel`.

**Path B: Reimplement `BacktestServiceImpl` to call `HistoricalRangeService.queryCandles` + `MatchingEngine.match` + `PnLLedger`.** This is the same logic as the CLI's `CliBacktestCommands.runSmaCrossover` (lines 109-198), which is **also incomplete** (it estimates wins as `tradeLog.size() / 4` — a fraction of trade count, not actual win tracking), but at least uses real candles.

Path A is faster. Path B is more honest about what "backtest" means in this codebase. Either is fine. **Doing nothing is the worst option** — researchers are using this and the data they produce is junk.

### P0-2 — Three different `BacktestResult` records that don't share a schema

**Where:**
- `core/src/main/java/com/tradej/core/service/BacktestService.java:36-48` — the **interface** type, 10 fields, no capital, no trade log
- `trading/simulation/.../BacktestServiceImpl.java:101-106` — implements the interface, fields map to the interface
- `cli/src/main/java/com/tradej/cli/command/CliBacktestCommands.java:257-271` — **separate** record, 13 fields, includes `initialCapitalPaisa`, `finalCapitalPaisa`, `tradeLog`, and **`sharpeRatio` (but the implementation always returns 0.0)**

This means:

- A researcher calling `BacktestService.run(...)` in code gets a `BacktestResult` with 10 fields, no trade log, no capital.
- A researcher using `tradej backtest run --strategy sma-crossover --symbol RELIANCE --from 2025-01-01 --to 2025-01-31` gets a CLI `BacktestResult` with 13 fields, including a trade log and a capital curve.
- A researcher calling the API gets **neither** of these — they get an `HttpResponse<BacktestDto>` (probably), which is whatever the controller maps it to. We don't know because I didn't open the API controller.

The schemas are **close enough to be confusing and different enough to be wrong**:

| Field | Core `BacktestResult` | CLI `BacktestResult` |
|---|---|---|
| `runId` | `String` | `String` |
| `strategyName` vs `strategy` | `strategyName` | `strategy` |
| `symbol` | `String` | `String` |
| Trade count | `long totalTrades` | `int trades` |
| Wins | `long winningTrades` | `int wins` |
| Losses | `long losingTrades` | `int losses` |
| PnL | `double totalPnlPaisa` | `long totalPnlPaisa` |
| Max drawdown | `double maxDrawdownPaisa` | `long maxDrawdownPaisa` |
| Sharpe | `double sharpeRatio` | `double sharpeRatio` |
| Orders | `List<Order> orders` | (absent — replaced by `tradeLog: List<String>`) |
| Status | `String status` | `String status` |
| Initial capital | (absent) | `long initialCapitalPaisa` |
| Final capital | (absent) | `long finalCapitalPaisa` |
| Trade log | (absent) | `List<String> tradeLog` |

Type mismatches: `long` vs `int` for trade counts. `double` vs `long` for PnL. This will cause a Jackson serialization round-trip to lose precision or truncate.

**Required fix:** declare one canonical `BacktestResult` in `core/domain/model/BacktestResult.java` (move it out of the `BacktestService` interface — it's not a service concept, it's a domain concept). Use the same record type for CLI, REST, and core. The CLI and the simulation both implement against this type. If you want to keep trade log + capital in the CLI display only, build a `BacktestDisplay` projection in the CLI module; do not invent a parallel record.

### P0-3 — `HistoricalRangeService` is a 1077-line god class

**Where:** `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java`

Five distinct responsibilities in one file:

1. **Candle queries** (`queryCandles`, lines 69-113)
2. **Tick queries** (`queryTicks`, lines 126-170)
3. **Order queries** (`queryOrders`, lines 186-229)
4. **Fill queries** (`queryFills`, lines 245-287)
5. **Fill-event queries** (`queryFillEvents`, lines 304-349)
6. **Trade-lifecycle queries** (`queryTradeLifecycle`, lines 365-412)
7. **Trade-lifecycle replay** (`replayTradeLifecycle`, lines 441-499)
8. **Market-tick replay** (`replayMarketTicks`, lines 515-577)
9. **Tick replay** (`replayTicks`, lines 601-618)
10. **Candle replay** (`replayCandles`, lines 631-652)
11. **Order replay** (`replayOrders`, lines 684-758)
12. **Fill-event replay** (`replayFillEvents`, lines 760-864)
13. **Range statistics** (`rangeStats`, lines 871-982)
14. **Connection management** (`close`)

It is `final` (not extensible), holds a single `Connection` (no pooling), uses raw `PreparedStatement` (no helper class), and has **four duplicate `coalesce(event_time_ms, ingested_at_ms) IS NULL OR coalesce(...)` filter clauses** that copy-paste across the queries. A future schema change means editing 6 places.

**Concrete defects:**

1. **Reconstructs lost information.** `replayOrders` (lines 716-735) builds `new Order(... ExchangeSegment.UNKNOWN, Side.UNKNOWN, ProductType.INTRADAY, OrderType.MARKET ...)` because the `orders` table doesn't store these. So replayed orders have **wrong** exchange segment, wrong side, wrong product type. If anyone downstream uses these fields (e.g. a margin calculator that filters by segment), the numbers will be wrong and the test will pass.

2. **`replayFillEvents` makes up data on missing fields.** Lines 818-820:
   ```java
   if ("PARTIALLY_FILLED".equals(eventType) && filledQty == 0L && useFilledQty >= useTotalQty) {
       useFilledQty = useTotalQty > qty ? useTotalQty - qty : qty / 2;
   }
   ```
   For legacy data, `useFilledQty = qty / 2` — a guess, not a reconstruction. A pre-migration order with `qty=100` becomes "partially filled 50 of 100" — an outright lie.

3. **All replay methods are capped at 10,000 rows.** `replayOrders` line 691, `replayFillEvents` line 772. The `replayOrders` method logs a warning at line 747-750 ("data may be truncated") and returns success. **There is no way for the caller to know the result is incomplete.** The `ReplayResult` record (line 1057 area) doesn't expose whether truncation occurred.

4. **Single `Connection`, no read-only mode.** A long replay holds the connection open; concurrent queries (e.g. a user opens a UI that queries candles while a backtest is replaying) will serialize. DuckDB supports concurrent reads on the same DB file with a separate process, but only one process at a time can hold a write lock. Set the connection to read-only (`connection.setReadOnly(true)`) for query methods.

5. **String-built SQL with optional symbol filter.** Lines 191-194, 250-253, 310-313, 374-376, 689-692, 770-773. The pattern is duplicated six times. Use a small `QueryBuilder` or just hardcode the two SQL variants per method.

6. **The class is not interface-segregated.** Every test that needs to mock one method has to mock the whole class. `FillReplayIntegrationTest` (in `app/`) uses the real class with a real DuckDB. There's no `HistoricalDataQuery` interface to mock.

**Required fix — split into:**

```java
public interface HistoricalDataQuery extends AutoCloseable {
    List<Candle> queryCandles(String symbol, String interval, long fromMs, long toMs, int limit);
    List<MarketTickEvent> queryTicks(String symbol, long fromMs, long toMs, int limit);
    List<HistoricalOrder> queryOrders(String symbol, long fromMs, long toMs, int limit);
    List<HistoricalFill> queryFills(...);
    RangeStats rangeStats(...);
}

public interface HistoricalReplayService {
    ReplayResult replayTradeLifecycle(...);
    ReplayResult replayMarketTicks(...);
    ReplayResult replayCandles(...);
    ReplayResult replayOrders(...);
    ReplayResult replayFillEvents(...);
}
```

The query interface is read-only and stateless from the caller's perspective. The replay interface depends on the query interface. Mocking, contract testing, and parallel development all become easier.

### P0-4 — `IsolatedReplayStateManager.afterReplay` is a no-op the second time it's called

**Where:** `replay/engine/src/main/java/com/tradej/replay/engine/IsolatedReplayStateManager.java:62-89`

```java
public void afterReplay() {
    log.info("Restoring pipeline state from pre-replay snapshot...");
    if (portfolioSnapshot != null) {
        portfolioEngine.restore(portfolioSnapshot);
        portfolioSnapshot = null;                  // <-- set to null
    }
    if (netPositionSnapshot != null) {
        netPositionProvider.restore(netPositionSnapshot);
        netPositionSnapshot = null;
    }
    if (riskSnapshot != null) {
        positionRiskHandler.restore(riskSnapshot);
        riskSnapshot = null;
    }
    ... (same pattern for all 6 components)
}
```

The `null` checks make this **idempotent in the wrong way**: the second call is a silent no-op. If a caller does:

```java
replayStateManager.beforeReplay();
replay();
// exception thrown
// caller decides to "clean up" by calling afterReplay() again
replayStateManager.afterReplay();   // first time, restores
replayStateManager.afterReplay();   // second time, no-op (good)
// BUT caller then calls beforeReplay() and then fails to call afterReplay()
replayStateManager.beforeReplay();
replayStateManager.afterReplay();   // restores from the SECOND snapshot
```

That is fine. But this is **not fine**:

```java
replayStateManager.beforeReplay();
replay();
// caller forgot to call afterReplay() (JVM crash, test interrupted, code bug)
replayStateManager.beforeReplay();   // overwrites the original snapshots!
replay();
replayStateManager.afterReplay();   // restores from the SECOND snapshot; first is lost
```

The first `beforeReplay()` snapshots are overwritten without being restored. The state is now the state from the second `beforeReplay`, not the pre-first-replay state. **All replay isolation invariants are violated**, and there is no exception, no log, no warning.

`ReplayOrchestrator` (`replay/engine/src/main/java/com/tradej/replay/engine/ReplayOrchestrator.java:75-84`) does the right thing in a try/finally, but **every other caller** (which I count as the admin replay controller, the CLI replay command, the test base classes) has to remember the protocol.

**Required fix:** rename `afterReplay` to `tryRestore` and throw `IllegalStateException` if called without a corresponding `beforeReplay`. Or — simpler — make `afterReplay` reset the snapshots only after a successful restore **and** keep the previous snapshots in a stack. Either way, the current "silent no-op" behavior is wrong.

### P0-5 — `ReplayClock.advanceTo` does not enforce monotonicity, can rewind time

**Where:** `data/persistence/src/main/java/com/tradej/persistence/replay/ReplayClock.java:29-38`

```java
public void advanceTo(long timestampMs) {
    long prev = currentTimeMs.getAndSet(timestampMs);
    if (timestampMs != prev) {
        eventBus.publish(new ReplayTimeChangedEvent(
                EventMetadata.root(),
                timestampMs,
                replaySpeedNanos.get()
        ));
    }
}
```

`getAndSet` blindly overwrites. If you call `advanceTo(1000)` then `advanceTo(500)`, the second call succeeds. The replay clock **moves backward**. Downstream consumers (gateway clients, candle aggregators with stateful open-candle windows, PnL ledger) get out-of-order `ReplayTimeChangedEvent` and re-process the previous window.

This is not theoretical. The data pipeline (HotPath → candle aggregator) processes events with `eventTimeMs` for windowing. If the replay clock rewinds and a new tick arrives, the aggregator may **close an already-closed candle** and emit a duplicate `CandleClosed` event.

The `VirtualClock` (referenced in `ReplayOrchestrator.java:110`) has its own `advanceVirtualTimeMs` — I haven't read it. It should be the source of truth for monotonicity, and `ReplayClock` should defer to it or be deleted.

**Required fix:** add `if (timestampMs < currentTimeMs.get()) throw new IllegalArgumentException(...)` in `advanceTo`. Or better: replace `ReplayClock` with a thin delegate to `VirtualClock`.

---

## P1 — Fix in the next two sprints

### P1-1 — `RuntimeModeHolder` is a `volatile` reference with no coordination

**Where:** `core/src/main/java/com/tradej/core/domain/runtime/RuntimeModeHolder.java:6-21`

```java
public final class RuntimeModeHolder {
    private volatile RuntimeMode mode = RuntimeMode.LIVE;
    public RuntimeMode mode() { return mode; }
    public void setMode(RuntimeMode newMode) { mode = newMode; }
    public boolean allowsBrokerOrders() { return mode.allowsBrokerOrders(); }
}
```

`setMode` is not synchronized. With three modes (LIVE, REPLAY, BACKTEST) and multiple Spring beans holding a reference, it's possible to:

1. Read LIVE.
2. Call setMode(REPLAY).
3. Read REPLAY (in a different thread).

The first read is stale. There is no point-in-time consistency.

In practice, mode transitions happen at process startup (see `app/src/main/java/com/tradej/app/config/RuntimeConfiguration.java`), so the race is unlikely to bite. But once a live operator flips REPLAY during a debugging session (CLI `tradej mode replay`), the race window is real.

**Required fix:** add a `lock` for write-then-read atomicity, or use `AtomicReference<RuntimeMode>` with a `compareAndSet` API that callers must use. The current API is too easy to misuse.

### P1-2 — `MatchingEngine` and `PnLLedger` use `System.currentTimeMillis()` directly — no virtual clock

**Where:**
- `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java:151, 163` — `System.currentTimeMillis()` in `match()`
- `trading/simulation/src/main/java/com/tradej/simulation/PnLLedger.java:59` — `snapshot(EventMetadata metadata)` takes a clock as parameter (good!) but the fill price is in the `Trade` object — the engine stamps it with wall clock

The simulation layer **partially** supports a virtual clock (PnLLedger takes a metadata). The matching engine does not. The result is that in REPLAY mode, the `Order` and `Trade` timestamps come from wall clock, the `ReplayTimeChangedEvent` is published by the `ReplayClock`, and the two clocks are not coordinated.

If you replay 1 year of historical data in 1 minute, the `Order.tradeTimeMs` is "now", not "the historical moment the trade should have happened". The PnL ledger will show trades that happened "in the last minute" rather than "on 2024-08-15".

**Required fix:** introduce a `Clock` interface, inject it into `MatchingEngine`, `PnLLedger`, and the `SimulatedOrderService`. The `ReplayClock` (or `VirtualClock`) is the implementation in REPLAY mode. The default is `System::currentTimeMillis`. 4-6 hours of work, fixes a real consistency bug.

### P1-3 — `BacktestService` is the **wrong abstraction layer**

The `BacktestService` interface in `core/.../service/BacktestService.java` is "framework-independent — no Spring." But:

- It is implemented only by `BacktestServiceImpl` in `trading/simulation` (P0-1)
- It is bypassed by `BacktestExecutionService` in `replay/engine/`
- It is bypassed by `CliBacktestCommands` in `cli/`
- It is bypassed by the `app/` API (probably — I didn't open the API controller but the API has a `BacktestController` because the `AdminTestBase` references one)

The interface promises "unified backtest facade" (line 9 Javadoc). It does not unify anything. Three other implementations exist that don't go through it.

**Required fix:** delete `BacktestService` and `BacktestServiceImpl`. The unified backtest facade is `BacktestExecutionService` (in `replay/engine/`). The CLI calls it via Spring or a thin adapter. The REST API calls it via the same adapter. The implementation in `trading/simulation/` is dead code that pretends to do something it does not.

### P1-4 — `BacktestExecutionService` is the real backtest, but has no progress reporting

**Where:** `replay/engine/src/main/java/com/tradej/replay/engine/BacktestExecutionService.java:28-44`

```java
public Map<String, Object> runBacktest(String graphId, List<DomainEvent> events) {
    PipelineRuntime runtime = dagPipelineRuntimeService.pipelineRuntime(graphId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive graph: " + graphId));
    int count = events == null ? 0 : events.size();
    
    replayStateManager.beforeReplay();
    try {
        runtime.backtestSequence(events == null ? List.of() : events, new DefaultBacktestFillModel());
    } finally {
        replayStateManager.afterReplay();
    }
    
    return Map.of(
            "graphId", graphId,
            "eventsProcessed", count,
            "mode", runtime.currentMode().name());
}
```

This is **synchronous and blocking**. A backtest over 1M events will run for 30+ seconds, blocking the caller (REST thread, CLI thread). The return type is `Map<String, Object>` — the user has no progress, no cancel, no result persistence.

**Required fix:** make `runBacktest` return a `BacktestRunHandle` (a `CompletableFuture<BacktestResult>` or a `BacktestRunId` the caller polls via `status(runId)`). Store intermediate progress in a `BacktestRunStore`. This is what the `BacktestService.status()` API pretends to do but doesn't.

### P1-5 — The three backtest implementations don't share a candle-fetching path

- `BacktestServiceImpl` (P0-1) does not fetch candles — uses hardcoded prices.
- `BacktestExecutionService` (P1-4) takes pre-fetched events — does not fetch.
- `CliBacktestCommands.runSmaCrossover` (line 62) fetches via `marketData().getCandles(...)` — broker dependency.

The CLI backtest depends on the broker being reachable. A backtest running offline (no broker connection) cannot use the CLI. There's no offline path.

**Required fix:** make `CliBacktestCommands` use `HistoricalRangeService.queryCandles(...)` (from `data/persistence/replay`) as the primary path, falling back to the broker. This makes offline backtest a first-class use case.

### P1-6 — The `MatchingEngine.onTick` variance estimate is a non-standard EWMA

**Where:** `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java:105-115`

```java
private void updateVarianceEstimate(String symbol, long ltpPaisa) {
    AtomicReference<Long> varRef = lastPriceVarianceBySymbol.computeIfAbsent(
            symbol, k -> new AtomicReference<>(0L));
    long prevLtp = lastPricePaisaBySymbol.get(symbol);
    if (prevLtp > 0 && ltpPaisa > 0) {
        long priceDelta = Math.abs(ltpPaisa - prevLtp);
        long prevVar = varRef.get();
        long newVar = (prevVar * 7 + priceDelta) / 8;       // <-- 7/8 decay
        varRef.set(newVar);
    }
}
```

This is labeled "exponential weighted variance" but it's actually an EWMA of **absolute price changes**, not a variance. The variable is named `lastPriceVarianceBySymbol` and used as if it were variance (line 218: `long volSlippageBps = (variancePaisa * 10000) / Math.max(1, basePricePaisa);`). But it's not variance — it's an EWMA of `|Δprice|`, which has different units (paisa, not paisa²).

The slippage formula `variancePaisa * 10000 / basePricePaisa` produces something in bps-like units, but it's the EWMA of the absolute change divided by price — **not a volatility** in the standard sense. Two strategies on the same data will get different slippage depending on the tick rate, not the volatility.

This is a backtest validity issue. If a researcher assumes their backtest slippage is "10 bps because that's what the data says", they are wrong — it's "10 bps × an undocumented function of tick frequency".

**Required fix:** either (a) implement a real variance estimator (Welford's online algorithm, or stddev of log returns) and update the slippage formula to be `vol * sqrt(T) * z`, or (b) rename `lastPriceVarianceBySymbol` to `lastPriceAbsChangeEwma` so the unit mismatch is visible. Option (a) is right; option (b) is honest.

### P1-7 — `HistoricalRangeService` uses `System.currentTimeMillis()` for `eventId` in reconstructions

`replayFillEvents` and `replayOrders` use `EventMetadata.correlated(correlationId, 0L)` — the sequence is 0. If the same correlation id is replayed twice (e.g. operator clicks "replay" in the admin UI), the **same `eventId`** is published twice, and the dedup in the event bus will drop the second one (P0-2 from the architecture review). The replay is silently incomplete.

**Required fix:** use a `UUID.randomUUID()` for the replayed `eventId` and pass the original as a separate `sourceEventId` field on `EventMetadata`. This requires an `EventMetadata` change, which is invasive but correct.

---

## P2 — Fix in the next quarter

### P2-1 — `matchingEngine` field in `BacktestServiceImpl.runSmaCrossover` is dead

`trading/simulation/src/main/java/com/tradej/simulation/service/BacktestServiceImpl.java:57-61` constructs `new MatchingEngine(...)` and never uses it. The variable `engine` is not referenced again in the function. This is **literal dead code** — left over from a refactor that wasn't finished. Delete the line, delete the import.

### P2-2 — `runBuyAndHold` and `runSmaCrossover` return identical schemas but the SMA run reports wrong trade counts

`trading/simulation/src/main/java/com/tradej/simulation/service/BacktestServiceImpl.java:191-193` (CLI version) computes:
```java
tradeLog.size() / 2, // trades = buy+sell per cycle
tradeLog.size() / 4, // rough win estimate
tradeLog.size() / 4, // rough loss estimate
```

This is wrong. If the strategy makes 3 buys and 3 sells (3 round-trip trades, all winners), `tradeLog.size() == 6`, and the function reports "3 trades, 1 win, 1 loss" — neither correct.

`wins + losses != trades` always. `trades` is `tradeLog.size() / 2` (each round trip is buy + sell, so 2 log entries per trade), but `wins + losses` is `tradeLog.size() / 2` (rounded down from `/4 + /4`). The math doesn't work.

**Required fix:** track actual win/loss in the loop. Use a `int wins, losses; if (pnl > 0) wins++; else losses++;` and report those.

### P2-3 — `simulatedOrderService` doesn't update PnL on partial fills correctly

`trading/simulation/src/main/java/com/tradej/simulation/SimulatedOrderService.java:46-50`:
```java
if (!result.rejected()) {
    result.fills().forEach(fill ->
            pnlLedger.applyFill(fill, fill.pricePaisa()));
}
```

The `markLtp` parameter to `applyFill` is the fill price, not the current LTP. `PnLLedger.applyFill` (line 39) uses `markLtp` only for mark-to-market: `long markPrice = markLtp > 0L ? markLtp : fill.pricePaisa();` — since `markLtp == fill.pricePaisa()`, the mark is at the fill price, so unrealized PnL is always 0 immediately after a fill. This is correct for **opening** trades, but for **closing** trades, the unrealized PnL is also 0 (because the position is flat).

The right pattern is: after a fill, mark the position to market at the **current LTP** (which is the last price the engine saw), not the fill price. The simulation does not have access to the LTP here. Either:
- Pass the LTP into `placeOrder` (caller's responsibility), or
- Add a `PnLLedger.markAll(long ltp)` call after every fill, with the engine's last LTP for the symbol.

### P2-4 — `BacktestStatus` uses `double progressPct` instead of `int percentComplete`

`core/.../service/BacktestService.java:50-56`:
```java
record BacktestStatus(
    String runId,
    String state,
    double progressPct,
    String startedAt,
    String completedAt
) {}
```

`double progressPct` allows `0.5`, `50.0`, `NaN`, `Infinity`. For a percent field, an `int` (0-100) is correct. Same for the `BacktestExecutionService` (which doesn't return a status at all — P1-4).

### P2-5 — `SimulatedOrderService.placeOrder` doesn't use the `MatchResult` rejection path for OMS

`SimulatedOrderService.placeOrder` returns the `MatchResult` to the caller. The caller (the OMS / `ExecutionHandler`) is expected to inspect `result.rejected()` and `result.reason()`. The contract isn't documented; if the caller forgets, the order is treated as filled when it was rejected.

**Required fix:** return a domain `OrderAck` with explicit `ACCEPTED`/`REJECTED` state, not the engine's `MatchResult`. The `MatchResult` is an implementation detail of the matching engine; the OMS shouldn't see it.

### P2-6 — `BacktestService.run(strategyName, symbol, from, to)` is ambiguous on timezone

`BacktestServiceImpl.java:20, 27-28`:
```java
private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
...
.from.atStartOfDay(IST).toInstant().toEpochMilli(),
to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli());
```

The `from`/`to` `LocalDate`s are interpreted as IST midnight. For a US researcher, "January 1" is January 1 in their local time, not IST. The 1-day offset (`plusDays(1)`) means the range is "IST Jan 1 00:00 to IST Feb 1 00:00" — but the user said "January 1 to January 31", which should be "Jan 1 00:00 to Feb 1 00:00" anyway. The behavior is correct **only** for IST users.

**Required fix:** accept an explicit `ZoneId` parameter on `BacktestConfig` and default to IST with a logged warning. Document the default in the Javadoc.

### P2-7 — `matchingEngine` thread safety is unspecified

`MatchingEngine.lastPricePaisaBySymbol` is a `ConcurrentHashMap`, but `lastPriceVarianceBySymbol` is updated via `AtomicReference.get` / `set` which is **not** a CAS. Two threads updating the same symbol can lose updates:

```java
long prevVar = varRef.get();      // read
long newVar = (prevVar * 7 + priceDelta) / 8;   // compute
varRef.set(newVar);                // write
```

The classical "read-modify-write" race. In a single-threaded backtest, this is fine. In REPLAY mode with a multi-threaded event bus, **it is wrong**.

**Required fix:** use `varRef.updateAndGet(prev -> (prev * 7 + priceDelta) / 8)`.

---

## Recommendations (concrete, ordered)

| # | Action | Module | Effort | Impact |
|---|---|---|---|---|
| 1 | Delete `BacktestServiceImpl` and route through `BacktestExecutionService` (P0-1, P1-3) | `trading/simulation`, `replay/engine` | 2d | High — researchers get real backtests |
| 2 | Unify the three `BacktestResult` types (P0-2) | `core/domain/model` | 1d | High — schema consistency |
| 3 | Split `HistoricalRangeService` into query + replay interfaces (P0-3) | `data/persistence` | 3d | High — testability, parallel development |
| 4 | Make `IsolatedReplayStateManager.afterReplay` strict (P0-4) | `replay/engine` | 0.5d | Medium — bug prevention |
| 5 | Enforce monotonicity in `ReplayClock.advanceTo` (P0-5) | `data/persistence` | 0.25d | Medium — bug prevention |
| 6 | Inject `Clock` into simulation layer (P1-2) | `trading/simulation` | 1d | Medium — replay consistency |
| 7 | Add progress reporting to `BacktestExecutionService` (P1-4) | `replay/engine` | 2d | Medium — usability |
| 8 | Fix `BacktestServiceImpl` win/loss math (P2-2) | `trading/simulation` | 0.5d | Low — delete-only path |
| 9 | Use `updateAndGet` in `MatchingEngine` variance (P2-7) | `trading/simulation` | 0.25d | Low — concurrent safety |

Total: **~10 person-days** to go from "we have three backtests, two of which lie" to "we have one backtest that is real, isolated, and progress-reporting."

---

## Closing thought

The simulation engine (`MatchingEngine` + `PnLLedger` + `SimulatedOrderService`) is **real and well-designed**: it has slippage, partial fills, tick-rounding for Indian markets, and a proper PnL state machine. The replay layer (`ReplayOrchestrator` + `IsolatedReplayStateManager` + `HistoricalRangeService`) is **mostly real and mostly correct**, with the caveats in P0-3 through P0-5. The problem is concentrated in the **`BacktestService` interface and its `BacktestServiceImpl`**, which is the entry point most researchers will reach for. It's a stub. It returns deterministic nonsense. It needs to be deleted or fixed this sprint.

The simulation and replay layers will do real work. The backtest layer is the facade that needs to be honest.

See also:
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — P0-2 dedup, P0-3 cancel-before-guard
- `docs/reports/TEST_COVERAGE_CHAOS_REVIEW_2026-06-06.md` — `FillReplayIntegrationTest` is the only fill-replay test; add second-run state-reset test
- `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` — `BacktestExecutionService` is a candidate for orTimeout-based progress polling
