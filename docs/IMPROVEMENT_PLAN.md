# Trade-J — Principal-Engineer Improvement Plan

> **Date:** 2026-06-01
> **Scope:** Full source tree (868 Java files, 27 Gradle modules)
> **Inputs:**
> - `docs/BACKLOG.md` (existing)
> - `docs/archive/CODE_LEVEL_REVIEW.md` (2026-05-28, prior pass)
> - Fresh in-depth review of every module flagged in the current tree
> **Out of scope:** the `frontend/` React console (out-of-tree review)
> **Companion documents:**
> - [BACKLOG.md](BACKLOG.md) — canonical issue registry (now extended with `NR-*` and `PR-*` IDs)
> - [ARCHITECTURE_REPORT.md](ARCHITECTURE_REPORT.md) — flows, components, test pyramid
> - [REGRESSION_MANIFEST.md](../REGRESSION_MANIFEST.md) — invariant map

---

## 0. TL;DR

The platform is production-shaped: acyclic module graph, strong domain modelling, mature broker abstraction, best-in-class docs. The blockers for live capital are **six P0 issues concentrated in the risk/execution path and the replay-mode isolation layer**, plus one P0 in the Upstox analytics profile. Everything else is operational debt that should be batched in the next three sprints.

**The single most important test in the repo is missing:** an integration test that publishes a `SignalPendingExecution` through the *full* `DisruptorEventBus` and asserts the OMS is populated. Its absence is what let P0 #1 ship.

---

## 1. New P0 — must be fixed before any live capital

| ID | Module | Issue | File:line | Test to add |
|----|--------|-------|-----------|-------------|
| **NR-01** | `trading-execution` | `PositionRiskHandler` silently drops passing signals (no `publisher.accept`, no `reserveSignal`) — the default `DisruptorEventBus` wiring never places an order | `trading/execution/src/main/java/com/tradej/execution/risk/PositionRiskHandler.java:120-164` | Rewrite `PositionRiskHandlerComponentTest.qualifiesSignalIntoExecutableOrderRequest` to assert forwarding; add full-pipeline integration test |
| **NR-02** | `app` | `RuntimeConfiguration.applyConfiguredMode` (`@EventListener(ApplicationReadyEvent.class)`) runs **after** `BrokerStartupOrchestrator`; non-LIVE startup silently hits the live broker | `app/src/main/java/com/tradej/app/config/RuntimeConfiguration.java:22-27` | Unit test that constructs `BrokerStartupOrchestrator` with holder pre-set to `BACKTEST` and asserts preflight reads `BACKTEST` |
| **NR-03** | `trading-execution` + `broker-dhan` | `OrderPartiallyFilled`/`OrderFullyFilled` from Dhan WS are dropped in the default wiring — DW-03 half-fix; only the DAG path consumes them | `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java:148-177`; `broker/dhan/src/main/java/com/tradej/broker/dhan/websocket/DhanWebSocketMultiplexer.java:352-358` | Integration test that fires `OrderPartiallyFilled` and asserts `OrderProjection.filledQuantity` increments |
| **NR-04** | `core` | `OrderStateMachine.OrderFullyFilled` corrupts VWAP on duplicate/cumulative events (else-branch overwrites `filledQuantity` and skips accumulate) | `core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java:64-72` | Property test (jqwik) over random valid event sequences asserting filled ≤ total, accumulated ≥ 0, idempotency on replay |
| **NR-05** | `trading-execution` | `ExecutionHandler.placeOrderWithTimeout` does not `future.cancel(true)` on timeout; `OrderIdentityRegistry` leaks; broker may still execute the order with no internal tracking | `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java:389-402` | Inject a future that succeeds 30s later; assert registry is empty after 11s and no duplicate order |
| **NR-06** | `trading-execution` | `EventSourcedNetPositionProvider` mis-handles out-of-order `TradeClosed` (early-returns before `contribution` lookup → perpetual phantom position) | `trading/execution/src/main/java/com/tradej/execution/position/EventSourcedNetPositionProvider.java:81-101` | Out-of-order test: `[TradeClosed, TradeOpened]` for same tradeId, assert net position = 0 |
| **NR-07** | `data-persistence` + `data-feature-store` | Clock divergence: replay uses virtual time for `EventMetadata.timestampMs()` but `ingested_at_ms` is wall clock; queries filter by `ingested_at_ms` | `data/persistence/src/main/java/com/tradej/persistence/duckdb/DuckDbEventStore.java:323-325`; `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java:184-220` | Replay 5 events; query by virtual-time range; assert all 5 return |
| **NR-08** | `app` + `broker-upstox` | `UPSTOX_ANALYTICS_REST` mode registers WS listeners but never fires (no `connect()`); `CandleAggregationService` stays empty; `StrategySandbox` never runs | `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java:157,164-176`; `broker/upstox/src/main/java/com/tradej/broker/upstox/UpstoxBrokerConnection.java:140-143` | With `analytics-only=true`, assert no `MarketTickEvent` subscribers exist and no DAG runtime is constructed |

