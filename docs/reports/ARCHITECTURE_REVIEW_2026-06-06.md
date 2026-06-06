# Trade-J — Principal Quant Engineer Architecture Review

> **Date:** 2026-06-06
> **Scope:** architecture, hot-path correctness, OMS/execution, broker gateway, pipeline, scanner, persistence, observability
> **Author:** Principal Quant Engineer review pass
> **Verdict:** ambitious and well-layered for a small team, but several defects would cause silent data loss or risk-rule bypass at production tick rates. Two of them are P0 for any live capital. Build is 24 Gradle subprojects, ~655 main classes, 213 tests.

---

## Strengths (keep these)

- **Hexagonal broker boundary.** `:broker-api` is genuinely framework-free; `IBrokerConnection` facades + capability markers (`OptionsCapable`, `NewsCapable`) is the right shape (`broker/api/src/main/java/com/tradej/broker/api/IBrokerConnection.java`).
- **Money in paisa as `long`.** Eliminates the #1 quant-system bug class (float drift on notionals).
- **Single-writer OMS state machine.** `OrderStateMachine` is a pure lookup-table FSM tracking `filledQuantity` / `accumulatedValuePaisa` for VWAP — deterministic, replayable, no hidden time-sources.
- **Async-dispatch off the ring.** `AsyncDispatchHandler` (Disruptor last stage, `runtime/disruptor/.../AsyncDispatchHandler.java:166-181`) keeps JDBC writes / Chronicle serialisation out of the hot path. That decision is correct and well-executed.
- **DLQ + audit log separation.** Chronicle Queue is the right tool for the append-only OMS audit + dead-letter; DuckDB is right for queryable time-series.
- **Pipeline-on-Disruptor (Config A).** Replacing the legacy fixed risk→candle→strategy chain with a compiled DAG executed inside the ring (`DisruptorEventBus.java:122-130`) is the right long-term direction. Legacy Configs B/C are correctly retired.
- **Reactive test pyramid.** INV-01…INV-32 invariants in `REGRESSION_MANIFEST.md` mapped to specific tests, with opt-in chaos (Dhan live/sandbox) — exactly what a quant team should run nightly.

---

## P0 — Defects that will lose money or data at scale

### 1. `AsyncDispatchHandler` silently drops OMS / persistence events on subscriber backpressure
`runtime/disruptor/src/main/java/com/tradej/disruptor/config/AsyncDispatchHandler.java:166-181`

```java
if (!dispatchQueue.offer(event)) {
    droppedEventCount.incrementAndGet();
    deadLetterQueue.append("async-dispatch", event, "Dispatch queue full");
    log.warn("Dispatch queue full — dropping event ...");
}
```

- This queue feeds `DuckDbEventStore`, `ChronicleAuditLogWriter`, `EventSourcedNetPositionProvider`, `ReconciliationAlertLogger`, `ReadModelStore`. Dropped = **silent loss of OMS state, position state, and audit trail**. 4096 events ≈ 5–10s of NIFTY BANKNIFTY tick flow on a busy morning.
- There is **no replay path for dropped events** and no `disruptor.dispatchDroppedEventCount()` health-check alarm. The DLQ is treated as an after-thought; the OMS and PnL layers will diverge from the ring with no operator signal.
- **Fix:** make the dispatch stage unbounded with a *blocking* high-water-mark, or wire the DLQ into the event bus itself so the next stage retries. Add a `DisruptorReadinessHealthIndicator` alarm when drop count > 0 over any 60s window. The `DisruptorReadinessHealthIndicator` exists (`app/.../health/`) but does not look at drop counters.

### 2. Dedup is not a dedup — it's a TTL cache with a 200k LRU
`runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:368-388`

```java
private boolean isDuplicate(DomainEvent event) {
    if (seenEvents.size() >= MAX_SEEN_EVENTS) { ... }
    Long previous = seenEvents.putIfAbsent(event.eventId(), System.currentTimeMillis());
    return previous != null;
}
```

- Keyed on `event.eventId()`. Two problems for a quant system:
  - **`eventId` is not a monotonic source/sequence tuple**; it's a UUID. Dhan and Upstox are both prone to *re-emitting the same `MarketTickEvent` after a WebSocket reconnect* — the cache catches it, but only for ≤30s. After that the same event re-enters the ring. For `OrderAccepted` / `OrderFilled` this would cause double OMS transitions.
  - The 200k cap with periodic eviction of "anything older than 30s" is silent. If a broker replays 250k events in 5 minutes, you start getting false positives (events appear "new" because the cache churned).
