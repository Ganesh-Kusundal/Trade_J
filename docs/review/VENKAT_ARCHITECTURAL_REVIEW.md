# Trade-J Architectural Review

## Dr. Venkat Subramaniam Perspective

**Reviewer:** Dr. Venkat Subramaniam  
**Date:** 2026-06-14  
**System:** Trade-J — Event-Driven Quantitative Trading Platform  
**Scope:** Full platform, all modules, all runtimes, all event flows  
**Mandate:** Identify race conditions, concurrency bugs, architectural weaknesses, and simplification opportunities — without sacrificing performance.

---

## 1. SYSTEM INTENT

Trade-J is an event-driven quantitative trading platform that ingests market data from multiple Indian brokers (Dhan, Upstox, ICICI), processes it through a configurable pipeline graph (risk → candle → strategy → execution), manages orders and positions, persists everything to DuckDB/Chronicle, and serves a real-time web dashboard.

**Production reality:** The system has multiple runtime modes (LIVE, REPLAY, BACKTEST, PAPER), multiple composition paths (Spring Boot, FullComposition, CLI), multiple event bus implementations (Disruptor, Simple, Sharded, BrokerScoped), and multiple replay mechanisms. Not all of these are equally production-hardened. The Disruptor pipeline is the hot path. Everything else is support infrastructure.

---

## 2. THREAD OWNERSHIP MAP

Every component that creates or owns a thread:

### 2.1 Core Infrastructure Threads

| Thread | Owner | Purpose | What It Processes |
|--------|-------|---------|-------------------|
| `dedup-pruner` | `DisruptorEventBus` | Periodic eviction of stale dedup keys (every 1 min) | `seenEvents` ConcurrentHashMap |
| `downstream-publisher` | `DisruptorEventBus` | Drains `downstreamQueue` → re-publishes to ring buffer | CandleDeveloping/CandleClosed/SignalGenerated events from graph |
| Disruptor worker threads | LMAX Disruptor | Process ring buffer events through handler chain | Graph → Strategy → Dispatch |
| `dispatch-stage` | `AsyncDispatchHandler` | Dispatches events to subscribers | All events from pipeline output |
| `portfolio-engine` | `PortfolioEngine` | Processes portfolio commands (trade lifecycle) | TradeOpened/TradeClosed/OrderAccepted/OrderRejected |
| `execution-handler-N` (0-3) | `ExecutionHandler` | Processes signal/fill execution commands (partitioned) | SignalPendingExecution/OrderFilled/BrokerFill |
| `execution-fill-defer` | `ExecutionHandler` | Scheduled retry of deferred fills | Fill retry commands |
| Virtual threads | `GraphStrategySandbox` | Per-plugin async strategy evaluation (LIVE mode) | Domain events → strategy plugins |

### 2.2 Broker Threads (External/Daemon)

| Thread | Owner | Purpose |
|--------|-------|---------|
| Dhan WebSocket threads | `DhanWebSocketConnectionManager` | Market data + order stream I/O |
| Upstox WebSocket threads | `UpstoxWebSocketMultiplexer` | Multiplexed WebSocket I/O |
| ICICI WebSocket threads | `BreezeWebSocketMultiplexer` | Quote + order stream I/O |
| Broker reconnect threads | `ReconnectManager` (per broker) | Exponential backoff reconnection |
| Token refresh threads | `DhanTokenManager`/`UpstoxTokenManager`/`BreezeTokenManager` | Scheduled token refresh |

### 2.3 Consumer/Producer Analysis

```
Market Data (WebSocket thread)
  ↓ publishes
DisruptorEventBus ring buffer
  ↓ GraphPipelineDisruptorHandler (graph runtime thread)
  ↓ GraphStrategyDisruptorHandler (strategy sandbox thread)
  ↓ AsyncDispatchHandler (dispatch thread)
  ↓ Subscribers (multiple threads)
```

**Who owns what state:**
- `DisruptorEventBus.seenEvents` — owned by ring buffer + dedup-pruner + drainer threads
- `DisruptorEventBus.subscribers` — owned by publish + subscribe/unsubscribe threads
- `PortfolioEngine` maps — owned by portfolio-engine thread + any caller when `forceSync=true`
- `ExecutionHandler` queues — partitioned, one consumer thread per partition
- `PositionRiskHandler` state (AtomicLong/etc) — shared across any calling thread
- `CandleAggregationService.currentCandles` — ConcurrentHashMap, accessed from ring buffer thread (via graph) AND downstream queue drainer thread

**⚠️ Ownership violation:** `CandleAggregationService.currentCandles` is a `ConcurrentHashMap` accessed by both the ring buffer thread (during graph execution) AND the downstream queue drainer thread (when CandleDeveloping events are re-published). Two threads can call `currentCandles.compute()` concurrently for the SAME symbol → race condition on candle state.

---

## 3. RACE CONDITION REGISTER

### 3.1 CRITICAL — CandleAggregationService Shared State Race

**Location:** `CandleAggregationService.onTick()` → `currentCandles.compute(key, ...)`  
**Threads:** Ring buffer thread (from `GraphPipelineDisruptorHandler`) + downstream publisher drainer thread  
**Scenario:**
1. Ring buffer processes MarketTickEvent → CandleNode → `candleService.onDomainEvent(tick)` → `currentCandles.compute(symbol, fn)` → emits CandleDeveloping to downstream queue
2. Drainer picks up CandleDeveloping → re-publishes to ring buffer → GraphPipelineDisruptorHandler → CandleNode → `candleService.onDomainEvent(candleDeveloping)` → `currentCandles.compute(same symbol, fn)`
3. TWO concurrent `compute` calls on the SAME `ConcurrentHashMap` key → `ConcurrentHashMap.compute` is atomic per key, BUT the bucket rollover check `current.endTimeMs() != bEnd` could see stale state if the drainer's event is processed before or after the next tick

