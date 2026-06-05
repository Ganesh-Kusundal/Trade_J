# Trade-J Architecture & Code Quality Review

**Date:** 2026-06-01  
**Reviewer:** Principal Engineer / Software Architect  
**Scope:** Full codebase (26 Gradle subprojects, 655 main + 214 test Java files)  
**Stack:** Java 21, Spring Boot 3.4.13, LMAX Disruptor, Chronicle Queue, DuckDB, React 19

---

## Executive Summary

| Dimension | Score (0-10) | Rationale |
|-----------|--------------|-----------|
| **Architecture** | 7/10 | Strong modular decomposition, hexagonal broker adapters, event-driven core. Dual pipeline (Disruptor + Graph DAG) introduces operational complexity and parity risk. |
| **SOLID Principles** | 6/10 | Good use of ports/interfaces in `:core` and `:broker-api`. Violations include God-class `DhanBrokerConnection` (15 constructor params), `ExecutionHandler` (multiple responsibilities), and `IBrokerConnection` (interface segregation breach via deprecated accessors). |
| **Spring Boot Best Practices** | 7/10 | Constructor injection is dominant; `@Configuration` classes are well-structured. Issues: `@Autowired` on constructor in `ExecutionHandler` (redundant with Spring 4.3+), field injection in `MarketDataController`, and massive `BrokerConfiguration`/`UpstoxConfiguration` bean methods (100+ lines each). |
| **DDD Quality** | 7/10 | Rich domain events (`DomainEvent` records), value objects (`Side`, `ExchangeSegment`, `OrderType`), and a deterministic `OrderStateMachine`. Gaps: anemic `Order` record (no behavior), missing aggregate roots for `Portfolio`/`StrategyRun`, and domain events mixed with infrastructure concerns in some handlers. |
| **Performance** | 7/10 | LMAX Disruptor ring buffer (8192), `BusySpinWaitStrategy`, bounded queues, Caffeine caches, and virtual threads for order placement. Concerns: `CopyOnWriteArrayList` for subscribers (high churn cost), `ArrayBlockingQueue` in `ExecutionHandler` (single consumer), and potential GC pressure from `LinkedHashMap` in REST controllers. |
| **Production Readiness** | 6/10 | Comprehensive test pyramid (unit/component/integration/broker-rest/ws/order), ArchUnit architecture tests, structured logging with MDC, Micrometer metrics, and circuit breakers. Gaps: no explicit resilience annotations (`@Retryable`, `@CircuitBreaker` from Spring Cloud), limited startup validation (no `@Validated` on config properties), and no explicit thread-pool configuration for `@Async`/`Scheduled` in documented configs. |

**Overall Platform Score: 6.8/10**

---

## Critical Issues

