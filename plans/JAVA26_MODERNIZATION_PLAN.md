# Trade-J: Java 26 & Spring Boot 4 Modernization Plan

**Created:** 2026-06-01  
**Baseline:** Java 21 / Spring Boot 3.4.13  
**Target:** Java 26 / Spring Boot 4.x  
**Horizon:** 4 months

---

## Baseline Facts (Verified from Source)

| Item | Current | Target |
|---|---|---|
| Java version | 21 (`JavaLanguageVersion.of(21)`) | 26 |
| Spring Boot | 3.4.13 | 4.x (via 3.5 bridge) |
| Lombok | None | N/A |
| Records | Extensive (events, models, config) | Maintain |
| Sealed classes | 4 files | All domain hierarchies |
| Virtual threads | 4 call sites | All I/O paths |
| StructuredTaskScope | 1 call site | Scanner, multi-broker |
| ScopedValue | 0 | MDC propagation |
| Panama (MemorySegment) | 0 | Tick buffer (optional) |
| OpenTelemetry | 0 | Full trace pipeline |
| `Optional` in hot path | `MarketTickEvent.depth` | Removed |
| Telescoping constructors | `DisruptorEventBus` (8) | Builder |
| Reflection in hot path | `ReflectionSupport` (Dhan) | Typed adapter |

---

## Phase 0 — Toolchain Upgrade (Week 1)

**Goal:** Compile and run on Java 26 with zero regressions.

### Tasks

- [ ] **P0-1** Update `build.gradle` toolchain to Java 26
  ```groovy
  java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }
  ```
- [ ] **P0-2** Update `gradle.properties`
  ```properties
  org.gradle.java.home=/opt/homebrew/opt/openjdk@26
  ```
- [ ] **P0-3** Enable preview features for modules that will use them
  ```groovy
  tasks.withType(JavaCompile).configureEach {
      options.compilerArgs += ['--enable-preview', '--release', '26']
  }
  ```
- [ ] **P0-4** Run full regression suite: `./gradlew fullRegressionTest`
- [ ] **P0-5** Fix any compilation errors from Java 26 stricter checks
- [ ] **P0-6** Update Chronicle Queue JVM args if needed (already present in `build.gradle`)
- [ ] **P0-7** Update CI workflow (`.github/workflows/ci.yml`) to use Java 26

**Acceptance:** `./gradlew build` passes on Java 26. All unit + component tests green.

---

## Phase 1 — Hot Path Correctness (Week 1–2)

**Goal:** Eliminate allocation and anti-patterns on the critical tick processing path.

### P1-1: Remove `Optional<MarketDepth>` from `MarketTickEvent`

**File:** `core/src/main/java/com/tradej/core/domain/event/MarketTickEvent.java`

```java
// BEFORE
Optional<MarketDepth> depth

// AFTER
@Nullable MarketDepth depth   // null = no depth data

public boolean hasDepth() { return depth != null; }
```

Update all call sites:
- `DepthUpdateFactory.fromMarketTickEvent()`
- `MarketDataPipeline.onMarketTickEvent()`
- All broker adapters that construct `MarketTickEvent`
- All consumers that call `tick.depth().isPresent()`

**Why:** `Optional` allocates a wrapper object on every tick. At 50K ticks/sec across 500 symbols this is ~50K unnecessary allocations/sec.

### P1-2: Replace `UUID.randomUUID()` in `EventMetadata.root()`

**File:** `core/src/main/java/com/tradej/core/domain/event/EventMetadata.java`

```java
// BEFORE
UUID.randomUUID().toString()  // SecureRandom — contended

// AFTER
private static final AtomicLong SEQ = new AtomicLong(System.nanoTime());

public static EventMetadata root() {
    return new EventMetadata(
        Long.toHexString(System.nanoTime()) + "-" + Long.toHexString(SEQ.incrementAndGet()),
        Instant.now().toEpochMilli(),
        System.nanoTime(),
        0L, "", EventSchemaVersion.CURRENT
    );
}
```

**Why:** `UUID.randomUUID()` uses `SecureRandom` which is a contention point under high event rates.

### P1-3: Make `OrderStateMachine.on()` Switch Exhaustive

**File:** `core/src/main/java/com/tradej/core/domain/oms/OrderStateMachine.java`