**Impact:** Duplicate or dropped CandleClosed events → incorrect candles → incorrect signals → bad trades  
**Detection:** Silent — no log warning  
**Fix:** `CandleAggregationService` should NOT be called from multiple threads. The downstream queue re-publish loop needs a re-entrancy guard OR the candle service should use a single-threaded executor.

### 3.2 CRITICAL — PositionRiskHandler ThreadLocal + Shared State

**Location:** `PositionRiskHandler.onDomainEvent()` → `currentPublisher.set(publisher)` ThreadLocal  
**Scenario:** The `currentPublisher` ThreadLocal is set in `onDomainEvent()` and cleared in a finally block. If `onDomainEvent` is called re-entrantly (subscriber calls back into risk handler), the inner call OVERWRITES the outer publisher. The finally block of the inner call then clears it, leaving the outer call with a null publisher.

**Impact:** `handleSignalPending()` will check `if (publisher != null)` → null → signal silently dropped  
**Detection:** Silent  
**Fix:** Replace ThreadLocal with an explicit publisher parameter passed through the visitor chain.

### 3.3 HIGH — ExecutionHandler Partition Reorder Risk

**Location:** `ExecutionHandler.visit(SignalPendingExecution)` → `partitionFor(symbol)` → enqueues to one of 4 queues  
**Scenario:** Two signals for the same symbol (e.g., SBIN) from different strategies. Both map to the same partition (symbol hash). Strategy A's signal is enqueued first but partition thread B processes its queue before partition thread A processes its. The signals are processed out of order relative to their generation time.

**Impact:** Order of signal processing is non-deterministic across runs. In REPLAY mode, signals may be processed in different order than in LIVE mode.  
**Detection:** Impossible from logs. Only visible in end-to-end parity tests.  
**Severity:** HIGH for replay determinism. The `TripleModePNLParityTest` does not validate execution order — it only validates signal generation.

### 3.4 HIGH — ShardedDisruptorEventBus Cross-Shard Ordering

**Location:** `ShardedDisruptorEventBus.publish()` → `shards.get(shardIndex(event)).publish(event)`  
**Scenario:** Two events for the same symbol (e.g., OrderAccepted followed by OrderPartiallyFilled) may hash to different shards if the dedupKey computation differs. OrderAccepted uses `ORDER:orderId:OrderAccepted` as key, OrderPartiallyFilled might not have a specific dedup key and falls back to `event.eventId()`.

**Impact:** OrderAccepted and OrderPartiallyFilled for the same order arrive on different shards → processed by different ring buffer threads → order lifecycle events can be reordered.  
**Detection:** Silent  
**Fix:** Shard routing must guarantee all events for the same order go to the same shard.

### 3.5 HIGH — PortfolioEngine Async Queue Non-Determinism

**Location:** `PortfolioEngine.onDomainEvent()` → `eventQueue.offer()` vs `processEvent()`  
**Scenario:** In LIVE mode (non-forceSync), `TradeOpened` events are enqueued to `ArrayBlockingQueue(1024)`. The portfolio-engine thread processes them asynchronously. If the queue is full, events are **dropped silently** (`offer()` returns false, incremented `droppedEventCount` but no other action).

**Impact:** Lost trade lifecycle events → incorrect position tracking → incorrect PnL → divergence between portfolio state and actual fills.  
**Detection:** `droppedEventCount()` metrics exist but no alert is wired.  
**Fix:** The `forceSync` flag (P0-7 fix) correctly bypasses the queue for REPLAY/BACKTEST. For LIVE, the queue should use `put()` (blocking) rather than `offer()` for critical events, or use a larger queue with backpressure monitoring.

### 3.6 MEDIUM — Broker Token Refresh Race

**Location:** `DhanTokenManager` + `BreezeTokenManager` — concurrent token refresh  
**Scenario:** Multiple API calls arrive simultaneously while a token refresh is in progress. Each call independently checks `isTokenExpired()` → true → each triggers a refresh → multiple concurrent refresh requests → race to update the stored token → one caller gets a stale/overwritten token.

**Impact:** API calls fail with authentication errors → orders not placed → fills missed  
**Detection:** "Token expired" errors in broker API responses  
**Fix:** Use a `CompletableFuture` that caches the in-flight refresh, so concurrent callers await the SAME refresh rather than triggering their own.

### 3.7 MEDIUM — Broker Reconnect + Subscription Race

**Location:** `DhanReconnectController.reconnect()` + `DhanWebSocketConnectionManager.connect()`  
**Scenario:** Reconnect is triggered (e.g., network flap). While reconnecting, the subscription manager also tries to resubscribe. These two paths race:
1. Reconnect creates new WebSocket → starts subscription reconciliation
2. Subscription manager independently calls `subscribe()` on the old/new socket

**Impact:** Duplicate subscriptions on the broker side → duplicate market data → duplicate events → 2x event rate → queue overflow → dropped events  
**Detection:** Elevated `dispatchDroppedEventCount` or downstream queue full warnings  
**Fix:** Reconnect must be atomic: close old socket → create new → THEN resubscribe. Subscription manager must be blocked during reconnect.

### 3.8 MEDIUM — GatewayEventBridge Thread Handoff

**Location:** `GatewayEventBridge` — WebSocket event → EventBus  
**Scenario:** Gateway receives events from WebSocket threads and publishes to `EventBus`. The `EventBus.publish()` call is made from the WebSocket thread. If the EventBus is `SimpleEventBus` (not Disruptor), the subscriber is invoked directly on the WebSocket I/O thread → blocking I/O in the event handler stalls the WebSocket connection.