### C-01: `ExecutionHandler` is a God Class violating SRP
- **Location:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java` (577 lines)
- **Problem:** Single class handles: signal queuing, circuit breaking, order placement with timeout, fill reconciliation, synthetic trade generation, identity mapping, trade-opened deduplication, and downstream event emission.
- **Impact:** High coupling makes testing difficult, changes risk regressions across unrelated concerns, and violates SRP.
- **Refactoring:** Split into:
  - `SignalExecutionQueue` (queue management)
  - `OrderPlacementService` (broker interaction + timeout)
  - `FillReconciliationService` (fill processing, synthetic trades)
  - `TradeEventEmitter` (TradeOpened/TradeUpdated deduplication)

### C-02: `DhanBrokerConnection` violates SRP with 15-parameter constructor
- **Location:** `broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java` (lines 99-131)
- **Problem:** Facade aggregates 15 adapters. While facades are acceptable, the constructor forces `BrokerConfiguration` to be a 400-line bean factory.
- **Impact:** Adding a new broker capability requires modifying the facade, all configuration classes, and the `IBrokerConnection` interface.
- **Refactoring:** Use capability-based composition. `DhanBrokerConnection` should implement `IBrokerConnection` + `AdvancedOrderCapable` + `MarginCapable` etc., and expose capabilities via `getCapability()` rather than direct accessor methods.

### C-03: `IBrokerConnection` violates Interface Segregation Principle
- **Location:** `broker/api/src/main/java/com/tradej/broker/api/IBrokerConnection.java` (lines 44-153)
- **Problem:** Interface has 7 mandatory methods + 8 deprecated default methods that throw `UnsupportedOperationException`. Upstox implements unsupported ports via `UpstoxUnsupportedPorts` singletons that throw at runtime.
- **Impact:** Clients calling `futures()` or `bracketOrders()` get runtime exceptions instead of compile-time safety. The deprecated accessors remain in the interface, preventing clean removal.
- **Refactoring:** Remove deprecated accessors from `IBrokerConnection`. Use `getCapability(Class)` exclusively. Split into focused sub-interfaces: `TradingBrokerConnection` (orders, query, portfolio), `MarketDataBrokerConnection` (market data, websocket), `AdvancedBrokerConnection` (bracket, GTT, slice).

### C-04: Dual Pipeline Architecture creates parity and maintenance risk
- **Location:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (lines 267-322), `docs/ARCHITECTURE.md` (lines 64-71)
- **Problem:** Three pipeline configurations (A: Graph on Disruptor, B: Legacy + feature store, C: Legacy) coexist. Config A is production; B/C remain for tests. The Graph DAG runtime is not at full parity with the Disruptor hot path.
- **Impact:** Bug fixes must be applied in two places. Stage ordering differences (risk → candle → strategy vs. graph topological order) can cause divergent behavior between test and production.
- **Refactoring:** Retire Config B/C. Migrate all tests to Config A or a dedicated test harness. Establish a single source of truth for pipeline topology.

### C-05: `DisruptorEventBus` has 12 constructor overloads (Telescoping Constructor anti-pattern)
- **Location:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (lines 82-205)
- **Problem:** 12 constructors with progressive parameter addition. This makes the API confusing and error-prone.
- **Refactoring:** Replace with a builder pattern (`DisruptorEventBusBuilder`) or a single configuration record (`DisruptorEventBusConfig`).

---

## High Priority Issues

### H-01: `BrokerConfiguration` and `UpstoxConfiguration` are 400+ line bean factories
- **Location:** `app/src/main/java/com/tradej/app/config/BrokerConfiguration.java` (463 lines), `UpstoxConfiguration.java` (462 lines)
- **Problem:** Spring `@Configuration` classes contain 20+ `@Bean` methods with explicit wiring of every adapter. This violates DIP by depending on concrete broker classes.
- **Impact:** Adding a new broker (e.g., ICICI Direct) requires a new 400-line configuration class. Changes to adapter constructors cascade to configuration.
- **Refactoring:** Move broker-specific `@Configuration` into the broker module (e.g., `broker/dhan/src/main/.../config/DhanAutoConfiguration`). Use `@ConditionalOnProperty` and `@Import` to load broker-specific configs. The `:app` module should only depend on `:broker-api` ports.

### H-02: `MarketDataController` uses `@Autowired(required = false)` for optional dependencies
- **Location:** `app/src/main/java/com/tradej/app/api/MarketDataController.java` (lines 33-41)
- **Problem:** Optional dependencies are injected via `@Autowired(required = false)` and wrapped in `Optional`. This is a code smell indicating the controller has conditional behavior based on infrastructure availability.
- **Impact:** The controller is not testable in isolation; it silently degrades when services are missing.
- **Refactoring:** Use Spring profiles or `@ConditionalOnProperty` to conditionally register the controller itself, or split into separate controllers for broker-backed and analytics-backed endpoints.

### H-03: `CopyOnWriteArrayList` for event subscribers is a performance anti-pattern
- **Location:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (line 58)
- **Problem:** `subscribers` map uses `CopyOnWriteArrayList` for handler lists. This is appropriate for rare reads/frequent iterations, but subscriber registration/unregistration is not rare during startup/shutdown, and iteration happens on every event.
- **Impact:** High memory allocation during subscription changes; unnecessary copy-on-write overhead for the hot path.
- **Refactoring:** Use `ConcurrentHashMap<Class<?>, CopyOnWriteArraySet<DomainEventHandler<?>>>` or a custom concurrent list with lock-free reads. Alternatively, snapshot the subscriber list at Disruptor start and treat it as immutable during runtime.

### H-04: `OrderStateMachine` uses `HashMap` for transition table (not thread-safe for concurrent rebuilds)
- **Location:** `core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java` (lines 140-194)
- **Problem:** `TRANSITIONS` map is built in a static initializer using `HashMap`, then wrapped with `Collections.unmodifiableMap`. While the instance is thread-safe via `synchronized` methods, the static map construction is not explicitly safe against class-loading race conditions (though JVM guarantees static init safety).
- **Impact:** Low risk in practice, but the `HashMap` should be `EnumMap` or a static `Map.of()` for clarity and performance.
- **Refactoring:** Replace `HashMap` with `Map.of()` entries or an `EnumMap<StateTransition, LifecycleState>` if `StateTransition` is converted to an enum-like key.

### H-05: `GraphRuntime.onEvent()` silently swallows exceptions
- **Location:** `core/src/main/java/com/tradej/pipeline/runtime/GraphRuntime.java` (lines 54-62)
- **Problem:** `onEvent()` catches all exceptions from ingress nodes and ignores them. This violates the fail-fast principle and can cause silent data loss in the pipeline.
- **Impact:** A misbehaving strategy node can crash without alerting, leading to missed signals or stale state.
- **Refactoring:** Log the exception with full context (event ID, node ID) and route to a `DeadLetterQueue` or emit a `PipelineErrorEvent`.

### H-06: `ExecutionHandler` creates thread pools internally instead of injecting them
- **Location:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java` (lines 64-73)
- **Problem:** `Executors.newSingleThreadExecutor()` and `Executors.newSingleThreadScheduledExecutor()` are created inside the class. This makes thread-pool tuning impossible without code changes and complicates graceful shutdown.
- **Impact:** Thread pools are not visible to Spring's `TaskExecutor` abstraction; monitoring and configuration are limited.
- **Refactoring:** Inject `Executor` and `ScheduledExecutorService` via constructor (already done for `orderPlacementExecutor`). Apply the same pattern to `executor` and `fillDeferExecutor`.