Remove the implicit `default` fallthrough from the `switch (event)` block. Since `OrderEvent` is already `sealed`, the compiler will enforce exhaustiveness. Add a compile-time check that all `OrderEvent` subtypes are handled.

**Acceptance:** `./gradlew :core:test` passes. Tick throughput benchmark shows ≥10% reduction in GC pause frequency.

---

## Phase 2 — Sealed Domain Hierarchy (Week 2–3)

**Goal:** Make the compiler enforce exhaustive event handling across all dispatchers.

### P2-1: Seal `DomainEvent`

**File:** `core/src/main/java/com/tradej/core/domain/event/DomainEvent.java`

```java
public sealed interface DomainEvent
    permits MarketTickEvent, DepthUpdateEvent, CandleClosed,
            SignalGenerated, SignalPendingExecution, SignalSuppressed,
            OrderUpdateEvent, TradeOpened, TradeClosed,
            PositionUpdateEvent, PnlUpdatedEvent, ScanHitProduced,
            ScanResultsPublished, KillSwitchEngaged, StreamHealthChanged,
            MarketStatusEvent, ReplayTimeChangedEvent, BrokerAdapterError,
            EventBusBackpressure, StrategyError, TradeExecutionEvent,
            TradeUpdated, StrategyError { }
```

Steps:
1. Add `permits` clause to `DomainEvent`
2. Ensure every event record/class in `core/domain/event/` has `implements DomainEvent` (already true)
3. Fix any non-exhaustive `switch` statements that the compiler now flags

### P2-2: Convert `instanceof` Chains to Pattern-Matching Switch

Files to update (priority order):

| File | Current Pattern | Action |
|---|---|---|
| `ExecutionHandler.java` | `if (event instanceof X)` chain | `switch (event)` |
| `PositionRiskHandler.java` | `if (event instanceof X)` chain | `switch (event)` |
| `RiskPipelineHandler.java` | `if (event instanceof X)` | `switch (event)` |
| `FillReconciliationService.java` | `if (event instanceof X)` | `switch (event)` |
| `SignalExecutionQueue.java` | `if (event instanceof X)` | `switch (event)` |
| `GatewayEventBridge.java` | `if (event instanceof X)` | `switch (event)` |
| `ScanService.runProfile()` | `if (mode == X)` chain | `switch (mode)` |

Example transformation:
```java
// BEFORE — ExecutionHandler
if (event instanceof SignalPendingExecution) {
    signalQueue.onDomainEvent(event, downstream);
} else if (event instanceof OrderFilled || ...) {
    fillService.onDomainEvent(event, downstream);
}

// AFTER
switch (event) {
    case SignalPendingExecution e -> signalQueue.processSignal(e, downstream);
    case OrderFilled e           -> fillService.processFill(e, downstream, 0);
    case OrderPartiallyFilled e  -> fillService.processFill(e, downstream, 0);
    case OrderFullyFilled e      -> fillService.processFill(e, downstream, 0);
    default                      -> { }
}
```

### P2-3: Seal `Signal` Hierarchy

```java
// core/domain/event — group signal events
public sealed interface SignalEvent extends DomainEvent
    permits SignalGenerated, SignalPendingExecution, SignalSuppressed { }
```

**Acceptance:** `./gradlew :core:test :trading-execution:test` passes. No `instanceof` chains remain in event dispatchers.

---

## Phase 3 — Concurrency Modernization (Week 3–4)

**Goal:** Migrate all I/O-bound paths to virtual threads. Introduce structured concurrency for scanner and multi-broker operations.

### P3-1: Virtual Threads for All Broker REST Calls

**File:** `app/src/main/java/com/tradej/app/config/BrokerInfrastructureConfiguration.java`