### 1.1 NR-01 detail — `PositionRiskHandler` is a silent pass-through

**Symptom:** the success branch of `handleSignalPending` only `log.debug`s. It never `publisher.accept(pending)` and never calls `portfolioEngine.reserveSignal(...)`.

**Downstream effects:**
- `ExecutionHandler.onDomainEvent` only ever fires for `SignalPendingExecution` and `OrderFilled`. No `SignalPendingExecution` arriving ⇒ no order ever placed in the default wiring.
- The new `OrderPlacementNode` only runs in the DAG-pipeline graph runtime path, which is *additive* (the DAG bridge is only subscribed to ticks/candles).
- `PortfolioEngine.reserveSignal` is never invoked. After a fill arrives, `PortfolioEngine.onTradeOpened` falls back to `strategyName="default"` and `Math.max(0, adjusted)`.
- `SignalSuppressed` events do propagate, so rejections still work — giving the illusion of correct risk enforcement.

**The test that enshrines the bug** is at `app/src/test/java/com/tradej/app/integration/PositionRiskHandlerComponentTest.java:71-73`:
```
assertTrue(emitted.isEmpty(), "Signal should pass risk checks and produce no suppression event");
```

**Fix:**
1. Inject `PortfolioEngine` into `PositionRiskHandler` and call `reserveSignal` on the success path.
2. Forward the `SignalPendingExecution` via `publisher.accept(pending)`.
3. Update the component test to assert exactly one forwarded event per passing signal and a reserved-capital delta in `PortfolioEngine`.
4. Add an integration test that publishes through the *full* `DisruptorEventBus` (the `OmsToExecutionSandboxIntegrationTest` currently bypasses risk).

### 1.2 NR-02 detail — `RuntimeMode` ordering bug

**Symptom:** `@EventListener(ApplicationReadyEvent.class)` fires *after* every `ApplicationRunner`. `BrokerStartupOrchestrator.runStartup` calls `OrderManagementService.placeOrder` and `ExecutionHandler.publishSimulatedFillIfNeeded`, both of which read `runtimeModeHolder.mode()`.

**Impact:** with `trade.runtime.mode=REPLAY` or `BACKTEST`, the holder is still `LIVE` for all startup work. Any operator booting in a non-LIVE mode is silently hitting the live broker for the first few hundred ms of work.

**Fix:** move the assignment into `@PostConstruct` on `RuntimeConfiguration` (or a `BeanFactoryPostProcessor`). Spring guarantees `@PostConstruct` runs before any `ApplicationRunner#run`.

### 1.3 NR-03 detail — partial fills dropped

`DhanWebSocketMultiplexer` now publishes `OrderPartiallyFilled`/`OrderFullyFilled` (DW-03 fix), but with **empty `fills` lists**. The default `DisruptorEventBus` wiring has no consumer for these events. Only `FillReconciliationNode` (DAG path) processes them, and the DAG path is additive (not default).

**Net:** partial fills are lost. OMS only ever sees the final `OrderFilled` event. VWAP and `filledQuantity` are wrong on partial-fill instruments.

**Fix:** wire `FillReconciliationNode` as a stage of the default `DisruptorEventBus`, *or* synthesize an `OrderFilled` from the partials inside `ExecutionHandler.onDomainEvent`. Option (b) is the smallest change.

### 1.4 NR-04 detail — VWAP corruption

```java
case OrderFullyFilled fill -> {
    long additional = fill.totalQuantity() - this.filledQuantity;
    if (additional > 0) {
        this.filledQuantity = fill.totalQuantity();
        this.accumulatedValuePaisa += additional * fill.pricePaisa();
    } else {
        this.filledQuantity = fill.totalQuantity();   // unconditional, no accumulate
    }
}
```