- **Fix:** dedup on `(brokerSource, exchangeSegment, instrumentId, sequenceNumber)` for market data, and on `(brokerOrderId, transition)` for OMS. Make dedup persistence-backed (DuckDB `seen_events` table) so a process restart doesn't allow the broker to replay.

### 3. `PositionRiskHandler.activateKillSwitch` is a TOCTOU and never atomically unwinds
`trading/execution/src/main/java/com/tradej/execution/risk/PositionRiskHandler.java:279-294`, `212-261`

```java
if (killSwitch || reconciliationHalt) {
    rejectSignal(...);  // <-- this is checked once per SignalPendingExecution
    return;
}
...
private void activateKillSwitch(String reason) {
    this.killSwitch = true;       // volatile
    killSwitchCoordinator.engage(reason);  // separate class
}
```

- `killSwitch` is a `volatile boolean` with no atomic compare-and-set. After `activateKillSwitch` flips, in-flight `SignalPendingExecution` events already past the `if (killSwitch ...)` guard at `PositionRiskHandler.java:212` will still be risk-checked and forwarded. There's no `parker`-style critical section wrapping the gate.
- `ReconciliationHaltRequired` only blocks *new* signals. It does **not** cancel open orders, close partially-filled positions, or run a flat-out unwind. For a quant system "halt" must mean "all open positions in < 30s". Today it just refuses new entries while the broker can keep filling existing orders.
- **`reconciliationHalt = true` is set then `activateKillSwitch(...)` is called separately** (`PositionRiskHandler.java:114-122`); there's a microsecond window where new signals are rejected for the wrong reason string but kill switch isn't yet propagated to `KillSwitchCoordinator.engage()`.
- **Fix:** `AtomicBoolean` + `compareAndSet` for the kill gate; pre-trade `tryReserve()` per (strategy, symbol) atomically held by risk so the check-and-set is indivisible. Add a real unwind stage: emit `PositionCloseRequested` to all open trades and call `brokerConnection.cancelAll()` from the kill coordinator.

### 4. `OrderManagementService.cancelOrder` calls the broker before checking state and may double-fire
`trading/execution/src/main/java/com/tradej/execution/service/OrderManagementService.java:131-149`

```java
public boolean cancelOrder(String orderId) {
    OrderStateMachine machine = stateMachines.get(orderId);
    if (machine == null) {
        log.warn("Cancel requested for unknown orderId={}", orderId);
        return brokerConnection.orders().cancelOrder(orderId);  // <-- sends to broker unconditionally
    }
    LifecycleState state = machine.toProjection().status();
    if (state.isFinal()) {
        throw new IllegalStateException(...);
    }
    boolean cancelled = brokerConnection.orders().cancelOrder(orderId);
    if (cancelled) {
        persistAndApply(orderId, new CancelRequested(orderId));
    }
    return cancelled;
}
```

- Unknown orderId ⇒ broker call with no local guard. The broker will happily attempt to cancel a non-existent order; you get a 4xx back, but if the network is up and the OMS state is the source of truth, the broker cancel *can race* with a fill that arrives via the WebSocket. Result: an `OrderFilled` is processed while `CancelRequested` is in flight and you can end up with the OMS thinking it's `CANCELLED` while the broker reports a fill (the `OrderEvent` log will then reject one of them as illegal transition).
- More importantly, the `CANCEL_REQUESTED → CANCELLED` path requires broker ACK (the `OrderCancelled` event) which the OMS state machine accepts — but if the broker returns the cancel as success and *then* a fill comes in for the same order, the state machine at `core/.../oms/OrderStateMachine.java` will reject the `OrderFullyFilled` transition (good) but no `PositionMismatch` will be raised because the broker never sees the conflict — only the local OMS does. The next reconciliation tick catches it, but that's a 60s+ window.
- **Fix:** never call `brokerConnection.orders().cancelOrder` from a path that hasn't first persisted `CancelRequested` and read back the projected `CANCEL_REQUESTED` state. Treat `cancelOrder` as idempotent and let the broker's NACK be the truth source.

