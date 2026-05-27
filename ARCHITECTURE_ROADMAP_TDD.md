# Trade‑J Architectural Evolution — TDD Implementation Roadmap

> **Date:** May 26, 2026  
> **Based on:** Structural audit + external expert review (retail → institutional trading systems)  
> **Goal:** Transform Trade‑J from an advanced retail platform into an institutional-grade trading runtime  
> **Method:** Pure TDD — every architectural change starts with a failing test (RED), implements the green path (GREEN), then refactors (REFACTOR)

---

## Table of Contents

1. [Current State Assessment](#1-current-state-assessment)
2. [Priority Matrix & Phase Map](#2-priority-matrix--phase-map)
3. [Phase A — Foundation: Fix Existing Architecture (Weeks 1–3)](#3-phase-a--foundation-fix-existing-architecture-weeks-13)
4. [Phase B — OMS State Machine & Event Sourcing (Weeks 4–7)](#4-phase-b--oms-state-machine--event-sourcing-weeks-47)
5. [Phase C — Independent Risk Engine (Weeks 8–10)](#5-phase-c--independent-risk-engine-weeks-810)
6. [Phase D — Replay-First Runtime (Weeks 11–14)](#6-phase-d--replay-first-runtime-weeks-1114)
7. [Phase E — Observability & Operational Excellence (Weeks 15–16)](#7-phase-e--observability--operational-excellence-weeks-1516)
8. [Phase F — Strategy Isolation & Portfolio Engine (Weeks 17–20)](#8-phase-f--strategy-isolation--portfolio-engine-weeks-1720)
9. [Phase G — Feature Store & ML Pipeline (Weeks 21–24)](#9-phase-g--feature-store--ml-pipeline-weeks-2124)
10. [Phase H — Distributed & Deployment (Weeks 25–28)](#10-phase-h--distributed--deployment-weeks-2528)
11. [Appendix: SOLID Audit Tracker](#11-appendix-solid-audit-tracker)
12. [Appendix: Module Dependency Map](#12-appendix-module-dependency-map)

---

## 1. Current State Assessment

### What Is Excellent (Keep As-Is)

| Component | Strength |
|-----------|----------|
| Java 21 + Disruptor | Low-latitude event pipeline, BusySpinWaitStrategy |
| DuckDB + Chronicle Queue | Hot-path + analytical persistence combo |
| CQRS (`OrderCommand` / `OrderQuery`) | Clean command-query separation |
| Port/Adapter (Hexagonal) | `IBrokerConnection`, `MarketDataProvider`, etc. |
| Paise-based pricing | `PriceMath.toPaisa()` — no floating point |
| Multi-module Gradle setup | 8 well-partitioned modules |
| Domain events (`TickReceived`, `CandleClosed`, etc.) | 20 event types, ready for event sourcing |
| `StrategyPlugin` SPI | ServiceLoader-based plugin system |
| Event-driven `DisruptorEventBus` | Pipeline: risk → candle → strategy → execution → dispatch |

### What Needs Fixing Now (Phase A)

| # | Issue | Severity | File(s) |
|---|-------|----------|---------|
| DIP-1 | 8+ classes depend on `InMemoryInstrumentResolver` (concrete) not `InstrumentResolver` (interface) | 🔴 High | `DhanMarketDataProvider`, `DhanOrderCommandAdapter`, `DhanOrderQueryAdapter`, `DhanPortfolioProvider`, `DhanWebSocketMultiplexer`, `DhanFuturesAdapter`, `DhanOptionsAdapter`, `DhanBrokerConnection` |
| DI-1 | `DhanBrokerConnection` creates its own adapters (hardcoded `new`) | 🔴 High | `DhanBrokerConnection.java` |
| SRP-1 | `TradingRuntimeConfiguration` wires EVERYTHING — monolith | 🟡 Medium | `TradingRuntimeConfiguration.java` |
| SRP-2 | `DhanBrokerConnection` = factory + facade + loader — 3 responsibilities | 🟡 Medium | `DhanBrokerConnection.java` |
| OCP-1 | `DhanBrokerCapabilities.live()` hardcodes all venue configs | 🟡 Medium | `DhanBrokerCapabilities.java` |
| OCP-2 | `DhanSdkMapper` switch statements — closed to new types | 🟡 Medium | `DhanSdkMapper.java` |
| PERF-1 | Double-stream in `DhanOrderQueryAdapter.getExecutedPricePaisa()` | 🟢 Low | `DhanOrderQueryAdapter.java` |
| CAL-1 | No exchange calendar engine (holidays, MCX sessions, Muhurat trading) | 🟡 Medium | `trade-core/.../calendar/` |
| CFG-1 | No layered configuration hierarchy (global/broker/strategy/risk YAML) | 🟡 Medium | `trade-app/src/main/resources/` |

### External Expert's Priority Gaps

| Priority | Gap | Our Phase |
|----------|-----|-----------|
| 🔴 HIGH | OMS state machine | Phase B |
| 🔴 HIGH | Event sourcing | Phase B |
| 🔴 HIGH | Risk engine authority | Phase C |
| 🔴 HIGH | Replay-first runtime | Phase D |
| 🔴 HIGH | Observability | Phase E |
| 🔴 HIGH | Deterministic state recovery | Phase B + D |
| 🟡 MEDIUM | Strategy isolation | Phase F |
| 🟡 MEDIUM | Portfolio engine | Phase F |
| 🟡 MEDIUM | Feature store | Phase G |
| 🟢 LOW | ML inference layer | Phase G |

---

## 2. Priority Matrix & Phase Map

```
Now (Phase A)                    Near-term (Phase B-D)              Future (Phase F-H)
───────────────────────────      ──────────────────────────         ────────────────────────
Fix DIP violations               OMS state machine                  Strategy sandbox
Fix DI hardcoding                Event sourcing                     Portfolio engine
Split @Configuration monolith    Independent risk engine            Feature store
Fix concrete coupling            Replay architecture                ML pipeline
Add @Service annotations         Prometheus + Micrometer            Distributed deployment
Spring Boot → hot-path boundary  Deterministic clock                Binary tick store
```

### Module Change Heatmap

```
Module         A  B  C  D  E  F  G  H
─────────────────────────────────────
trade-core     █  █  █  █     █  █
trade-broker-api █  █  █  █        █
trade-broker-dhan █  █  █  █     █
trade-execution █  █  █  █  █  █  █
trade-strategy █     █  █  █  █  █
trade-disruptor    █     █  █  █
trade-persistence    █  █  █     █  █
trade-app      █  █  █  █  █  █  █  █
```

---

## 3. Phase A — Foundation: Fix Existing Architecture

> **Duration:** Weeks 1–3  
> **Goal:** Pay down architectural debt before building institutional features  
> **Principle:** Every change starts with a RED test. No breaking changes to public API.

### A.1 — Refactor `InstrumentResolver` Interface (DIP Fix)

**RED: Write test proving adapters can work with `InstrumentResolver` interface**

```java
// trade-broker-api/src/test/java/.../port/InstrumentResolverContractTest.java
abstract class InstrumentResolverContractTest {
    protected abstract InstrumentResolver createResolver();

    @Test
    void resolvesBySecurityId() {
        InstrumentResolver resolver = createResolver();
        // This method does NOT exist on InstrumentResolver yet
        Instrument result = resolver.resolveBySecurityId("3045");
        assertEquals("SBIN", result.canonicalSymbol());
    }
}
```

**GREEN: Extend `InstrumentResolver` interface with Dhan-generic methods**

File: `trade-broker-api/src/main/java/.../port/InstrumentResolver.java`

```java
public interface InstrumentResolver {
    Instrument resolve(InstrumentKey key);
    Instrument getBySymbol(InstrumentKey key);
    List<Instrument> allInstruments();

    // NEW — moved from InMemoryInstrumentResolver concrete class
    Instrument resolveBySecurityId(String securityId);
    Instrument requireDefinition(InstrumentKey key);
    Instrument resolvePayload(Object payload);
    boolean isLoaded();
    int catalogSize();
}
```

Then update `InMemoryInstrumentResolver` to delegate to `DhanInstrumentCatalog` (which implements `InstrumentResolver`).

**Files changed:**
- `trade-broker-api/.../port/InstrumentResolver.java` — add methods
- `trade-broker-dhan/.../adapter/InMemoryInstrumentResolver.java` — delegate, don't duplicate
- `trade-broker-dhan/.../instrument/DhanInstrumentCatalog.java` — already implements `InstrumentResolver` ✓
- All 8 adapters — change field type from `InMemoryInstrumentResolver` → `InstrumentResolver`

**Test:** `InstrumentResolverContractTest` + verify all 8 adapters compile with interface

### A.2 — Decouple `DhanBrokerConnection` Adapter Wiring

**RED: Test that adapters can be injected from outside**

```java
// trade-broker-dhan/src/test/java/.../DhanBrokerConnectionInjectionTest.java
@Test
void acceptsInjectedAdapters() {
    MarketDataProvider mockMarketData = mock(MarketDataProvider.class);
    // ... mock other adapters
    DhanBrokerConnection conn = new DhanBrokerConnection(
        mockMarketData, mockFutures, mockOptions,
        mockOrderCommand, mockOrderQuery, mockPortfolio,
        mockResolver, mockWebSocket
    );
    assertSame(mockMarketData, conn.marketData());
}
```

**GREEN: Add multi-adapter constructor, keep old constructor for backward compat**

```java
// DhanBrokerConnection.java — new constructor for DI
public DhanBrokerConnection(
    MarketDataProvider marketDataProvider,
    FuturesProvider futuresProvider,
    OptionsProvider optionsProvider,
    OrderCommand orderCommand,
    OrderQuery orderQuery,
    PortfolioProvider portfolioProvider,
    InstrumentResolver instrumentResolver,
    WebSocketMultiplexer webSocketMultiplexer
) { ... }

// Old constructor — @Deprecated, kept for backward compat only.
// New code should inject adapters via the multi-adapter constructor or
// Spring-managed beans from TradingRuntimeConfiguration.
@Deprecated
public DhanBrokerConnection(
    DhanConnectionSettings settings,
    MultiBucketRateLimiter rateLimiter,
    IdempotencyCachePort idempotencyCachePort
) {
    this(
        new DhanMarketDataProvider(...),
        new DhanFuturesAdapter(...),
        new DhanOptionsAdapter(...),
        new DhanOrderCommandAdapter(...),
        new DhanOrderQueryAdapter(...),
        new DhanPortfolioProvider(...),
        new InMemoryInstrumentResolver(),
        new DhanWebSocketMultiplexer(...)
    );
}
```

**Update `TradingRuntimeConfiguration`:**
- Extract `MarketDataProvider`, `FuturesProvider`, `OptionsProvider`, `OrderCommand`, `OrderQuery`, `PortfolioProvider` as individual `@Bean` methods
- Inject them into `DhanBrokerConnection` via the new constructor

**Test:** Verify all beans created in `TradingRuntimeConfiguration` are valid + injected correctly. Run unit tests.

### A.3 — Split `TradingRuntimeConfiguration` Monolith

**RED: Test that each domain config loads independently**

```java
// trade-app/src/test/java/.../config/BrokerConfigurationTest.java
@SpringBootTest(classes = BrokerConfiguration.class)
class BrokerConfigurationTest {
    @Autowired IBrokerConnection brokerConnection;
    @Autowired MultiBucketRateLimiter rateLimiter;
    @Autowired IdempotencyCachePort idempotencyCache;

    @Test void allBrokerBeansCreated() { ... }
}
```

Repeat for `RiskConfiguration`, `EventBusConfiguration`, `StrategyConfiguration`, `PersistenceConfiguration`, `StartupConfiguration`.

**GREEN: Split into 6 configuration classes**

| Config Class | Beans |
|--------------|-------|
| `BrokerConfiguration` | `MultiBucketRateLimiter`, `CaffeineIdempotencyCache`, `IBrokerConnection`, `BrokerCapabilities` |
| `RiskConfiguration` | `RiskLimits`, `PositionRiskHandler` |
| `StrategyConfiguration` | `CandleAggregationService`, `StrategyEngine` |
| `EventBusConfiguration` | `TradingCircuitBreaker`, `OrderManagementService`, `ExecutionHandler`, `EventBus` (DisruptorEventBus) |
| `PersistenceConfiguration` | `ChronicleAuditLogWriter`, `DuckDbEventStore`, `OrderReconciler` |
| `StartupConfiguration` | `ApplicationRunner` + startup orchestration |

Each config imports only the beans it needs via `@Import` or method injection.

**REFACTOR:** Use `@Profile` annotations so each config can be toggled independently for testing.

### A.4 — Add `@Service` Annotations & Component Scan

**RED: Test component scanning discovers services**

```java
// trade-app/src/test/java/.../config/ServiceScanTest.java
@Test
void strategyBeansAreDiscoverable() {
    Set<Class<?>> candidates = new Reflections("com.tradej").getTypesAnnotatedWith(Service.class);
    assertTrue(candidates.contains(CandleAggregationService.class));
}
```

**GREEN: Add `@Service` to:**
- `CandleAggregationService`
- `StrategyEngine`
- `TradingCircuitBreaker`
- `OrderManagementService`
- `ExecutionHandler`
- `PositionRiskHandler`
- `OrderReconciler`
- `DuckDbEventStore`
- `ChronicleAuditLogWriter`

**REFACTOR:** Add `@ComponentScan("com.tradej")` to `TradingApplication` and remove duplicate `@Bean` definitions from `TradingRuntimeConfiguration`.

### A.5 — Config-Driven Broker Capabilities (OCP Fix)

**RED: Test capabilities loaded from YAML**

```java
@Test
void capabilitiesLoadedFromProperties() {
    BrokerCapabilities caps = new PropertiesBrokerCapabilities(testProperties());
    assertTrue(caps.venue(ExchangeSegment.NSE_FNO).supportsFeedMode(FeedMode.DEPTH_20));
}
```

**GREEN:**
- Create `PropertiesBrokerCapabilities` that reads venue config from `application.yml`
- Add `trade.venues` section to `application.yml`
- `DhanBrokerCapabilities.live()` becomes a default fallback

### A.9 — Optimize Double-Stream in `getExecutedPricePaisa`

**RED: Test that VWAP calculation is correct and single-pass**

```java
@Test
void vwapCalculatedInSinglePass() {
    OrderQuery query = new DhanOrderQueryAdapter(...);
    OptionalLong vwap = query.getExecutedPricePaisa("order-1");
    assertEquals(150_00L, vwap.getAsLong()); // 150.00 paise
}
```

**GREEN:** Replace two streams with a single reducing loop:

```java
public OptionalLong getExecutedPricePaisa(String orderId) {
    List<Trade> trades = getTradeBook().stream()
        .filter(trade -> orderId.equals(trade.orderId()))
        .toList();
    long totalValue = 0L, totalQuantity = 0L;
    for (Trade trade : trades) {
        totalValue += trade.pricePaisa() * trade.quantity();
        totalQuantity += trade.quantity();
    }
    return totalQuantity == 0 ? OptionalLong.empty()
        : OptionalLong.of(totalValue / totalQuantity);
}
```

---

## 4. Phase B — OMS State Machine & Event Sourcing

> **Duration:** Weeks 4–7  
> **Goal:** Make order lifecycle deterministic and replayable via event sourcing  
> **Expert refs:** #2 OMS, #3 Event Sourcing, #6 Replay

### B.1 — OMS State Machine

**RED: Test deterministic order lifecycle transitions**

```java
// trade-core/src/test/java/.../oms/OrderLifecycleTest.java
@Test
void orderProceedsThroughDeterministicStates() {
    OrderStateMachine oms = new OrderStateMachine(orderId, "SBIN", 100);
    assertEquals(OrderStatus.NEW, oms.currentStatus());

    oms.on(OrderSubmitted.event(orderId));
    assertEquals(OrderStatus.PENDING_SUBMIT, oms.currentStatus());

    oms.on(OrderAcknowledged.event(orderId, "exchange-123"));
    assertEquals(OrderStatus.SUBMITTED, oms.currentStatus());

    oms.on(OrderPartiallyFilled.event(orderId, 25, 150_00L));
    assertEquals(OrderStatus.PARTIALLY_FILLED, oms.currentStatus());

    oms.on(OrderFullyFilled.event(orderId, 100, 150_50L));
    assertEquals(OrderStatus.FILLED, oms.currentStatus());
}

@Test
void invalidTransitionThrows() {
    OrderStateMachine oms = new OrderStateMachine(orderId, "SBIN", 100);
    assertThrows(IllegalStateException.class, () ->
        oms.on(OrderFullyFilled.event(orderId, 100, 150_00L))); // Can't fill without submitting
}
```

**GREEN: Create `OrderStateMachine`**

File: `trade-core/src/main/java/.../domain/oms/OrderStateMachine.java`

```java
public final class OrderStateMachine {
    public enum LifecycleState {
        NEW,
        PENDING_SUBMIT,
        SUBMITTED,
        PARTIALLY_FILLED,
        FILLED,
        CANCEL_PENDING,
        CANCELLED,
        REJECTED,
        EXPIRED
    }

    private LifecycleState state = LifecycleState.NEW;
    private final Map<StateTransition, LifecycleState> transitions = ...;

    public synchronized void on(OrderEvent event) {
        LifecycleState next = transitions.get(new StateTransition(state, event.type()));
        if (next == null) throw new IllegalStateException(...);
        state = next;
    }
}
```

Define event types:
- `OrderSubmitted` (correlationId → orderId)
- `OrderAcknowledged` (orderId → exchangeOrderId)
- `OrderPartiallyFilled`, `OrderFullyFilled`
- `OrderCancelled`, `OrderRejected`, `OrderExpired`
- `CancelRequested` (PENDING_CANCEL)

### B.2 — Event-Sourced Order Repository

**RED: Test rebuilding state from event stream**

```java
@Test
void rebuildsStateFromEventStream() {
    List<OrderEvent> events = List.of(
        new OrderSubmitted("corr-1", "ORD-1"),
        new OrderAcknowledged("ORD-1", "EX-1"),
        new OrderPartiallyFilled("ORD-1", 25, 150_00L),
        new OrderFullyFilled("ORD-1", 100, 150_50L)
    );
    EventSourcedOrderRepository repo = new EventSourcedOrderRepository();
    repo.appendAll(events);

    OrderProjection order = repo.rebuild("ORD-1");
    assertEquals(OrderStatus.FILLED, order.status());
    assertEquals(100, order.filledQuantity());
}
```

**GREEN: Create `EventSourcedOrderRepository`**

```java
public final class EventSourcedOrderRepository {
    private final ChronicleQueue eventLog; // Persisted event store
    private final Map<String, List<OrderEvent>> pending = new ConcurrentHashMap<>();

    public void append(OrderEvent event) {
        eventLog.createAppender().writeText(serialize(event));
        pending.computeIfAbsent(event.orderId(), k -> new ArrayList<>()).add(event);
    }

    public OrderProjection rebuild(String orderId) {
        OrderStateMachine sm = new OrderStateMachine(...);
        for (OrderEvent event : pending.getOrDefault(orderId, List.of())) {
            sm.on(event);
        }
        return sm.toProjection();
    }
}
```

### B.3 — Wire OSM into Execution Handler

**RED: Test execution handler uses OSM**

```java
@Test
void executionHandlerUpdatesOmsOnFill() {
    ExecutionHandler handler = new ExecutionHandler(omsRepo, orderMgmt, circuitBreaker);
    List<DomainEvent> emitted = new ArrayList<>();

    handler.onDomainEvent(new OrderFilled(metadata, order, fills), emitted::add);

    OrderProjection proj = omsRepo.rebuild("ORD-1");
    assertEquals(OrderStatus.FILLED, proj.status());
}
```

**GREEN:** Modify `ExecutionHandler.onDomainEvent()` to route all `Order*` events through the OSM.

### B.4 — Deterministic Order Reducer

**RED: Test broker vs. local state reconciliation**

```java
@Test
void reconcilerDetectsMismatch() {
    // Local state says filled 100 shares
    // Broker says filled 75 shares
    OrderReconciler reconciler = new OrderReconciler(omsRepo, brokerConnection);
    List<DomainEvent> emitted = new ArrayList<>();

    reconciler.reconcileAll(emitted::add);

    assertInstanceOf(PositionMismatch.class, emitted.get(0));
}
```

**GREEN:** Enhance `OrderReconciler` to compare `EventSourcedOrderRepository` against broker's order book.

---

## 5. Phase C — Independent Risk Engine

> **Duration:** Weeks 8–10  
> **Goal:** Risk engine as independent authority that no strategy can bypass  
> **Expert ref:** #5 Risk Engine Authority

### C.1 — `OrderIntent` Protocol

**RED: Test that strategies produce intents, not orders**

```java
@Test
void strategyProducesIntent() {
    StrategyPlugin strategy = new MyBreakoutStrategy();
    OrderIntent intent = strategy.evaluate(tickReceived);
    assertInstanceOf(OrderIntent.class, intent);
    assertNotNull(intent.symbol());
    assertTrue(intent.quantity() > 0);
    // Intent does NOT contain broker-specific fields
    assertNull(intent.orderType()); // Strategy shouldn't set order type
}
```

**GREEN: Create `OrderIntent`**

```java
public record OrderIntent(
    String signalId,
    String symbol,
    Side side,
    long quantity,
    long entryPricePaisa,
    Long stopLossPaisa,
    Long takeProfitPaisa,
    Map<String, Object> attributes
) {}
```

Modify `StrategyPlugin` interface to return `Optional<OrderIntent>` instead of working with `CandleClosed`.

### C.2 — Risk Engine as Standalone Authority

**RED: Test risk engine rejects and accepts intents independently**

```java
@Test
void riskEngineRejectsIntentExceedingMaxDailyLoss() {
    RiskEngine riskEngine = new RiskEngine(riskLimits, positionRiskHandler);
    riskEngine.onIntent(tradeOpened); // Opens a losing trade

    List<RiskVerdict> verdicts = new ArrayList<>();
    riskEngine.evaluate(new OrderIntent("sig-3", "SBIN", BUY, 10, 750_00L, null, null, Map.of()),
        verdict -> { verdicts.add(verdict); return true; });

    assertEquals(Verdict.REJECTED, verdicts.get(0).verdict());
    assertEquals("Max daily loss exceeded", verdicts.get(0).reason());
}

@Test
void riskEngineApprovesValidIntent() {
    RiskEngine riskEngine = new RiskEngine(riskLimits, positionRiskHandler);
    List<RiskVerdict> verdicts = new ArrayList<>();

    riskEngine.evaluate(validIntent, verdict -> { verdicts.add(verdict); return true; });

    assertEquals(Verdict.APPROVED, verdicts.get(0).verdict());
}
```

**GREEN: Create `RiskEngine`**

```java
public final class RiskEngine {
    private final RiskLimits limits;
    private final PositionRiskHandler positionHandler;
    private final KillSwitch killSwitch;

    public void evaluate(OrderIntent intent, Consumer<RiskVerdict> downstream) {
        // 1. Kill switch check
        if (killSwitch.isEngaged()) { downstream.accept(RiskVerdict.rejected(...)); return; }

        // 2. Position limits
        if (positionHandler.openTrades() >= limits.maxOpenPositions()) { ... }

        // 3. Daily loss check
        if (positionHandler.realizedLossPaisa() >= limits.maxDailyLossPaisa()) { ... }

        // 4. Consecutive loss check
        if (positionHandler.consecutiveLosses() >= limits.maxConsecutiveLosses()) { ... }

        // 5. Order value check
        if (intent.quantity() * intent.entryPricePaisa() > limits.maxOrderValuePaisa()) { ... }

        // 6. Margin check (NEW — integrate with balance provider)
        if (!marginProvider.hasSufficientMargin(...)) { ... }

        // 7. Volatility scaling (NEW)
        long adjustedQuantity = volatilityScaler.scale(intent.symbol(), intent.quantity());

        downstream.accept(RiskVerdict.approved(intent, adjustedQuantity));
    }
}
```

### C.3 — Refactor Flow: Strategy → Risk → Execution

```
CURRENT FLOW:
SignalGenerated → PositionRiskHandler → SignalPendingExecution → ExecutionHandler → Order

NEW FLOW:
StrategyPlugin → OrderIntent → RiskEngine → RiskVerdict → ExecutionHandler → OSM → Broker
```

**Files changed:**
- `PositionRiskHandler.qualifySignal()` → delegate to `RiskEngine`
- `ExecutionHandler` — receive `RiskVerdict` instead of `SignalPendingExecution`
- `DisruptorEventBus` — add `RiskEngine` as a stage between strategy and execution

---

## 6. Phase D — Replay-First Runtime

> **Duration:** Weeks 11–14  
> **Goal:** Same pipeline for live, backtest, replay, simulation  
> **Expert ref:** #10 Replay-First Architecture

### D.1 — Inject `Clock` Everywhere

**RED: Test that time-dependent code accepts injectable clock**

```java
@Test
void candleAggregationUsesInjectedClock() {
    Clock testClock = Clock.fixed(Instant.parse("2026-05-26T09:30:00Z"), ZoneOffset.UTC);
    CandleAggregationService agg = new CandleAggregationService(testClock);
    agg.onDomainEvent(tickReceived, downstream);

    CandleDeveloping dev = (CandleDeveloping) downstream.get(0);
    assertEquals(9_30_000, dev.candle().startTimeMs()); // Fixed, not System.currentTimeMillis()
}
```

**GREEN:** Replace all `System.currentTimeMillis()` and `Instant.now()` with injectable `Clock`:

| Class | Before | After |
|-------|--------|-------|
| `CandleAggregationService` | `System.currentTimeMillis()` | `clock.millis()` |
| `TradingCircuitBreaker` | `System.currentTimeMillis()` | `clock.millis()` |
| `DisruptorEventBus` | `System.currentTimeMillis()` | `clock.millis()` |
| `ExecutionHandler` | Thread sleep | `clock.instant()` |
| `DhanWebSocketMultiplexer` | `System.currentTimeMillis()` | `clock.millis()` |
| `EventMetadata.root()` | `Instant.now()` | Clock parameter |
| `PositionRiskHandler` | `System.currentTimeMillis()` | `clock.millis()` |
| `DhanPayloadNormalizer` | `System.currentTimeMillis()` (fallback) | `clock.millis()` |
| `DhanSdkMapper` | `System.currentTimeMillis()` (timestamp fallback) | `clock.millis()` |

**REFACTOR:** Create `TimeProvider` interface (wraps `Clock`) for easier mocking across module boundaries.

### D.2 — Replay-Aware Event Bus Mode

**RED: Test replay events produce identical state**

```java
@Test
void replayProducesIdenticalState() {
    List<DomainEvent> liveEvents = loadLiveSession("2026-05-25");
    EventBus replayBus = new DisruptorEventBus(riskHandler, candleAgg, strategy, execution);

    for (DomainEvent event : liveEvents) {
        replayBus.publish(event);
    }

    // Replay mode: no broker connection, no real order placement
    assertFalse(replayBus.isLive());
    assertEquals(42, replayBus.processedCount());
}
```

**GREEN:**
- Add `REPLAY` vs `LIVE` mode to `DisruptorEventBus`
- In REPLAY mode, downstream handlers get events but execution is blocked from actual broker calls
- Create `ReplayEventStore` wrapper that streams from Chronicle Queue

### D.3 — Backtest Compatibility Layer

**RED: Test strategy produces same results in backtest and live**

```java
@Test
void strategyDeterministicAcrossModes() {
    List<CandleClosed> candles = loadCandles("SBIN", "2026-05-01", "2026-05-26");
    StrategyPlugin plugin = new MyBreakoutStrategy();

    // Run in backtest mode
    List<OrderIntent> backtestResults = runBacktest(plugin, candles, riskEngine);

    // Run in replay mode (same candles, same sequence)
    List<OrderIntent> replayResults = runReplay(plugin, candles, riskEngine);

    assertEquals(backtestResults, replayResults); // Identical
}
```

**GREEN:** Create `BacktestEngine` that feeds historical candles through the same Disruptor pipeline with a virtual clock.

---

## 7. Phase E — Observability & Operational Excellence

> **Duration:** Weeks 15–16  
> **Goal:** Production-grade monitoring, metrics, and tracing  
> **Expert ref:** #9 Observability

### E.1 — Micrometer Metrics

**RED: Test that key metrics are recorded**

```java
@Test
void tickLatencyMetricsRecorded() {
    MeterRegistry registry = new SimpleMeterRegistry();
    MarketDataProvider provider = new ObservableMarketDataProvider(realProvider, registry);

    provider.getQuote(instrumentKey);

    Timer timer = registry.get("dhan.quote.latency").timer();
    assertTrue(timer.totalTime(TimeUnit.MILLISECONDS) > 0);
}
```

**GREEN: Add Micrometer to Gradle dependencies**

```groovy
// build.gradle (root or trade-app)
implementation 'io.micrometer:micrometer-registry-prometheus'
implementation 'io.micrometer:micrometer-core'
```

Create `ObservableMarketDataProvider` decorator:

```java
public final class ObservableMarketDataProvider implements MarketDataProvider {
    private final MarketDataProvider delegate;
    private final Timer quoteTimer;

    public Quote getQuote(InstrumentKey key) {
        return quoteTimer.record(() -> delegate.getQuote(key));
    }
}
```

**Key metrics to instrument:**

| Metric | Type | Where |
|--------|------|-------|
| `dhan.quote.latency` | Timer | `DhanMarketDataProvider` |
| `dhan.order.latency` | Timer | `DhanOrderCommandAdapter` |
| `dhan.order.place.count` | Counter | `DhanOrderCommandAdapter` |
| `dhan.websocket.delay` | Gauge | `DhanWebSocketMultiplexer` |
| `disruptor.queue.depth` | Gauge | `DisruptorEventBus` |
| `disruptor.event.latency` | Timer | `DisruptorEventBus` |
| `risk.engine.verdicts` | Counter (tagged) | `RiskEngine` |
| `strategy.processing.time` | Timer | `StrategyEngine` |
| `oms.state.transitions` | Counter (tagged) | `OrderStateMachine` |
| `execution.queue.depth` | Gauge | `ExecutionHandler` |

### E.2 — De-duplicated Event Logging

Add structured JSON logging via Logback with MDC enrichment:

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
</appender>
```

**Gradle dependency:** `implementation 'net.logstash.logback:logstash-logback-encoder:8.0'`

**Log context per event:** `eventId`, `correlationId`, `eventType`, `symbol`, `latencyMs`

### E.3 — Health Endpoint Enrichment

Enhance `BrokerHealthIndicator`:

```java
@Override
public Health health() {
    return Health.up()
        .withDetail("broker", "dhan")
        .withDetail("websocketConnected", websocket.isConnected())
        .withDetail("circuitBreakerOpen", circuitBreaker.isOpen())
        .withDetail("subscriptions", websocket.subscriptions().size())
        .withDetail("omsActiveOrders", omsRepo.activeCount())
        .withDetail("omsRejectedToday", omsRepo.rejectedCount())
        .withDetail("riskDailyLossPaisa", riskEngine.realizedLossPaisa())
        .withDetail("eventBusQueueUtilization", disruptorEventBus.ringBufferUtilization())
        .build();
}
```

---

## 8. Phase F — Strategy Isolation & Portfolio Engine

> **Duration:** Weeks 17–20  
> **Goal:** Multi-strategy support with sandbox isolation and portfolio-level risk  
> **Expert ref:** #4 Strategy Sandbox, #11 Portfolio Engine

### F.1 — Strategy Sandbox

**RED: Test that one strategy cannot crash another**

```java
@Test
void failingStrategyDoesNotBlockHealthyOne() throws Exception {
    StrategyPlugin failing = spy(new FailingStrategy());
    StrategyPlugin healthy = spy(new HealthyStrategy());

    StrategySandbox sandbox = new StrategySandbox(List.of(failing, healthy));
    sandbox.onDomainEvent(tickReceived, downstream);

    verify(healthy, times(1)).onCandleClosed(any());
    verify(failing, atLeast(1)).onCandleClosed(any());
    assertTrue(healthy.lastEvaluationTimeMs() > 0);
}
```

**GREEN:** Create per-strategy `ExecutorService` with bounded queue and timeout:

```java
public final class StrategySandbox {
    private final Map<String, StrategyPlugin> plugins;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        plugins.forEach((name, plugin) ->
            executor.submit(() -> {
                try {
                    plugin.onCandleClosed((CandleClosed) event)
                        .ifPresent(intent -> {
                            intent = intent.withStrategyName(name);
                            downstream.accept(intent);
                        });
                } catch (Exception ex) {
                    log.error("Strategy {} failed", name, ex);
                    downstream.accept(new StrategyError(name, ex.getMessage()));
                }
            })
        );
    }
}
```

### F.2 — Portfolio Engine

**RED: Test portfolio-level allocation across strategies**

```java
@Test
void portfolioAllocatesCapitalAcrossStrategies() {
    PortfolioEngine portfolio = new PortfolioEngine(1_000_000_00L); // ₹10L capital
    portfolio.registerStrategy("breakout", 0.5);  // 50% allocation
    portfolio.registerStrategy("mean-reversion", 0.3); // 30%
    portfolio.registerStrategy("hedge", 0.2); // 20%

    assertEquals(500_000_00L, portfolio.allocatedCapital("breakout"));
    assertEquals(300_000_00L, portfolio.allocatedCapital("mean-reversion"));
}
```

**GREEN: Create `PortfolioEngine`**

```java
public final class PortfolioEngine {
    private final long totalCapital;
    private final Map<String, Double> allocations; // strategy → fraction
    private final Map<String, Long> usedCapital = new ConcurrentHashMap<>();

    public boolean hasCapacity(String strategy, long requiredPaisa) {
        long allocated = (long) (totalCapital * allocations.get(strategy));
        return (allocated - usedCapital.getOrDefault(strategy, 0L)) >= requiredPaisa;
    }

    // Exposure netting across strategies
    public long netExposure(String symbol) {
        return openPositions.values().stream()
            .filter(p -> p.symbol().equals(symbol))
            .mapToLong(p -> p.side() == BUY ? p.notional() : -p.notional())
            .sum();
    }
}
```

### F.3 — Wire PortfolioEngine into Risk Pipeline

Update `RiskEngine` to include portfolio-level checks:

```java
riskEngine.evaluate(intent, downstream) {
    // ... existing checks ...
    // Portfolio check
    if (!portfolioEngine.hasCapacity(intent.strategyName(), notional)) {
        downstream.accept(RiskVerdict.rejected("Portfolio allocation exceeded"));
        return;
    }
    // Net exposure check
    if (Math.abs(portfolioEngine.netExposure(intent.symbol())) > exposureLimit) {
        downstream.accept(RiskVerdict.rejected("Net exposure limit exceeded"));
        return;
    }
}
```

---

## 9. Phase G — Feature Store & ML Pipeline

> **Duration:** Weeks 21–24  
> **Goal:** Structured feature generation, storage, and ML inference  
> **Expert ref:** #6 Feature Store, #17 ML Pipeline

### G.1 — Time-Series Feature Store

**RED: Test feature generation and retrieval**

```java
@Test
void featuresGeneratedAndStored() {
    FeatureStore store = new DuckDbFeatureStore(databasePath);
    store.feed(tickReceived);
    store.feed(tickReceived);
    store.feed(candleClosed);

    FeatureVector features = store.getFeatures("SBIN");
    assertTrue(features.rsi() > 0);
    assertTrue(features.ema9() > 0);
    assertTrue(features.volumeImbalance() >= 0);
}
```

**GREEN:**
- Create `FeatureStore` interface with `FeatureVector` return type
- Implement `DuckDbFeatureStore` using DuckDB's analytical capabilities
- Features: RSI, EMA5/9/21, VWAP, volume profile, volatility, bid-ask spread, order imbalance
- Store features partitioned by `symbol/date/interval`

### G.2 — Feature Generation Pipeline

**RED: Test deterministic feature computation from candles**

```java
@Test
void featuresComputedFromCandles() {
    List<Candle> candles = loadCandles("SBIN", "1d", 100);
    FeatureGenerator gen = new FeatureGenerator();
    FeatureVector features = gen.compute(candles);
    assertEquals(100, features.lookback());
    assertTrue(features.rsi() >= 0 && features.rsi() <= 100);
}
```

**GREEN:** Create `FeatureGenerator` as pure function from `List<Candle> → FeatureVector`. No side effects.

### G.3 — ML Inference Adapter (Optional)

**RED: Test model inference**

```java
@Test
void modelProducesSignalFromFeatures() {
    ModelRegistry registry = new ModelRegistry(modelPath);
    MLInferenceEngine engine = new MLInferenceEngine(registry);

    InferenceResult result = engine.evaluate(featureVector);
    assertEquals(Side.BUY, result.side());
    assertTrue(result.confidence() > 0.7);
}
```

**GREEN:** Create `MLInferenceEngine` that:
- Loads ONNX/TorchScript models from `ModelRegistry`
- Converts `FeatureVector` → model input tensor
- Runs inference
- Converts output → `InferenceResult` (side, quantity, confidence)

---

## 10. Phase H — Distributed & Deployment

> **Duration:** Weeks 25–28  
> **Goal:** Multi-instance deployment readiness, hot/cold path separation  
> **Expert ref:** #7 Spring Boot Hot Path, #8 Actor/Bus, #13 Persistence, #18 Frontend

### H.1 — Spring Boot → Hot Path Boundary

**RED: Test hot path runs without Spring proxies**

```java
@Test
void tickPipelineBypassesSpring() {
    MarketDataProvider mdProvider = new DhanMarketDataProvider(...);
    CandleAggregationService agg = new CandleAggregationService(clock);
    ExecutionHandler exec = new ExecutionHandler(omsRepo, orderMgmt, circuitBreaker);

    // Pipeline is pure Java, no Spring proxies involved
    warmup(mdProvider, instrumentKey); // JVM warmup iteration
    long start = System.nanoTime();
    mdProvider.getQuote(instrumentKey);
    long elapsed = System.nanoTime() - start;
    // Benchmark-style assertion: log warning instead of failing in CI.
    // Adjust threshold if running on constrained hardware.
    assertTrue(elapsed < 10_000_000, "Hot-path latency exceeded 10ms: " + elapsed + "ns");
}
```

**GREEN:**
- Create `trade-hotpath` module with NO Spring dependencies
- Move: `TickReceived` processing, candle aggregation, risk evaluation, OSM transitions
- `trade-app` configures Spring beans, then passes them to hot-path module via constructor
- Disruptor pipeline lives in `trade-hotpath`, not in Spring context

Module structure:

```
trade-hotpath/
├── src/main/java/.../hotpath/
│   ├── MarketDataPipeline.java     — TickReceived → Candle → Risk → OSM
│   ├── OrderPipeline.java          — OrderIntent → RiskVerdict → OSM → Broker
│   └── PipelineConfig.java         — Pure Java wiring (no Spring)
```

### H.2 — Binary Tick Store

Replace JSON serialization with compact binary:

- Use Chronicle Queue's built-in binary serialization for hot path
- Parquet for analytical queries via DuckDB
- Partition: `/date/exchange/symbol/timeframe/`

### H.3 — Multi-Bus Topology

Introduce separate Disruptor instances per domain:

```java
// Ring buffer sizes should be configurable via application.yml, not hardcoded
marketDataBus = new DisruptorEventBus(config.ringSize("market-data", 8192));
signalBus = new DisruptorEventBus(config.ringSize("signal", 4096));
executionBus = new DisruptorEventBus(config.ringSize("execution", 1024));
riskBus = new DisruptorEventBus(config.ringSize("risk", 1024));
```

Each bus has independent ring buffer size and handlers:
- Market data bus: large ring (8192 default), high throughput
- Execution bus: small ring (1024 default), low latency
- Risk bus: synchronous priority

**Tuning:** Ring sizes should be loaded from `config/runtime.yaml` rather than hardcoded, allowing production tuning without recompilation.

### H.4 — Frontend Architecture

- Spring Boot backend exposes REST + WebSocket endpoints only
- Frontend: React + Zustand + TradingView Lightweight Charts
- Streaming: Server-Sent Events for live data
- No business logic in frontend

---

## 11. Appendix: SOLID Audit Tracker

### DIP Violations (Phase A.1)

| Class | Current Dependency | Target |
|-------|-------------------|--------|
| `DhanMarketDataProvider` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanOrderCommandAdapter` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanOrderQueryAdapter` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanPortfolioProvider` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanWebSocketMultiplexer` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanFuturesAdapter` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanOptionsAdapter` | `InMemoryInstrumentResolver` | `InstrumentResolver` |
| `DhanBrokerConnection` | `InMemoryInstrumentResolver` | `InstrumentResolver` |

### SRP Violations (Phase A.2–A.3)

| Class | Responsibilities | Action |
|-------|-----------------|--------|
| `DhanBrokerConnection` | Factory wiring + facade + loader | Extract loader to resolver layer |
| `TradingRuntimeConfiguration` | All wiring | Split into 6 config classes |
| `PositionRiskHandler` | Risk + trade lifecycle | Extract trade lifecycle to `PortfolioEngine` |

### Files That Need `@Service` (Phase A.4)

- `CandleAggregationService`
- `StrategyEngine`
- `TradingCircuitBreaker`
- `OrderManagementService`
- `ExecutionHandler`
- `PositionRiskHandler`
- `OrderReconciler`
- `DuckDbEventStore`
- `ChronicleAuditLogWriter`

---

## 12. Appendix: Module Dependency Map

### Current Dependencies

```
trade-core          ← (no intra-project deps)
trade-broker-api    ← trade-core
trade-broker-dhan   ← trade-broker-api, trade-core
trade-strategy      ← trade-core, trade-broker-api
trade-execution     ← trade-broker-api, trade-core
trade-disruptor     ← trade-core, trade-execution, trade-strategy
trade-persistence   ← trade-core
trade-app           ← ALL
```

### Target Dependencies (After Phase H)

```
trade-core              ← (no intra-project deps)
trade-broker-api        ← trade-core
trade-broker-dhan       ← trade-broker-api, trade-core
trade-strategy          ← trade-core, trade-broker-api
trade-execution         ← trade-broker-api, trade-core
trade-disruptor         ← trade-core, trade-execution, trade-strategy
trade-persistence       ← trade-core
trade-hotpath           ← trade-core, trade-broker-api (NO Spring)
trade-feature-store     ← trade-core, trade-persistence
trade-portfolio         ← trade-core, trade-broker-api
trade-app               ← ALL (wires everything, no hot-path logic)
```

---

## Execution Summary

```
Phase   Duration   Tests   Files   Risk Reduction
─────────────────────────────────────────────────
A       4 weeks    45+     25+     🔴 High (DIP, DI, SRP, OCP)
B       4 weeks    50+     15+     🔴 High (determinism, replay)
C       3 weeks    30+     10+     🔴 High (safety, isolation)
D       4 weeks    40+     12+     🟡 Medium (parity, testing)
E       2 weeks    20+     8+      🟡 Medium (ops, monitoring)
F       4 weeks    35+     10+     🟡 Medium (scale, portfolio)
G       4 weeks    25+     8+      🟢 Low (ML readiness)
H       4 weeks    20+     10+     🟢 Low (deployment)

Total:  29 weeks   265+    98+

> **Note:** Timeline estimates assume one developer working full-time. Event sourcing (Phase B) is the most architecturally complex change — budget extra planning/review time. Add 30% buffer for production-grade testing and edge cases (~38 weeks real-world).
```

---

> **Next step:** Begin Phase A.1 — start by writing the `InstrumentResolverContractTest.java` that proves the interface is missing methods, then extend the interface. All changes are TDD RED → GREEN → REFACTOR.