If the broker reports cumulative `OrderFullyFilled` (i.e. the `totalQuantity` is cumulative, not residual), `additional <= 0`, the else-branch overwrites `filledQuantity` and silently discards the price for that batch. Worse, if a prior `OrderPartiallyFilled` reported a slightly larger total (broker off-by-one), the unconditional assignment *shrinks* `filledQuantity` to a wrong value.

**Fix:** use `Math.max(this.filledQuantity, fill.totalQuantity())` and assert the delta is non-negative (log+skip otherwise). Add jqwik property test.

### 1.5 NR-05 detail — leaked orderId on timeout

`CompletableFuture.get(10s)` times out but never `future.cancel(true)`. The broker SDK call continues on `ForkJoinPool`; if it eventually succeeds, the result is discarded, the `ORD-N` orderId stays in the identity registry, and any subsequent `OrderFilled` cannot be attributed.

**Worst case:** a live, executed order with no internal tracking.

**Fix:** call `future.cancel(true)` and `identityRegistry.remove(orderId)` on timeout. Allocate orderId as a *tentative* slot until broker ack arrives.

### 1.6 NR-06 detail — out-of-order position race

`activeTradeIds.remove(tradeId)` returning `false` (out-of-order delivery) early-returns and **skips** the `contribution` lookup. The later `TradeOpened` will increment `tradeLongs/tradeShorts` but no matching close ever decrements them — **perpetual phantom position**.

The sharded Disruptor route (`Math.floorMod(symbol.hashCode(), shardCount)`) can place `TradeOpened` and `TradeClosed` for the same tradeId on different shards because fills for one trade can come from different venues.

**Fix:** look up `TradeContribution` *before* removing from `activeTradeIds`; if close arrives first, queue it and replay when the matching open arrives. Or run the position provider on a single-threaded executor.

### 1.7 NR-07 detail — clock divergence

Replay uses `clock.millis()` (virtual) for `EventMetadata.timestampMs()` but `ingestedAtMs = System::currentTimeMillis()`. The historical queries filter by `ingested_at_ms`. Two replays of the same data produce different windows; queries by event time are unreliable.

**Fix:** add an `event_time_ms` column (rename or add), migrate the reader to use it. Backfill existing rows.

### 1.8 NR-08 detail — Upstox analytics is a no-op

`setupWebSocketHandlers` runs unconditionally; `brokerConnection.connect()` is skipped when `expectsWebSocket() == false`. Listeners are wired to an idle multiplexer. `CandleAggregationService` stays empty; `StrategySandbox` is never invoked.

**Fix:** gate `setupWebSocketHandlers` on `mode.expectsWebSocket()`. Document the analytical intent, or wire REST polling to the same downstream.

---

## 2. New P1 — high impact, address in the next iteration