**Impact:** WebSocket ping/pong delayed → connection timeout → reconnect storm  
**Detection:** WebSocket reconnects  
**Fix:** Always hand off from I/O threads to a dedicated processing thread or use the Disruptor bus for all production paths.

### 3.9 LOW — Subscription Lifecycle Race

**Location:** `RuntimeSubscriptionManager.subscribeStaticAtStartup()` vs `BrokerStartupOrchestrator.connectBrokerAndSubscribe()`  
**Scenario:** During startup, `subscribeStaticAtStartup()` and `connectBrokerAndSubscribe` both try to subscribe to market data feeds. The first call subscribes, the second call may subscribe the same symbols again (duplicate) or fail because the subscriptions already exist.

**Impact:** Duplicate subscriptions or startup failure  
**Detection:** Log warnings about duplicate subscriptions  
**Fix:** Single subscription path during startup — either subscription manager OR explicit subscribe, not both.

### 3.10 LOW — Snapshot/Restore Non-Atomicity

**Location:** `PortfolioEngine.snapshot()` + `restore()` — multiple ConcurrentHashMap copies  
**Scenario:** `snapshot()` copies 7 maps sequentially. `restore()` clears and re-populates 7 maps sequentially. Between copying map 3 and map 4 in `snapshot()`, another thread modifies map 5. The snapshot is internally inconsistent.

**Impact:** Isolated replay state may reference trades without corresponding capital reservations  
**Detection:** Silent in replay — incorrect PnL  
**Fix:** Single-threaded access during snapshot/restore (already handled in replay by `forceSync=true`, but the code allows concurrent modification)

---

## 4. EVENT ORDERING REPORT

### 4.1 Disruptor Pipeline Ordering

The `DisruptorEventBus` with a single ring buffer preserves event order within a single producer thread. When multiple producers publish concurrently (e.g., broker WebSocket threads + drainer thread), order between events from different producers is **not guaranteed** but this is acceptable for trading.

**Violation:** The downstream queue re-publish loop (drainer thread) re-publishes events back through the ring buffer. This creates a secondary event stream that interleaves with the primary event stream. For example:
1. `MarketTickEvent` enters ring buffer → processing emits `CandleDeveloping` to downstream queue
2. Drainer picks up `CandleDeveloping` → re-publishes to ring buffer
3. Between steps 1 and 3, another `MarketTickEvent` may enter the ring buffer
4. The `CandleDeveloping` (from tick 0) is processed AFTER `MarketTickEvent` (from tick 1) → chronological order violated

### 4.2 Sharded Event Bus Ordering

`ShardedDisruptorEventBus` uses `SymbolShardRouter.shardFor(event, shardCount)` to route events. Events for the SAME symbol are guaranteed to go to the same shard. Events for DIFFERENT symbols are processed in parallel — correct by design.

**Exception:** Events that don't have a resolvable symbol (e.g., global market status events) go to shard 0. This is fine.

### 4.3 BrokerScopedEventBus Ordering

`BrokerScopedEventBus` wraps a single `EventBus` (shared across all brokers). Events from Dhan, Upstox, and ICICI all go through the SAME EventBus. Order between brokers is not guaranteed but each broker's own event order is preserved.

**Risk:** If one broker's pipeline backs up (e.g., ICICI slow), it blocks ALL brokers' events through the shared EventBus.

---

## 5. REACTIVE ARCHITECTURE ASSESSMENT

### 5.1 What Works Well

The **core reactive pipeline** (Disruptor ring buffer → graph runtime → strategy → dispatch) is well-designed:
- Bounded, lock-free ring buffer (8192 slots)
- Three different wait strategies for LIVE/REPLAY modes
- Re-entrancy guard via `ThreadLocal<Boolean> IN_DISPATCH`
- Event dedup to suppress broker retransmissions
- Write-ahead log for crash recovery

The **graph compiler and runtime** provide a clean abstraction for pipeline composition:
- Topological sort via Kahn's algorithm
- Partitioned node support
- Hot-path publisher injection for Disruptor semantics

### 5.2 What Hurts

**1. Downstream queue re-publish loop is fragile**

The `downstreamQueue` (ArrayBlockingQueue, capacity 4096) is a workaround for the ring buffer's lack of re-entrancy support. Events emitted by graph nodes go to this queue, then the drainer thread re-publishes them to the ring buffer. This creates:
- An extra thread + queue that can overflow
- Non-deterministic event ordering
- Potential for infinite re-publish loops (mitigated by dedup)
- Event DROPPING instead of backpressure

**Recommendation:** Replace the downstream-queue + drainer pattern with a second ring buffer for graph output events, chained as a multi-stage Disruptor graph. This eliminates the bounded queue overflow risk and improves ordering.

**2. Async dispatch stage has no backpressure**

`AsyncDispatchHandler` has a `DEFAULT_DISPATCH_QUEUE_CAPACITY = 4096`. When full, events are dropped. Subscribers (DuckDB writer, Chronicle writer, WebSocket broadcaster) can cause backpressure if they write slower than the pipeline produces events.

**Recommendation:** Remove the dispatch queue entirely. Use Disruptor's built-in multi-cast semantics to fan out to subscribers. Or at minimum, use `put()` (blocking) instead of `offer()` for dispatch events.

**3. PortfolioEngine async queue drops events**

Same pattern: `ArrayBlockingQueue(1024)` with silent `offer()` drops. The `droppedEventCount` counter exists but is not monitored or alerted.

**Recommendation:** For LIVE mode, use a larger bounded queue with backpressure monitoring. Never silently drop trading events.

---

## 6. BACKPRESSURE REPORT

### 6.1 Backpressure Points

