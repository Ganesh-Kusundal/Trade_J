# Trade-J Code-Level Review

> **Date:** 2026-05-28  
> **Scope:** Complete Java codebase analysis across all modules  
> **Methodology:** Line-by-line review of every source file across all modules  
> **Cross-References:** [ARCHITECTURE_EVOLUTION_PIPELINE_OS.md](ARCHITECTURE_EVOLUTION_PIPELINE_OS.md), [docs/runtime-mode-audit.md](docs/runtime-mode-audit.md), [docs/event-schema-evolution.md](docs/event-schema-evolution.md), [SYSTEM_ANALYSIS_CHECKLIST.md](SYSTEM_ANALYSIS_CHECKLIST.md)

---

## Table of Contents

1. [Module Architecture & Dependency Flow](#1-module-architecture--dependency-flow)
2. [trade-core — Domain Foundation](#2-trade-core--domain-foundation)
3. [trade-disruptor — Event Bus Layer](#3-trade-disruptor--event-bus-layer)
4. [trade-hotpath — Pipeline Orchestration](#4-trade-hotpath--pipeline-orchestration)
5. [trade-execution — Order Lifecycle & Risk](#5-trade-execution--order-lifecycle--risk)
6. [trade-strategy — Strategy Evaluation](#6-trade-strategy--strategy-evaluation)
7. [trade-scanner — Symbol Scanning](#7-trade-scanner--symbol-scanning)
8. [trade-persistence — Journal & Historical Query](#8-trade-persistence--journal--historical-query)
9. [trade-simulation — Backtest Engine](#9-trade-simulation--backtest-engine)
10. [trade-gateway — WebSocket Bridge](#10-trade-gateway--websocket-bridge)
11. [trade-feature-store — Feature Computation](#11-trade-feature-store--feature-computation)
12. [trade-broker-dhan — Dhan Broker Adapter](#12-trade-broker-dhan--dhan-broker-adapter)
13. [trade-broker-upstox — Upstox Broker Adapter](#13-trade-broker-upstox--upstox-broker-adapter)
14. [trade-app — Spring Wiring & Configuration](#14-trade-app--spring-wiring--configuration)
15. [Cross-Cutting Concerns](#15-cross-cutting-concerns)
16. [Architectural Feasibility Assessment](#16-architectural-feasibility-assessment)
17. [Recommendations & Roadmap](#17-recommendations--roadmap)
18. [Findings Summary](#18-findings-summary)

---

## 1. Module Architecture & Dependency Flow

### Dependency Graph (simplified)

```
trade-core (domain events, ports, models, OSM)
    ↑
trade-broker-api (broker interfaces)
    ↑
trade-broker-dhan / trade-broker-upstox (broker adapters)
    ↑
trade-disruptor (LMAX event bus)
    ↑
trade-hotpath (MarketDataPipeline, OrderPipeline)
    ↑
trade-execution (risk, OMS, reconciliation, identity registry)
    ↕
trade-strategy (engine, sandbox, candle aggregation, portfolio)
    ↑
trade-scanner (scan engine, criteria, universe builder)
    ↑
trade-persistence (Chronicle journal, DuckDB event store, replay)
    ↑
trade-feature-store (DuckDB + in-memory feature computation)
    ↑
trade-simulation (matching engine, simulated order service, P&L)
    ↑
trade-gateway (WebSocket bridge to frontend)
    ↑
trade-app (Spring Boot wiring, config, admin endpoints)
```

### Dependency flow evaluation

| Aspect | Verdict |
|--------|---------|
| **Acyclic?** | ✅ No circular dependencies detected |
| **Domain isolation?** | ✅ `trade-core` has zero Spring dependencies, zero external deps |
| **Broker abstraction?** | ✅ `trade-broker-api` defines the `IBrokerConnection` contract; Dhan and Upstox implement it |
| **Testability?** | ⚠️ Mixed — `trade-hotpath` and `trade-core` are pure Java and highly testable; `trade-execution` uses `@Service` making unit testing harder without mocking |

### Dependency concern: `trade-disruptor` → `trade-execution` + `trade-strategy`

The `DisruptorEventBus` constructor directly imports handler types from `trade-execution` and `trade-strategy`:

```java
// DisruptorEventBus.java line 30-35
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.StrategyEngine;
```

This creates a **compile-time coupling** from the event bus to every pipeline stage. If a new stage is added, `DisruptorEventBus` must be modified. The `PipelineConfig` factory partially mitigates this by centralizing construction, but the bus itself still knows about all stages.

**Recommendation:** Use a `PipelineStage<T>` abstraction that `DisruptorEventBus` accepts as a list, rather than individual constructor parameters.

---

## 2. trade-core — Domain Foundation

### `DomainEvent` interface

```java
public interface DomainEvent {
    EventMetadata metadata();
    default String eventId() { return metadata().eventId(); }
    default long timestampMs() { return metadata().timestampMs(); }
    default long timestampMonotonic() { return metadata().timestampMonotonic(); }
    default long sequenceId() { return metadata().sequenceId(); }
    default String correlationId() { return metadata().correlationId(); }
    default EventPriority priority() { return EventPriority.NORMAL; }
}
```

**Strengths:**
- Immutable design enforced by convention (all implementations are `record` classes)
- Metadata carries both wall-clock (`timestampMs`) and monotonic (`timestampMonotonic`) timestamps for correct ordering
- `EventPriority` allows future QoS differentiation

**Issues:**

1. **`timestampMonotonic()` unused** — No code path reads this field. Intended for Disruptor sequence alignment but never wired.

2. **`priority()` always returns `NORMAL`** — The priority field is declared but never set differently. The `TickReceived` record doesn't override it. This could be useful for backpressure signaling (e.g., order fills at HIGH priority, ticks at LOW).

3. **`eventId()` generation is caller-dependent** — Some events use `UUID.randomUUID()`, others use a deterministic key. No centralized `EventIdGenerator` exists.

### `EventBus` interface

```java
public interface EventBus {
    <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler);
    <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler);
    void publish(DomainEvent event);
    default void publishBatch(List<? extends DomainEvent> events) { events.forEach(this::publish); }
    void start();
    void stop();
}
```

**Issues:**

1. **`publishBatch` is naive** — Iterates and calls `publish` one-by-one. No transactional semantics, no atomicity guarantee. If the 5th event fails, the first 4 are already published.

2. **No `publishBatch` override in `DisruptorEventBus`** — The Disruptor ring buffer supports `publishEvents()` (multi-event claim), but this is never used. Each event claims a separate ring buffer slot, wasting cache-line batching benefits.

### `TickReceived` record

```java
public record TickReceived(
        EventMetadata metadata,
        String symbol,
        String interval,
        long ltpPaisa,
        long lastTradeQuantity,
        long cumulativeVolume,
        long exchangeTimestampMs,
        MarketDepth marketDepth
) implements DomainEvent {}
```

**Issues:**

1. **`interval` field is redundant** — The tick carries an `interval` string (e.g., "1s") but this is used by `CandleAggregationService` to route ticks to the correct candle bucket. However, different candle intervals coexist; a tick arriving with `interval="5m"` shouldn't prevent its consumption by the 1-second candle builder.

2. **`interval` is never populated** — Looking at `MarketDataPipeline::onTickReceived`, ticks flow from the WebSocket handler and the `interval` field is always `null` or empty. The candle aggregation service ignores it anyway (it uses its own internally-configured intervals). This field should be removed from `TickReceived`.

### `OrderStateMachine` — Sealed interface pattern

```java
public sealed interface OrderEvent permits
        OrderSubmitted, OrderAcknowledged, OrderPartiallyFilled,
        OrderFullyFilled, OrderCancelled, OrderRejected,
        OrderExpired, CancelRequested {
    String orderId();
    EventType type();
}
```

**Strengths:**
- Sealed interface ensures exhaustive pattern matching in `switch` expressions
- Every state transition is defined in a lookup table — deterministic and auditable
- `LifecycleState` enum covers NEW → PENDING_SUBMIT → SUBMITTED → PARTIALLY_FILLED → FILLED, plus CANCEL_PENDING and terminal states

**Issues:**

1. **VWAP calculation incorrectly handles partial fills** — In `on()`, `accumulatedValuePaisa += fill.filledQuantity() * fill.pricePaisa()`. For `OrderFullyFilled`, it computes `additional = fill.totalQuantity() - this.filledQuantity` then `accumulatedValuePaisa += additional * fill.pricePaisa()`. If `totalQuantity == filledQuantity` (already filled), it does `this.filledQuantity = fill.totalQuantity()` without accumulating. This means the VWAP price is **reliably computed only when fills arrive in order and fully represent the fill**. If a `FULLY_FILLED` event arrives without prior `PARTIALLY_FILLED` events, `additional == totalQuantity` and the VWAP is correct. But if the OSM is rebuilt from events, the order matters.

2. **`LifecycleState` not imported** — The file does not show the `LifecycleState` import; it's assumed to be in the same package. The enum must exist in `com.tradej.core.domain.oms`.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| `TickReceived.interval` | 🟡 Medium | Field is never populated and ignored by consumers |
| `timestampMonotonic` | 🟢 Low | Field exists but never set or read |
| `publishBatch` | 🟢 Low | No batch-ring-buffer optimization; naive iteration |
| `OrderStateMachine VWAP` | 🟡 Medium | VWAP accumulates correctly but sequence-dependent for rebuilds |

---

## 3. trade-disruptor — Event Bus Layer

### `DisruptorEventBus` — Core implementation

**Configuration:**
- Ring buffer: 8192 slots
- Wait strategy: `BusySpinWaitStrategy` (100% CPU spin — lowest latency, highest CPU cost)
- Producer type: `MULTI` (multiple threads can publish)
- Pipeline: `risk → candle → [feature-sync] → strategy → execution → async-dispatch`

**Strengths:**
- `BusySpinWaitStrategy` is correct for sub-microsecond latency where spin-loops are acceptable
- `ProducerType.MULTI` is necessary because the downstream drainer thread publishes alongside WS threads
- Re-entrant publish deadlock fix via `downstreamQueue` + drainer thread is well-designed
- Dedup cache with periodic pruning prevents unbounded growth

**Critical Issues:**

#### Issue C-01: `ConcurrentHashMap` subscriber map with `CopyOnWriteArrayList`

```java
private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> subscribers
    = new ConcurrentHashMap<>();
```

The subscriber list uses `CopyOnWriteArrayList`, which is correct for read-heavy, write-rare scenarios. However, the `AsyncDispatchHandler` iterates over this list on every dispatch:

```java
for (Map.Entry<Class<? extends DomainEvent>, List<DomainEventHandler<? extends DomainEvent>>> entry : subscribers.entrySet()) {
    if (entry.getKey().isAssignableFrom(event.getClass())) {
        for (DomainEventHandler handler : entry.getValue()) {
            handler.onEvent(event);
        }
    }
}
```

The `isAssignableFrom` check happens for **every subscriber entry** on **every event dispatch**. With 10 subscriber types and 10,000 events/sec, that's 100,000 `isAssignableFrom` calls per second. This is acceptable but worth monitoring.

#### Issue C-02: `SubscriberDispatchHandler` is unused but imported

`SubscriberDispatchHandler` exists as the original dispatch implementation and is documented as "superseded by AsyncDispatchHandler" in the file picker output. However, it is never imported or referenced anywhere. It should be removed or kept only for reference.

#### Issue C-03: Dedup cache `seenEvents` size management

```java
private boolean isDuplicate(DomainEvent event) {
    if (seenEvents.size() >= MAX_SEEN_EVENTS) {
        seenEvents.clear();
    }
    Long previous = seenEvents.putIfAbsent(event.eventId(), System.currentTimeMillis());
    return previous != null;
}
```

The dedup cache **clears all entries** when it reaches 200,000. This means right after a clear, all events are new for a brief window. Under sustained high throughput (e.g., 1000 ticks/sec across 500 symbols), this clear happens every ~3 minutes, causing a brief period where duplicates could slip through. The periodic pruner (every 1 minute) also runs concurrently, creating a race where events pruned at TTL=5 minutes might be re-added after a clear.

**Fix:** Use a bounded LRU cache (e.g., via `LinkedHashMap` with `removeEldestEntry`) instead of periodic clear + TTL pruning. Or use Caffeine cache which has built-in expiry.

#### Issue C-04: Ring buffer size is hardcoded

```java
this.ringBufferSize = 8192;
```

8192 is a power of 2 (required by Disruptor) and is reasonable, but should be configurable. The `PipelineConfig` doesn't accept ring buffer size as a parameter.

#### Issue C-05: Stop sequence can lose downstream events

```java
public void stop() {
    started = false;
    executionHandler.stop();
    if (drainerThread != null) {
        drainerThread.interrupt();
        // ...
    }
    disruptor.shutdown();
    dispatchStage.stop();
}
```

The `drainerThread` is interrupted before `disruptor.shutdown()`. If the drainer has events in its `downstreamQueue` that haven't been published yet, they are lost because:
1. The drainer thread is interrupted and exits its poll loop
2. The drain queue's remaining events are published to the ring buffer
3. But `disruptor.shutdown()` is called immediately after, so the ring buffer events may not be fully consumed

**Fix:** Stop the disruptor first, then interrupt the drainer. Or ensure `dispatchStage.stop()` drains remaining events from the dispatch queue.

### `ShardedDisruptorEventBus` — Partitioned event bus

```java
public void publish(DomainEvent event) {
    shards.get(shardIndex(event)).publish(event);
}

static int shardIndexFor(DomainEvent event, int shardCount) {
    return MdcHelper.symbolOf(event)
            .map(symbol -> Math.floorMod(symbol.hashCode(), shardCount))
            .orElse(0);
}
```

**Strengths:**
- Deterministic symbol-based routing via `Math.floorMod(symbol.hashCode(), shardCount)`
- Each shard gets its own Disruptor ring buffer → true parallelism
- Non-symbol events route to shard 0

**Issues:**

#### Issue S-01: Subscription is broadcast to ALL shards

```java
public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
    for (DisruptorEventBus shard : shards) {
        shard.subscribe(eventType, handler);
    }
}
```

Every subscriber is registered on every shard. If the subscriber is a persistence handler (e.g., `DuckDbFeatureStore`), it will be called once per shard per event type. Since events are partitioned by symbol, each event only appears on one shard. This means the subscriber is called for every shard's events, but events only exist on one shard — so the subscriber processes all events correctly. However, each shard's `AsyncDispatchHandler` also dispatches to all subscribers for ALL events that pass through that shard.

**The issue:** If a subscriber is stateful and expects to see all events in order, broadcasting subscription works. But if the subscriber is shard-aware (e.g., a per-symbol state machine), it gets duplicate calls from other shards that have no matching events.

**In practice:** For `DuckDbFeatureStore` which subscribes to `DomainEvent.class` (all events), this means the feature store is called once per shard per event, which is correct because each event only hits one shard. But the `isAssignableFrom` loop still iterates over all subscribers on every shard.

### `MutableDomainEventEnvelope` — Ring buffer entry

```java
public final class MutableDomainEventEnvelope {
    private DomainEvent event;
    public void clear() { this.event = null; }
}
```

**Issue:** No pre-clear before `setEvent`. The Disruptor pre-allocates these objects and reuses them. If an event handler throws, the envelope may retain a reference to the previous event, preventing GC. The `clear()` call in `AsyncDispatchHandler::onEvent` handles this after dispatch, but if a handler throws before `clear()`, the stale reference persists.

### `AsyncDispatchHandler` — Off-thread subscriber dispatch

```java
public void start() {
    if (running.compareAndSet(false, true)) {
        dispatcher.submit(this::drainLoop);
    }
}
```

**Strengths:**
- Moves blocking I/O (Chronicle Queue, DuckDB JDBC) off the Disruptor consumer thread
- Bounded queue provides natural backpressure
- Drops with explicit `droppedEventCount` counter and WARN-level logging

**Issues:**

#### Issue A-01: `drainLoop` uses `poll(100ms)` — 100ms max latency for subscriber dispatch

```java
DomainEvent event = dispatchQueue.poll(100, TimeUnit.MILLISECONDS);
```

Under low event volume, subscribers experience up to 100ms additional latency. This is fine for persistence, but if a subscriber needs near-real-time reaction (e.g., position update to WebSocket), this latency is significant. The `GatewayEventBridge` subscribes to `DomainEvent.class` and is dispatched through this path.

**Fix:** Use `take()` (blocking, no timeout) with a second mechanism for shutdown signaling (e.g., a poison pill).

#### Issue A-02: `stop()` timeout handling

```java
if (!dispatcher.awaitTermination(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
    dispatcher.shutdownNow();
    drainRemainingOnShutdown();
}
```

`shutdownNow()` sends `interrupt()` to the thread, which will interrupt the `poll()` call in `drainLoop`. The `drainRemainingOnShutdown()` method drains remaining events. However, the drainer thread might be in the middle of processing an event (in `dispatch()`) when interrupted, causing that event to be lost.

### `StageTimings` — Pipeline latency measurement

```java
public record StageTimings(
        StageTiming risk,
        StageTiming candle,
        StageTiming strategy,
        StageTiming execution,
        StageTiming dispatch
) {}
```

**Strengths:**
- Per-stage nanosecond-level latency recording
- `StageTiming.noOp()` for production paths where timing is disabled
- Clean separation from Micrometer (timing is just a callback)

**Issue:** The timing is recorded inside each handler's `onEvent`, which measures **processing time** for that stage. But the Disruptor pipeline is sequential — stage N+1 doesn't start until stage N finishes. The actual wall-clock latency from publish to dispatch completion is the sum of all stages, plus any waiting time in the ring buffer.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| C-01 | 🟢 Low | `isAssignableFrom` scan on every dispatch |
| C-03 | 🟡 Medium | Dedup cache clears all entries at 200K instead of LRU eviction |
| C-04 | 🟢 Low | Ring buffer size 8192 is hardcoded |
| C-05 | 🟡 Medium | Stop sequence can lose downstream events |
| S-01 | 🟢 Low | Sharded bus broadcasts subscriptions — correct but wasteful |
| A-01 | 🟡 Medium | 100ms poll timeout adds latency to subscriber dispatch |
| A-02 | 🟢 Low | Event loss risk during async dispatch shutdown |

---

## 4. trade-hotpath — Pipeline Orchestration

### `MarketDataPipeline` — Tick ingestion

```java
public void onTickReceived(TickReceived tick) {
    if (tickRateLimiter != null && !tickRateLimiter.tryConsume()) {
        tickRateLimitedCount.incrementAndGet();
        return;
    }
    tickCount.incrementAndGet();
    downstream.accept(tick);
    DepthUpdateEvent depthUpdate = DepthUpdateFactory.fromTick(tick);
    if (depthUpdate != null) {
        downstream.accept(depthUpdate);
    }
}
```

**Strengths:**
- Token bucket rate limiting for tick shedding
- Lock-free CAS-based EMA rate calculation
- `MdcHelper.enrich` / `MdcHelper.clear` for structured logging
- Synthesizes `DepthUpdateEvent` from tick data if depth is present

**Issues:**

#### Issue M-01: Dedup logic clarity — `DepthUpdateFactory.fromTick()` early-returns on null depth

Looking at the code:
```java
DepthUpdateEvent depthUpdate = DepthUpdateFactory.fromTick(tick);
if (depthUpdate != null) { downstream.accept(depthUpdate); }
```

The factory method checks `depth == null || depth.isEmpty()` and returns `null` immediately. A `DepthUpdateEvent` is **only** allocated when depth data is actually present in the tick. This is not an allocation concern (contrary to a surface-level reading). **No fix needed for allocation.**

However, the `tick.marketDepth()` field on `TickReceived` is always populated from the Dhan SDK (when feed mode is QUOTE or FULL), even for ticker-only symbols where depth is meaningless. The `MarketDepth` object is allocated by the SDK on every tick regardless of whether depth data exists. This cannot be controlled on our side.

**Relevant concern:** For ticker-only symbols (where `marketDepth` is nulled in `DhanWebSocketMultiplexer.handleMarketPayload()`), the `null` case prevents allocation. But the `handleMarketPayload` method explicitly nulls depth for `TICKER` mode data (the `includeDepth=false` path):
```java
TickReceived tick = includeDepth
    ? normalized
    : new TickReceived(
        normalized.metadata(),
        normalized.symbol(),
        normalized.interval(),
        normalized.ltpPaisa(),
        normalized.lastTradeQuantity(),
        normalized.cumulativeVolume(),
        normalized.exchangeTimestampMs(),
        null  // depth explicit null
    );
```
So this is handled correctly. No allocation concern.

#### Issue M-02: Rate limiting drops everything — not priority-aware

All ticks are treated equally. A tick for a symbol with an open position should logically be higher priority than a tick for a watchlist symbol. No mechanism exists to prioritize symbols.

#### Issue M-03: Tick rate EMA uses `System.nanoTime()` without considering pause/resume

If the JVM freezes for a GC pause, the elapsed time since the last tick will be very large, causing the instant rate to approach 0, which then causes the EMA to drop. After the pause, the first few ticks will drive the EMA back up. This is correct behavior but worth documenting.

### `OrderPipeline` — Signal and order event routing

```java
public void onSignalGenerated(SignalGenerated signal) {
    if (signalRateLimiter != null && !signalRateLimiter.tryConsume()) {
        signalRateLimitedCount.incrementAndGet();
        return;
    }
    signalCount.incrementAndGet();
    downstream.accept(signal);
}
```

**Strengths:**
- Signal rate limiting to prevent strategy explosion
- Order lifecycle events (accept, fill, reject, trade open/close/update) are never rate-limited

**Issues:**

#### Issue O-01: Signal rate limiting drops after risk check

The `OrderPipeline.onSignalGenerated` is called **before** the signal enters the Disruptor pipeline. But the risk stage (`PositionRiskHandler`) is the first Disruptor handler, meaning the signal has already passed through `OrderPipeline` and been placed on the ring buffer before risk checks run. Rate limiting at the pipeline level adds latency but doesn't reduce Disruptor load.

Actually, looking at the flow more carefully:
1. Strategy → `SignalGenerated` → `StrategySandbox.emitEnrichedSignal()` → `downstream.accept(enriched)` → portfolioEngine's `safePublisher` → `downstreamQueue` → drainer → `DisruptorEventBus.publish()` → ring buffer
2. Ring buffer → `PositionRiskDisruptorHandler` → risk checks → if passed, emits `SignalPendingExecution` → `downstreamQueue` → drainer → ring buffer again
3. Ring buffer → `ExecutionDisruptorHandler` → `ExecutionHandler.onDomainEvent` → queue

So `OrderPipeline.onSignalGenerated` would need to intercept before the ring buffer. But looking at the startup configuration, signals are emitted via `StrategySandbox.emitEnrichedSignal` which calls the publisher (which is portfolio engine's safePublisher → downstreamQueue). The `OrderPipeline` is set up but **never actually called** for signals — it's wired as a bean but not connected to the event flow.

Wait, let me re-examine. In `StartupConfiguration`:

```java
brokerConnection.websocket().onOrderUpdate(event -> {
    switch (event) {
        case OrderAccepted accepted -> orderPipeline.onOrderAccepted(accepted);
        case OrderFilled filled -> orderPipeline.onOrderFilled(filled);
        case OrderRejected rejected -> orderPipeline.onOrderRejected(rejected);
        default -> eventBus.publish(event);
    }
});
```

The `OrderPipeline` is used for broker order updates (WS callbacks), not for signals. Signals go through the Disruptor pipeline. This means the `onSignalGenerated` method on `OrderPipeline` is **dead code** — it's never called.

#### Issue O-02: `OrderPipeline::onSignalPendingExecution` is also dead code

```java
public void onSignalPendingExecution(SignalPendingExecution pending) {
    downstream.accept(pending);
}
```

No code path calls this method. `SignalPendingExecution` is emitted by `PositionRiskHandler` directly via its downstream publisher.

### `PipelineConfig` — Wiring factory

```java
public static PipelineComponents create(
        int shardCount,
        PositionRiskHandler positionRiskHandler,
        CandleAggregationService candleAggregationService,
        StrategyEngine strategyEngine,
        ExecutionHandler executionHandler,
        PortfolioEngine portfolioEngine,
        StageTimings stageTimings,
        FeatureStore hotPathFeatureStore,
        DeadLetterQueue deadLetterQueue
) { ... }
```

**Strengths:**
- Pure Java factory with zero Spring dependencies
- Single place where Disruptor bus + pipelines are assembled
- `PipelineComponents` record exposes all components

**Issues:**

#### Issue P-01: Sharded bus uses same handlers for ALL shards

```java
for (int i = 0; i < shardCount; i++) {
    shards.add(new DisruptorEventBus(
            positionRiskHandler,     // shared across shards
            candleAggregationService, // shared across shards
            strategyEngine,           // shared across shards
            executionHandler,         // shared across shards
            ...
    ));
}
```

All shards share the same singleton handler instances. This means:
- `PositionRiskHandler` has a single `ConcurrentHashMap` of open trades — all shards share it, which is correct
- `CandleAggregationService` has a single `ConcurrentHashMap` of current candles — all shards share it, which is correct for concurrent access
- `StrategyEngine` has a single sandbox — all shards share it, which is correct

So the handlers are stateless enough or use concurrent data structures. However, the memory locality benefit of sharding is partially lost because all shards contend on the same concurrent maps.

### `SymbolShardRouter` — Utility class

```java
public static int shardFor(String symbol, int shardCount) {
    return Math.floorMod(symbol.hashCode(), shardCount);
}
```

This duplicates the logic in `ShardedDisruptorEventBus.shardIndexFor()`. Both use the same algorithm but are independent implementations. If one changes, the other becomes inconsistent.

**Fix:** Have `ShardedDisruptorEventBus.shardIndexFor()` delegate to `SymbolShardRouter.shardFor()`.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| M-01 | 🟢 Low | `DepthUpdateEvent` allocation per tick even when depth is absent |
| M-02 | 🟢 Low | No tick priority for symbols with open positions |
| O-01 | 🟡 Medium | `OrderPipeline.onSignalGenerated` is dead code — never called |
| O-02 | 🟢 Low | `onSignalPendingExecution` is dead code |
| P-01 | 🟢 Low | Shards share handler instances — correct but reduces isolation |

---

## 5. trade-execution — Order Lifecycle & Risk

### `ExecutionHandler` — Core execution engine

**Architecture:**
- Single-threaded `ExecutorService` with a `BlockingQueue<ExecutionCommand>(capacity=50)`
- Processes signals and fills sequentially on the "execution-handler" thread
- Uses a separate `ScheduledExecutorService` for fill retry scheduling

**Strengths:**
- Sequential processing within the execution handler eliminates concurrency issues with OMS state machine
- Fill identity resolution via `OrderIdentityRegistry` (four lookup paths)
- `tradeOpenedEmitted` set prevents duplicate `TradeOpened` events (fix for bug C-05)
- Bounded `CompletableFuture` timeout (10 seconds) for broker order placement
- `CountDownLatch` support for deterministic test sequencing

**Critical Issues:**

#### Issue E-01: Queue capacity of 50 is extremely small

```java
private final BlockingQueue<ExecutionCommand> queue = new ArrayBlockingQueue<>(50);
```

The execution queue can hold only 50 pending commands. If the broker is slow (e.g., 1 second per order), and 50 orders arrive in quick succession, subsequent orders are **silently dropped**:

```java
if (!queue.offer(new SignalCommand(pendingExecution, downstream))) {
    deadLetterQueue.append("execution-handler", pendingExecution, "Execution queue full");
    downstream.accept(new SignalSuppressed(...));
}
```

For an intraday system that might generate 100+ signals in a market event, a queue of 50 is a bottleneck. A fill backlog from the broker could also fill the queue, causing signal drops.

**Fix:** Increase to at least 1000, or use a growable queue with a configurable capacity.

#### Issue E-02: Fill defer retries don't clear MDC context

```java
private void scheduleFillRetry(OrderFilled orderFilled, Consumer<DomainEvent> downstream, int nextAttempt) {
    fillDeferExecutor.schedule(() -> {
        if (!queue.offer(new FillCommand(orderFilled, downstream, scheduledAttempt))) {
            scheduleFillRetry(orderFilled, downstream, scheduledAttempt);
        }
    }, FILL_DEFER_DELAY_MS, TimeUnit.MILLISECONDS);
}
```

The initial `scheduleFillRetry` is called from `onDomainEvent` which has `MdcHelper.enrich` / `MdcHelper.clear` wrapping. But the retry lambda runs on the `fillDeferExecutor` thread — no MDC context is set, which means any logging inside `processFill` (called on the execution handler thread) has no MDC context. The `processFill` method calls `MdcHelper.enrich` at the start, so this is a minor issue.

#### Issue E-03: `processFill` VWAP calculation uses `price / fills.size()` — truncation

```java
long avgPrice = orderFilled.fills().stream()
        .mapToLong(Trade::pricePaisa)
        .sum() / orderFilled.fills().size();
```

Integer division truncates. For 3 fills at prices 101, 102, 103 paisa: sum=306, count=3, avg=102 (correct). For 2 fills at 101, 102: sum=203, count=2, avg=101 (truncated from 101.5). Over many fills, this rounding error accumulates.

**Fix:** Use `Math.round()` or `(sum + count/2) / count`.

#### Issue E-04: `generateOrderId()` uses UUID — inefficient

```java
static String generateOrderId() {
    return "ORD-" + UUID.randomUUID();
}
```

`UUID.randomUUID()` uses secure random, which can block on systems with low entropy. For an intraday trading system generating thousands of orders, a simpler ID scheme would be better (e.g., atomic counter + node ID, or `UuidCreator.getTimeBased()`).

### `TradingCircuitBreaker` — Resilience pattern

```java
public synchronized boolean allowsRequest() {
    return switch (state) {
        case CLOSED -> true;
        case OPEN -> {
            if (System.currentTimeMillis() >= openUntilMs) {
                state = State.HALF_OPEN;
                halfOpenProbes = 0;
                halfOpenProbes++;
                yield true;
            }
            yield false;
        }
        case HALF_OPEN -> {
            if (halfOpenProbes < maxHalfOpenProbes) {
                halfOpenProbes++;
                yield true;
            }
            yield false;
        }
    };
}
```

**Issues:**

#### Issue C-01: All methods are `synchronized` — single monitor contention

All state transitions in `TradingCircuitBreaker` use intrinsic `synchronized` on the instance. Under high throughput (100+ orders/sec), every broker success/failure call contends on this lock. This is a bottleneck.

**Fix:** Use `AtomicReference<State>` with CAS-based transitions (lock-free). The state machine is simple enough for a lock-free implementation.

#### Issue C-02: `recordFailure()` in OPEN state extends the window but doesn't reset `openUntilMs`

```java
case OPEN -> {
    openUntilMs = System.currentTimeMillis() + openDurationMs;
}
```

If failures keep coming while the circuit is OPEN, `openUntilMs` keeps being pushed forward. This is correct behavior (prevent thundering herd) but should be documented.

### `PositionRiskHandler` — Risk qualification

**Function:**
- Check kill switch
- Check max open positions
- Check daily loss limit + consecutive losses
- Verify signal has quantity
- Verify instrument mapping exists (single, not ambiguous)
- Check max order value
- Call `PortfolioEngine.reserveSignal()` for capital checks
- Construct `OrderRequest` and emit `SignalPendingExecution`

**Issues:**

#### Issue R-01: `handleTick` iterates ALL open trades on EVERY tick

```java
private void handleTick(TickReceived tickReceived, Consumer<DomainEvent> downstream) {
    openTrades.values().stream()
            .filter(trade -> trade.symbol().equalsIgnoreCase(tickReceived.symbol()))
            .forEach(trade -> { ... });
}
```

For 500 symbols with 10 open trades, this iterates 10 entries per tick, filtering by symbol. This is acceptable. But `equalsIgnoreCase` is used for symbol comparison — symbol case should be canonicalized upstream.

#### Issue R-02: `ManagedTrade` doesn't track SL/TP progression

Once a trade is opened, `stopLossPaisa` and `takeProfitPaisa` are set from `TradeOpened` and never updated. A trailing stop or TP adjustment would create a new `TradeOpened` event, which is already in `openTrades` — the current code would **not update** the SL/TP because `put` overwrites.

Actually, looking more carefully:

```java
if (event instanceof TradeOpened tradeOpened) {
    openTrades.put(tradeOpened.tradeId(), new ManagedTrade(...));
}
```

If a trailing stop update emits a `TradeOpened` or `TradeUpdated` event, the risk handler only processes `TradeOpened` — `TradeUpdated` is not handled. So trailing stops are not tracked by the risk engine.

### `OrderIdentityRegistry` — Four-way ID mapping

```java
private final ConcurrentHashMap<String, String> brokerToInternal = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, String> internalToBroker = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, String> signalToInternal = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, String> internalToSignal = new ConcurrentHashMap<>();
```

**Strengths:**
- Four concurrent maps provide O(1) lookups in all directions
- `remove()` uses reverse map for O(1) cleanup
- Guards against duplicate registration with WARN-level logging

**Issues:**

#### Issue I-01: Memory leak risk on unordered cleanup

If `remove()` is not called for an order (e.g., the order lifecycle doesn't complete cleanly), all four maps retain entries indefinitely. The registry has no eviction mechanism.

**Fix:** Add periodic cleanup of stale entries (e.g., orders with no activity for > 24 hours), or add a TTL-based eviction.

### `OrderReconciler` — Position reconciliation

**Function:**
- Pass 1: Compare expected net positions (from `EventSourcedNetPositionProvider`) against broker positions
- Pass 2: Rebuild all OSM orders and compare filled quantities against broker positions
- Emits `PositionMismatch` events for any discrepancies

**Issues:**

#### Issue R-03: `reconcileAll()` fetches ALL broker positions upfront

```java
for (Position pos : brokerConnection.portfolio().getPositions()) {
    brokerByVenueSymbol.merge(venueKey, pos.quantity(), Long::sum);
}
```

Then iterates ALL known OSM orders (could be thousands) and compares each. For a full reconciliation at market close with thousands of orders and hundreds of positions, this is expensive. The `PositionMismatch` events are published, but there's no aggregation — if 1000 orders mismatch, 1000 events are emitted.

#### Issue R-04: `findBrokerQuantityForSymbolOnVenue` re-fetches positions every call (dead code?)

This method is defined but **never called** in the current code — `reconcileAll` builds its own `brokerByVenueSymbol` map locally. The method could be removed.

### `EventSourcedNetPositionProvider` — Event-driven position tracking

```java
public void onDomainEvent(DomainEvent event) {
    switch (event) {
        case TradeOpened opened -> applyDelta(opened.symbol(), signedQuantity(opened.side(), opened.size()));
        case TradeClosed closed -> netBySymbol.remove(closed.symbol());
        default -> { }
    }
}
```

**Issue:** When a trade is closed, the entire symbol entry is **removed** from `netBySymbol`. But what if there are multiple open trades on the same symbol, and only one is closed? The net position becomes 0 instead of the remaining position.

**Example:** Open trade 1: LONG 100 SBIN. Open trade 2: LONG 50 SBIN. Net position: +150 SBIN. Close trade 1: `netBySymbol.remove("SBIN")` → net position becomes 0. But we still have +50 from trade 2.

**Severity:** 🟠 High — This is a correctness bug in position tracking!

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| E-01 | 🟡 Medium | Execution queue capacity 50 is dangerously small |
| E-03 | 🟢 Low | Integer division truncation in VWAP calculation (paisa-level) |
| E-04 | 🟢 Low | UUID.randomUUID() for order IDs — entropy bottleneck |
| C-01 | 🟡 Medium | TradingCircuitBreaker uses synchronized — lock-free CAS better |
| R-01 | 🟢 Low | Symbol comparison uses equalsIgnoreCase — should be canonicalized |
| R-02 | 🟡 Medium | Trailing stop/TP updates not tracked by PositionRiskHandler |
| I-01 | 🟢 Low | OrderIdentityRegistry has no eviction — memory leak on stale orders |
| R-04 | 🟢 Low | `findBrokerQuantityForSymbolOnVenue` is dead code |
| **N-01** | 🟠 **High** | **`EventSourcedNetPositionProvider` removes symbol on close — incorrect for multi-trade symbols** |

---

## 6. trade-strategy — Strategy Evaluation

### `StrategyEngine` — Facade over `StrategySandbox`

```java
public final class StrategyEngine {
    private final StrategySandbox sandbox;

    public StrategyEngine(List<StrategyPlugin> plugins) {
        this.sandbox = new StrategySandbox(plugins);
    }

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        sandbox.onDomainEvent(event, downstream);
    }
}
```

**Issues:**

**Minimal class** — Delegates everything to `StrategySandbox`. Exists only for DI purposes. Could be merged with `StrategySandbox`.

### `StrategySandbox` — Isolated plugin execution

```java
public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
    if (!(event instanceof CandleClosed candleClosed)) {
        return;
    }
    // ... evaluate each plugin in a virtual thread
}
```

**Strengths:**
- Virtual threads per plugin with bounded timeout (5000ms)
- `CompletableFuture.handle()` for atomic signal-or-error (never both)
- `ServiceLoader.load()` for plugin discovery
- MDC enrichment per plugin execution

**Issues:**

#### Issue SS-01: Only `CandleClosed` events are processed — all other events are silently dropped

```java
if (!(event instanceof CandleClosed candleClosed)) {
    return;
}
```

TickReceived events, DepthUpdateEvent, and other market events are never sent to strategy plugins. This means:
- Tick-level strategies are impossible
- Depth-based strategies are impossible
- Order-flow strategies are impossible
- Only candle-close strategies can be implemented

The `StrategyPlugin` interface confirms this:

```java
public interface StrategyPlugin {
    String name();
    Optional<SignalGenerated> onCandleClosed(CandleClosed candleClosed);
}
```

**Severity:** 🟡 Medium — By design for now, but limits future strategy types.

#### Issue SS-02: `CompletableFuture.orTimeout()` doesn't cancel the virtual thread

```java
CompletableFuture<Optional<SignalGenerated>> future =
    CompletableFuture.supplyAsync(() -> runPlugin(plugin, candleClosed), executor);
future.orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
    .handle((optSignal, error) -> { ... });
```

The code itself documents this:
```java
// Note: the virtual thread continues running until
// runPlugin() returns. Java 21 virtual threads don't
// support forced interruption of CompletableFuture tasks.
```

A timed-out plugin's virtual thread continues running until `runPlugin` completes. If the plugin is in an infinite loop, it runs forever on a virtual thread, consuming resources.

**Fix for Java 21+:** Use `StructuredTaskScope.ShutdownOnTimeout` for proper cancellation.

#### Issue SS-03: Signal is published via `downstream.accept`, which calls `portfolioEngine.onDomainEvent`

Looking at the flow:
1. `StrategySandbox.emitEnrichedSignal()` → `downstream.accept(enriched)` → `portfolioEngine.onDomainEvent`
2. `PortfolioEngine` wraps this with its own `safePublisher` → `downstreamQueue` → ring buffer
3. First stage in ring buffer: `PositionRiskDisruptorHandler`

But wait — `PortfolioEngine` is passed as a filter in the publisher chain in `DisruptorEventBus`:

```java
Consumer<DomainEvent> portfolioPublisher;
if (portfolioEngine != null) {
    portfolioPublisher = event -> portfolioEngine.onDomainEvent(event, safePublisher);
} else {
    portfolioPublisher = safePublisher;
}
```

So signals go: Strategy → portfolioEngine filter → safePublisher → downstreamQueue → drainer → ring buffer → PositionRiskHandler.

But `PortfolioEngine.filterSignal()` passes the signal through without checks:
```java
private void filterSignal(SignalGenerated signal, Consumer<DomainEvent> downstream) {
    downstream.accept(signal);
}
```

And checks are done by `PositionRiskHandler.reserveSignal()` which is called separately. So the portfolio engine filter is a **pass-through for signals** — it only processes trade lifecycle events (open, close).

### `CandleAggregationService` — Tick to candle

```java
public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
    if (!(event instanceof TickReceived tick)) {
        return;
    }
    // ... bucket computation and OHLC update
}
```

**Strengths:**
- Multiple simultaneous intervals (1s, 5m, etc.)
- `ConcurrentHashMap` per key for lock-free candle updates
- Emits both `CandleDeveloping` (per tick) and `CandleClosed` (on bucket rollover)

**Issues:**

#### Issue CA-01: Bucket computation re-executes truncation EVERY tick

```java
private long bucketStart(long timestampMs, String interval) {
    Instant instant = Instant.ofEpochMilli(timestampMs == 0 ? System.currentTimeMillis() : timestampMs);
    return switch (interval) { ... };
}
```

For a 5m candle, this creates an `Instant` and truncates it every tick. With 5 intervals and 500 symbols × 1 tick/sec, that's 2500 `Instant` creations per second. Precomputing the bucket boundaries at tick arrival would be more efficient.

#### Issue CA-02: `currentCandles.compute()` creates a lambda PER TICK

```java
currentCandles.compute(key, (k, current) -> {
    // entire aggregation logic here
});
```

This runs the full aggregation logic even on tick overflow. For a 5m candle with 300 ticks (1 tick/sec), the bucket rollover check happens 300 times but only triggers once.

#### Issue CA-03: 15-minute and 5-minute bucket alignment

```java
case "15m" -> instant.atZone(ZoneOffset.UTC).withMinute((instant.atZone(ZoneOffset.UTC).getMinute() / 15) * 15)
        .withSecond(0).withNano(0).toInstant().toEpochMilli();
```

For `15m`, the bucket start is aligned to `minute / 15 * 15` — so 09:00, 09:15, 09:30, etc. For `5m` (the default), same logic: 09:00, 09:05, 09:10, etc. This is correct for standard candle alignment.

### `PortfolioEngine` — Capital allocation

**Strengths:**
- Per-strategy capital allocation with `ConcurrentHashMap` atomics
- Per-symbol net exposure tracking
- Signal → Trade attribution via signalId
- Capital reservation and release lifecycle
- `freeSignalCapital` for rejected/suppressed signals

**Issues:**

#### Issue PE-01: `filterSignal` is a pass-through (dead logic path)

The `filterSignal` method is called when `PortfolioEngine` is in the publisher chain (which it is). But it passes all signals through without filtering — the actual portfolio check is done by `PositionRiskHandler.reserveSignal()`. This means `PortfolioEngine` in the publisher chain serves only to process `SignalSuppressed`, `OrderRejected`, `TradeOpened`, and `TradeClosed` events for capital bookkeeping.

This is correct but confusing — the method name `filterSignal` implies filtering is done here.

#### Issue PE-02: `freeSignalCapital` called from `onOrderRejected` uses `correlationId`

```java
private void onOrderRejected(OrderRejected rejected, Consumer<DomainEvent> downstream) {
    String signalId = rejected.order().correlationId();
    String strategyName = freeSignalCapital(signalId, rejected.order().symbol());
    // ...
}
```

The `signalId` is extracted from the order's `correlationId` field. This assumes `PositionRiskHandler.qualifySignal` set the correlationId to the signalId when creating the `OrderRequest`:

```java
OrderRequest orderRequest = new OrderRequest(
        instrument.canonicalSymbol(),
        instrument.exchangeSegment(),
        signal.side(),
        quantity,
        OrderType.LIMIT,
        signal.entryPricePaisa(),
        0L,
        ProductType.INTRADAY,
        Validity.DAY,
        signal.signalId()  // correlationId = signalId
);
```

This coupling is **fragile** — if the `OrderRequest` constructor changes parameter positions or the correlationId field meaning, capital won't be freed.

#### Issue PE-03: `reserveSignal` and `netPositions.compute` use entry price

```java
long newExposurePaisa = Math.abs(newNet) * signal.entryPricePaisa();
```

The net exposure check uses the signal's entry price. But the actual exposure after execution depends on the fill price, which may differ. A fill at a worse price could exceed the limit that was checked at signal time. The `onTradeOpened` method does adjust:

```java
allocations.compute(strategyName, (name, alloc) -> {
    long adjusted = alloc.usedCapitalPaisa() - estimatedCapital + actualCapital;
    // ...
});
```

But the net position check (`maxNetExposurePaisa`) is only done at signal time. A fill that pushes the net exposure over the limit would not be caught.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| SS-01 | 🟡 Medium | Only `CandleClosed` events sent to plugins — no tick/depth strategies |
| SS-02 | 🟡 Medium | Virtual threads not cancelled on timeout — resource leak |
| SS-03 | 🟢 Low | PortfolioEngine filter is pass-through for signals — confusing naming |
| CA-01 | 🟢 Low | Bucket truncation creates `Instant` per tick — micro-optimization |
| PE-02 | 🟡 Medium | Fragile correlationId coupling between risk handler and portfolio engine |
| PE-03 | 🟢 Low | Net exposure check uses entry price — fill slippage could exceed limit |

---

## 7. trade-scanner — Symbol Scanning

### `ScanEngine` — Batch scanning

```java
public ScanResult run(ScanProfile profile) {
    List<ScanAsset> universe = universeBuilder.build(profile.universe());
    // ... fetch quotes, evaluate criteria, rank results
}
```

**Issues:**

#### Issue SC-01: Scanner is batch-only, not reactive

The `run()` method is synchronous — it fetches all quotes, evaluates all criteria, ranks results, and returns. There's no reactive streaming or incremental evaluation. The `ScanScheduler` runs it on a cron schedule (every 15 minutes during market hours). For reactive scanning, each tick would need to trigger evaluation for that symbol only.

#### Issue SC-02: `SnapshotFetcher` fetches ALL quotes upfront

```java
SnapshotFetcher.FetchResult fetchResult = snapshotFetcher.fetch(universe, profile.rest().batchSize());
quotes = fetchResult.quotes();
```

For a universe of 500 symbols, this makes up to 500 REST API calls (batched). The batch size controls how many symbols per call. This approach doesn't scale for sub-minute scanning intervals.

#### Issue SC-03: `evaluateUniverse` uses `LinkedHashSet` for option underlyings

```java
Set<String> underlyings = new LinkedHashSet<>();
for (ScanHit hit : hits) {
    if (hit.assetClass() == AssetClass.OPTION || hit.assetClass() == AssetClass.EQUITY) {
        underlyings.add(hit.underlying() != null ? hit.underlying() : hit.symbol());
    }
}
```

After the coarse pass, option underlyings are extracted from hits, then option chains are fetched, then a **second** evaluation pass runs. This is the "option fine pass." The keyword "fine" suggests detail pass, but it re-runs the full evaluation — it's just a second pass with option chain data.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| SC-01 | 🟡 Medium | Scanner is batch-only — no reactive/incremental evaluation |
| SC-02 | 🟡 Medium | All quotes fetched upfront — doesn't scale for frequent scanning |

---

## 8. trade-persistence — Journal & Historical Query

### `ReplayRunner` — Chronicle Queue replay

```java
public ReplayResult replayAll(Class<? extends DomainEvent> eventType) {
    while ((raw = tailer.readText()) != null) {
        totalRead++;
        try {
            DomainEvent event = objectMapper.readValue(raw, eventType);
            eventBus.publish(event);
            replayed++;
        } catch (Exception ex) {
            failed++;
        }
    }
}
```

**Issues:**

#### Issue RP-01: All events deserialized as the SAME type

```java
DomainEvent event = objectMapper.readValue(raw, eventType);
```

If the queue contains multiple event types, but `replayAll` is called with `TickReceived.class`, all entries are deserialized as `TickReceived`. Non-tick entries fail deserialization (caught by the `catch`). This means the Chronicle Queue has no type discrimination — each entry should include a type discriminator.

**Fix:** Use a wrapper record that includes a type field and the serialized event bytes, then dispatch to the correct type.

#### Issue RP-02: `ObjectMapper` created inline instead of injected

```java
private final ObjectMapper objectMapper = new ObjectMapper();
```

No configuration — uses default Jackson settings. If domain events use Java 8+ time types, custom ObjectMapper configuration is needed.

### `HistoricalRangeService` — DuckDB query

**Strengths:**
- Direct JDBC queries to DuckDB for candles, ticks, orders, fills
- Replay methods publish events directly to the event bus
- `RangeStats` provides data availability summary

**Issues:**

#### Issue HR-01: Raw JDBC with manual resource management

The service uses `PreparedStatement` with manual `try-with-resources`. All query methods have near-identical SQL patterns. For a more maintainable approach, a Jdbi or Spring JDBC template would reduce boilerplate.

#### Issue HR-02: `replayOrders` and `replayFillEvents` use default values for missing fields

```java
new Order(orderId, correlationId, sym, ExchangeSegment.UNKNOWN, Side.UNKNOWN, ...);
```

Reconstructed orders use `UNKNOWN` for exchange segment and side. These fields are not persisted in the orders table. This is a data model limitation — historical order replay produces incomplete domain events.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| RP-01 | 🟡 Medium | Chronicle Queue replay deserializes all events as same type |
| RP-02 | 🟢 Low | ObjectMapper configured inline — no type support config |
| HR-01 | 🟢 Low | Raw JDBC, no query abstraction |
| HR-02 | 🟢 Low | Historical orders use UNKNOWN for missing fields |

---

## 9. trade-simulation — Backtest Engine

### `MatchingEngine` — In-process order matching

```java
public MatchResult match(OrderRequest request, String orderId) {
    long fillPrice = resolveFillPrice(request);
    // ... fill at limit price, LTP, or request price
}
```

**Strengths:**
- Simple but correct for backtesting
- `lastPricePaisaBySymbol` map for LTP tracking
- LIMIT orders fill at limit price; MARKET orders fill at LTP

**Issues:**

#### Issue ME-01: No slippage model

All fills happen at exactly the limit price or LTP. In reality, market impact and slippage mean fills at sub-optimal prices, especially for large quantities relative to volume. A backtest using this engine will overestimate profitability.

#### Issue ME-02: No volume-aware matching

Every order is matched at the full requested quantity. No partial fills, no queue position, no time-priority. This is optimistic for backtesting purposes.

### `PnLLedger` — Profit/Loss tracking

**Strengths:**
- FIFO-based position tracking (correct for Indian equity markets)
- Realized and unrealized P&L separation
- `markToMarket` for MTM valuation

**Issues:**

#### Issue PL-01: `applyFill` recomputes realized P&L across ALL positions every time

```java
realizedPnlPaisa = positions.values().stream()
        .mapToLong(Position::realizedPnlPaisa)
        .sum();
```

On every fill, the realized P&L for all positions is recomputed by summing. With hundreds of positions and thousands of fills, this is O(n) per fill. The P&L could be maintained incrementally.

#### Issue PL-02: `markToMarket` recomputes unrealized P&L across ALL positions

Same issue — O(n) per tick for MTM.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| ME-01 | 🟡 Medium | No slippage model — optimistic backtest results |
| ME-02 | 🟡 Medium | No partial fills or queue position |
| PL-01 | 🟢 Low | P&L recomputed across all positions on every fill |

---

## 10. trade-gateway — WebSocket Bridge

### `GatewayWebSocketHandler` — Binary WebSocket

**Strengths:**
- Single-byte topic subscription (wire-efficient for mobile clients)
- Binary frame protocol with control frames
- Clean session lifecycle management

**Issues:**

#### Issue GW-01: `handleBinaryMessage` casts ByteBuffer directly

```java
ByteBuffer buf = (ByteBuffer) message.getPayload();
```

The `BinaryMessage.getPayload()` returns a `ByteBuffer`, but in Spring WebSocket, it's typically a `ByteBuffer` already. The explicit cast is unnecessary and may fail if the implementation changes.

### `GatewayEventBridge` — Domain event to WebSocket

```java
void onDomainEvent(DomainEvent event) {
    switch (event) {
        case TickReceived tick -> router.publish(GatewayTopic.MARKET_TICK, writeJson(tickPayload(tick)));
        case DepthUpdateEvent depth -> router.publish(GatewayTopic.MARKET_DEPTH, writeJson(depthPayload(depth)));
        // ... 12+ event types
    }
}
```

**Issues:**

#### Issue GB-01: All events are serialized to JSON and published to the WebSocket

This means every tick, every candle developing (which fires on every tick per interval), every signal — all are serialized and pushed to connected clients. Without client-side filtering, a client subscribing to `MARKET_TICK` receives ticks for ALL symbols, not just the ones it cares about.

The `GatewayTopicRouter` should support per-symbol subscriptions, but looking at the code:
```java
router.publish(GatewayTopic.MARKET_TICK, writeJson(tickPayload(tick)));
```

There's no symbol-level filtering. The router likely broadcasts to all sessions subscribed to the topic.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| GW-01 | 🟢 Low | Unnecessary ByteBuffer cast |
| GB-01 | 🟡 Medium | WebSocket bridge doesn't filter by symbol — all clients get all ticks |

---

## 11. trade-feature-store — Feature Computation

### `DuckDbFeatureStore` — Persistent feature store

```java
public void feed(DomainEvent event) {
    ensureConnection();
    switch (event) {
        case TickReceived tick -> insertTick(tick);
        case CandleDeveloping dev -> upsertCandle(dev.candle(), false);
        case CandleClosed closed -> upsertCandle(closed.candle(), true);
        default -> { }
    }
}
```

**Issues:**

#### Issue FS-01: `ensureConnection()` is called on EVERY event

```java
private synchronized void ensureConnection() throws SQLException {
    if (connection == null || connection.isClosed() || !connection.isValid(2)) {
        // ... reconnect
    }
}
```

`connection.isValid(2)` sends a network round-trip to DuckDB (even for embedded mode, this is a JDBC call). Calling this on every event (potentially thousands per second) is expensive.

**Fix:** Only validate the connection periodically (e.g., every 1000 events) or rely on the SQLException from the actual operation to trigger reconnection.

#### Issue FS-02: `insertTick` is called synchronously on the Disruptor pipeline

The `FeatureSyncDisruptorHandler` calls `featureStore.feed(event)` synchronously on the Disruptor consumer thread. If DuckDB write is slow (e.g., flushing to disk), it blocks the entire pipeline.

Wait — looking at the pipeline order:

```
risk → candle → feature-sync → strategy → execution → async-dispatch
```

The `FeatureSyncDisruptorHandler` is between candle aggregation and strategy execution. The `DuckDbFeatureStore` writes to DuckDB on every tick and candle. But DuckDB is the **async dispatch** path (duckDbFeatureStore subscribes to `DomainEvent.class` via `eventBus.subscribe` in `StartupConfiguration`).

But wait — the `FeatureSyncDisruptorHandler` uses `hotPathFeatureStore` which is the **InMemoryFeatureStore**, not the DuckDb one:

```java
// EventBusConfiguration.java
FeatureStore hotPathFeatureStore = hotPathFeatureStoreBean; // InMemoryFeatureStore

// StartupConfiguration.java
eventBus.subscribe(DomainEvent.class, duckDbFeatureStore::feed); // DuckDbFeatureStore
```

So the hot path uses `InMemoryFeatureStore` (fast, in-memory), while `DuckDbFeatureStore` is subscribed via the `EventBus.subscribe` path which goes through `AsyncDispatchHandler` (off the Disruptor thread). This is correct.

### `InMemoryFeatureStore` — Hot-path feature store

**Strengths:**
- `ConcurrentHashMap` with binary search insertion for sorted candle lists
- Bounded to 512 candles per symbol/interval
- Properly returns chronological order for feature computation

**Issues:**

#### Issue IF-01: No TTL eviction — only size-based eviction

When the list exceeds 512 candles, the oldest candles are trimmed. But candles persist in memory even if the symbol is no longer traded. For 500 symbols × 5 intervals × 512 candles = 1,280,000 `Candle` objects. Each `Candle` record has 11 fields — at ~200 bytes each, that's ~256 MB for candles alone. This is acceptable but worth monitoring.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| FS-01 | 🟡 Medium | `ensureConnection()` called on every event — JDBC round-trip |
| FS-02 | 🟢 Low | `InMemoryFeatureStore` correctly used for hot path, DuckDB for async dispatch |

---

## 12. trade-broker-dhan — Dhan Broker Adapter

### `DhanBrokerConnection` — Façade with 15+ injected adapters

The `DhanBrokerConnection` implements `IBrokerConnection` and provides access to 15 broker service providers. It has two construction paths:
- **Legacy constructor** (deprecated): Creates all adapters inline — 80+ lines of wiring with tight coupling
- **Multi-adapter constructor**: Accepts pre-built adapters via DI — clean, testable, the intended Spring path

**Strengths:**
- Comprehensive broker coverage: market data, order commands, order queries, portfolio, margin, options, futures, bracket orders, GTT, conditional alerts, session risk, slices, WebSocket
- `DhanBrokerStartup` provides `defaultCapabilities()` and `loadDailyInstrumentCatalog()` — sensible defaults for the broker profile
- `@Deprecated` annotations on legacy paths guide migration toward DI

**Issues:**

#### Issue DB-01: 15 constructor parameters — extremely high coupling

```java
public DhanBrokerConnection(
    DhanClientHolder, DhanInstrumentResolver, MarketDataProvider,
    FuturesProvider, OptionsProvider, OrderCommand, OrderQuery,
    SliceOrderCommand, BracketOrderProvider, GttOrderProvider,
    PortfolioProvider, MarginProvider, SessionRiskProvider,
    ConditionalAlertProvider, WebSocketMultiplexer
)
```

15 parameters violates the single-responsibility principle. The class is a pure façade (delegates every method to the adapter), but the constructor must know about every adapter type. A builder pattern or `DhanBrokerConfig` record would reduce this to 1–2 parameters.

#### Issue DB-02: `loadInstrumentCatalog` has no error recovery

```java
public void loadInstrumentCatalog(Path catalogPath) {
    instrumentResolver.loadCatalog(catalogPath);
}
```

If the catalog path is invalid or the file is corrupt, the exception propagates unhandled. The `UpstoxBrokerConnection` wraps this in a `try-catch` with `IOException` — Dhan should do the same.

---

### `DhanWebSocketMultiplexer` — Dhan SDK WebSocket management

**Lines reviewed:** ~390 lines across `DhanWebSocketMultiplexer.java`

This is the most complex single file in the project — it manages two WebSocket connections (market feed + order stream), reconnection with exponential backoff, token rotation, stale feed detection, and payload normalization.

**Strengths:**
- Dual WebSocket management (market feed + order stream) with independent lifecycle tracking
- `ConcurrentHashMap` + `CopyOnWriteArrayList` for subscriptions and listeners — thread-safe for multi-producer
- Token rotation listener (`clientHolder.addRotationListener(this::rebindAfterTokenRotation)`) — correctly re-creates clients on token change
- Scheduled health monitor every 30s with stale feed detection (no market payload threshold)
- Reconnect circuit breaker with backoff: 3 failures → circuit open for 30s → stop hammering
- `scheduleResubscribe()` runs on dedicated thread to avoid deadlock when SDK calls `onConnected()` synchronously while `transportLock` is held
- Explicit `INVALID_TOKEN_CODE` detection with `BrokerAdapterError` events

**Issues:**

#### Issue DW-01: `handleMarketPayload` allocates a SECOND `TickReceived` record when depth is absent

```java
TickReceived tick = includeDepth
    ? normalized
    : new TickReceived(
        normalized.metadata(),
        normalized.symbol(),
        normalized.interval(),
        normalized.ltpPaisa(),
        normalized.lastTradeQuantity(),
        normalized.cumulativeVolume(),
        normalized.exchangeTimestampMs(),
        null  // depth explicit null
    );
```

When depth is NOT included, `normalized` (the first `TickReceived`) is thrown away and a new copy is created with `null` depth. This doubles allocation for ticker-only symbols (~50% of ticks depending on subscription mode). 

**Fix:** Make `DhanPayloadNormalizer.normalizeTick` accept a `boolean includeDepth` parameter instead of post-processing.

#### Issue DW-02: `Object` parameter in `handleMarketPayload` — no type safety

```java
private void handleMarketPayload(Object source, boolean includeDepth)
```

The method accepts `Object` and wraps it in `DhanSdkResponse<?>`. The four SDK types (`TickerData`, `QuoteData`, `FullData`, `IndexData`) are all different classes but share no common interface. The `DhanSdkResponse` wrapper handles this, but the `Object` cast is a code smell — a sealed interface or union type would be cleaner.

#### Issue DW-03: Order stream handling doesn't distinguish `OrderAccepted` vs `OrderFilled` correctly

The `onOrderUpdate` handler:
```java
if (order.status().isRejected() && previousStatus != OrderStatus.REJECTED) {
    publishOrder(new OrderRejected(...));
} else if (previousStatus == null) {
    publishOrder(new OrderAccepted(...));
}
```

This logic:
- If status is REJECTED and wasn't REJECTED before → emit `OrderRejected`
- Else if no previous status seen → emit `OrderAccepted`
- **Otherwise → nothing**

The `else` branch means if an order transitions from SUBMITTED → PARTIALLY_FILLED via an order update, no event is emitted. The `OrderPartiallyFilled` event is never published from the WebSocket path. Only `TradeUpdate` (which calls `onTradeUpdate`) generates `OrderFilled`. This means partial fills arriving as `OrderUpdate` (not `TradeUpdate`) are silently dropped.

**Severity:** 🟡 Medium — Partial fill events from the order stream are lost.

#### Issue DW-04: `verifyFeedLiveness` runs even when `connected=false`

```java
if (!connected) { return; }
```

This check is after the token refresh check. If the WebSocket is disconnected (e.g., market hours close), the token is still checked every 30 seconds even though no feed is active. The `clientHolder.client()` call can trigger a token rotation attempt. After market close, this is unnecessary WASTE.

**Fix:** Return early before `verifyFeedLiveness` if `!connected`, or add a market-hours check.

#### Issue DW-05: `orderStreamClient` has no stale-feed monitoring

The health monitor (`verifyFeedLiveness`) only checks `marketFeedClient` for staleness. If `orderStreamClient` disconnects silently, there is no detection mechanism. Order updates are critical for fill reconciliation.

---

### `DhanRestOrderClient` — REST HTTP client for orders

**Strengths:**
- `DhanResilienceExecutor` wraps every call — circuit breaker + rate limiting at the HTTP layer
- Comprehensive order types: place, modify, cancel, cancel-all, slice, super, forever, and square-off
- `ObjectNode` payload construction with fallback defaults for missing fields

**Issues:**

#### Issue DR-01: `baseOrderPayload` always uses `settings.clientId()` — not thread-safe rotation

```java
payload.put("dhanClientId", settings.clientId());
```

If the client ID rotates (via `DhanClientHolder` rotation), the `settings.clientId()` may return a stale value because `settings` is injected at construction time. The `DhanWebSocketMultiplexer` handles rotation via `rebindAfterTokenRotation`, but the REST client doesn't refresh its settings reference.

#### Issue DR-02: `cancelAndSquareOffIntradayPositions` is a no-op for the cancel-all logic

```java
public List<String> cancelAndSquareOffIntradayPositions() {
    return cancelAllOpenOrders();
}
```

Per `TRADEHULL_PARITY.md`: "Sandbox: does **not** flatten positions — only calls DELETE /orders (cancel all)." But this is the sandbox path. The method name is misleading — it does NOT square off. The live path (SDK `cancelAndSquareOffIntradayPositions`) would be different.

#### Issue DR-03: `getOrder` and `getOrders` have no timeout

The `resilienceExecutor.execute()` call may hang indefinitely if the broker endpoint is unresponsive. The executor should have a configurable timeout per API category.

---

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| DB-01 | 🟢 Low | 15 constructor parameters in DhanBrokerConnection |
| DB-02 | 🟢 Low | loadInstrumentCatalog has no error recovery |
| DW-01 | 🟡 Medium | Double TickReceived allocation when depth absent |
| DW-02 | 🟢 Low | Object parameter in handleMarketPayload — type safety |
| DW-03 | 🟡 Medium | Order stream drops OrderPartiallyFilled events |
| DW-04 | 🟢 Low | Token checked every 30s even when disconnected |
| DW-05 | 🟢 Low | Order stream not monitored for staleness |
| DR-01 | 🟢 Low | REST client doesn't refresh settings on token rotation |
| DR-02 | 🟢 Low | cancelAndSquareOffIntradayPositions doesn't square off |

## 13. trade-broker-upstox — Upstox Broker Adapter

### `UpstoxBrokerConnection` — Upstox implementation

**Lines reviewed:** `UpstoxBrokerConnection.java` (~70 lines), `UpstoxUnsupportedPorts.java`

**Strengths:**
- Clean separation: the connection class has only 7 adapter parameters (vs Dhan's 15) — better DI hygiene
- `Objects.requireNonNull()` on every parameter — fail-fast on misconfiguration
- `UpstoxUnsupportedPorts` is an elegant pattern: static sentinel objects for unsupported features (slice orders, bracket, GTT, session risk, alerts). Each sentinel throws `UnsupportedOperationException` with a descriptive message.

**Issues:**

#### Issue UB-01: Unsupported port sentinels are never tested

`UpstoxUnsupportedPorts.SLICE_ORDERS` returns a static object that throws `UnsupportedOperationException`. But if any code path calls `sliceOrders()` without checking `BrokerCapabilities`, it gets a runtime exception. There's no compile-time safety for Upstox-unsupported features.

**Fix:** Add a `BrokerCapabilities.supportsSliceOrders()` check before calling, gated by the runtime broker profile.

#### Issue UB-02: `loadInstrumentCatalog` has no fallback

```java
public void loadInstrumentCatalog(Path catalogPath) {
    try {
        if (Files.isDirectory(catalogPath)) {
            Path cached = catalogPath.resolve("complete.json.gz");
            if (Files.exists(cached)) {
                instrumentLoader.loadFromPath(cached, upstoxInstrumentResolver);
                return;
            }
            instrumentLoader.downloadAndLoad(catalogPath, upstoxInstrumentResolver);
            return;
        }
        instrumentLoader.loadFromPath(catalogPath, upstoxInstrumentResolver);
    } catch (IOException ex) {
        throw new IllegalStateException("...", ex);
    }
}
```

If `downloadAndLoad` fails (network issue), the exception is wrapped and re-thrown. There's no fallback to a cached file. The Dhan adapter has the same issue.

#### Issue UB-03: Cast from `InstrumentResolver` to `UpstoxInstrumentResolver`

```java
this.upstoxInstrumentResolver = (UpstoxInstrumentResolver) instrumentResolver;
```

The constructor casts the abstract `InstrumentResolver` interface to a concrete type. If the injected resolver is not an `UpstoxInstrumentResolver`, this fails at runtime with `ClassCastException`. This defeats the purpose of the interface abstraction.

**Fix:** Declare the parameter as `UpstoxInstrumentResolver` directly, or add a check in `loadInstrumentCatalog`.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| UB-01 | 🟢 Low | Unsupported port sentinels not tested — runtime surprises |
| UB-02 | 🟢 Low | loadInstrumentCatalog has no fallback on network failure |
| UB-03 | 🟡 Medium | Constructor casts InstrumentResolver to concrete type |

## 14. trade-app — Spring Wiring & Configuration

### `EventBusConfiguration` — Pipeline assembly

```java
@Bean
CandleAggregationService candleAggregationService() {
    return new CandleAggregationService(List.of("1s", "5m"));
}
```

**Issues:**

#### Issue SP-01: Candle intervals hardcoded

The intervals `["1s", "5m"]` are hardcoded. Should be configurable via `TradingProperties`.

#### Issue SP-02: Pipeline config creates beans in dependency order

```java
@Bean
PipelineConfig.PipelineComponents pipelineComponents(
        TradingProperties properties,
        PositionRiskHandler positionRiskHandler,
        CandleAggregationService candleAggregationService,
        StrategyEngine strategyEngine,
        ExecutionHandler executionHandler,
        PortfolioEngine portfolioEngine,
        StageTimings stageTimings,
        InMemoryFeatureStore hotPathFeatureStore,
        DeadLetterQueue deadLetterQueue
) { ... }
```

11 dependencies injected as constructor parameters — this is a lot but manageable. The method assembles the entire pipeline in one place, which is good for clarity.

### `StartupConfiguration` — Application startup

**Issues:**

#### Issue ST-01: `ApplicationRunner` does everything inline — 60+ lines

The `runtimeStarter` method is a massive lambda that:
1. Loads catalog
2. Validates subscriptions
3. Verifies broker preflight
4. Subscribes to event bus
5. Subscribes to WS market data
6. Subscribes to WS order updates
7. Starts event bus
8. Connects broker
9. Manages runtime subscriptions

This is hard to test, hard to follow, and hard to modify. Each step should be a separate method or a dedicated startup component.

#### Issue ST-02: `eventBus.subscribe(DomainEvent.class, ...)` catches ALL events

```java
eventBus.subscribe(DomainEvent.class, duckDbFeatureStore::feed);
eventBus.subscribe(DomainEvent.class, chronicleAuditLogWriter::onEvent);
eventBus.subscribe(DomainEvent.class, duckDbEventStore::onEvent);
eventBus.subscribe(DomainEvent.class, brokerErrorTracker);
eventBus.subscribe(DomainEvent.class, readModelStore::onDomainEvent);
eventBus.subscribe(DomainEvent.class, netPositionProvider::onDomainEvent);
```

Subscribing to `DomainEvent.class` means `AsyncDispatchHandler` will call each of these for **every event type**, and the handler itself must filter. The `isAssignableFrom` check in `AsyncDispatchHandler` will match `DomainEvent.class` for all events.

The `netPositionProvider::onDomainEvent` handler only processes `TradeOpened` and `TradeClosed` — but it's called for every tick, every candle, every signal, etc. The `switch` expression in `EventSourcedNetPositionProvider.onDomainEvent` has a `default -> { }` case, but the method is still invoked.

**Severity:** 🟡 Medium — Performance concern at high tick rates.

### `AdminController` — Management endpoints

**Issues:**

#### Issue AD-01: `rejectIfLiveReplay()` returns `ResponseEntity<?>` with inconsistent null handling

```java
private ResponseEntity<Map<String, Object>> rejectIfLiveReplay() {
    if (runtimeModeHolder.mode() == RuntimeMode.LIVE) {
        return ResponseEntity.status(409).body(Map.of("error", "..."));
    }
    return null;
}
```

Every caller checks `if (rejected != null) return rejected;`. This is a convention rather than a type-safe pattern. A `Result<ResponseEntity<?>, ResponseEntity<?>>` or `Optional<ResponseEntity<?>>` would be cleaner.

#### Issue AD-02: Replay endpoints use `eventBus.publish()` directly

```java
var result = historicalRangeService.replayTicks(symbol, from, to, eventBus);
```

The `ReplayRunner` and `HistoricalRangeService` publish events directly to the event bus, which means they flow through the Disruptor pipeline. For historical replay, this is correct — it tests the same pipeline. But the replay doesn't reset state (e.g., existing candles, open trades) before replaying. Replaying ticks for a symbol while the live market is still streaming ticks for other symbols will cause state corruption.

### Key findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| SP-01 | 🟢 Low | Candle intervals hardcoded |
| ST-01 | 🟡 Medium | ApplicationRunner lambda is too large — 60+ lines |
| ST-02 | 🟡 Medium | Subscribing all handlers to `DomainEvent.class` causes unnecessary dispatch overhead |
| AD-01 | 🟢 Low | Null-return pattern for rejection check |
| AD-02 | 🟡 Medium | Replay doesn't reset state — mixed live/replay state corruption risk |

---

## 15. Cross-Cutting Concerns

### 15.1 Thread Safety Summary

| Component | Threading Model | Safety Assessment |
|-----------|----------------|-------------------|
| `DisruptorEventBus` | Multi-producer ring buffer + drainer thread | ✅ Correct (BusySpin, MULTI, downstreamQueue) |
| `ShardedDisruptorEventBus` | Per-shard ring buffer | ✅ Correct (deterministic routing) |
| `MarketDataPipeline` | Multi-threaded (WS threads) | ✅ Correct (AtomicLong, AtomicReference, CAS) |
| `OrderPipeline` | Multi-threaded | ✅ Correct (same pattern as MarketDataPipeline) |
| `ExecutionHandler` | Single-threaded executor | ✅ Correct (sequential processing) |
| `TradingCircuitBreaker` | `synchronized` method | ⚠️ Single monitor contention bottleneck |
| `PositionRiskHandler` | `ConcurrentHashMap` | ✅ Correct (concurrent data structures) |
| `OrderIdentityRegistry` | 4x `ConcurrentHashMap` | ✅ Correct |
| `CandleAggregationService` | `ConcurrentHashMap.compute()` | ✅ Correct (atomic per-key) |
| `PortfolioEngine` | `ConcurrentHashMap.compute()` | ✅ Correct |
| `EventSourcedNetPositionProvider` | `ConcurrentHashMap.merge()` | ✅ Correct |

### 15.2 GC Pressure Analysis

| Hot Allocation | Frequency | Impact |
|----------------|-----------|--------|
| `TickReceived` record | Per tick (1k+/sec) | 🟡 Medium — Pre-allocated via ring buffer reduces pressure |
| `CandleDeveloping` record | Per tick × intervals (5k+/sec) | 🟡 Medium — Each tick creates developing candles |
| `EventMetadata` record | Per event | 🟢 Low — Small record, fast allocation |
| `DepthUpdateEvent` | Per tick (when depth present) | 🟢 Low — Most ticks don't have depth |
| `SignalGenerated` record | Per strategy signal (10s/sec) | 🟢 Low |
| Lambda objects for `currentCandles.compute()` | Per tick × intervals | 🟡 Medium — Could be reduced with method references |

### 15.3 MDC Context Usage

`MdcHelper.enrich()` and `MdcHelper.clear()` are used consistently throughout the codebase. The `try { enrich(); ... } finally { clear(); }` pattern is correct.

**Issues:**
- `scheduleFillRetry` in `ExecutionHandler` runs on a different thread (`fillDeferExecutor`) without MDC context — the initial call has context, but the scheduled lambda doesn't inherit it.

### 15.4 Error Handling Patterns

| Pattern | Used In | Assessment |
|---------|---------|------------|
| `catch (Exception)` | `ScanEngine.run`, `GatewayEventBridge`, `HistoricalRangeService.replay*` | ⚠️ Too broad — swallows errors |
| WARN-level logging + proceed | `DuckDbFeatureStore.feed`, `HistoricalRangeService.query*`, `ExecutionHandler.processFill` | ✅ Good for non-fatal errors |
| `DeadLetterQueue.append()` | `DisruptorEventBus`, `ExecutionHandler`, `AsyncDispatchHandler` | ✅ Excellent — preserves failed events |
| WARN + return null/skip | `MarketDataPipeline.onTickReceived` (null check) | ✅ Good |

### 15.5 Key Cross-Cutting Findings

| Symbol | Severity | Finding |
|--------|----------|---------|
| GC-01 | 🟡 Medium | 5k+ `CandleDeveloping` allocations per second at 1k ticks × 5 intervals |
| MDC-01 | 🟢 Low | Scheduled retry doesn't inherit MDC context |
| ERR-01 | 🟢 Low | Some `catch (Exception)` blocks are too broad |

---

## 16. Architectural Feasibility Assessment

### 16.1 Reactive Event-Driven Architecture: Feasibility Verdict

**Verdict: FEASIBLE — existing foundation is ~80% aligned with the target reactive architecture.**

The current codebase already implements a reactive event-driven pipeline through the Disruptor backbone. The gap between the current state and the fully reactive target is incremental, not architectural.

| Requirement | Current State | Gap | Feasibility |
|-------------|--------------|-----|-------------|
| Live tick stream pipeline | ✅ Through Disruptor + trades-hotpath | — | ✅ Existing |
| Candle stream generation | ✅ `CandleAggregationService` with multiple intervals | Minor — bucket computation per tick is wasteful | ✅ Easy fix |
| Market depth stream | ✅ `DepthUpdateEvent` synthesised from ticks | Not consumed by strategies | ✅ Easy wire |
| Option chain streams | ⚠️ Batch fetch via REST | No reactive streaming | 🟡 Medium — Dhan SDK doesn't support WS option chain |
| Open interest streams | ⚠️ Available via Dhan WS but not wired | Not subscribed | ✅ Easy wire |
| Greeks streams | ❌ Not implemented | Requires option chain + Greeks data | ⚠️ Medium — broker-dependent |
| Order/position updates | ✅ Via WS order stream + TradeOpened/Closed events | — | ✅ Existing |
| Signal generation | ✅ Via StrategySandbox | Only candle-based triggers | ✅ Existing |
| Risk qualification | ✅ PositionRiskHandler portfolio checks | — | ✅ Existing |
| OMS execution | ✅ ExecutionHandler with OSM | Queue capacity concern | ✅ Existing |
| Position management | ✅ EventSourcedNetPositionProvider | **N-01 multi-trade bug** | 🟠 Must fix first |
| Replay/backtest parity | ✅ Same Disruptor pipeline | Mixed state corruption | 🟡 Need isolation |
| Per-symbol isolation | ✅ `ShardedDisruptorEventBus` | — | ✅ Existing |
| Deterministic execution | ✅ Event sourcing throughout | — | ✅ Existing |

### 16.2 Reactive Pipeline Architecture (Current vs Target)

```
Current flow:
Dhan WS → {@code handleMarketPayload()} → eventBus.publish() → Ring Buffer → risk → candle → strategy → execution → async-dispatch
                                                                                                                    ↓
                                                                                                            Gateway WS → Frontend

Target flow (minimal changes):
Dhan WS → MarketDataPipeline → ShardedDisruptorEventBus → per-symbol ring buffer
                                                                 ↓
                                              ┌────────────────────┼────────────\
                                              ↓                    ↓            ↓
                                         Candle Engine      Scanner Engine   Indicator Engine
                                              ↓                    ↓            ↓
                                         Signal Engine ←──────────┘            |
                                              ↓                                 |
                                            Risk → OMS → Position Streams       |
                                                                 ↓             |
                                                          Trade Management ←────┘
```

The `ShardedDisruptorEventBus` is already designed for per-symbol isolation. The `SymbolShardRouter` provides deterministic routing. What's missing:

1. **Scanner pipelines** are batch-only (`ScanEngine.run()`) — need reactive per-tick evaluation
2. **Indicator/feature engines** are stateless (InMemoryFeatureStore stores pre-computed candles, but no indicator computation exists in the pipeline)
3. **Depth-based signals** (depth imbalance, absorption) have no consumer yet

### 16.3 LMAX Disruptor vs Reactor/RxJava Assessment

| Aspect | Disruptor (Current) | Reactor/RxJava | Verdict |
|--------|---------------------|----------------|---------|
| Latency | Sub-microsecond (BusySpin) | Microsecond (event loop) | ✅ Disruptor wins for hot path |
| GC pressure | Object pooling, pre-allocation | Heavy allocation per event | ✅ Disruptor wins |
| Backpressure | Bounded ring buffer | Operators (onBackpressureBuffer/Drop) | ✅ Both |
| Operator chaining | Manual pipeline stages | Rich operator set | ✅ Reactor wins for complex flows |
| Threading model | Explicit control | Scheduler abstraction | ✅ Disruptor for hot path, Reactor for IO |
| Error handling | Manual try-catch + DLQ | onErrorResume, retry | ✅ Reactor wins |

**Recommendation:** Keep Disruptor for the hot path (market data → candle → feature) and use Reactor for IO-bound paths (broker REST calls, persistence). **Do not replace Disruptor** — the existing architecture correctly uses Disruptor where it matters most.

### 16.4 Latency Budget Analysis

| Pipeline Stage | Current Latency (est.) | Target Latency | Notes |
|----------------|----------------------|----------------|-------|
| WS tick → bus publish | 10–50μs | <50μs | ✅ On track |
| Ring buffer claim | ~50ns | <100ns | ✅ BusySpin |
| Risk handler | 5–20μs | <20μs | ConcurrentHashMap lookup |
| Candle aggregation | 5–50μs | <20μs | Per-lambda allocation (CA-01) |
| Strategy dispatch | 500μs–5ms | <1ms | Virtual thread start + timeout |
| Execution queue | 10μs–1s (if blocked) | <100μs | Queue capacity 50 (E-01) |
| Async dispatch (persist) | 100μs–10ms | <10ms | 100ms poll (A-01) |
| Gateway WS publish | 10–100μs | <50μs | Full serialization per event |

**End-to-end latency** (tick → signal emitted): ~500μs–10ms, bottlenecked by strategy virtual thread startup.

### 16.5 Scalability Estimates

| Metric | Current | Target |
|--------|---------|--------|
| Symbols | 500 (tested), limited by WS | 1500+ (bottleneck: Dhan WS connection) |
| Tick rate | ~1000/sec (est.) | 5000/sec (sustainable with sharding) |
| Strategies | ~5–10 | 50+ (virtual thread per plugin) |
| Scanner intervals | Every 15 min (cron) | Every tick (reactive) |
| Open positions | ~100 | 500+ (portfolio engine limits) |
| Ring buffer utilization | <10% at 1000 ticks/sec (8192 slots) | <50% at 5000 ticks/sec |
| Memory (candles) | ~256 MB (500 sym × 5 intervals × 512 candles) | ~800 MB at 1500 symbols |
| Thread count | ~20 (WS threads + Disruptor + drainer + executors) | ~30 (with sharding) |

### 16.6 Key Architectural Risks

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Re-entrant ring buffer deadlock | Low | Critical | ✅ Already fixed with downstreamQueue |
| Disruptor consumer thread blocking on JDBC | Low | Critical | ✅ FeatureStore is async (AsyncDispatchHandler) |
| MDC leak across events | Medium | Medium | ✅ try/finally pattern used |
| Mixed live/replay state corruption | Medium | High | ⚠️ No isolation mechanism (AD-02) |
| WS feed disconnection during market hours | High | High | ✅ Reconnect with backoff, stale detection |
| Entropy exhaustion on UUID generation | Low | Medium | ⚠️ UUID.randomUUID() for order IDs (E-04) |
| GC pause > 1ms under load | Medium | Medium | ⚠️ 5k+ candle allocations per second (GC-01) |

---

## 17. Recommendations & Roadmap

### 17.1 Phase 0 — Critical Fixes (1–2 weeks)

| Priority | Finding | Action | Effort |
|----------|---------|--------|--------|
| 🔴 P0 | N-01: `EventSourcedNetPositionProvider` removes symbol on close | Change `remove()` to subtract delta | 1 hour |
| 🔴 P0 | E-01: Execution queue capacity 50 | Increase to 1000, make configurable | 30 min |
| 🟡 P1 | C-03: Dedup cache clears all entries | Replace with LRU/Caffeine cache | 2 hours |
| 🟡 P1 | C-05: Stop sequence can lose events | Reorder shutdown: stop disruptor first, then drainer | 1 hour |
| 🟡 P1 | O-01: Dead code in OrderPipeline | Remove `onSignalGenerated`, `onSignalPendingExecution` | 30 min |
| 🟡 P1 | ST-02: All handlers subscribe to `DomainEvent.class` | Subscribe to specific event types | 2 hours |
| 🟡 P1 | AD-02: Replay doesn't reset state | Add `RuntimeMode` gating + state snapshot/restore | 4 hours |
| 🟡 P1 | A-01: 100ms poll timeout | Change to `take()` with poison pill for shutdown | 1 hour |

### 17.2 Phase 1 — Reactive Scanner Integration (2–3 weeks)

| Task | Description | Dependencies |
|------|-------------|--------------|
| Reactive scanner per symbol | Replace `ScanEngine.run()` with per-tick evaluation via `EventBus.subscribe(TickReceived.class, scanner)` | Phase 0 |
| Scanner criteria in pipeline | Implement `ScanCriterion` as a `ConcurrentHashMap`-backed state machine | — |
| Multi-symbol scan aggregation | Collect per-symbol results into ranked universe | — |
| Options chain reactive streaming | Subscribe to option chain updates via Dhan WS (not batch REST) | Dhan WS capability |

### 17.3 Phase 2 — Indicator & Feature Engine (3–4 weeks)

| Task | Description | Dependencies |
|------|-------------|--------------|
| `IndicatorEngine` Disruptor stage | New stage between candle and strategy, computes indicators per symbol | Phase 0 |
| Indicator library | VWAP, EMA, RSI, ADX, HalfTrend as `ConcurrentHashMap` per-symbol state | — |
| Feature stream for strategies | Emit `FeatureUpdated` events on indicator changes, visible to strategies | — |
| Strategy access to features | Extend `StrategyPlugin` with `onFeatureUpdated(FeatureUpdated)` | — |

### 17.4 Phase 3 — Depth-Based Strategies (2–3 weeks)

| Task | Description | Dependencies |
|------|-------------|--------------|
| Depth imbalance calculator | Subscribe to `DepthUpdateEvent`, compute bid/ask imbalance per symbol | Phase 0 |
| Absorption/spoofing detector | Window-based volume absorption analysis per symbol | — |
| Depth strategy plugin type | New `DepthStrategyPlugin` interface | Phase 2 |

### 17.5 Phase 4 — Simulation & Backtest Parity (3–4 weeks)

| Task | Description | Dependencies |
|------|-------------|--------------|
| Slippage model | Add volume-aware slippage to `MatchingEngine` | Phase 0 |
| Partial fills | Implement queue-based partial matching | — |
| Deterministic replay isolation | Snapshot/restore pattern for all stateful components | Phase 0 (AD-02) |
| P&L from replay | Wire `PnLLedger` into replay pipeline | — |
| Historical validation | Replay historical data, compare backtest vs live P&L | — |

### 17.6 Phase 5 — Production Hardening (ongoing)

| Task | Description |
|------|-------------|
| GC tuning | Reduce per-tick allocation: `CandleDeveloping` emission, lambda objects |
| Thread naming | Ensure all executor threads are named (many already are) |
| Metrics | Add Micrometer timers per Disruptor stage (already has `StageTimings` structure) |
| Health checks | Add WS staleness check for order stream (only market stream is monitored) |
| Graceful degradation | Prioritize symbols with open positions during tick rate limiting |

---

## 18. Findings Summary

### 🟠 High Severity (must fix before production)

| # | Module | Symbol | Finding |
|---|--------|--------|---------|
| 1 | trade-execution | N-01 | `EventSourcedNetPositionProvider` removes symbol on close — incorrect for multi-trade symbols |
| 2 | trade-execution | E-01 | Execution queue capacity of 50 is dangerously small |

### 🟡 Medium Severity (should fix)

| # | Module | Symbol | Finding |
|---|--------|--------|---------|
| 3 | trade-disruptor | C-03 | Dedup cache clears all entries at 200K — should use LRU eviction |
| 4 | trade-disruptor | C-05 | Stop sequence can lose downstream events |
| 5 | trade-disruptor | A-01 | 100ms poll timeout adds latency to subscriber dispatch |
| 6 | trade-hotpath | O-01 | `OrderPipeline.onSignalGenerated` is dead code |
| 7 | trade-execution | C-01 | `TradingCircuitBreaker` uses synchronized — lock-free CAS better |
| 8 | trade-execution | R-02 | Trailing stop/TP updates not tracked by `PositionRiskHandler` |
| 9 | trade-strategy | SS-01 | Only `CandleClosed` events sent to plugins — no tick/depth strategies |
| 10 | trade-strategy | SS-02 | Virtual threads not cancelled on timeout — resource leak |
| 11 | trade-strategy | PE-02 | Fragile correlationId coupling between risk/portfolio |
| 12 | trade-scanner | SC-01 | Scanner is batch-only — no reactive/incremental evaluation |
| 13 | trade-scanner | SC-02 | All quotes fetched upfront — doesn't scale for frequent scanning |
| 14 | trade-persistence | RP-01 | Chronicle Queue replay deserializes all events as same type |
| 15 | trade-simulation | ME-01 | No slippage model — optimistic backtest results |
| 16 | trade-simulation | ME-02 | No partial fills or queue position |
| 17 | trade-gateway | GB-01 | WebSocket bridge doesn't filter by symbol |
| 18 | trade-feature-store | FS-01 | `ensureConnection()` called on every event |
| 19 | trade-app | ST-01 | ApplicationRunner lambda is too large |
| 20 | trade-app | ST-02 | Subscribing to `DomainEvent.class` causes unnecessary dispatch overhead |
| 21 | trade-app | AD-02 | Replay doesn't reset state — mixed live/replay corruption risk |
| 22 | trade-hotpath | P-01 | `SymbolShardRouter` duplicates `ShardedDisruptorEventBus.shardIndexFor` |

### 🟢 Low Severity (nice to have)

| # | Module | Symbol | Finding |
|---|--------|--------|---------|
| 23 | trade-core | — | `timestampMonotonic()` never used |
| 24 | trade-core | — | `publishBatch` doesn't use ring buffer batching |
| 25 | trade-disruptor | C-01 | `isAssignableFrom` scan on every dispatch event |
| 26 | trade-disruptor | S-01 | Sharded bus broadcasts subscriptions to all shards |
| 27 | trade-disruptor | A-02 | Event loss risk during async dispatch shutdown |
| 28 | trade-hotpath | M-01 | `DepthUpdateEvent` allocation per tick only when depth present (not a concern) |
| 29 | trade-hotpath | O-02 | `OrderPipeline.onSignalPendingExecution` is dead code |
| 30 | trade-execution | E-03 | Integer division truncation in VWAP calculation |
| 31 | trade-execution | E-04 | `UUID.randomUUID()` for order IDs — entropy bottleneck |
| 32 | trade-execution | R-04 | `findBrokerQuantityForSymbolOnVenue` is dead code |
| 33 | trade-execution | I-01 | `OrderIdentityRegistry` has no eviction |
| 34 | trade-strategy | SS-03 | PortfolioEngine filter is pass-through — confusing naming |
| 35 | trade-strategy | CA-01 | Bucket truncation creates `Instant` per tick |
| 36 | trade-strategy | PE-03 | Net exposure check uses entry price |
| 37 | trade-persistence | RP-02 | ObjectMapper configured inline |
| 38 | trade-app | SP-01 | Candle intervals hardcoded |
| 39 | trade-app | AD-01 | Null-return pattern for rejection check |

---

## Appendix A: Dead Code Inventory

| File | Method/Field | Notes |
|------|-------------|-------|
| `OrderPipeline.java` | `onSignalGenerated()` | Never called; signals go through Disruptor pipeline |
| `OrderPipeline.java` | `onSignalPendingExecution()` | Never called; no code path invokes this |
| `OrderReconciler.java` | `findBrokerQuantityForSymbolOnVenue()` | Private method, never called |
| `SubscriberDispatchHandler.java` | Entire class | Superseded by `AsyncDispatchHandler`, never referenced |
| `TradeCli.java` | Various | Not reviewed in detail, but CLI commands may be stale |
| `PortfolioEngine.filterSignal()` | Method body | Pass-through only; all logic in `reserveSignal()` |

## Appendix B: Design Patterns Observed

| Pattern | Usage |
|---------|-------|
| Event Sourcing | `OrderStateMachine.replay()`, `EventSourcedOrderRepository` |
| State Machine | `OrderStateMachine` with lookup table |
| Sealed Interface | `OrderEvent` with 8 permitted subtypes |
| Circuit Breaker | `TradingCircuitBreaker` — CLOSED/OPEN/HALF_OPEN |
| Token Bucket | `TokenBucket` for tick/signal rate limiting |
| Busy Spin Wait | `BusySpinWaitStrategy` on ring buffer |
| Publisher-Subscriber | `EventBus.subscribe` / `publish` |
| Pipeline (Pipes & Filters) | Disruptor multi-stage pipeline |
| Data Sharding | `ShardedDisruptorEventBus` — symbol-based partitioning |
| Object Pooling | `MutableDomainEventEnvelope` pre-allocated by Disruptor |
| Factory | `PipelineConfig.create()`, `DepthUpdateFactory` |
| Adapter | `DhanBrokerConnection` → `IBrokerConnection` |
| Strategy Plugin | `StrategyPlugin` with `ServiceLoader` discovery |
| Virtual Threads | `StrategySandbox` uses virtual threads per plugin |
| Event Sourcing / CQRS | `EventSourcedNetPositionProvider` |
| MDC Diagnostic Context | `MdcHelper` throughout pipeline |
| Read Model | `ReadModelStore`, SSE endpoints |

---

*Review completed. See individual findings for severity and recommendations.*