| ID | Module | Issue | File:line |
|----|--------|-------|-----------|
| **PR-01** | `core` | `RuntimeModeHolder` is not hot-reloadable despite `runtime-mode-audit.md` claiming otherwise | `core/src/main/java/com/tradej/core/domain/runtime/RuntimeModeHolder.java:8` |
| **PR-02** | `trading-execution` | `OrderIdentityRegistry.remove` leaks when `acknowledge` arrives after `remove` (compounds with NR-05) | `trading/execution/src/main/java/com/tradej/execution/identity/OrderIdentityRegistry.java:112-121` |
| **PR-03** | `app` | `DagPipelineIngressBridge` uses `CallerRunsPolicy` on a 2-thread executor; stalls the async-dispatch thread on slow DAG I/O | `app/src/main/java/com/tradej/app/pipeline/DagPipelineIngressBridge.java:32-39` |
| **PR-04** | `data-persistence` | `HistoricalRangeService.replayTradeLifecycle` writes `TradeClosed` with `size=0`, `entry_price=0`; `usedCapitalPaisa` is never freed | `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java:439-489` |
| **PR-05** | `trading-execution` | `OrderReconciler.reconcile` uses non-venue-qualified symbol key; over-aggregates cross-venue positions | `trading/execution/src/main/java/com/tradej/execution/reconcile/OrderReconciler.java:48-64, 100-107` |
| **PR-06** | `trading-strategy` | `CandleAggregationService` emits `CandleDeveloping` on every tick even when LTP/qty unchanged (5k wasted events/s) | `trading/strategy/src/main/java/com/tradej/strategy/service/CandleAggregationService.java:128-141` |
| **PR-07** | `broker-dhan` | `DhanSdkResponse.invoke` does raw reflection per call on the hot tick path | `broker/dhan/src/main/java/com/tradej/broker/dhan/mapper/DhanSdkResponse.java:81-87` |
| **PR-08** | `trading-simulation` | `MatchingEngine.match` emits `OrderStatus.TRADED` for partial fills → wrong P&L in backtest | `trading/simulation/src/main/java/com/tradej/simulation/MatchingEngine.java:138-153` |
| **PR-09** | `data-feature-store` | `DuckDbFeatureStore.upsertCandle` `event_id` column is useless for closed candles | `data/feature-store/src/main/java/com/tradej/feature/store/DuckDbFeatureStore.java:205-231` |
| **PR-10** | `data-persistence` | `DuckDbEventStore` + `HistoricalRangeService` open separate connections; "database is locked" silently swallowed | `data/persistence/src/main/java/com/tradej/persistence/duckdb/DuckDbEventStore.java:47`; `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java:51-56` |
| **PR-11** | `app` | `IsolatedReplayStateManager` doesn't snapshot `OrderIdentityRegistry`, `DuckDbFeatureStore`, `InMemoryFeatureStore`, `PipelineRuntimeService` → replay pollutes live state | `app/src/main/java/com/tradej/app/pipeline/IsolatedReplayStateManager.java:67-77` |
| **PR-12** | `data-persistence` | `HistoricalRangeService.queryTicks` returns legacy `TickReceived`; never reconstructs canonical `MarketTickEvent` | `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java:127-161` |
| **PR-13** | `data-persistence` | `HistoricalRangeService.replayFillEvents` synthesises `exchangeTimeMs=0` | `data/persistence/src/main/java/com/tradej/persistence/replay/HistoricalRangeService.java:805-810` |
| **PR-14** | `runtime-disruptor` | Disruptor ring buffer size hardcoded `8192`; no `TradingProperties.hotPath.ringBufferSize` knob | `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:183` |
| **PR-15** | `runtime-disruptor` | `DisruptorEventBus.dedupCache` mixes 200K clear with 1024-call throttled eviction and 1-minute pruner; subtle race | `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:430-451` |
| **PR-16** | `runtime-disruptor` | `AsyncDispatchHandler.dispatch` does linear `isAssignableFrom` scan per event | `runtime/disruptor/src/main/java/com/tradej/disruptor/config/AsyncDispatchHandler.java:198-204` |
| **PR-17** | `broker-dhan` | `cancelAndSquareOffIntradayPositions` doesn't square off in sandbox; live-only behaviour silently inconsistent | `broker/dhan/src/main/java/com/tradej/broker/dhan/orders/DhanRestOrderClient.java:131-133` |
| **PR-18** | `broker-dhan` | `getOrder/getOrders/getTrades` have no per-request timeout | `broker/dhan/src/main/java/com/tradej/broker/dhan/orders/DhanRestOrderClient.java:85-110` |
| **PR-19** | `broker-dhan` | `STALE_FEED_THRESHOLD_MS=30s` hardcoded; false positives in quiet intraday | `broker/dhan/src/main/java/com/tradej/broker/dhan/constants/DhanProtocolConstants.java:23` |
| **PR-20** | `broker-upstox` | `redirectServerPort=18080` hardcoded; two CLI sessions collide | `broker/upstox/src/main/java/com/tradej/broker/upstox/config/UpstoxConnectionSettings.java:34-46`; `broker/upstox/src/main/java/com/tradej/broker/upstox/auth/UpstoxRedirectServer.java:48-50` |
| **PR-21** | `broker-core` | `TokenBucketRateLimiter.refill` releases lock during sleep; race on `tokens` | `broker/core/src/main/java/com/tradej/broker/core/rate/TokenBucketRateLimiter.java:25-48` |
| **PR-22** | `app` | `BrokerStartupOrchestrator` is 500+ lines, 7+ concerns; untestable in isolation | `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java` |
| **PR-23** | `app` | `OrderPipelineHealthIndicator` always reports UP | `app/src/main/java/com/tradej/app/health/OrderPipelineHealthIndicator.java:30-43` |
| **PR-24** | `app` | `BrokerHealthIndicator` uses `isOpen()` (misses `HALF_OPEN`) | `app/src/main/java/com/tradej/app/health/BrokerHealthIndicator.java:33` |
| **PR-25** | `app` | `UpstoxHealthIndicator.probeRestMarketData` makes a network LTP call on every health probe (rate-limit risk) | `app/src/main/java/com/tradej/app/health/UpstoxHealthIndicator.java:65-78` |
| **PR-26** | `app` | `RuntimeHealthState` is a non-atomic POJO; torn reads | `app/src/main/java/com/tradej/app/admin/RuntimeHealthState.java` |
| **PR-27** | `app` | `BrokerStartupOrchestrator.subscribeExplicitly` ignores the validated `subscriptions` parameter | `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java:283-297` |
| **PR-28** | `app`, `data-*`, `gateway` | MDC enrichment missing in 5 of 6 async paths (DuckDB writers, gateway router, DAG bridge) | see §5.1 |
| **PR-29** | `app` | No integration test for the full Disruptor pipeline (the gap that let NR-01 ship) | new test in `app/src/test/java/.../integration/` |
| **PR-30** | `gateway` + `frontend` | Frontend↔backend JSON contract has no snapshot test | `gateway/.../bridge`, `frontend/src/dto` |
| **PR-31** | `app` | `ClockConfiguration` + `RuntimeConfiguration` + `RuntimeModeConfiguration` + `TradingRuntimeConfiguration` overlap; 29 config classes with no documented placement rule | `app/.../config/` |
| **PR-32** | `broker-dhan` | `DhanRestOrderClient` fixture is flat (no `data` wrapper); production path is never exercised by unit test | `broker/dhan/src/test/resources/dhan-fixtures/place-order-response.json` |
| **PR-33** | `trading-strategy` | `CandleIntervalSpec.parse` re-allocated per tick | `trading/strategy/src/main/java/com/tradej/strategy/service/CandleAggregationService.java:160-169` |
| **PR-34** | `runtime-hotpath` | `OrderPipeline.onOrderAccepted` is effectively a forwarder — fold into `BrokerStartupOrchestrator` | `runtime/hotpath/src/main/java/com/tradej/hotpath/OrderPipeline.java:69-79` |
| **PR-35** | `app` | `ExecutionConfiguration` is an empty `@Deprecated` marker — delete | `app/src/main/java/com/tradej/app/config/ExecutionConfiguration.java` |