```java
@Bean(name = "brokerRestExecutor", destroyMethod = "shutdown")
public ExecutorService brokerRestExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

Wire into:
- `DhanAuthenticatedHttpClient`
- `DhanHistoricalDataClient`
- `DhanOptionChainClient`
- `DhanRollingOptionClient`
- `UpstoxBrokerConnection` REST calls
- `IciciBrokerConnection` REST calls

Also add to `application.properties`:
```properties
spring.threads.virtual.enabled=true
```

### P3-2: `StructuredTaskScope` for Parallel Scanner

**File:** `app/src/main/java/com/tradej/app/scanner/ScanService.java`

```java
private List<ScanHit> runParallelScan(List<String> symbols, ScanProfile profile) {
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        var tasks = symbols.stream()
            .map(sym -> scope.fork(() -> scanEngine.runForSymbol(sym, profile)))
            .toList();
        scope.join().throwIfFailed();
        return tasks.stream().flatMap(t -> t.get().stream()).toList();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return List.of();
    }
}
```

### P3-3: `ScopedValue` for Trade Context Propagation

**File:** `core/src/main/java/com/tradej/core/support/MdcPropagatingExecutorService.java`

Replace `ThreadLocal` MDC copy with `ScopedValue` (Java 25 GA, available as preview in Java 21+):

```java
public static final ScopedValue<Map<String, String>> MDC_CONTEXT =
    ScopedValue.newInstance();

@Override
public void execute(Runnable command) {
    Map<String, String> snapshot = MDC.getCopyOfContextMap();
    ScopedValue.where(MDC_CONTEXT, snapshot != null ? snapshot : Map.of())
        .run(() -> {
            MDC.setContextMap(MDC_CONTEXT.get());
            try { command.run(); } finally { MDC.clear(); }
        });
}
```

### P3-4: `DisruptorEventBus` Builder Refactor

**File:** `runtime/disruptor/src/main/java/com/tradej/disruptor/DisruptorEventBus.java`

Replace 8 telescoping constructors with a single `Builder`. All existing call sites in `EventBusConfiguration.java` and `ShardedDisruptorEventBus.java` updated to use `DisruptorEventBus.builder()...build()`.

**Acceptance:** `./gradlew :runtime-disruptor:test :app:test` passes. Scanner runs 500 symbols in parallel. Virtual thread count visible in JVM metrics.

---

## Phase 4 — Observability (Week 4–5)

**Goal:** Add distributed tracing across the full tick → order → fill lifecycle.

### P4-1: Add OpenTelemetry Dependencies

**File:** `app/build.gradle`

```groovy
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
implementation 'io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter'
```

### P4-2: Instrument Critical Paths

| Span Name | Entry Point | Exit Point |
|---|---|---|
| `market-tick` | `MarketDataPipeline.onMarketTickEvent()` | `downstream.accept(tick)` |
| `candle-aggregation` | `CandleAggregationDisruptorHandler.onEvent()` | candle closed |
| `strategy-evaluation` | `StrategyDisruptorHandler.onEvent()` | signal emitted |
| `risk-check` | `PositionRiskHandler.handleSignalPending()` | pass/reject |
| `order-placement` | `OrderPlacementService.place()` | broker ack |
| `fill-reconciliation` | `FillReconciliationService.processFill()` | trade event emitted |

### P4-3: Custom Micrometer Metrics (Missing)

Add to `MicrometerConfiguration.java`:

```java
// Signal-to-order latency histogram
Timer.builder("execution.signal_to_order.latency")
    .description("Time from SignalPendingExecution to order placement")
    .register(meterRegistry);

// Option chain fetch latency
Timer.builder("broker.option_chain.fetch.latency")
    .tag("broker", "dhan")
    .register(meterRegistry);

// Scanner throughput
Counter.builder("scanner.symbols.processed")
    .tag("profile", profileId)
    .register(meterRegistry);

// Risk rejection rate
Counter.builder("risk.signals.rejected")
    .tag("reason", reason)
    .register(meterRegistry);