| Point | Queue Type | Capacity | Behavior When Full | Risk |
|-------|-----------|----------|-------------------|------|
| Disruptor ring buffer | LMAX RingBuffer | 8192 | Blocking (backpressure to publisher) | Low — correct |
| Downstream queue | ArrayBlockingQueue | 4096 | **DROPPED** — dead letter queue | **HIGH** |
| Dispatch queue | ArrayBlockingQueue | 4096 | **DROPPED** — dead letter queue | **HIGH** |
| PortfolioEngine queue | ArrayBlockingQueue | 1024 | **DROPPED** — counter only | **CRITICAL** |
| ExecutionHandler queues | ArrayBlockingQueue | 1000 | Signal suppressed to dead letter queue | MEDIUM (signal loss) |
| DuckDB writer | Async queue | Unknown | Unknown | Unknown |

### 6.2 Risk Scenarios

**10,000 symbols × 1 tick/sec × 50 strategies:**
- Each tick generates 1 CandleDeveloping → 10,000 events/sec to downstream queue
- Queue capacity: 4096 → overflows in < 0.5 seconds
- CandleDeveloping events dropped → no CandleClosed → no signals → missed trading opportunities

**Mitigation:** CandleDeveloping events should NOT go through the dispatch queue. They are intermediate events consumed only by the graph runtime and strategy sandbox. Only CandleClosed and SignalGenerated should enter the dispatch path.

---

## 7. REPLAY vs LIVE DETERMINISM REPORT

### 7.1 Determinism Safeguards Implemented (P0/P1)

| Fix | Component | Status |
|-----|-----------|--------|
| IdGenerator injection | MatchingEngine, SimulatedOrderService, OMS, all strategies | ✅ Tested |
| DeterministicIdGenerator | New in core, deterministic UUIDs | ✅ Tested |
| TradingClock injection | TradingCircuitBreaker (replaces System.currentTimeMillis) | ✅ Tested |
| Synchronous sandbox mode | GraphStrategySandbox (REPLAY/BACKTEST) | ✅ Tested |
| PortfolioEngine forceSync | PortfolioEngine (bypass async queue) | ✅ Tested |
| VirtualClock EventBus publishing | VirtualClock + ReplayClock unification | ✅ Tested |
| TripleModePNLParityTest | End-to-end signal + candle parity | ✅ Tested |

### 7.2 Remaining Determinism Gaps

| Gap | Component | Impact |
|-----|-----------|--------|
| ExecutionHandler partition non-determinism | 4 async threads, symbol-based routing | SignalPendingExecution processing order may vary |
| PortfolioEngine queue (when forceSync=false) | ArrayBlockingQueue, non-blocking offer() | Trade lifecycle events dropped in LIVE mode |
| CandleAggregationService shared state race | ConcurrentHashMap accessed by 2 threads | Duplicate/missed candles in LIVE edge cases |
| System.currentTimeMillis() in dedup pruner | `pruneOldEntries()` uses wall clock | 5-min TTL, acceptable for non-critical dedup |
| System.nanoTime() for plugin timing | `runPlugin()` timing metric | Only used for logging, not decision logic |
| Virtual thread scheduling in async sandbox mode | CompletableFuture + virtual threads | Non-deterministic signal ordering in LIVE mode |

### 7.3 Verdict

**Signal generation is deterministic** in REPLAY/BACKTEST. Trade execution (order placement, fill processing) has remaining non-determinism due to async queues and partitioned executors. The `TripleModePNLParityTest` correctly validates signal/candle determinism. An equivalent test for trade execution parity would require deeper isolation of the execution pipeline.

---

## 8. BROKER CONCURRENCY REPORT

### 8.1 Token Refresh Race (All Brokers)

**Pattern:** `ensureValid()` is called from multiple threads before each API call:
```java
// In DhanTokenManager:
public String getAccessToken() {
    ensureValid();  // may trigger refresh
    return accessToken;
}
```

If 10 threads call `getAccessToken()` simultaneously while the token is expired, all 10 trigger refresh. With HTTP rate limits on broker APIs (typically 10-50 req/sec), this causes 429 rate-limit errors.

**Mitigation:** Most TokenManager implementations use `synchronized` on `ensureValid()`. This serializes all token checks and refresh, which is correct but blocks ALL broker API calls during a token refresh (typically 500ms-2s).

### 8.2 Reconnect Duplicate Subscription

**Pattern:** `DhanReconnectController` reconnects → creates new WebSocket → the broker may re-deliver the last N ticks with `sequenceId` matching already-processed events. The dedup in `DisruptorEventBus` catches duplicate events by `symbol+segment+exchangeTimestamp+sequenceId`. However, the dedup cache has a 5-minute TTL — after 5 minutes, old dedup keys are evicted and old ticks would be re-processed as new.

**Risk:** If a reconnect happens >5 minutes after the last event, the broker's retransmitted ticks are processed as NEW ticks → duplicate signals → duplicate orders.

**Mitigation:** The `MarketTickEvent` dedup key includes `sequenceId` from the broker. If the broker uses monotonically increasing sequence IDs, events with old sequence IDs would be rejected. But this depends on broker behavior.

### 8.3 ICICI Session Refresh

ICICI uses session-based auth (not tokens). Session expiration is detected by API errors. The `BreezeTokenManager` may trigger a session refresh AND a WebSocket reconnect simultaneously → race between the refresh API call and the reconnect.

---

## 9. STRATEGY SAFETY REPORT

### 9.1 Strategy Plugin Isolation

Each `GraphStrategyPlugin` runs in its own virtual thread (LIVE mode) or synchronously (REPLAY mode). Plugin crashes are caught:
```java
try {
    Optional<SignalGenerated> result = plugin.onEvent(event);
    // ...
} catch (Exception e) {
    log.error("Graph strategy plugin threw", e);
    return Optional.empty();
}
```