---

## 3. New P2 — operational debt (non-blocking)

`broker-dhan` `DhanTokenManager` overly broad `catch (Exception ignored)`; `broker-upstox` `UpstoxTokenManager.bootstrapFromConfiguredToken` misleading fallback name; `broker-dhan` `DhanApiEnvironment` URL normalisation missing `/v2`; `broker-dhan` `DhanWebSocketMultiplexer.verifyFeedLiveness` runs token check when `connected=false`; `runtime-hotpath` `MarketDataPipeline.updateTickRate` allocates `RateState` per tick; `data-persistence` `ChronicleDeadLetterQueue.append` propagates `UncheckedIOException` (worsens a bad state); `data-persistence` `ReplayRunner.tailLag` is wrong across cycle rollover; `trading-execution` `OrderIdentityRegistryStressTest` doesn't actually stress; missing property tests on `OrderStateMachine` and `PriceMath`; missing contract test for `DhanRestOrderClient` round-trip via WireMock; `app` `DhanTokenForcedGenerationIntegrationTest` requires live creds and is never run in CI.

---

## 4. Phased plan

### Phase 0 — *Capital-Ready Gate* (1–2 sprints, blocks live capital)

Close all 8 P0 items with their tests. Each fix lands as a single PR with:
- the code change
- the new unit / integration test
- a new invariant entry in `REGRESSION_MANIFEST.md`
- a row in the verification checklist below

**Acceptance:** `fullRegressionTest` green for 5 consecutive runs.