### 5. The 8 deprecated `DisruptorEventBus` constructors are a foot-gun
`runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:140-256`

- Eight `@Deprecated` overloads, plus the new `DisruptorEventBus(DisruptorPipelineConfig)`. This screams "we tried to make the old and new world coexist and gave up". Anyone wiring this in a test will hit a constructor that compiles a different Disruptor graph (Config B/C), pass all tests, and ship a runtime without risk checks. The Config A constructor explicitly *throws* on a missing `pipelineRuntimeBridge` (`DisruptorEventBus.java:76-79`) — good — but the deprecated path quietly builds a different graph.
- **Fix:** delete every `@Deprecated` constructor. Move `DisruptorPipelineBuilder` from "additional constructor" to the only constructor, and the next person who touches a test will catch it at compile-time.

---

## P1 — Architectural debt

### 6. Single-threaded `ExecutionHandler` is a throughput ceiling and a latency tail
`trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java:64-160`

- One daemon thread services an `ArrayBlockingQueue<ExecutionCommand>` of capacity 1000. Each command does a `CompletableFuture.supplyAsync(orderManagementService::placeOrder)` with `orderPlacementTimeoutMs = 10_000` (`ExecutionHandler.java:539-554`). That timeout means a hung Dhan socket can stall a queue slot for 10 seconds.
- During a burst (gap-down, NIFTY expiry, 5x volume), 1000 signals queue up; once full, `visit(SignalPendingExecution)` (`ExecutionHandler.java:206-220`) drops new ones to the DLQ as `SignalSuppressed`. For quant strategies this is a **silent signal loss** in the worst possible market state.
- **Fix:** partition by `symbol` or `accountId`. 4-8 worker threads, one per partition, each with a small bounded queue. The OMS is already stateless across orders (`ConcurrentHashMap` stateMachines) so this is mechanical. Add per-partition queue-depth metrics and reject signals at the *strategy* layer, not the execution layer.

### 7. `PortfolioEngine` runs inside the ring and shares a thread with risk/strategy
`runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java:106-111`

```java
if (config.portfolioEngine() != null) {
    portfolioPublisher = event -> config.portfolioEngine().onDomainEvent(event, safePublisher);
}
```

- `PortfolioEngine.onDomainEvent` does a `switch (event)` over records (`trading/strategy/.../PortfolioEngine.java:65-75`) but its `DefaultCapitalReservationService` and `DefaultExposureTracker` perform `ConcurrentHashMap` lookups. Cheap, but the same thread that runs the graph runtime is now doing capital reservation per event. If one capital-reserve call ever blocks (DB I/O, network), it stalls the entire Disruptor consumer.
- **Fix:** keep `PortfolioEngine` purely a "check" service (no I/O), or move it to the `AsyncDispatchHandler` ring side. Add a stopwatch metric per `case` and alarm at p99 > 50µs.

### 8. The gateway is a fake failover
`broker/core/src/main/java/com/tradej/broker/core/routing/LoadBalancedBrokerGateway.java:65-141`

- `primary()` rotates only on broker failure inside `FailoverOrderCommand.rotatePrimary`, but every other accessor (`orders()`, `portfolio()`, `instruments()`, `margin()`, `sessionRisk()`, `bracketOrders()`, `gttOrders()`, `sliceOrders()`, `orderQuery()`) hard-codes `primary()`. If Dhan is primary and goes down, `orders()` rotates — but `portfolio()` keeps calling Dhan, which is the very connection that's failing. The whole `LoadBalancedBrokerGateway` is a façade for an honest *single* broker, decorated with two useless siblings.
- `ICICI` adapter is two files (`IciciMarketDataProvider` + `BreezeWebSocketMultiplexer`); the audit correctly flagged it. The runtime cost of carrying it (`LoadBalancedBrokerGateway` iteration, capability lookups, config classes) > value.
- **Fix:** for Dhan-first go-live, collapse to a single `DhanBrokerConnection` bean. Add real per-port health: `health()` returns 0 if last WS heartbeat > N seconds, and the gateway picks the first `IBrokerConnection` whose `health()` is non-zero. Drop ICICI from the runtime classpath.