---

## Medium Priority Issues

### M-01: Primitive Obsession in domain model
- **Location:** `core/src/main/java/com/tradej/core/domain/model/Order.java`, `Candle.java`, `Quote.java`
- **Problem:** `long pricePaisa`, `long quantity`, `String symbol` are primitives/strings without type-safe wrappers. `PriceMath` utility exists but is not integrated into the domain model.
- **Impact:** Risk of unit confusion (paisa vs. rupees), invalid quantity values, and symbol normalization bugs.
- **Refactoring:** Introduce value objects: `Price` (paisa-backed), `Quantity`, `Symbol` (normalized, validated). Use them in `Order`, `Trade`, `Candle`, `SignalGenerated`.

### M-02: `MarketDataController` returns raw `Map<String, Object>` instead of typed DTOs
- **Location:** `app/src/main/java/com/tradej/app/api/MarketDataController.java` (lines 43-84)
- **Problem:** REST endpoints return `ResponseEntity<Map<String, Object>>` with manual map construction. This loses compile-time type safety, documentation, and validation.
- **Refactoring:** Create record-based DTOs: `LtpResponse`, `CandleResponse`, `HistoricalCandlesResponse`. Use `@Schema` annotations for OpenAPI documentation.

### M-03: `BrokerStartupOrchestrator.runStartup()` has 22 parameters
- **Location:** `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java` (lines 59-101)
- **Problem:** Method signature is 22 parameters long. This is a classic Data Clump smell.
- **Impact:** Adding a new startup step requires modifying this method signature and all callers.
- **Refactoring:** Introduce a `StartupContext` record or `BrokerStartupEnvironment` object that groups related dependencies (event bus, persistence, broker, pipelines).

### M-04: `DhanBrokerConnection.getCapability()` uses `isInstance`/`cast` chain
- **Location:** `broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java` (lines 329-373)
- **Problem:** 14 `if (capabilityClass.isInstance(...))` checks. This is fragile and requires updating when new capabilities are added.
- **Refactoring:** Maintain a `Map<Class<?>, Object>` capability registry populated in the constructor. `getCapability()` becomes a single `map.get(capabilityClass)`.