| Item | PR title template | Verification |
|------|-------------------|--------------|
| NR-01 | `fix(execution): forward passing signals and reserve capital in PositionRiskHandler` | `PositionRiskHandlerComponentTest` + new `DisruptorEventBusRiskIntegrationTest` |
| NR-02 | `fix(app): set RuntimeModeHolder before ApplicationRunner fires` | New `RuntimeConfigurationOrderingTest` |
| NR-03 | `fix(execution): consume OrderPartiallyFilled/OrderFullyFilled in default Disruptor path` | New `OrderPartiallyFilledIntegrationTest` |
| NR-04 | `fix(oms): guard VWAP accumulation against cumulative OrderFullyFilled` | New `OrderStateMachinePropertyTest` (jqwik) |
| NR-05 | `fix(execution): cancel placeOrder future and release identity on timeout` | New `ExecutionHandlerTimeoutTest` |
| NR-06 | `fix(execution): handle out-of-order TradeClosed in NetPositionProvider` | New `EventSourcedNetPositionProviderOutOfOrderTest` |
| NR-07 | `fix(persistence): persist event_time_ms and filter by it in HistoricalRangeService` | New `ClockDivergenceTest` + DuckDB migration script |
| NR-08 | `fix(app+upstox): gate WS handler registration on expectsWebSocket()` | New `UpstoxAnalyticsModeNoListenerTest` |

### Phase 1 — *Replay Correctness & State Isolation* (1 sprint)

PR-04, PR-07, PR-11, PR-12, PR-13, plus existing `AD-02`.

Key deliverable: `IsolatedReplayStateManager` snapshots `OrderIdentityRegistry`, `DuckDbFeatureStore`, `InMemoryFeatureStore`, `PipelineRuntimeService`. Add a `beforeReplay` / `afterReplay` contract test asserting state equality.

### Phase 2 — *Hot-path & Latency* (1 sprint)

PR-06, PR-07, PR-14, PR-15, PR-16, PR-33.

Key deliverable: ring buffer size + wait strategy exposed in `TradingProperties`; Caffeine replaces ad-hoc dedup cache; `subscribe(Class, handler)` resolves exact-type subscriber lists; `DhanSdkResponse` method cache.

### Phase 3 — *Broker Adapter Hardening* (1 sprint)

PR-17, PR-18, PR-19, PR-20, PR-21, PR-32, plus the P2 token-manager items.

Key deliverable: `DhanRestOrderClient` round-trip test against WireMock; sandbox `cancelAndSquareOff` throws `UnsupportedOperationException`; Upstox redirect server binds to ephemeral port.

### Phase 4 — *App Wiring Refactor & Observability* (1 sprint)

PR-22, PR-23, PR-24, PR-25, PR-26, PR-27, PR-28, PR-35.

Key deliverable: `BrokerStartupOrchestrator` decomposed into a `List<StartupStep>` composed in order; MDC helper applied to all 6 async paths with a smoke test.

### Phase 5 — *Test Pyramid Closing* (continuous, parallel to other phases)

PR-29, PR-30, plus property tests on `OrderStateMachine` and `PriceMath`, frontend DTO contract test, actual stress on `TradingCircuitBreaker`.

### Phase 6 — *Architectural Clean-up* (opportunistic)

- Frontend TypeScript type generation from Java records (Pkl or `jsonschema2ts`).
- Config sprawl: document a placement rule; rename/move violators.
- `McpServer` + `research/*` — schedule a follow-up review at the same depth.

---

## 5. Cross-cutting improvements

### 5.1 MDC propagation (PR-28)

Introduce a single helper:
```java
public final class MdcHelper {
    public static Runnable withContext(DomainEvent e, Runnable r) { ... }
    public static <T> Callable<T> withContext(DomainEvent e, Callable<T> c) { ... }
}
```

Apply to: `AsyncDispatchHandler` (already does it), `AsyncDuckDbEventStore`, `AsyncDuckDbWriter`, `GatewayTopicRouter`, `DagPipelineIngressBridge`. Smoke test asserts that every log line from these classes contains `eventId` and `symbol`.

### 5.2 Error-handling philosophy (PR-22a, P2)

The codebase already uses a clear pattern (unchecked, typed exceptions). Document it in `core/src/main/java/com/tradej/core/package-info.java`:

- **Fail fast** for invariant violations (`IllegalStateException`).
- **Degrade** at trust boundaries (broker I/O, persistence) via typed exceptions.
- **Never** `catch (Exception ignored)`. Always log + counter.