✅ A crashing plugin cannot crash the pipeline.  
⚠️ A hanging plugin is terminated by the `orTimeout(timeoutMs, ...)`.  
⚠️ Side effects (state mutation) in `plugin.onEvent()` are NOT isolated between plugins — they share the same `Consumer<DomainEvent>` downstream.

### 9.2 State Mutation Risk

`SmaCrossStrategy` maintains per-symbol state:
```java
private final Map<String, Deque<Double>> closes = new ConcurrentHashMap<>();
```

In LIVE mode (virtual threads), two plugins could process events for the same symbol concurrently. The `synchronized(window)` block prevents data races on the deque, but signal generation is serialized per symbol.

✅ Per-symbol synchronization prevents state corruption.  
⚠️ Throughput bottleneck: ALL strategies for the same symbol are serialized.

---

## 10. OMS CONCURRENCY REPORT

### 10.1 Order State Machine

`OrderManagementService` stores `ConcurrentHashMap<String, OrderStateMachine>`. `onBrokerEvent()` calls `stateMachines.compute(orderId, ...)` which is atomic.

✅ State machine updates are serialized per order ID.  
✅ `replayAll()` clears and rebuilds from persisted events — correct for crash recovery.  
⚠️ `snapshot()` copies the map non-atomically (see section 3.10).

### 10.2 Duplicate Order Detection

`DisruptorEventBus.isDuplicate()` has specific dedup keys for order events:
- `OrderAccepted` → `ORDER:{orderId}:OrderAccepted`  
- `OrderFilled` → `ORDER:{orderId}:OrderFilled`  
- `OrderRejected` → `ORDER:{orderId}:OrderRejected`  

This prevents duplicate processing of broker callbacks. But `OrderPartiallyFilled` and `OrderFullyFilled` (from `core.domain.event`) fall through to `event.eventId()` dedup.

✅ Order lifecycle events from brokers are dedup'd by orderId + event type.  
⚠️ Multiple partial fills for the same order will have different eventIds → not dedup'd — this is CORRECT (each partial fill is a new event).

---

## 11. LIFECYCLE SAFETY REPORT

### 11.1 Startup Sequence (Spring Boot)

```
1. Bean creation (no guaranteed order)
2. Bean post-processing
3. @PostConstruct
4. ApplicationRunner (runtimeStarter)
   4a. BrokerStartupOrchestrator.runStartup()
       4a1. loadCatalogAndValidateSubscriptions()
       4a2. ensureBrokerTokens()
       4a3. runBrokerPreflight()
       4a4. wireRuntimeSubscribers()
       4a5. wireWebSocketHandlers()
       4a6. startRuntimeAndRecover()
       4a7. connectBrokerAndSubscribe()
       4a8. markStartupCompleted()
```

**Risk:** `wireRuntimeSubscribers()` subscribes event handlers to the EventBus. But `startRuntimeAndRecover()` calls `eventBus.start()` + `orderManagementService.replayAll()`. If events are replayed BEFORE subscribers are wired, events are lost.

**Current state:** `wireRuntimeSubscribers()` is called at step 4a4 BEFORE `startRuntimeAndRecover()` at step 4a6. ✅ Correct.

**Risk:** `BrokerStartupOrchestrator` is a `@Component` — its constructor runs during Spring bean creation. But its dependencies (like `EventBus`) are `@Lazy` and may not be fully initialized. The `ApplicationRunner` ensures startup runs after all beans are created. ✅ Correct.

### 11.2 Shutdown Sequence

`DisruptorEventBus.stop()`:
```
1. started = false
2. Interrupt drainer thread
3. Wait for drainer (1s timeout)
4. executionHandler.stop()
5. disruptor.shutdown()
6. dispatchStage.stop()
7. dedupPruner.shutdownNow()
```

⚠️ The 1-second timeout on drainer join means events still in the `downstreamQueue` at shutdown may be dropped. This is acceptable for graceful shutdown but means the WAL is the only recovery mechanism for in-flight events.

⚠️ `executionHandler.stop()` is called BEFORE `disruptor.shutdown()`. ExecutionHandler has in-flight signals being processed. If the handler's executor is shut down before signals complete, orders are neither placed nor rejected — they are lost.

### 11.3 Order Management Lifecycle

`BrokerStartupOrchestrator.startRuntimeAndRecover()`:
1. `eventBus.start()`
2. `orderManagementService.replayAll()` — rebuilds in-memory state machines from Chronicle
3. `orderReconciler.reconcileAll()` — reconciles OMS state with broker state
4. `positionStateRebuilder.rebuild()` — rebuilds position state from event store

✅ `replayAll()` is called BEFORE `reconcileAll()` — correct: you must know what you expected before checking what the broker has.

✅ `repositionStateRebuilder.rebuild()` uses the EventBus — correct: it publishes events that the `NetPositionProvider` subscribes to.

---

## 12. DESIGN PRINCIPLES ASSESSMENT

### 12.1 SOLID

**Single Responsibility:** ✅ Mostly good. Each module has clear ownership.  
**Open/Closed:** ✅ Strategy and scanner plugins use SPI — extensible without modification.  
**Liskov Substitution:** ⚠️ `EventBus` interface has multiple implementations (Disruptor, Simple, Sharded, BrokerScoped) that behave differently — specifically around ordering guarantees and synchronous vs async dispatch.  
**Interface Segregation:** ✅ `EventBus`, `GraphStrategyPlugin`, `BrokerProvider` are focused.  
**Dependency Inversion:** ⚠️ `Composition` module depends on specific implementations, not just interfaces.

### 12.2 Domain-Driven Design

**Domain model is in `core` module.** Events, ports, and value objects are framework-independent.  