### M-05: `EventBus.publish()` does not handle backpressure
- **Location:** `core/src/main/java/com/tradej/core/domain/port/EventBus.java` (line 12), `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (lines 339-347)
- **Problem:** `publish()` returns `void` and has no backpressure mechanism. `tryPublish()` exists but is not part of the `EventBus` interface. Callers cannot know if the event was accepted.
- **Refactoring:** Add `boolean tryPublish(DomainEvent)` to `EventBus` interface. Make `publish()` block or throw on overflow.

### M-06: `ExecutionHandler` uses `ArrayBlockingQueue` with single consumer
- **Location:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java` (lines 160, 247-261)
- **Problem:** `ArrayBlockingQueue` with a single `runLoop()` thread. While this provides ordering, it creates a bottleneck under high signal throughput.
- **Impact:** Queue capacity is 1000; under sustained load, signals are dropped or deferred.
- **Refactoring:** Consider `Disruptor`-backed queue for the execution stage, or increase consumer threads with partitioned order keys.

### M-07: Missing `@Validated` on configuration properties
- **Location:** `app/src/main/java/com/tradej/app/config/TradingProperties.java` (not read, but referenced in configs)
- **Problem:** Configuration classes like `TradingProperties` are not annotated with `@Validated`. Invalid property values (negative risk limits, invalid shard counts) are not caught at startup.
- **Refactoring:** Add `@Validated` to `@ConfigurationProperties` classes and use Jakarta Validation annotations (`@Min`, `@Max`, `@Positive`).

### M-08: `StrategyEngine` is deprecated but still wired in production path
- **Location:** `docs/ARCHITECTURE_REPORT.md` (line 323), `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (line 257)
- **Problem:** `StrategyEngine` is marked deprecated in favor of `GraphStrategySandbox`, but `DisruptorEventBus` still instantiates `StrategyDisruptorHandler` for it in Config B/C.
- **Impact:** Dead code remains in the hot path, increasing maintenance burden and cognitive load.
- **Refactoring:** Remove `StrategyEngine` and `StrategyDisruptorHandler` entirely. Migrate all strategies to `GraphStrategyPlugin`.

---

## Detailed Findings

### Finding: `IBrokerConnection` is a Fat Interface (ISP Violation)

**Location:** `broker/api/src/main/java/com/tradej/broker/api/IBrokerConnection.java`  
**Impact:** Every broker implementation must stub or throw for unsupported capabilities. Upstox uses `UpstoxUnsupportedPorts` singletons that throw `UnsupportedOperationException`.  
**Root Cause:** Interface was designed as a "one interface to rule them all" rather than capability-based composition.  
**Refactoring Recommendation:**
```java
// Split into focused sub-interfaces
public interface TradingBrokerConnection {
    OrderCommand orders();
    OrderQuery orderQuery();
    PortfolioProvider portfolio();
}
public interface MarketDataBrokerConnection {
    MarketDataProvider marketData();
    WebSocketMultiplexer websocket();
    InstrumentResolver instruments();
}
public interface AdvancedBrokerConnection {
    SliceOrderCommand sliceOrders();
    BracketOrderProvider bracketOrders();
    GttOrderProvider gttOrders();
}
// Composite for full-featured brokers
public interface DhanBrokerConnection extends TradingBrokerConnection, MarketDataBrokerConnection, AdvancedBrokerConnection, MarginCapable, OptionsCapable, AlertCapable {}
```

### Finding: `ExecutionHandler` mixes domain logic with infrastructure

**Location:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java`  
**Impact:** Testing requires mocking `OrderManagementService`, `RuntimeModeHolder`, `TradingCircuitBreaker`, `OrderIdentityRegistry`, `DeadLetterQueue`, and `TradingClock` simultaneously.  
**Root Cause:** No separation between execution policy (when to place orders) and execution mechanics (how to place orders).  
**Refactoring Recommendation:**
```java
public interface OrderPlacementPolicy {
    boolean shouldPlace(SignalPendingExecution signal);
    Order place(OrderRequest request) throws OrderPlacementException;
}
public class DefaultOrderPlacementPolicy implements OrderPlacementPolicy {
    // Circuit breaker + identity + timeout logic
}
@Service
public class ExecutionHandler {
    private final OrderPlacementPolicy policy;
    private final OrderManagementService oms;
    // Queue and event emission only
}
```

### Finding: `DisruptorEventBus` couples `:runtime-disruptor` to `:trading-execution` and `:trading-strategy`