### 9. Dual pipeline runtime is real and undocumented for operators
`docs/ARCHITECTURE_REPORT.md:447-510` and `runtime/disruptor/.../DisruptorEventBus.java:122-130`

- Config A is wired in production: `GraphPipelineDisruptorHandler` runs inside the ring. *Separately*, `DagPipelineIngressBridge` (`app/.../pipeline/DagPipelineIngressBridge.java`) feeds `CandleClosed` / `MarketTickEvent` / `TickReceived` into `DagPipelineRuntimeService` for the Studio DAG. The same tick can therefore:
  1. Hit the Disruptor graph (config A)
  2. Get aggregated to a `CandleClosed`
  3. Hit the `AsyncDispatchHandler`
  4. Be re-injected into the DAG runtime via the cold ingress
  5. The DAG emits a `SignalGenerated`
  6. The `SignalGenerated` re-enters the Disruptor ring (config A) → risk → execution
- Net effect: a single candle-close can be scored twice. For a strategy that doesn't gate on `event.metadata().source()` you get *double exposure on the same setup*. This is in `BACKLOG.md` as a known issue but it's never been given a "no, you cannot" guarantee.
- **Fix:** make the DAG a *pure* cold-path tool (replay, scan studio, replay backtest). Block `DagPipelineIngressBridge` in `RuntimeMode.LIVE`. In LIVE, all signals come from the hot-path graph. The same source-of-truth invariant should hold in REPLAY.

### 10. `MarketDataPipeline` drops ticks on rate-limit; no `Tick`/`OrderBook` distinction
`runtime/hotpath/src/main/java/com/tradej/hotpath/MarketDataPipeline.java:117-122`

- A single `TokenBucket` for *all* symbols. If a strategy is interested in NIFTY + 3 of its constituents, a burst on one constituent consumes the bucket for all four. The drop counter is incremented and the LTP for the 3 quiet symbols is lost. For a market-making or arbitrage strategy this is a 5-min black hole.
- Worse, `tickRateLimitedCount` exists, but no health indicator maps it. The `DisruptorReadinessHealthIndicator` is the right place.
- **Fix:** token-bucket per `(ExchangeSegment, instrumentKey)`. Add a `trade.hot-path.symbol-burst-permits` config and a 95th-percentile shed-rate metric.

### 11. `ScanEngine` is batch-only; `StreamingScanCriterion` exists but is not wired into a hot loop
`trading/scanner/src/main/java/com/tradej/scanner/engine/ScanEngine.java:50-79`

- `ScanEngine.run(ScanProfile)` is a synchronous batch: build universe → REST snapshot (unless `WS_LIVE`) → evaluate → rank. `StreamingScanCriterion` is in the same package but `ScanEngine` doesn't use it.
- The cron in `application.yml` fires `0 15,30,45 9-15 * * MON-FRI` — three times an hour, IST, weekday only. That's fine for end-of-day screening, **catastrophic** for a scanner that should feed the strategy in real time.
- **Fix:** drive streaming criteria from a `ScanNode` wired into the DAG runtime (your `StreamingScanCriterionNode` already exists — wire it). The cron becomes a backfill / universe refresh, not the live signal path.

### 12. `ExecutionHandler` calls `processFill` and `processBrokerFill` with `currentDownstream` captured by a mutable field
`trading/execution/.../ExecutionHandler.java:192-219`, `203`

```java
private Consumer<DomainEvent> currentDownstream;
...
public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
    this.currentDownstream = downstream;  // <-- shared mutable state
    ...
    event.accept(this);
    ...
    this.currentDownstream = null;
}
```