### 5.3 Frontend↔backend contract (PR-30)

Pick **one** of:
- (A) Snapshot test `GatewayEventBridge` JSON against `frontend/src/dto/types.ts` golden files.
- (B) Generate TS from Java records using Pkl or `jsonschema2ts` + a CI step.

**Recommended:** (B). The DTOs already exist as records; the cost is one new Gradle module.

### 5.4 Configuration sprawl (PR-31)

One-sentence rule per config class:
- `Broker*Configuration` — broker client wiring (separate files per broker)
- `RuntimeConfiguration` — ONE file, owns `RuntimeModeHolder` + starter + health
- `Persistence*Configuration`, `FeatureStore*Configuration` — storage wiring
- `Pipeline*Configuration` — pipeline runtime wiring
- `Studio*Configuration`, `Admin*Configuration` — feature flags

Rename `ExecutionConfiguration` (deprecated marker) → delete. Audit all 29 files for compliance.

### 5.5 Architectural boundary test (PR-22b)

Extend `architecture-test` to enforce:
- `core` has zero Spring imports (already true; lock it).
- `trading-execution` does not import `runtime-disruptor` directly (currently goes through `EventBus`).
- `broker-*` does not import `trading-*` (currently true; lock it).
- `runtime-disruptor` does not import specific handler types — push to a `PipelineStage` list.

---

## 6. Coverage of existing `docs/BACKLOG.md`

| Existing item | Status this review |
|---|---|
| N-01 (NetPosition remove on close) | Fixed in `tradeContributions` map ✅ |
| E-01 (queue capacity 50) | Fixed (1000) ✅ |
| RP-01 (Chronicle event type discriminator) | Fixed ✅ |
| AD-02 (replay state reset) | **Still partial** — see PR-11 (4 components not snapshotted) |
| PE-02 (correlationId coupling) | **Unchanged** — still fragile |
| ST-02 (broad DomainEvent subs) | **Unchanged** — see PR-16 |
| UB-03 (Upstox unchecked cast) | **Unchanged** |
| DW-03 (partial fill dropped) | **Regressed** in default wiring — see NR-03 |
| GB-01 (WebSocket broadcasts all) | **Unchanged** |
| FS-01 (JDBC per-event) | **Unchanged** — still does a connection check per event |
| SC-01 (batch-only scan) | **Unchanged** |
| ME-01 (no slippage) | **Unchanged** |
| ME-02 (no partial fills / queue) | **Still incorrect in simulation** — see PR-08 |
| UB-01 (untested port sentinels) | **Unchanged** |
| GC-01 (CandleDeveloping allocations) | **Unchanged** — see PR-06 |
| ST-01 (large startup orchestrator) | **Confirmed** — see PR-22 |

**New P0 regressions not in backlog:** NR-01, NR-02, NR-04, NR-05, NR-06, NR-07, NR-08.

---

## 7. Acceptance criteria for "ready for live capital"

1. Phase 0 items closed with their tests; all marked `Done` in `BACKLOG.md`.
2. `fullRegressionTest` green for 5 consecutive runs.
3. A live paper-trading session (Dhan sandbox) running for 2 consecutive trading days with:
   - no `SignalPendingExecution` drops in metrics
   - `OrderIdentityRegistry.size()` returns to 0 each day
   - `EventSourcedNetPositionProvider.netPositions` consistent with `OrderReconciler` reports
4. `DagPipelineIngressBridge` dispatch queue depth never exceeds 50% of capacity in a 2-day backtest with 5 graphs.
5. Two replay runs of the same data produce identical OMS projections (event-time filter, not ingestion-time).
6. `RuntimeHealthState` does not have torn reads in a 24h soak test.
7. All MDC-async-path tests green; grep of logs shows `eventId` + `symbol` in every async path.

---

## 8. Tracking

- All new IDs (`NR-*`, `PR-*`) are added to `docs/BACKLOG.md` alongside the existing items.
- Each Phase N PR must:
  - Land the fix
  - Land the new test
  - Add a row to `REGRESSION_MANIFEST.md` referencing the new invariant
  - Update `BACKLOG.md` with the new status
- Each phase end is gated by `./gradlew fullRegressionTest` and the relevant `runtimeE2eTest` / `crossLayerRegressionTest`.