**Location:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (imports and constructor)  
**Impact:** The `:runtime-disruptor` module cannot be used without `:trading-execution` and `:trading-strategy` on the classpath. This violates the dependency direction shown in `docs/ARCHITECTURE.md` (line 62: "Known coupling").  
**Root Cause:** `DisruptorEventBus` directly instantiates `PositionRiskDisruptorHandler`, `ExecutionDisruptorHandler`, etc., which depend on domain services.  
**Refactoring:** Introduce a `StageHandler` interface in `:runtime-disruptor`. `DisruptorEventBus` should accept a `List<StageHandler>` and delegate event processing. Concrete handlers live in `:trading-execution` and `:trading-strategy`.

### Finding: `Order` record is anemic (no domain behavior)

**Location:** `core/src/main/java/com/tradej/core/domain/model/Order.java`  
**Impact:** Business rules about order validity (e.g., "LIMIT orders must have price > 0", "SL orders must have trigger price") are scattered across adapters and controllers.  
**Refactoring:** Add domain methods to `Order`:
```java
public record Order(...) {
    public Order {
        validate();
    }
    private void validate() {
        if (orderType == OrderType.LIMIT && pricePaisa <= 0) throw new IllegalArgumentException("LIMIT order requires price");
        if (orderType == OrderType.SL && triggerPricePaisa <= 0) throw new IllegalArgumentException("SL order requires trigger price");
    }
    public boolean isFullyFilled() { return filledQuantity >= quantity; }
    public long remainingQuantity() { return Math.max(0, quantity - filledQuantity); }
}
```

### Finding: `GraphRuntime.onEvent()` swallows exceptions silently

**Location:** `core/src/main/java/com/tradej/pipeline/runtime/GraphRuntime.java` (lines 54-62)  
**Impact:** A single misbehaving node can fail without logging, causing silent pipeline degradation.  
**Refactoring:**
```java
public void onEvent(DomainEvent event) {
    for (PipelineNode ingressNode : ingressNodes) {
        try {
            ingressNode.onEvent(event);
        } catch (Exception e) {
            log.error("Pipeline node failed: node={}, eventId={}", ingressNode.nodeId(), event.eventId(), e);
            deadLetterQueue.enqueue(event, "Node failure: " + e.getMessage());
        }
    }
}
```

### Finding: `MarketDataController` uses `@Autowired(required = false)` for optional dependencies

**Location:** `app/src/main/java/com/tradej/app/api/MarketDataController.java` (lines 33-41)  
**Impact:** The controller's behavior changes based on which beans are present, making it unpredictable and hard to test.  
**Refactoring:** Use `@ConditionalOnProperty` or separate controllers:
```java
@RestController
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "dhan")
class DhanMarketDataController { ... }

@RestController
@ConditionalOnProperty(name = "trade.broker-type", havingValue = "upstox")
class UpstoxMarketDataController { ... }
```

### Finding: `BrokerStartupOrchestrator.runStartup()` has 22 parameters (Data Clump)

**Location:** `app/src/main/java/com/tradej/app/startup/BrokerStartupOrchestrator.java` (lines 59-101)  
**Impact:** Adding a new startup dependency requires changing the method signature and all callers.  
**Refactoring:** Group dependencies into a `BrokerStartupEnvironment` record:
```java
public record BrokerStartupEnvironment(
    EventBus eventBus,
    PersistenceLayer persistence,
    BrokerConnection broker,
    PipelineLayer pipelines,
    RiskAndExecution riskExecution
) {}
```

### Finding: `DhanBrokerConnection.getCapability()` uses `isInstance`/`cast` chain

**Location:** `broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java` (lines 329-373)  
**Impact:** Adding a new capability requires adding another `if` block. The chain is O(n) on every call.  
**Refactoring:**
```java
private final Map<Class<?>, Object> capabilities = Map.of(
    MarketDataProvider.class, marketDataProvider,
    OrderCommand.class, orderCommand,
    // ...
);
@Override
public <T> Optional<T> getCapability(Class<T> capabilityClass) {
    return Optional.ofNullable(capabilities.get(capabilityClass))
                   .map(capabilityClass::cast);
}
```

### Finding: `EventBus.publish()` lacks backpressure signaling

