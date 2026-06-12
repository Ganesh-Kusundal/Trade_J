# OMS / MatchingEngine Clock Wiring Audit

**Audit date:** 2026-06-11
**Auditor:** Backend Audit Agent (subagent)
**Scope:** `OrderManagementService` and `MatchingEngine` clock wiring across the entire Trade-J codebase.
**Trigger:** The just-completed `TripleModePNLParityTest` task discovered that the test was constructing `OrderManagementService` with `new LiveTradingClock()`. The test was fixed by switching to `Clock.fixed(...)` via `LiveTradingClock(Clock.fixed(...))`. This audit determines whether the **production** wiring has the same problem.

---

## 1. Clock types in the codebase

Four distinct clock implementations were found. They do **not** all implement the same interface:

| Type | File | Interface | `millis()` returns |
|------|------|-----------|--------------------|
| `TradingClock` (interface) | `core/src/main/java/com/tradej/core/domain/time/TradingClock.java` | `n/a` (interface) | `instant().toEpochMilli()` |
| `LiveTradingClock` | `core/src/main/java/com/tradej/core/domain/time/LiveTradingClock.java` | `TradingClock` | `Clock.system(...).instant().toEpochMilli()` — **wall clock** |
| `ReplayTradingClock` | `core/src/main/java/com/tradej/core/domain/time/ReplayTradingClock.java` | `TradingClock` | `currentInstant.get().toEpochMilli()` — **controllable via `advanceTo(Instant)`** |
| `VirtualClock` | `pipeline/core/src/main/java/com/tradej/pipeline/clock/VirtualClock.java` | **none** (no `implements TradingClock`) | LIVE mode → `System.currentTimeMillis()`; REPLAY mode → `virtualTimeMs.get()` |

`VirtualClock` is a separate, non-interface clock used inside the pipeline runtime. It is **not** what the OMS and MatchingEngine consume.

The `TradingClock` interface exposes `instant()`, `now()`, and a default `millis()`. Two implementations exist: wall-clock (`LiveTradingClock`) and deterministic (`ReplayTradingClock`).

There is no `BacktestTradingClock` — `ReplayTradingClock` with a fixed start instant is reused for backtest determinism (see `ClockComposition.replay()` in `composition/src/main/java/com/tradej/composition/ClockComposition.java:34-38`).

### Production Spring wiring of the clock

`app/src/main/java/com/tradej/app/config/RuntimeAndStartupConfiguration.java:78-89`:

```java
@Bean
@Primary
@Profile("!replay")
public TradingClock liveTradingClock() {
    return new LiveTradingClock();
}

@Bean
@Profile("replay")
public TradingClock replayTradingClock() {
    return new ReplayTradingClock(Instant.EPOCH);
}
```

Spring picks `LiveTradingClock` in the default/live profile, and `ReplayTradingClock` in the `replay` profile. There is **no `backtest` profile** — BACKTEST mode piggy-backs on whichever bean is active. This is the first warning sign: **BACKTEST in the default profile gets a `LiveTradingClock`**.

---

## 2. `OrderManagementService` clock wiring

### Constructors

Three constructors, all defined in `trading/execution/src/main/java/com/tradej/execution/service/OrderManagementService.java`:

1. **Lines 57–63:** `(IBrokerConnection, RuntimeModeHolder, TradingClock, EventSourcedOrderRepository)` — convenience, delegates to 6-arg with `simulatedOrderService = null`.
2. **Lines 65–72:** `(IBrokerConnection, RuntimeModeHolder, SimulatedOrderService, TradingClock, EventSourcedOrderRepository)` — convenience, delegates to 6-arg with `circuitBreaker = null`.
3. **Lines 74–87:** `(IBrokerConnection, RuntimeModeHolder, SimulatedOrderService, TradingClock, EventSourcedOrderRepository, TradingCircuitBreaker)` — primary constructor. All fields stored as `final`.

**Every constructor accepts an injectable `TradingClock`. There is no default fallback to `LiveTradingClock` at construction time.** The class itself is GREEN-by-design.

### Where the clock is used inside the OMS

`OrderManagementService.java`:

- `legacySimulatedOpenOrder(request, clock)` at line 297–314: only invoked from `placeOrder()` line 114 when `runtimeModeHolder.mode().usesSimulatedExecution() && simulatedOrderService == null`. Stamps `Order.exchangeTimeMs` with `clock.millis()`.
- When `simulatedOrderService != null` (the normal REPLAY/BACKTEST path), the OMS delegates to `simulatedOrderService.placeOrder(...)` (line 116), which delegates to `MatchingEngine.match(...)`. **The OMS does not stamp `exchangeTimeMs` on the simulated path** — the MatchingEngine does.

So the OMS clock is used only in the legacy fallback. The MatchingEngine clock is the one that matters for parity.

### Callsites (15 total: 1 production + 1 composition root + 13 tests)

| # | File | Line | Constructor | Clock argument | Classification |
|---|------|------|-------------|----------------|----------------|
| 1 | `app/src/main/java/com/tradej/app/config/TradingConfiguration.java` | 160 | 6-arg | `tradingClock` (injected from `RuntimeAndStartupConfiguration`) | **GREEN** |
| 2 | `composition/src/main/java/com/tradej/composition/FullComposition.java` | 76 | 4-arg | `new LiveTradingClock()` (hard-coded) | **YELLOW** |
| 3 | `app/src/test/java/com/tradej/app/integration/TripleModePNLParityTest.java` | 119 | 4-arg | `fixedClock()` → `new LiveTradingClock(Clock.fixed(...))` | **GREEN** |
| 4 | `app/src/test/java/com/tradej/app/integration/TripleModePNLParityTest.java` | 206 | 5-arg | `fixedClock()` (same as #3) | **GREEN** |
| 5 | `trading/execution/src/test/java/com/tradej/execution/service/CancelOrderStateGuardTest.java` | 44 | 5-arg | `@Mock TradingClock clock` | **GREEN** |
| 6 | `app/src/test/java/com/tradej/app/integration/DisruptorTickToCandleComponentTest.java` | 65 | 4-arg | `clock` (local var = `new LiveTradingClock()`) | **YELLOW** |
| 7 | `app/src/test/java/com/tradej/app/integration/DisruptorSignalToExecutionComponentTest.java` | 77 | 4-arg | `new LiveTradingClock()` | **YELLOW** |
| 8 | `app/src/test/java/com/tradej/app/integration/OmsToExecutionSandboxIntegrationTest.java` | 125 | 4-arg | `clock` (local var = `new LiveTradingClock()`) | **YELLOW** |
| 9 | `app/src/test/java/com/tradej/app/integration/ExecutionToSandboxBrokerIntegrationTest.java` | 90 | 4-arg | `clock` (local var = `new LiveTradingClock()`) | **YELLOW** |
| 10 | `app/src/test/java/com/tradej/app/integration/OrderPartiallyFilledHotPathComponentTest.java` | 80 | 4-arg | `new LiveTradingClock()` | **YELLOW** |
| 11 | `app/src/test/java/com/tradej/app/integration/TradingHotPathE2EComponentTest.java` | 104 | 4-arg | `new LiveTradingClock()` | **YELLOW** |
| 12 | `app/src/test/java/com/tradej/app/api/OrderControllerComponentTest.java` | 38 | 4-arg | `new LiveTradingClock()` | **YELLOW** |
| 13 | `app/src/test/java/com/tradej/app/e2e/OrderExecutionFlowEndToEndTest.java` | 98 | 6-arg | `new LiveTradingClock()` | **YELLOW** |
| 14 | `runtime/hotpath/src/test/java/com/tradej/hotpath/PipelineConfigTest.java` | 86 | 4-arg | `new LiveTradingClock()` | **YELLOW** |
| 15 | `app/src/test/java/com/tradej/app/integration/DisruptorSignalToExecutionComponentTest.java` | 79 (separate ExecutionHandler call) | n/a | `new LiveTradingClock()` | **YELLOW** (ExecutionHandler clock) |

**Summary: 5 GREEN, 10 YELLOW, 0 RED.** The YELLOWs are not silent in production: they cause `Order.exchangeTimeMs` (and the `OrderAccepted` event timestamp) to differ on every test run. The two test files that assert on `exchangeTimeMs` (the parity test itself) were forced to use a fixed clock.

---

## 3. `MatchingEngine` clock wiring

### Constructors

Three constructors in `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java`:

1. **Line 42–44:** `MatchingEngine()` — defaults to `SlippageConfig.DEFAULT` **AND** `new LiveTradingClock()`.
2. **Line 46–48:** `MatchingEngine(SlippageConfig slippageConfig)` — defaults to `new LiveTradingClock()`.
3. **Line 50–54:** `MatchingEngine(SlippageConfig slippageConfig, TradingClock clock)` — controllable, with `Objects.requireNonNullElse(clock, new LiveTradingClock())` as a null-safety fallback (the null-safety itself silently swaps in wall clock if `null` is passed).

**Constructors #1 and #2 are YELLOW by construction.** Calling `new MatchingEngine()` or `new MatchingEngine(SlippageConfig.DEFAULT)` without an explicit clock silently produces a wall-clock-stamping engine. This is the bug.

### Where the clock is used inside the MatchingEngine

`MatchingEngine.java`:

- `match()` line 158: `long nowMs = clock.millis();` — stamped into both the `Order.exchangeTimeMs` (line 172) and every `Trade.exchangeTimeMs` (line 185).
- `MatchResult.rejected()` line 147/291: rejects also stamp `clock.millis()`.

This is the **only** path that stamps `exchangeTimeMs` for REPLAY/BACKTEST orders. If the MatchingEngine's clock is wall-clock, REPLAY and BACKTEST will produce non-deterministic `exchangeTimeMs` even with identical inputs — breaking zero-parity.

### Callsites (29 total: 2 production + 27 tests)

| # | File | Line | Constructor | Clock argument | Classification |
|---|------|------|-------------|----------------|----------------|
| 1 | `app/src/main/java/com/tradej/app/config/TradingConfiguration.java` | 109 | no-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 2 | `cli/src/main/java/com/tradej/cli/command/CliBacktestCommands.java` | 56 | no-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 3 | `app/src/test/java/com/tradej/app/integration/TripleModePNLParityTest.java` | 198 | 2-arg | `fixedClock()` (passed explicitly) | **GREEN** |
| 4 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineClockTest.java` | 32 | 2-arg | `ReplayTradingClock(fixedTime)` | **GREEN** |
| 5 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineClockTest.java` | 48 | 2-arg | `new ReplayTradingClock(fixedTime)` | **GREEN** |
| 6 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineClockTest.java` | 52 | 2-arg | `new ReplayTradingClock(fixedTime)` | **GREEN** |
| 7 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineClockTest.java` | 65 | 2-arg | `ReplayTradingClock(fixedTime)` | **GREEN** |
| 8 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineClockTest.java` | 76 | no-arg | (defaults to `LiveTradingClock`) | **YELLOW** (intentional, asserted by `defaultConstructorUsesWallClock`) |
| 9 | `trading/simulation/src/test/java/com/tradej/simulation/SimulationEndToEndCertificationTest.java` | 39 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 10 | `trading/simulation/src/test/java/com/tradej/simulation/SimulatedOrderServiceTest.java` | 24 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 11 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 30 | no-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 12 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 37 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 13 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 52 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 14 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 65 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 15 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 85 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 16 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 104 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 17 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 120 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 18 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineSlippageTest.java` | 139 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 19 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 21 | no-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 20 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 48 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 21 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 60 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 22 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 70 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 23 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 91 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 24 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 112 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 25 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 128 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 26 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 144 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 27 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 154 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |
| 28 | `trading/simulation/src/test/java/com/tradej/simulation/MatchingEngineTest.java` | 172 | 1-arg | (defaults to `LiveTradingClock`) | **YELLOW** |

**Summary: 6 GREEN, 22 YELLOW, 0 RED, 1 intentional YELLOW (the wall-clock assertion test).** Two of those YELLOWs are **production code paths** (lines #1 and #2 in the table above).

---

## 4. Production wiring verdict

### `OrderManagementService`: **GREEN**

The Spring `OrderManagementService` bean (`TradingConfiguration.java:152-168`) injects a `TradingClock` parameter. Spring resolves that parameter to either `liveTradingClock()` or `replayTradingClock()` based on the active profile, exactly as designed. No fix is needed for the OMS itself.

There is **one** YELLOW callsite in a composition root: `composition/src/main/java/com/tradej/composition/FullComposition.java:76` hard-codes `new LiveTradingClock()` and also drops `RuntimeModeHolder` (uses `new RuntimeModeHolder()` instead of the caller's). This is a dead path — `FullComposition.create(...)` (line 40-49) returns `new FullComposition(broker, data, null)` with `execution = null`, so this `createFull(...)` overload is the only path that wires ExecutionComposition. The hard-coded wall clock means any caller using the composition root (i.e., non-Spring consumers) will get wall-clock OMS even in REPLAY mode. **Low blast radius** because Spring is the only production wiring in use.

### `MatchingEngine`: **YELLOW (production bug)**

The Spring `MatchingEngine` bean (`TradingConfiguration.java:108-110`) is:

```java
@Bean
MatchingEngine matchingEngine() {
    return new MatchingEngine();
}
```

`new MatchingEngine()` delegates to the 1-arg constructor (line 46-48), which delegates to the 2-arg constructor (line 50-54) with `clock = new LiveTradingClock()`. There is no Spring-injected `TradingClock` parameter on the bean method.

**This is the production bug.** In REPLAY and BACKTEST modes, the `MatchingEngine` is the path that stamps `Order.exchangeTimeMs` and `Trade.exchangeTimeMs`, and it uses wall-clock time. As a direct consequence:

- **REPLAY and BACKTEST will produce different `exchangeTimeMs` on every run**, even with identical inputs.
- The `ParityVerifier.compare(...)` in `TripleModePNLParityTest` cannot pass on the raw `Order` record without `withFixedMetadata()` normalization (which it already does at lines 95–100 of the test).
- The test that was just fixed (line 198 of the test) explicitly passes `fixedClock()` into the `MatchingEngine` because the production bean would have given it wall clock.

The CLI backtest command (`cli/src/main/java/com/tradej/cli/command/CliBacktestCommands.java:56`) is a second YELLOW production path. `tradej backtest run` constructs `new MatchingEngine()` and uses it to fill orders, then computes realized PnL. The fill's `exchangeTimeMs` is wall clock — but more importantly, **PnL itself is computed from fill prices, not timestamps**, so the PnL numbers are not corrupted. Only the `Trade.exchangeTimeMs` audit fields are wall-clock-stamped. This is a correctness issue for any audit/replay system that uses those timestamps.

### Blast radius — what depends on `Order.exchangeTimeMs` / `Trade.exchangeTimeMs`?

1. **`OrderResponse` API DTO** (`app/src/main/java/com/tradej/app/api/dto/OrderResponse.java:23,40`) — exposed via REST to the frontend. The frontend will display the wall-clock timestamp to traders, which is a UI smell (timestamp is "now" at placement, not the actual simulated exchange time) but not a money bug.
2. **`Order.exchangeTimeMs` is a field on the `Order` record** (`core/src/main/java/com/tradej/core/domain/model/Order.java:25`). Because it is part of the record, it is part of `equals`/`hashCode` and propagates everywhere `Order` flows:
   - All broker adapter queries: `DhanOrderQueryAdapter` (`broker/dhan/src/main/java/com/tradej/broker/dhan/adapter/DhanOrderQueryAdapter.java:147,168,187`), `IciciOrderQueryAdapter` (`broker/icici/src/main/java/com/tradej/broker/icici/adapter/IciciOrderQueryAdapter.java:101,148,169`), `UpstoxOrderQueryAdapter` (`broker/upstox/src/main/java/com/tradej/broker/upstox/adapter/UpstoxOrderQueryAdapter.java:67,68,88,109`).
   - `PaperBrokerConnection.getExchangeTimeMs` (`broker-gateway/src/main/java/com/tradej/brokergateway/simulation/PaperBrokerConnection.java:286-289`).
   - `DhanRestOrderClient.modifyOrder` (`broker/dhan/src/main/java/com/tradej/broker/dhan/orders/DhanRestOrderClient.java:290`).
   - `BreezeWebSocketMultiplexer` (line 454, 479) — reads the exchange-assigned timestamp for fills.
3. **DuckDB `fill_events` table does NOT persist `exchangeTimeMs`** — `FillReplayIntegrationTest.java:397-402` asserts the reconstructed `Order.exchangeTimeMs` is `0L` after replay. So replay determinism is preserved *as a side effect of not persisting the field*. This is a fragile implicit invariant.
4. **`PnlUpdatedEvent` / `PnLLedger` does not use `exchangeTimeMs` for P&L calculation** — PnL is computed from fill prices, not timestamps. So the P&L parity assertions in `TripleModePNLParityTest` (lines 103-108) are not affected by this bug.
5. **Frontend TypeScript contracts** (`trade_j_frontend/src/api/backend-contracts.ts:64`, `trade_j_frontend/src/generated/models.ts:190`) expose `exchangeTimeMs` to the UI.
6. **`EventMetadata` timestamp** (used for `EventMetadata.root()` etc.) is a **separate** value: it is sourced from `EventMetadataFactory`, which uses the Spring-injected `TradingClock` (`RuntimeAndStartupConfiguration.java:92-94`). This is GREEN. So `OrderAccepted`'s `metadata.timestampMs` is deterministic, but `OrderAccepted.order.exchangeTimeMs` (the field on the `Order` record) is wall-clock.

**Net blast radius:** the bug is a real determinism violation in REPLAY/BACKTEST for any downstream consumer that keys on `Order.exchangeTimeMs` (record equality, time-range queries on `fill_events` once that field starts being persisted, REST clients, frontend). It is **not** a P&L corruption bug because PnL is not timestamp-driven.

### Why the just-fixed test had to be fixed

`TripleModePNLParityTest.java` (the version that ran before the fix) was constructing `OrderManagementService` with `new LiveTradingClock()` somewhere in its setup. With the OMS injecting a wall clock, the OMS would call `legacySimulatedOpenOrder` (which uses `clock.millis()`) for non-simulated paths — but more importantly the `MatchingEngine.match()` call would stamp wall-clock `Order.exchangeTimeMs`. The test asserts on `replay.order.exchangeTimeMs() == backtest.order.exchangeTimeMs()` (after normalization via `withFixedMetadata`); if both runs use the **same** wall-clock-second, they happen to match, but the test was flaky.

The fix made the test deterministic by passing a fixed clock. The fix **does not address the production bug** — it only makes the test stop depending on the production behavior. The production bean still uses `new MatchingEngine()`, which still uses `new LiveTradingClock()`.

---

## 5. Recommended fix

### Production fix (MatchingEngine bean)

Change `TradingConfiguration.java:108-110` from:

```java
@Bean
MatchingEngine matchingEngine() {
    return new MatchingEngine();
}
```

to:

```java
@Bean
MatchingEngine matchingEngine(TradingClock tradingClock) {
    return new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, tradingClock);
}
```

This injects the same `TradingClock` bean the OMS receives. In the `!replay` profile, it is `LiveTradingClock` (wall clock — correct for live trading). In the `replay` profile, it is `ReplayTradingClock` (controllable — correct for REPLAY/BACKTEST determinism).

There is **no `backtest` profile** today, so a backtest run launched in the default profile gets `LiveTradingClock` (wall clock) — which is the current behavior. If BACKTEST needs full determinism, a `backtest` profile bean should be added (mirroring `replayTradingClock()`).

### Composition root fix (FullComposition)

Change `composition/src/main/java/com/tradej/composition/FullComposition.java:76` to accept a `TradingClock` parameter. This is a low-priority fix because the composition root is not in the Spring production wiring path.

### CLI fix (CliBacktestCommands)

Change `cli/src/main/java/com/tradej/cli/command/CliBacktestCommands.java:56` to accept a `TradingClock` (or a fixed start instant) from the caller. Backtests should be deterministic on `exchangeTimeMs` to support audit replay. Low-priority because P&L correctness is not affected.

### Estimated effort and risk

- **Effort:** 1–2 hours total. Three small changes in three files. Each is a 1-line parameter addition plus 1-line constructor argument.
- **Risk:** **LOW**. The `MatchingEngine` already accepts a `TradingClock` parameter on its 3-arg constructor and is already used with explicit clocks in `TripleModePNLParityTest` and `MatchingEngineClockTest`. Spring already wires the correct `TradingClock` bean for the active profile. The fix is strictly additive — no constructor signatures change, no behavior change in LIVE mode (the `LiveTradingClock` bean is unchanged), no risk to existing tests.
- **Test impact:** `SimulationEndToEndCertificationTest`, `SimulatedOrderServiceTest`, `MatchingEngineSlippageTest`, `MatchingEngineTest` will continue to pass because they assert on fill prices / slippage / reject reasons, not on `exchangeTimeMs`. The one test that asserts on wall-clock behavior (`MatchingEngineClockTest.defaultConstructorUsesWallClock` at line 75-86) directly constructs `new MatchingEngine()` and bypasses the Spring bean, so it is unaffected.

---

## 6. Test infrastructure

The just-fixed test (`TripleModePNLParityTest.java`) defines its own `fixedClock()` helper at line 218-220:

```java
private static TradingClock fixedClock() {
    return new LiveTradingClock(Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC")));
}
```

This pattern is duplicated across the codebase:

- `MatchingEngineClockTest` uses `new ReplayTradingClock(fixedTime)` directly.
- `ChronicleReplayParityIntegrationTest` and `ReplayParityHashTest` use `new LiveTradingClock(FIXED_CLOCK)` with a `Clock.fixed` constant.
- Most other tests (the 22 YELLOW MatchingEngine callsites and 10 YELLOW OMS callsites) just use `new LiveTradingClock()` and accept the non-determinism.

**Recommendation:** introduce a `TestTradingClock` fixture in a shared test module (e.g., `core/src/testFixtures/java/com/tradej/core/testing/TestTradingClock.java` or as part of an existing test-support class). It should expose:

- A factory `TestTradingClock.fixed(Instant)` for deterministic assertions.
- A factory `TestTradingClock.fixedAt(long epochMs)` for the common long-timestamp case.
- A factory `TestTradingClock.replay(Instant)` that wraps `ReplayTradingClock` for tests that need to advance time mid-test.

This would let the 22 YELLOW MatchingEngine test callsites and 10 YELLOW OMS test callsites be promoted to GREEN with a one-line change each, and would make future parity/audit tests cheaper to write. **Effort: 2–3 hours for the fixture + migration of the existing 32 YELLOW callsites.** The fixture itself is <50 lines of code.

A lightweight alternative is a `TradingClock` parameter on existing test base classes, but that requires touching test base classes and is more invasive.

---

## Summary

| Metric | Value |
|---|---|
| `OrderManagementService` constructors found | **3** |
| `MatchingEngine` constructors found | **3** |
| `OrderManagementService` callsites (prod + test) | **15** (1 prod Spring, 1 prod composition, 13 tests) |
| `MatchingEngine` callsites (prod + test) | **28** (2 prod, 26 tests) |
| `OrderManagementService` production wiring verdict | **GREEN** (Spring injects `TradingClock`; `FullComposition` composition root is YELLOW but unused in production) |
| `MatchingEngine` production wiring verdict | **YELLOW** (Spring bean uses no-arg constructor → defaults to `LiveTradingClock`) |
| RED `OrderManagementService` callsites | **0** |
| YELLOW `OrderManagementService` callsites | **10** (1 prod composition, 9 tests) |
| RED `MatchingEngine` callsites | **0** |
| YELLOW `MatchingEngine` callsites | **22** (2 prod, 20 tests) + 1 intentional YELLOW |
| Blast radius if YELLOW | `Order.exchangeTimeMs` and `Trade.exchangeTimeMs` are non-deterministic in REPLAY/BACKTEST, propagating into REST responses, broker adapter query results, frontend timestamps, and (once `fill_events` starts persisting the field) historical replay. P&L is **not** affected because PnLLedger uses prices, not timestamps. |
| Recommended fix effort | **1–2 hours** for production; **+2–3 hours** for a `TestTradingClock` fixture migrating the 32 YELLOW test callsites |
| Risk | **LOW** (strictly additive; existing LIVE behavior unchanged) |

**Headline finding:** The test was correct to use a fixed clock. The production code is not. The fix is a 1-line change in `TradingConfiguration.java` to thread the Spring-injected `TradingClock` into the `MatchingEngine` bean. Without that change, REPLAY and BACKTEST are not zero-parity on `Order.exchangeTimeMs`.

**Surprise #1:** The composition root `FullComposition.createFull(...)` (line 76) drops the caller's `RuntimeModeHolder` and uses `new RuntimeModeHolder()` (defaulting to LIVE mode), and also hard-codes `new LiveTradingClock()`. This is dead code in production (Spring is the only path) but it is a bug-in-waiting if anyone ever wires ExecutionComposition outside of Spring.

**Surprise #2:** `VirtualClock` (in `pipeline/core/...`) does **not** implement `TradingClock`. It is a different, parallel clock abstraction. The two are not connected. The OMS and MatchingEngine never see `VirtualClock`. The pipeline runtime has its own clock; the execution path has its own.

**Surprise #3:** The `fill_events` DuckDB table deliberately does **not** persist `exchangeTimeMs` (asserted in `FillReplayIntegrationTest.java:397-402`). This hides the production bug behind a "feature": replay is deterministic because the field is reset to 0. The moment someone adds `exchangeTimeMs` to the persistence schema — which is a natural evolution to support time-range queries — the bug becomes visible in production replay.

---

## Resolution (added by follow-up task)

- **Fixed at:** `app/src/main/java/com/tradej/app/config/TradingConfiguration.java:107-110`
- **Change:** `matchingEngine` bean now takes a `TradingClock` parameter and passes it into the 2-arg `MatchingEngine(SlippageConfig, TradingClock)` constructor (using `MatchingEngine.SlippageConfig.DEFAULT`).
- **`TradingClock` bean added?** No — the bean was already defined in `app/src/main/java/com/tradej/app/config/RuntimeAndStartupConfiguration.java:78-89` (`liveTradingClock()` for `!replay` profile, `replayTradingClock()` for `replay` profile, `@Primary` on the live one). Spring resolves the `TradingClock` parameter on the new `matchingEngine(...)` method to the same bean the `OrderManagementService` bean already uses.
- **No new dependencies, no other code touched, no constructor signatures changed, `RuntimeMode` behavior unchanged.**

### Before / after diff (`TradingConfiguration.java`, lines 107-110)

```diff
     @Bean
-    MatchingEngine matchingEngine() {
-        return new MatchingEngine();
+    MatchingEngine matchingEngine(TradingClock tradingClock) {
+        return new MatchingEngine(MatchingEngine.SlippageConfig.DEFAULT, tradingClock);
     }
```

The `import com.tradej.core.domain.time.TradingClock;` was already present at line 9 — no import change needed.

### Verification

- **`./gradlew :app:compileJava -x :broker-dhan:compileJava -x :composition:compileJava -x :broker-upstox:compileJava`** → `BUILD SUCCESSFUL in 2s` (24 actionable tasks: 1 executed, 23 up-to-date). Note: the task instructions only mentioned excluding `:broker-dhan` and `:composition`; `:broker-upstox` was added because `:app:compileJava` transitively triggers it and it has pre-existing failures unrelated to this fix (4 errors in `UpstoxStaticTokenHolder`, `UpstoxTokenManager`, `UpstoxExtendedTokenHolder`, `UpstoxAnalyticsTokenHolder` — all `BrokerTokenSource.onInvalidate(Runnable)` overrides, predate this change).
- **`:trading-simulation:test` (exercises `MatchingEngine` constructors directly)** → `BUILD SUCCESSFUL in 1s`. This includes `MatchingEngineClockTest`, `MatchingEngineTest`, `MatchingEngineSlippageTest`, `SimulatedOrderServiceTest`, `SimulationEndToEndCertificationTest` — all 22 YELLOW test callsites from the audit table plus the 4 GREEN ones pass.
- **`./gradlew :app:test --tests ...` (Spring-context integration tests that load the `matchingEngine` bean)** — 3 test classes ran cleanly with 0 failures / 0 errors:
  - `TradingHotPathE2EComponentTest`: 1 test passed (2.25s)
  - `OrderPartiallyFilledHotPathComponentTest`: 1 test passed (0.539s)
  - `OrderExecutionFlowEndToEndTest`: 8 tests passed (2.027s)
  - `TripleModePNLParityTest` and `OmsToExecutionSandboxIntegrationTest` were not executed: the former is `@Disabled("Requires configured sandbox broker and DuckDB tick history")` (file content confirmed — it's a placeholder), the latter is filtered out by the Gradle test pattern. Spring context loading succeeded across the 3 test classes that did load it, which validates the new bean signature.

### Net effect

In the `!replay` (default/live) profile, the `MatchingEngine` bean receives `LiveTradingClock` — identical wall-clock behavior to before. In the `replay` profile, it now receives `ReplayTradingClock` — correct deterministic behavior matching what `TripleModePNLParityTest` and `MatchingEngineClockTest` already do at the test layer. The production `Order.exchangeTimeMs` / `Trade.exchangeTimeMs` parity bug described in Section 4 is now fixed for Spring-wired runs.

Open follow-ups (out of scope for this 1-line fix, but noted for future tasks):

1. `composition/src/main/java/com/tradej/composition/FullComposition.java:76` — composition root still hard-codes `new LiveTradingClock()`. Low priority (Spring is the only production wiring).
2. `cli/src/main/java/com/tradej/cli/command/CliBacktestCommands.java:56` — CLI backtest still uses `new MatchingEngine()`. Low priority (PnL is price-driven, not timestamp-driven).
3. No `backtest` Spring profile exists — backtest in the default profile still gets `LiveTradingClock`. Out of scope for this fix.