```

**Acceptance:** Traces visible in OTLP-compatible backend (Jaeger/Tempo). Signal-to-order latency histogram exported to Prometheus.

---

## Phase 5 — AOT & Native Image Readiness (Week 5–6)

**Goal:** Eliminate reflection blockers. Enable Spring AOT compilation.

### P5-1: Replace `ReflectionSupport` in Dhan Mapper

**File:** `broker/dhan/src/main/java/com/tradej/broker/dhan/mapper/ReflectionSupport.java`

Create a typed `DhanResponseMapper` that accesses Dhan SDK objects via their public API:

```java
// Replace dynamic reflection with direct method calls
public final class DhanOrderMapper {
    public static Order toOrder(DhanSdkOrderResponse r) {
        return new Order(
            r.getOrderId(),
            r.getCorrelationId(),
            r.getTradingSymbol(),
            DhanSegmentMapper.toSegment(r.getExchangeSegment()),
            DhanSdkConverters.toSide(r.getTransactionType()),
            DhanSdkConverters.toProductType(r.getProductType()),
            DhanSdkConverters.toOrderType(r.getOrderType()),
            DhanSdkConverters.toOrderStatus(r.getOrderStatus()),
            r.getQuantity(),
            r.getFilledQty(),
            DhanSdkConverters.toPaisa(r.getPrice()),
            DhanSdkConverters.toPaisa(r.getTriggerPrice()),
            DhanSdkConverters.toEpochMs(r.getCreateTime()),
            r.getRejectedReason()
        );
    }
}
```

If the Dhan SDK does not expose a stable public API, deserialize via Jackson to a typed record instead.

### P5-2: Add Spring AOT Hints for Remaining Reflection

**File:** `app/src/main/java/com/tradej/app/TradingApplication.java`

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan("com.tradej")
@ImportRuntimeHints(TradeJRuntimeHints.class)
public class TradingApplication { ... }

class TradeJRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader cl) {
        // Register any remaining reflection needs
        hints.reflection().registerType(DhanSdkOrderResponse.class,
            MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
```

### P5-3: Enable Spring AOT Processing

```groovy
// app/build.gradle
tasks.named('bootBuildImage') {
    buildpacks = ['paketobuildpacks/java-native-image']
}
```

**Acceptance:** `./gradlew :app:processAot` completes without errors. No reflection warnings in AOT output.

---

## Phase 6 — Spring Boot 4 Migration (Week 7–10)

**Goal:** Migrate from Spring Boot 3.4 → 3.5 → 4.x.

### P6-1: Spring Boot 3.5 Bridge

```groovy
id 'org.springframework.boot' version '3.5.0'
```

Fix any deprecation warnings flagged by Spring Boot 3.5 migration guide.

### P6-2: Spring Boot 4.0 Migration

Key breaking changes to address:

| Change | Impact | Action |
|---|---|---|
| Jakarta EE 11 | `jakarta.*` namespace (already migrated) | Verify all imports |
| Virtual threads default | `spring.threads.virtual.enabled` default `true` | Remove explicit config |
| `RestTemplate` deprecated | Any `RestTemplate` usage | Migrate to `RestClient` |
| Security defaults | CSRF, CORS changes | Review `ConsoleCorsConfiguration` |
| Actuator changes | Endpoint path changes | Update monitoring config |
| `@ConfigurationProperties` records | Already used | Verify binding |

### P6-3: Migrate to `RestClient` (Spring 6.1+)

Replace any `RestTemplate` usage with `RestClient`:

```java
// BEFORE
RestTemplate restTemplate = new RestTemplate();
ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

// AFTER
RestClient restClient = RestClient.create();
String response = restClient.get().uri(url).retrieve().body(String.class);
```

**Acceptance:** `./gradlew :app:bootRun` starts on Spring Boot 4. All integration tests pass.

---

## Phase 7 — Java 26 Language Features (Week 10–12)

**Goal:** Adopt Java 26-specific language features where they add value.

### P7-1: Stream Gatherers for Tick Aggregation

**Java 24 GA — available in Java 26**

```java
// Sliding window of ticks for VWAP calculation
import java.util.stream.Gatherers;

List<MarketTickEvent> ticks = tickStream
    .gather(Gatherers.windowSliding(20))
    .map(window -> computeVwap(window))
    .toList();
```

Apply in `CandleAggregationService` for multi-tick aggregation windows.

### P7-2: Unnamed Patterns for Event Routing

**Java 22 GA — available in Java 26**

```java
// BEFORE
switch (event) {
    case TradeOpened ignored -> openTrades.incrementAndGet();
    case TradeClosed ignored -> openTrades.decrementAndGet();
    ...
}

// AFTER — unnamed pattern variable
switch (event) {
    case TradeOpened _  -> openTrades.incrementAndGet();
    case TradeClosed _  -> openTrades.decrementAndGet();
    ...
}
```

Apply across all event handlers that don't use the bound variable.

### P7-3: Value Classes (Valhalla Preview — Java 26)

Evaluate `InstrumentKey`, `Symbol`, `CapitalPaisa`, `StrategyId` as candidates for value classes:

```java
// Candidate — currently a record with identity
public record InstrumentKey(String canonicalSymbol, ExchangeSegment exchangeSegment) { }

// Target — value class (no identity, stack-allocated)
public value record InstrumentKey(String canonicalSymbol, ExchangeSegment exchangeSegment) { }
```

**Note:** Value classes are preview in Java 26. Adopt behind `--enable-preview`. Do not use in production until GA.

### P7-4: Class-File API for Dynamic Strategy Loading

**Java 24 GA**

Replace any ASM/Javassist usage in dynamic strategy plugin loading with the standard `java.lang.classfile` API:

```java
import java.lang.classfile.*;

ClassFile cf = ClassFile.of();
ClassModel model = cf.parse(strategyBytecode);
// Inspect and transform strategy class at load time
```

**Acceptance:** All Java 26 features compile with `--enable-preview`. Benchmarks show measurable improvement in tick aggregation throughput.

---

## Phase 8 — Options Analytics & Scanner Scale (Week 12–16)

**Goal:** Scale to 5000+ option contracts with streaming Greeks and IV pipeline.

### P8-1: Streaming Options Analytics Pipeline

Add a dedicated `OptionsAnalyticsPipeline` node to the Disruptor pipeline:

```
MarketTickEvent (option)
  → GreeksCalculationNode (Black-Scholes / Binomial)
  → IVSurfaceNode
  → GammaExposureNode
  → DealerPositioningNode
  → OptionChainAggregatorNode
  → ScanResultsPublished
```

### P8-2: Parallel Option Chain Processing

Use `StructuredTaskScope` to process multiple expiries in parallel:

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var tasks = expiries.stream()
        .map(expiry -> scope.fork(() -> processExpiry(expiry, chain)))
        .toList();
    scope.join().throwIfFailed();
    return tasks.stream().map(StructuredTaskScope.Subtask::get).toList();
}
```

### P8-3: Market Profile / Volume Profile / Footprint

Add pipeline nodes:
- `VolumeProfileNode` — TPO / volume at price per symbol
- `MarketProfileNode` — value area, POC, IB range
- `FootprintNode` — bid/ask volume per price level per candle

These consume `DepthUpdateEvent` and `CandleClosed` events already flowing through the pipeline.

---

## Modernization Backlog (Full Table)

| # | Module | Current | Recommended | Benefit | Complexity | Risk | Priority |
|---|---|---|---|---|---|---|---|
| B-01 | `build.gradle` | Java 21 | Java 26 | All new language features | Low | Low | P0 |
| B-02 | `MarketTickEvent` | `Optional<MarketDepth>` | `@Nullable MarketDepth` | -50K allocs/sec | Low | Low | P0 |
| B-03 | `EventMetadata` | `UUID.randomUUID()` | Sequence-based ID | Reduced contention | Low | Low | P0 |
| B-04 | `DomainEvent` | Unsealed interface | `sealed interface` | Exhaustive dispatch | Medium | Low | P1 |
| B-05 | `ExecutionHandler` | `instanceof` chain | `switch` pattern | Compiler safety | Low | Low | P1 |
| B-06 | `PositionRiskHandler` | `instanceof` chain | `switch` pattern | Compiler safety | Low | Low | P1 |
| B-07 | `OrderStateMachine` | Implicit default | Exhaustive switch | Compile-time safety | Low | Low | P1 |
| B-08 | `DisruptorEventBus` | 8 constructors | Builder | Maintainability | Medium | Low | P1 |
| B-09 | `ConcurrencyConfiguration` | 4 virtual thread sites | All I/O paths | Thread efficiency | Low | Low | P1 |
| B-10 | `ScanService` | Sequential scan | `StructuredTaskScope` | 500-symbol parallelism | Medium | Low | P1 |
| B-11 | `MdcPropagatingExecutorService` | `ThreadLocal` copy | `ScopedValue` | Structured propagation | Medium | Low | P2 |
| B-12 | `ReflectionSupport` (Dhan) | Dynamic reflection | Typed adapter | GraalVM native image | High | Medium | P1 |
| B-13 | `MicrometerConfiguration` | Gauges only | + Timers, Counters | Latency visibility | Medium | Low | P2 |
| B-14 | All modules | No OpenTelemetry | OTLP tracing | End-to-end trace | High | Low | P1 |
| B-15 | `app/build.gradle` | Spring Boot 3.4 | Spring Boot 4.x | Framework currency | High | Medium | P2 |
| B-16 | `CandleAggregationService` | Manual windowing | Stream Gatherers | Readability | Medium | Low | P3 |
| B-17 | `InstrumentKey`, `Symbol` | Records | Value classes (preview) | Stack allocation | High | High | P3 |
| B-18 | `OptionsAnalytics` | REST poll | Streaming pipeline | Real-time Greeks | High | Medium | P2 |
| B-19 | `ScanService` | if/else mode dispatch | `switch` pattern | Exhaustive dispatch | Low | Low | P1 |
| B-20 | `EventMetadata.root()` | `Instant.now()` | `System.nanoTime()` only | Monotonic timestamps | Low | Low | P2 |

---

## Migration Sequence (Dependency Order)

```
Phase 0: Toolchain (Java 26)
    ↓