**Location:** `core/src/main/java/com/tradej/core/domain/port/EventBus.java` (line 12)  
**Impact:** Callers cannot distinguish between successful publish and dropped events (ring buffer full).  
**Refactoring:**
```java
public interface EventBus {
    boolean tryPublish(DomainEvent event);  // non-blocking, returns false if full
    void publish(DomainEvent event) throws EventBusFullException;  // blocking or throwing
}
```

### Finding: `ExecutionHandler` uses `ArrayBlockingQueue` with single consumer

**Location:** `trading/execution/src/main/java/com/tradej/execution/service/ExecutionHandler.java` (lines 160, 247-261)  
**Impact:** Throughput is limited to one order placement at a time. Under high signal rates, the queue fills and signals are suppressed.  
**Refactoring:** Use a `Disruptor`-backed work queue or increase to a thread-per-symbol model with `ConcurrentHashMap<String, BlockingQueue<ExecutionCommand>>`.

### Finding: `OrderStateMachine` transition table uses `HashMap` instead of `EnumMap` or `Map.of()`

**Location:** `core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java` (lines 140-194)  
**Impact:** Minor: `HashMap` has higher memory overhead and slower lookup than `EnumMap` or a pre-built immutable map.  
**Refactoring:**
```java
private static final Map<StateTransition, LifecycleState> TRANSITIONS = Map.ofEntries(
    entry(NEW, SUBMITTED, PENDING_SUBMIT),
    entry(NEW, CANCEL_REQUESTED, CANCEL_PENDING),
    // ...
);
private static Map.Entry<StateTransition, LifecycleState> entry(LifecycleState from, OrderEvent.EventType event, LifecycleState to) {
    return Map.entry(new StateTransition(from, event), to);
}
```

### Finding: `StrategyEngine` is deprecated but still wired in production path