**Violations:**
- `OrderEvent` hierarchy is split between `core.domain.oms` and `core.domain.event` — `OrderAcknowledged`, `OrderSubmitted`, `OrderCancelled` live in `oms`, while `OrderAccepted`, `OrderFilled` live in `event`. These represent the SAME domain concept with different serialization.
- `ExecutionHandler` has SEPARATE handling paths for `OrderFilled` (from simulation) and `OrderPartiallyFilled`/`OrderFullyFilled` (from broker) — the representation difference leaks into the handler logic.

### 12.3 Composition vs Inheritance

✅ Codebase favors composition. The pipeline graph model is a good example of composition over inheritance.

⚠️ `GatewayEventBridge` has complex composition: `GatewayTopicRouter` + `ObjectMapper` + `InstrumentResolver` + WebSocket handler chain. The `GatewayTopicRouter` itself composes multiple `GatewayTopicHandler` instances. This is correct but the complexity suggests a missing abstraction.

---

## 13. ARCHITECTURE CLARITY REPORT

### 13.1 Module Structure

Current modules (24+ build.gradle files):

| Module | Purpose | Classification |
|--------|---------|---------------|
| `core` | Domain model, ports, events | ✅ ACTIVE |
| `broker/api` | Broker SPI | ✅ ACTIVE |
| `broker/core` | Broker routing, reconnect, auth base | ✅ ACTIVE |
| `broker/dhan` | Dhan adapter | ✅ ACTIVE |
| `broker/upstox` | Upstox adapter | ✅ ACTIVE |
| `broker/icici` | ICICI adapter | ✅ ACTIVE |
| `gateway` | WebSocket gateway for frontend | ✅ ACTIVE |
| `broker-gateway` | Broker Gateway SPI | ⚠️ CONDITIONAL |
| `runtime/disruptor` | Disruptor event bus | ✅ ACTIVE |
| `runtime/hotpath` | Market data + order pipeline | ✅ ACTIVE |
| `pipeline/core` | Graph model, compiler, runtime | ✅ ACTIVE |
| `pipeline/runtime` | Pipeline runtime service | ✅ ACTIVE |
| `pipeline/analytics/trade-analytics` | Trade analytics | ⚠️ CONDITIONAL |
| `pipeline/platform/trade-pipeline-platform` | Pipeline platform | ⚠️ CONDITIONAL |
| `trading/strategy` | Strategy SDK, sandbox, portfolio | ✅ ACTIVE |
| `trading/execution` | OMS, risk, execution handler | ✅ ACTIVE |
| `trading/scanner` | Scanner engine | ⚠️ PARTIAL |
| `trading/indicators` | Technical indicators | ⚠️ PARTIAL |
| `trading/simulation` | Matching engine, PnL ledger | ✅ ACTIVE |
| `trading/institutional-scanner` | Institutional scanner | ⚠️ PARTIAL |
| `trading/options-analytics` | Options Greeks, OI analysis | ⚠️ PARTIAL |
| `data/persistence` | DuckDB, Chronicle | ✅ ACTIVE |
| `data/feature-store` | Feature store | ✅ ACTIVE |
| `data/historical-ingest` | Historical data pipeline | ⚠️ PARTIAL |
| `data/analytics` | Analytics queries | ⚠️ PARTIAL |
| `composition` | Non-Spring composition root | ✅ DELETED (P3 simplification complete; configs moved to core/broker-gateway, BrokerComposition → broker-gateway/wiring) |
| `app` | Spring Boot application | ✅ ACTIVE |
| `cli` | CLI client | ✅ ACTIVE |
| `replay/engine` | Replay engine | ✅ ACTIVE |
| `architecture-test` | ArchUnit tests | ✅ ACTIVE |
| `frontend` (trade_j_frontend) | React dashboard | ✅ ACTIVE |
| `trade-node-library` | Pipeline node library | ✅ DELETED (P3 simplification; zero external references, no active implementations) |
| `gateway` | WebSocket gateway | ✅ ACTIVE |
| `mcp-server` | MCP server | ⚠️ EXPERIMENTAL |

### 13.2 Module Dependency Graph (Simplified)

```
app
 ├── composition (CONDITIONAL alternative to Spring)
 ├── broker/dhan
 ├── broker/upstox
 ├── broker/icici
 ├── broker/core
 ├── broker/api
 ├── runtime/disruptor
 ├── runtime/hotpath
 ├── pipeline/core
 ├── trading/strategy
 ├── trading/execution
 ├── trading/simulation
 ├── data/persistence
 └── core (everything depends on core)
```

**Issue:** `app` depends on EVERYTHING. This is the `@SpringBootApplication` classpath — expected for a Spring Boot app, but means the app module must compile all modules.

**Issue:** `composition` module duplicates Spring wiring logic without Spring. This is used by the CLI and tests. It provides `FullComposition.create()` which mirrors `@Configuration` classes. Two composition paths = two sources of truth for wiring.

### 13.3 Runtime Topology

```
                          ┌──────────────┐
                          │   WebSocket   │
                          │   Gateway     │
                          │  (external)   │
                          └──────┬───────┘
                                 │ events
                                 ▼
┌──────────┐           ┌──────────────────┐
│  Broker  │──ticks──▶│  DisruptorEventBus│
│ WebSocket│──orders─▶│  (ring buffer)    │
└──────────┘           └────────┬─────────┘
                                │
                    ┌───────────▼───────────┐
                    │ GraphPipelineDisruptor │
                    │  Handler (Compiled     │
                    │   Graph Runtime)       │
                    └───────────┬───────────┘
                                │
                    ┌───────────▼───────────┐
                    │ GraphStrategyDisruptor │
                    │  Handler (Sandbox)     │
                    └───────────┬───────────┘
                                │
                    ┌───────────▼───────────┐
                    │   AsyncDispatchHandler │
                    └───────────┬───────────┘
                                │
                    ┌───────────▼───────────┐
                    │   Subscribers:        │
                    │   - DuckDB writer     │
                    │   - Chronicle writer  │
                    │   - ReadModelStore    │
                    │   - Gateway broadcast │
                    │   - PortfolioEngine   │
                    └───────────────────────┘
```