Phase 1: Hot Path Correctness (Optional removal, ID generation)
    ↓
Phase 2: Sealed Hierarchy (DomainEvent sealed → exhaustive switches)
    ↓
Phase 3: Concurrency (Virtual threads everywhere, StructuredTaskScope, ScopedValue)
    ↓
Phase 4: Observability (OpenTelemetry, Micrometer timers)
    ↓
Phase 5: AOT Readiness (Reflection elimination, Spring AOT)
    ↓
Phase 6: Spring Boot 4 (3.5 bridge → 4.0)
    ↓
Phase 7: Java 26 Features (Gatherers, unnamed patterns, value classes preview)
    ↓
Phase 8: Domain Scale (Options analytics pipeline, 5000-contract scanner)
```

---

## Test Strategy Per Phase

| Phase | Test Gate |
|---|---|
| P0 | `./gradlew fullRegressionTest` — all green on Java 26 |
| P1 | Tick throughput benchmark ≥ baseline; GC pause frequency reduced |
| P2 | `./gradlew :core:test :trading-execution:test` — no `instanceof` chain warnings |
| P3 | Scanner 500-symbol run completes in < 2s; virtual thread count in JVM metrics |
| P4 | Traces visible in OTLP backend; signal-to-order latency histogram exported |
| P5 | `./gradlew :app:processAot` — zero reflection warnings |
| P6 | `./gradlew :app:bootRun` — starts on Spring Boot 4; all integration tests pass |
| P7 | Preview features compile; no regression in stress tests |
| P8 | 5000-contract option chain processed in < 500ms |

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Chronicle Queue incompatible with Java 26 | Medium | High | Test early in Phase 0; Chronicle 2026.x should support Java 21+ |
| Dhan SDK reflection removal breaks order flow | Medium | Critical | Parallel-run typed adapter alongside reflection path; feature flag |
| Spring Boot 4 breaking changes in broker config | Medium | Medium | Use 3.5 as bridge; run full broker integration tests before 4.0 |
| `ScopedValue` API changes between Java 21 preview and 25 GA | Low | Low | Pin to Java 26 GA API; remove `--enable-preview` flag |
| Value classes (Valhalla) breaking record semantics | High | Medium | Keep behind `--enable-preview`; do not use in production until GA |
| `sealed DomainEvent` breaks external event consumers | Low | Medium | All consumers are internal; verify with `architecture-test` module |

---

## Definition of Done

The platform is considered Java 26 / Spring Boot 4 ready when:

- [ ] Compiles and runs on Java 26 with zero `--enable-preview` flags in production code
- [ ] Spring Boot 4.x with virtual threads enabled by default
- [ ] `DomainEvent` is sealed; all dispatchers use exhaustive `switch`
- [ ] Zero `Optional` in hot-path event records
- [ ] All broker REST calls use virtual threads
- [ ] `StructuredTaskScope` used for scanner and multi-broker parallel operations
- [ ] OpenTelemetry traces exported for tick → signal → order → fill lifecycle
- [ ] `ReflectionSupport` eliminated; `./gradlew :app:processAot` passes
- [ ] Signal-to-order latency P99 < 5ms (measured via Micrometer timer)
- [ ] 500-symbol scan completes in < 2 seconds
- [ ] All existing regression tests pass