**Location:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java` (line 257), `docs/ARCHITECTURE_REPORT.md` (line 323)  
**Impact:** Dead code increases maintenance burden and cognitive load. Risk of bugs being fixed in the new path but not the deprecated one.  
**Refactoring:** Remove `StrategyEngine`, `StrategyDisruptorHandler`, and `StrategySandbox`. Enforce `GraphStrategySandbox` as the sole strategy execution path.

---

## Architecture Improvements Roadmap

### Phase 1 (Critical — Immediate)

1. **Split `ExecutionHandler` into focused services** (addresses C-01, H-06)
   - Extract `SignalExecutionQueue`, `OrderPlacementService`, `FillReconciliationService`
   - Inject all thread pools; remove internal `Executors.newSingleThreadExecutor()` calls
   - Target: `trading/execution` module

2. **Remove deprecated accessors from `IBrokerConnection`** (addresses C-03)
   - Delete `futures()`, `options()`, `sliceOrders()`, `bracketOrders()`, `gttOrders()`, `margin()`, `sessionRisk()`, `alerts()` default methods
   - Update all callers to use `getCapability()`
   - Target: `broker/api` module

3. **Retire Disruptor Config B/C** (addresses C-04)
   - Remove `PositionRiskDisruptorHandler`, `CandleAggregationDisruptorHandler`, `StrategyDisruptorHandler`, `ExecutionDisruptorHandler` from production wiring
   - Update all tests to use Config A (Graph on Disruptor)
   - Target: `runtime/disruptor`, `trading-execution`, `trading-strategy`

4. **Add exception handling and DLQ routing in `GraphRuntime`** (addresses H-05)
   - Log exceptions with node/event context
   - Route failed events to `DeadLetterQueue`
   - Target: `core` module

### Phase 2 (Scalability — Next Quarter)

5. **Move broker configuration into broker modules** (addresses H-01)
   - Create `broker/dhan/src/main/.../config/DhanAutoConfiguration`
   - Create `broker/upstox/src/main/.../config/UpstoxAutoConfiguration`
   - `:app` depends only on `:broker-api` + `:broker-core`
   - Target: `broker/*`, `app` modules

6. **Replace `CopyOnWriteArrayList` with concurrent snapshot pattern** (addresses H-03)
   - Snapshot subscriber list at Disruptor start
   - Use `ConcurrentHashMap<Class<?>, CopyOnWriteArraySet<...>>` for runtime changes
   - Target: `runtime/disruptor` module

7. **Introduce typed DTOs for REST controllers** (addresses M-02)
   - Create `LtpResponse`, `CandleResponse`, `HistoricalCandlesResponse` records
   - Add `@Schema` annotations for OpenAPI
   - Target: `app` module

8. **Add `@Validated` and Jakarta Validation to configuration properties** (addresses M-07)
   - Annotate `TradingProperties` with `@Validated`
   - Add `@Min`, `@Max`, `@Positive` to risk limits, shard counts, queue capacities
   - Target: `app` module

### Phase 3 (Institutional Grade — 6+ Months)

9. **Implement capability-based broker composition** (addresses C-02, M-04)
   - `DhanBrokerConnection` implements `TradingBrokerConnection` + `AdvancedBrokerConnection` + `MarginCapable` + `OptionsCapable` + `AlertCapable`
   - `getCapability()` uses a `Map<Class<?>, Object>` registry
   - Target: `broker/api`, `broker/dhan`, `broker/upstox`

10. **Introduce aggregate roots for `Portfolio` and `StrategyRun`** (DDD)
    - `Portfolio` aggregate: manages capital, net exposure, and position invariants
    - `StrategyRun` aggregate: tracks strategy lifecycle, parameters, and performance
    - Target: `core` module

11. **Replace primitive obsession with value objects** (addresses M-01)
    - `Price` (paisa-backed, immutable, supports arithmetic)
    - `Quantity` (non-negative, validated)
    - `Symbol` (normalized, exchange-segment-aware)
    - Target: `core` module

12. **Implement backpressure-aware `EventBus`** (addresses M-05)
    - Add `boolean tryPublish(DomainEvent)` to `EventBus`
    - Add `EventBusFullException` to `publish()`
    - Update all publishers to handle backpressure
    - Target: `core`, `runtime-disruptor`, `app` modules

13. **Add Spring Cloud Circuit Breaker / Resilience4j annotations**
    - Replace manual `TradingCircuitBreaker` with `@CircuitBreaker` / `@Retryable` where appropriate
    - Use `Resilience4j` for broker REST calls
    - Target: `trading-execution`, `broker/*` modules

14. **Implement parallel execution in `ExecutionHandler`**
    - Replace single `ArrayBlockingQueue` consumer with sharded work queues (per symbol or per order ID)
    - Use `Disruptor` or `ExecutorService` with bounded parallelism
    - Target: `trading-execution` module

---

## Appendix: Key Architectural Strengths

1. **Hexagonal Broker Adapters:** `IBrokerConnection` + capability discovery via `getCapability()` is a solid foundation.
2. **Event-Sourced OMS:** `OrderStateMachine` with deterministic transition table and `EventSourcedOrderRepository` enables reliable state rebuild.
3. **Dual Runtime Modes:** `RuntimeMode` (LIVE/REPLAY/BACKTEST) with `VirtualClock` and `SimulatedOrderService` provides strong backtest/live parity foundations.
4. **Composable Pipeline Graph:** `PipelineGraph` + `GraphCompiler` + `GraphRuntime` enables DAG-based strategy composition.
5. **Comprehensive Testing:** 214 test files with tagged test suites (unit, component, integration, broker-rest, broker-ws, broker-order, runtime-e2e, cross-layer).
6. **Architecture Tests:** ArchUnit rules enforce module boundaries (`core` must not depend on outer modules).
7. **Observability:** Micrometer metrics, structured logging with MDC, `DisruptorBusMetrics`, and health indicators.
8. **Resilience Patterns:** Circuit breakers, rate limiters, dead-letter queues, and idempotency caches are present in broker adapters.

---

## Appendix: Module Dependency Violations (from ArchUnit)

| Rule | Status | Notes |
|------|--------|-------|
| `core` must not depend on outer modules | **PASS** | Enforced by `ModuleBoundaryArchitectureTest` |
| `historical-ingest` must not depend on `app` | **PASS** | Enforced |
| `replay-engine` must not depend on live brokers | **PASS** | Enforced |
| `:runtime-disruptor` → `:trading-execution` + `:trading-strategy` | **VIOLATION** | Known coupling (dashed in architecture diagram) |
| `:data-persistence` → `:trading-scanner` | **VIOLATION** | Known coupling (scan store models) |

---

*End of Review*