---

## 14. CODE DISPOSITION

### DELETE

1. **`composition` module** — ✅ **DELETED**. Configs migrated to core/broker-gateway/app. BrokerComposition → broker-gateway/wiring. All callers updated.

2. **`RuntimeBus.SIMPLE` option** — `SimpleEventBus` is not thread-safe, has no ordering guarantees, drops events silently. It should never be used in production. **DELETE** the `SimpleEventBus` implementation.

3. **`DefaultBacktestFillModel` legacy 3-arg constructor** — Backward compat from P0 changes. All callers should be updated to pass `IdGenerator`.

4. **`replay/engine` module** — The replay functionality is split between `data/persistence` (HistoricalEventReplayService) and `replay/engine` (ReplayOrchestrator). The engine module appears to be thin delegation. **MERGE** into `data/persistence`.

5. **`nodes/trade-node-library`** — ✅ **DELETED**. Contained no active node implementations. All nodes are defined in the strategy and execution modules.

6. **`pipeline/analytics/trade-analytics`** — Empty or experimental. **DELETE** if no active code.

7. **`pipeline/platform/trade-pipeline-platform`** — Empty or experimental. **DELETE** if no active code.

8. **`mcp-server`** — Experimental MCP server. **DELETE** or move to a separate repository. It adds startup dependencies to the main build.

9. **`broker-gateway`** — Separate Broker Gateway module with only a `BrokerProvider` services file. **MERGE** into `broker/core` or `gateway`.

10. **`ReplayClock`** — Now delegates to `VirtualClock` (P1 fix). **DELETE** and migrate all callers to `VirtualClock` directly.

### MERGE

1. **`OrderEvent` hierarchy** — Merge `core.domain.oms.OrderEvent` and `core.domain.event.OrderEvent` into a single canonical hierarchy. Currently events are split across packages with the same names but different fields.

2. **`EventBus` implementations** — `ShardedDisruptorEventBus` wraps multiple `DisruptorEventBus` instances. `BrokerScopedEventBus` wraps a single EventBus. These should be unified into a single configurable bus.

3. **`ReplayMarketTicks` paths** — At least 3 paths for replaying market ticks: `HistoricalEventReplayService.replayMarketTicks()`, `replayTicks()`, and the ReplayOrchestrator wrapper. Consolidate to one.

4. **Candle representations** — `Candle` is defined in `core`, but `CandleDeveloping` wraps it. `CandleClosed` wraps it again. Consider combining developing/closed into a single `Candle` with a `closed` boolean flag, eliminating the wrapper events.

### REWRITE

1. **Downstream queue + drainer pattern** — The ArrayBlockingQueue + drainer thread in `DisruptorEventBus` is a workaround that should be replaced with a proper multi-stage ring buffer design. The current design drops events when the queue overflows.

2. **PortfolioEngine async queue** — The `forceSync` fix (P0-7) works for REPLAY/BACKTEST. For LIVE mode, the async queue should use `put()` (blocking) or a persistent queue with replay capability.

3. **Broker reconnect + subscription reconciliation** — Current design has separate reconnect controllers, subscription managers, and WebSocket multiplexers that race during reconnection. A single `BrokerSessionManager` per broker should own connection lifecycle atomically.

### KEEP

1. **Core domain model** — Clean, framework-independent, well-factored. ✅
2. **DisruptorEventBus (minus downstream queue)** — Solid foundation for low-latency event processing. ✅
3. **Graph compiler and runtime** — Declarative pipeline composition is a strong abstraction. ✅
4. **Strategy Plugin SPI** — Clean extension point for strategies. ✅
5. **Broker SPI** — Clean adapter pattern for broker integration. ✅
6. **Determinism infrastructure** — `DeterministicIdGenerator`, `ReplayTradingClock`, `VirtualClock`. ✅
7. **Certification test infrastructure** — `TripleModePNLParityTest`, `ReplayDeterminismCertificationTest`. ✅
8. **DuckDB-based analytics** — Clean separation of OLAP from OLTP. ✅
9. **Architecture tests** — ArchUnit tests prevent dependency violations. ✅
10. **CLI client** — Clean, focused, no framework coupling. ✅

---

## 15. SHOTGUN SURGERY ANALYSIS

### Concept: Adding a new broker adapter

**Files that change:** 10+
1. New `broker/{name}` module with SPI implementation
2. `app/.../BrokerConfiguration.java` — add @Bean
3. `app/.../BrokerStartupOrchestrator.java` — add to strategies list
4. `app/.../broker-startup/{Name}StartupStrategy.java` — new file
5. `app/.../config/TradingConfiguration.java` — maybe
6. `gateway/.../GatewayEventBridge.java` — maybe
7. `broker/core/.../LoadBalancedBrokerGateway.java` — maybe
8. `broker/api/BrokerCapabilities.java` — maybe extend
9. `frontend/.../brokerRegistry.ts` — add to UI
10. Various test files

**Violation:** Single Responsibility — broker integration touches `app`, `broker`, `gateway`, and `frontend`.

**Recommendation:** A `BrokerPlugin` mechanism that auto-registers via SPI (already partially done via `BrokerProvider`). The broker module should be fully self-describing: capabilities, startup strategy, UI config, all bundled in the module.

### Concept: Adding a new event type

**Files that change:** 3-5
1. New record in `core/domain/event/`
2. `DomainEventVisitor` interface — add `visit(NewEvent)` default method
3. DuckDB event store — add table/column in `DuckDbEventStore`
4. Gateway — add to `GatewayEventBridge` mapping
5. Frontend — new handler