- This is a *thread-local-style pattern* implemented as a field. If the Disruptor ever re-orders two `onDomainEvent` calls (it doesn't on a single consumer, but `ShardedDisruptorEventBus` with N shards has N consumer threads), a partial call from thread A can be observed by thread B reading `currentDownstream`. Java memory model-wise, this is a data race even if it doesn't currently deadlock.
- **Fix:** pass the downstream consumer through the visitor pattern or as a `ThreadLocal`. Or, better, change the visitor to return the (event, downstream) tuple from `accept()` and have `onDomainEvent` return a `DomainEvent`-list of `downstream` emissions.

### 13. `ExecutionHandler` emits `TradeUpdated` with `quantity=0, pnl=0` placeholders
`trading/execution/.../ExecutionHandler.java:556-589`

- The second-and-later fills emit `TradeUpdated(symbol, fillPrice, 0L, 0L)` (line 565-571). The `(quantity, pnl)` are always 0. The only data the `TradeUpdated` carries is the *price*. Any downstream PnL consumer has to recompute quantity from elsewhere. For a quant system this is an obvious information-loss bug.
- **Fix:** carry `cumulativeQuantity`, `incrementalQuantity`, `realizedPnlPaisa` on `TradeUpdated` and populate them.

### 14. The deprecated `onMarketTickEvent` `@Deprecated` annotation is on a method body
`runtime/hotpath/.../MarketDataPipeline.java:96-99`

- The `@Deprecated` annotation is *before* the `/**` Javadoc of the new method and references the *old* method. Cosmetic, but it indicates copy-paste error in the area of your hot path. Worth fixing because reviewers will eventually trust the deprecation hint.

### 15. `LoadBalancedBrokerGateway.primary()` is not atomic
`broker/core/.../LoadBalancedBrokerGateway.java:73-75`

```java
private IBrokerConnection primary() {
    return connections.get(Math.floorMod(primaryIndex.get(), connections.size()));
}
```

- `primaryIndex` is an `AtomicInteger` but the `get` + `Math.floorMod` is a TOCTOU if `removeConnection` runs concurrently. If a connection is being removed while a `placeOrder` reads the index, you can call `.orders()` on a half-disconnected broker. The safe pattern is `Math.floorMod(primaryIndex.getAndIncrement(), snapshot.size())` after taking a local `CopyOnWriteArrayList` snapshot.
- **Fix:** snapshot the list, then read the index, or use a `primary` reference field updated only inside `rotatePrimary`.

---

## Module hygiene

- `app/` is the god module: `BrokerConfiguration` (460+ lines of Dhan beans), `UpstoxConfiguration` (420+), `IciciConfiguration` (280+), `MarketDepthConfiguration` (`instanceof DhanBrokerConnection`), `SubscriptionConfiguration` (refers `DhanWebSocketMultiplexer`), `DefaultAuthValidationService` (refers `DhanTokenProvider` + `BreezeTokenProvider`). These should live in `:broker-dhan`, `:broker-upstox`, `:broker-icici` as Spring Boot auto-configurations, loaded via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. The `AUDIT_REPORT.md` already recommends this.
- The Dhan SDK is the de-facto single broker despite the hexagonal scaffolding. `DhanConfigPaths` is used by Upstox CLI as a generic path resolver — that's coupling that will bite when you switch SDKs.
- 24 Gradle subprojects is a lot for a team this size. `:data-historical-ingest` and `:data-analytics` could be one module; `:trading-indicators` should be a leaf library under `:trading-strategy`. The settings file has 24 entries that mostly have 5-20 classes each.
- `SubscribeCommand` is a CLI command in `:cli` that duplicates the `SubscriptionCoordinator` / `SubscriptionManager` / `SubscriptionRecoveryManager` triplet in `app/.../subscription/`. Three classes for subscription management is one too many.
- `marketdata.csv` is 28 MB and lives in repo (`security_id_list.csv`). This is a moving security master; bake it into the historical-ingest flow and remove from the repo.

---

## Observability & ops

- `BrokerErrorTracker` exists but `tickRateLimitedCount`, `dispatchDroppedEventCount`, `downstreamQueueDepth`, `dlqEnqueueCount` are *not* mapped to a `MeterBinder`. The `/actuator/prometheus` endpoint therefore will not show the metrics an on-call engineer needs to know "are we losing ticks" without JMX attach. Add Micrometer binders for the four counters in `DisruptorEventBus` and the one in `MarketDataPipeline`.
- `DisruptorReadinessHealthIndicator` exists but is not documented as checking drop counters. Make it part of `liveness` (fail readiness if drop > 0 over 60s) and `readiness` (always) — current behaviour lets the app report healthy while the OMS is dropping `OrderAccepted` events.
- No tracing around `processSignal` / `processFill` in `ExecutionHandler`. A `tracer.spanBuilder("order.place")` would let you see broker latency vs. ring latency in Jaeger / Tempo. OpenTelemetry is in the stack but I don't see the ExecutionHandler using it.
- The `DagPipelineIngressBridge` is described in the audit as "cold ingress". Why? "Cold" implies heavy/async; in practice it's a `Consumer<DomainEvent>` from the `AsyncDispatchHandler` to `DagPipelineRuntimeService` — fine, but rename it to make the intent obvious.

---

## Quant / strategy correctness

- **Paisa is right, but sign convention is ambiguous.** `unrealizedLossPaisa` is `Math.max(0, lossPaisa)` (`PositionRiskHandler.java:138`). `realizedLossPaisa.addAndGet(-realized)` is used for negative PnL (`PositionRiskHandler.java:184`). Fine, but `combined_loss` (`PositionRiskHandler.java:141-148`) sums `realized + unrealized` — but `unrealized` is computed at the strategy's mark, not the LTP. If mark-to-market is stale, the combined-loss check is wrong by however much LTP moved.
- **PortfolioEngine capital reservation runs on `reserveSignal(signal)`** (`PositionRiskHandler.java:251`) but the only caller in the path is `PositionRiskHandler`. The `PortfolioEngine.onDomainEvent` path also reserves on `SignalGenerated` via the ring (`PortfolioEngine.java:67`). That means the same signal can be reserved *twice* — once by `PositionRiskHandler` and once by the ring-stage `PortfolioEngine`. Verify by integration test before go-live.
- **`maxOpenPositionQuantity()` is the same thing as `maxOpenPositions` in `application.yml`** (`max-open-positions: 3`). The config value is the *count* of positions, but the code compares against `|qty|` (line 222-234). For options, 1 lot of NIFTY = 75 qty. So `max-open-positions: 3` means you can have 3 *positions* but `maxOpenPositionQuantity` will trip much sooner. The naming lies about the semantics.
- **`ConcurrentHashMap` for `symbolsWithOpenPosition` (`PositionRiskHandler.java:47`)** is correct, but `Set.copyOf` on snapshot (`PositionRiskHandler.java:314`) snapshots only the keys, not the per-symbol qty. So `restore(snapshot)` loses position state on a kill-switch reset. Make the snapshot also carry `netPositions` from `NetPositionProvider`.
- **`MarketSessionPolicy` is in the SPI but I don't see it enforced anywhere in the hot path.** A NIFTY F&O order placed at 09:08:30 (pre-open) or 15:30:05 (post-close) is a regulatory risk. The audit never mentions this; make sure the OMS rejects with a `session_risk` reason.

---

## Tests (213) — gaps I'd close

- **No chaos test for the `AsyncDispatchHandler` drop path.** The 4096-deep queue is exactly the kind of place a $5M bug hides. Write a test that publishes 10k events at 1ms intervals to a subscriber that sleeps 100ms — assert drop count > 0 and that the DLQ captured the dropped events. Then the *next* test must prove those DLQ events get replayed.
- **No kill-switch race test.** Two threads racing to set kill-switch + publish a `SignalPendingExecution` — assert that the signal is *never* placed. Use `Thread.onSpinWait` to widen the window.
- **No round-trip reconciliation test.** Place an order, then *manually* desync the OMS by deleting the state machine, then publish a fill. The reconciler should re-derive state and emit `PositionMismatch` if divergence.
- **The `crossLayerRegressionTest` is opt-in** (`DHAN_CROSS_LAYER_TEST_ENABLED=true`) — that's correct for sandbox, but for the `OMS+exec` cross-layer (INV-28) there should be a *non-opt-in* in-memory broker that runs on every PR.
- **Dhan instrument catalog has no boundary test** for "what happens at market open when the catalog was last refreshed T-1". Add a test that loads a Friday catalog and tries to subscribe Monday morning.

---

## Operational gaps

- **No order-replay-from-DLQ path.** When `AsyncDispatchHandler` drops events, the DLQ Chronicle Queue has them, but nothing reads it back into the bus. This is a real recovery gap.
- **No broker clock skew compensation.** A Dhan `OrderAccepted` carries broker timestamp; the OMS uses `clock.millis()` for `generateOrderId` (`ExecutionHandler.java:591-593`). The two are not reconciled. For a 200ms skew between app and broker, an OrderId sequence can be reordered relative to brokerOrderId. This is invisible until you try to debug a "where did my fill go" incident.
- **Reconciliation runs every 60s by default** (`trade.reconciliation.interval-seconds`); in a 5-tick-a-second market that's 300 ticks of potential divergence. For a quant system I'd want 5-10s interval plus a "reconcile-on-disconnect" trigger.
- **No circuit-breaker policy for cascading failures.** If Dhan returns HTTP 503 on a market data call, the rate limiter catches it, but the call *still goes to the disruptor* as a `MarketDataEvent` with no LTP. Strategies that gate on `ltp > 0` will silently skip. Make the normalizer drop ticks with LTP=0 (Dhan uses 0 to mean "not yet known").

---

## Prioritized action list

| # | Severity | Action | File |
|---|---------|--------|------|
| 1 | **P0** | Make `AsyncDispatchHandler` block on full, or replay DLQ into the bus; wire drop counters to `/actuator/prometheus` and `DisruptorReadinessHealthIndicator` | `runtime/disruptor/.../AsyncDispatchHandler.java:166` |
| 2 | **P0** | Replace eventId dedup with `(source, exchange, instrument, seq)` for market data and `(brokerOrderId, transition)` for OMS; persist dedup | `runtime/disruptor/.../DisruptorEventBus.java:368` |
| 3 | **P0** | Convert `killSwitch` to `AtomicBoolean` with CAS gate; implement unwind-on-halt (cancel-all + position-close fan-out) | `trading/execution/.../PositionRiskHandler.java:48, 279` |
| 4 | **P0** | Never call broker cancel/modify without first persisting local `CancelRequested`/`OrderModified` and asserting state-machine guard | `trading/execution/.../OrderManagementService.java:131` |
| 5 | **P0** | Add chaos test: subscriber sleep + drop counter assertion + DLQ replay proof | new test |
| 6 | **P1** | Delete 8 deprecated `DisruptorEventBus` constructors; force the `DisruptorPipelineConfig` only path | `runtime/disruptor/.../DisruptorEventBus.java:140-256` |
| 7 | **P1** | Collapse `LoadBalancedBrokerGateway` to a single broker; add per-port `health()` and real failover | `broker/core/.../LoadBalancedBrokerGateway.java` |
| 8 | **P1** | Block `DagPipelineIngressBridge` in `RuntimeMode.LIVE` to stop double-evaluation of `CandleClosed` | `app/.../pipeline/DagPipelineIngressBridge.java` |
| 9 | **P1** | Per-symbol token bucket for ticks; per-partition execution workers (4-8) | `runtime/hotpath/.../MarketDataPipeline.java:117` |
| 10 | **P1** | `ThreadLocal` (or visitor-return) for `currentDownstream`; remove the shared mutable field | `trading/execution/.../ExecutionHandler.java:192` |
| 11 | **P1** | Carry cumulative qty / realized PnL on `TradeUpdated` | `core/.../event/TradeUpdated.java` + caller |
| 12 | **P1** | Wire `StreamingScanCriterion` into a DAG `ScanNode`; cron becomes universe refresh only | `trading/scanner/.../ScanEngine.java:50` |
| 13 | **P2** | Move `BrokerConfiguration` / `UpstoxConfiguration` / `IciciConfiguration` into broker modules as auto-config | `app/.../config/BrokerConfiguration.java` |
| 14 | **P2** | Reject orders placed outside market session with `session_risk` reason | `trading/execution/.../OrderManagementService.java:94` |
| 15 | **P2** | Snapshot/restore `NetPositionProvider` state in `PositionRiskHandler.StateSnapshot` so kill-switch reset doesn't lose positions | `trading/execution/.../PositionRiskHandler.java:302` |
| 16 | **P2** | Add OpenTelemetry spans around `processSignal` / `processFill` | `trading/execution/.../ExecutionHandler.java:367, 440` |

---

**Bottom line.** The architecture is *clean on paper*: hexagonal broker, event-sourced OMS, ring-buffer hot path, DAG cold path, DLQ + audit, dual storage. What's missing is the **operational safety net** that a trading system needs: blocking backpressure (not drop), atomic kill-switch + unwind, OMS-first cancel/modify, real broker failover, and per-symbol rate limits. Items 1-4 above are what I'd block a live-capital go-live on. Everything else is debt you can service in parallel without taking risk.