**Verdict:** Acceptable for an event-driven system. The visitor pattern minimizes changes.

---

## 16. INTEGRATION CERTIFICATION REPORT

| Integration | Preconditions | Guarantees | Failure Detection | Recovery | Production Ready |
|------------|---------------|------------|-------------------|----------|-----------------|
| Dhan WebSocket | Token valid, subscribed symbols loaded | Market data + order events → EventBus | Disconnect detected by socket closure / heartbeat timeout | ReconnectManager (exponential backoff) | ✅ YES |
| Upstox WebSocket | Token valid | Market data → EventBus | Socket closure | ReconnectManager | ✅ YES |
| ICICI WebSocket | Session valid | Market data → EventBus | Socket closure | ReconnectManager | ⚠️ Partial (session refresh may race) |
| DuckDB persistence | DB file writable | Events persisted | SQLException | WAL replay on restart | ✅ YES |
| Chronicle OMS | Directory writable | Order events persisted | IOException | `replayAll()` on startup | ✅ YES |
| WebSocket Gateway | EventBus started | Events → frontend | WebSocket close | Client reconnects | ✅ YES |
| Token refresh | Token expired | New token obtained | HTTP 401 | Retry with exponential backoff | ⚠️ Partial (concurrent refresh race) |

---

## 17. OBSERVABILITY GAPS

### Missing Metrics

| Metric | Where | Severity |
|--------|-------|----------|
| `candle_aggregation_conflicts` | CandleAggregationService | HIGH — shared state race |
| `execution_handler_signal_time_ms` | ExecutionHandler per partition | MEDIUM — latency monitoring |
| `portfolio_queue_dropped_events` | PortfolioEngine | CRITICAL — PnL divergence |
| `downstream_queue_full_count` | DisruptorEventBus | HIGH — dropped events |
| `dispatch_queue_full_count` | AsyncDispatchHandler | HIGH — dropped events |
| `broker_token_refresh_conflicts` | All TokenManagers | MEDIUM — concurrent refresh |

### Missing Alerts

| Condition | Severity | Current State |
|-----------|----------|---------------|
| `downstreamQueue` full events | PAGER | Logged to dead letter queue only |
| `dispatchQueue` full events | PAGER | Logged to dead letter queue only |
| PortfolioEngine queue full | PAGER | Counter exists, no alert |
| Broker reconnect count > 5/min | PAGER | No metric |
| Token refresh failure | PAGER | Log error only |
| Event replay mismatch count | WARNING | Log warning only |

---

## 18. DEPLOYMENT DECISION

### GO WITH CONDITIONS

**Decision:** The system can be deployed to production with the following conditions:

**Blocking Issues (must fix before next deployment):**

1. **CRITICAL:** `CandleAggregationService` concurrent access from ring buffer + drainer threads must be fixed. Add a re-entrancy guard or single-threaded executor.

2. **CRITICAL:** `PortfolioEngine` async queue drops events silently when full (`offer()` vs `put()`). In LIVE mode, `TradeOpened` events must not be dropped. Change to `put()` for ledger events.

3. **HIGH:** Downstream queue overflow drops `CandleDeveloping`/`CandleClosed` events. CandleDeveloping events should bypass the downstream queue entirely (they are intermediate, consumed by the graph runtime, not by subscribers).

4. **HIGH:** Add PAGER-level alerts for: downstream queue full, dispatch queue full, portfolio engine queue full, broker reconnect storms.

**Risk Level:** MEDIUM

**Required remediation before next deployment reconsideration:**
- Fix items 1-4 above
- Run full regression suite including `TripleModePNLParityTest`
- Verify `droppedEventCount` is zero after 1 hour of live operation

---

## 19. TOP 10 SIMPLIFICATIONS

1. **Delete `composition` module** — One wiring path (Spring), not two.
2. **Delete `SimpleEventBus`** — Only one EventBus implementation in production.
3. **Delete `ReplayClock`** — Use `VirtualClock` directly everywhere.
4. **Merge `OrderEvent` hierarchy** — One canonical representation.
5. **Fix downstream queue** — Multi-stage ring buffer instead of ArrayBlockingQueue + drainer.
6. **Single broker session manager** — Replace reconnect controller + subscription manager + multiplexer race with atomic session lifecycle.
7. **Remove CandleDeveloping from dispatch** — Intermediate events should not flow through the subscriber dispatch path.
8. **Unify replay paths** — One `ReplayService` with one `replayMarketTicks()` method.
9. **Replace `ThreadLocal<Consumer>` in risk handler** — Explicit publisher parameter.
10. **Consolidate `build.gradle` modules** — Merge the 5+ experimental/empty modules.

---

## 20. FINAL SUMMARY

Trade-J is a well-architected event-driven trading platform with:

**Strengths:**
- Clean domain model in `core` module
- Strategy and broker SPIs are clean extension points
- Disruptor pipeline provides low-latency event processing
- Strong determinism testing (P0/P1 fixes + TripleModePNLParityTest)
- Proper separation of LIVE/REPLAY/BACKTEST modes
- Architecture tests enforce module dependencies

**Weaknesses:**
- **Downstream queue + drainer pattern** is the single biggest architectural risk — it creates a secondary event stream that can overflow, drop events, and reorder them
- **PortfolioEngine async queue** drops trading events silently in LIVE mode
- **CandleAggregationService shared state** between ring buffer and drainer threads
- **24+ build modules** creates compilation overhead without proportional value
- **Two composition paths** (Spring + FullComposition) risks configuration drift
- **OrderEvent hierarchy split** between `oms` and `event` packages

**The most important thing to fix:** Replace the `downstreamQueue` + drainer pattern with a proper multi-stage ring buffer. This single change eliminates the most dangerous silent-failure mode in the system — events being dropped without the operator knowing why.
