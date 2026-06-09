# Trade-J Architecture Remediation — Implementation Plan

*Generated from the Dr. Venkat Subramaniam code review of the actual source code.*
*Each phase is ordered by impact/effort ratio — highest value first.*

---

## How to Use This Plan

Each phase is a self-contained, testable unit of work. Within each phase:

1. **Inspect** — Read the current files to understand before changing
2. **Change** — Apply the code transformations
3. **Test** — Run the targeted test suite for that module
4. **Verify** — Run the regression tests that exercise the changed code paths
5. **Commit** — Each phase should produce a green build

The testing strategy for each change is explicit. The goal is **no regression** — every existing test must continue to pass after each phase.

---

## Phase 0 — Foundation: Test Baseline & Safety Net

**Goal:** Before any changes, establish that all existing tests pass so we can detect regressions.

### Steps

1. **Run the full unit + component test suite and record the pass/fail state**
   ```bash
   ./gradlew test componentTest 2>&1 | tee baseline-test-results.txt
   ```

2. **Capture current JaCoCo coverage baselines**
   ```bash
   ./gradlew coverageReportAll
   ```

3. **Check the test tag taxonomy exists consistently**
   - Every test file should have an `@Tag("unit")`, `@Tag("component")`, or `@Tag("integration")`
   - This plan assumes we use these tags for all new/changed tests

4. **Identify test files that will be directly affected by each phase** (listed per phase below)

### Success Criteria
- [ ] All unit tests pass
- [ ] All component tests pass
- [ ] Baseline coverage report generated

### Commands
```bash
./gradlew test componentTest coverageReportAll
```

---

## Phase 1 — Eliminate Empty Marker Interfaces

**Files to change:**
```
broker/api/src/main/java/com/tradej/broker/api/capability/
  ├── AdvancedOrderCapable.java        → DELETE
  ├── AlertCapable.java                → DELETE
  ├── FuturesCapable.java              → DELETE
  ├── MarginCapable.java               → DELETE
  ├── NewsCapable.java                 → DELETE
  └── OptionsCapable.java              → DELETE
```

**Files referencing these interfaces (must be updated):**
```
broker/dhan/src/main/java/.../DhanBrokerConnection.java
broker/icici/src/main/java/.../IciciBrokerConnection.java
broker/upstox/src/main/java/.../UpstoxBrokerConnection.java
broker/core/src/main/java/.../LoadBalancedBrokerGateway.java
broker/api/src/testFixtures/.../IBrokerConnectionContractTest.java
```

### What changes?

**Replace marker interface usage with `Class<?>` capability keys directly.**

Instead of:
```java
// Current: empty marker interface
interface OptionsCapable { }

// Usage:
private static final OptionsCapable OPTIONS_CAPABLE = new OptionsCapable() { };
capabilityMap.register(OptionsCapable.class, OPTIONS_CAPABLE);

// ContrACT test assertion:
assertNotNull(connection.getCapability(OptionsCapable.class));
```

Do:
```java
// Just register the port class directly when non-null
// No marker interfaces at all

// In the connection:
// Remove all `private static final XxxCapable` instances
// Remove all `.register(XxxCapable.class, ...)` lines

// The capability was already redundant — you can check:
// if (optionsProvider != null) — which is already a field
```

**But wait** — the contract test asserts `getCapability(OptionsCapable.class).isPresent()`. We need to replace this with something more meaningful.

**Better approach: Replace marker interfaces with the actual port class as the key.**

If a broker has an `OptionsProvider`, then `getCapability(OptionsProvider.class)` should return it. The marker interfaces were just aliases for "do you have this port?" — and we already register the port classes. The marker interfaces are entirely redundant.

So the change is:
1. Delete all 6 marker interfaces
2. Remove all `private static final XxxCapable` fields from connections
3. Remove all `.register(XxxCapable.class, ...)` lines from CapabilityMap builders
4. Update `LoadBalancedBrokerGateway.getCapability()` — remove `OptionsCapable` and `NewsCapable` checks
5. Update `IBrokerConnectionContractTest` — change assertions to use port class keys
6. Update any other test that references the marker interfaces

### Testing Strategy

**Phase 1 Tests:**
- Update `IBrokerConnectionContractTest`:
  - Replace `connection.getCapability(OptionsCapable.class)` with `connection.options()`
  - Replace `connection.getCapability(MarginCapable.class)` with `connection.margin()`
  - The contract test should verify that `getCapability(XxxProvider.class)` returns the same instance as `connection.xxx()` — testing consistency, not marker interface existence
- Run: `./gradlew :broker-api:test :broker-dhan:test :broker-icici:test :broker-upstox:test :broker-core:test`
- Run: `./gradlew test componentTest`

### Success Criteria
- [ ] All 6 marker interfaces deleted
- [ ] No remaining imports of `capability.*` in any broker connection
- [ ] All broker connections compile without marker interfaces
- [ ] Contract tests pass with port-class-based assertions
- [ ] All unit + component tests pass
- [ ] Lines removed: ~120 (6 interfaces × 5 lines + ~15 registration lines × 4 files + field declarations)

---

## Phase 2 — Eliminate Duplicate CapabilityMap Registration Blocks

**Files to change:**
```
broker/dhan/src/main/java/.../DhanBrokerConnection.java   (2 copies: lines ~135-160 and ~275-300)
broker/icici/src/main/java/.../IciciBrokerConnection.java   (1 copy)
broker/upstox/src/main/java/.../UpstoxBrokerConnection.java (1 copy)
```

### What changes?

**Pattern: Extract shared capability registration into a common method.**

Each connection currently builds its `CapabilityMap` in-line with a 15-20 line builder. The pattern is identical across all 3 brokers. After Phase 1, these blocks shrink but remain duplicated.

**Step 1:** Create a shared builder method (could be on `CapabilityMap.Builder` itself, or a static utility):

```java
// CapabilityMap.java — add a convenience method or use static factory

// Option A: Add fluent variadic registration
public static Builder fromPorts(Object... portPairs) {
    Builder b = builder();
    for (int i = 0; i < portPairs.length; i += 2) {
        Class<?> type = (Class<?>) portPairs[i];
        Object impl = portPairs[i + 1];
        if (impl != null) b.register(type, impl);
    }
    return b;
}
```

**Step 2:** Replace repetitive builder calls with:

```java
// Before (DhanBrokerConnection):
this.capabilityMap = CapabilityMap.builder()
    .register(MarketDataProvider.class, marketDataProvider)
    .register(FuturesProvider.class, futuresProvider)
    .register(OptionsProvider.class, optionsProvider)
    .register(OrderCommand.class, orderCommand)
    .register(OrderQuery.class, orderQuery)
    .registerIfNotNull(SliceOrderCommand.class, sliceOrderCommand)
    .registerIfNotNull(BracketOrderProvider.class, bracketOrderProvider)
    .registerIfNotNull(CoverOrderProvider.class, coverOrderProvider)
    .registerIfNotNull(GttOrderProvider.class, gttOrderProvider)
    .register(PortfolioProvider.class, portfolioProvider)
    .register(MarginProvider.class, marginProvider)
    .registerIfNotNull(SessionRiskProvider.class, sessionRiskProvider)
    .registerIfNotNull(ConditionalAlertProvider.class, conditionalAlertProvider)
    .register(InstrumentResolver.class, instrumentResolver)
    .register(WebSocketMultiplexer.class, webSocketMultiplexer)
    .register(MarketStatusProvider.class, marketStatusProvider)
    .build();
```

This is still repetitive but no longer has the marker interfaces. The real problem is the duplication across 3-4 places. **But** the actual duplication is small — each connection has slightly different capabilities (Upstox has `NewsProvider`, Dhan has `SessionRiskProvider`). The registration block is a cost we accept.

**Better approach:** Instead of extracting a shared builder, **eliminate `CapabilityMap` altogether and use direct delegation.** This is the larger simplification.

### Alternate (Preferred) Approach — Remove CapabilityMap Entirely

`CapabilityMap` was needed because marker interfaces were used as keys. After Phase 1 removes marker interfaces, the only remaining registrations are port interfaces — which can be checked directly.

**Replace `getCapability()` with direct `instanceof` checks or method delegation:**

```java
// IBrokerConnection interface:
@Override
public <T> Optional<T> getCapability(Class<T> capabilityClass) {
    if (capabilityClass == MarketDataProvider.class) return Optional.of(capabilityClass.cast(marketData()));
    if (capabilityClass == OptionsProvider.class) return Optional.of(capabilityClass.cast(options()));
    if (capabilityClass == OrderCommand.class) return Optional.of(capabilityClass.cast(orders()));
    // ... etc
    return Optional.empty();
}
```

This replaces a 20-line builder + a 15-line map + a class-scanning fallback loop with a few lines of straightforward `if` checks.

**Files to change for full CapabilityMap removal:**
```
broker/api/src/main/java/.../IBrokerConnection.java          — Add default getCapability impl
broker/core/src/main/java/.../capability/CapabilityMap.java  — Add deprecation, then delete after all refs removed
broker/dhan/.../DhanBrokerConnection.java                     — Replace capabilityMap with direct delegation
broker/icici/.../IciciBrokerConnection.java                   — Same
broker/upstox/.../UpstoxBrokerConnection.java                 — Same
broker/core/.../LoadBalancedBrokerGateway.java                — Same (delegates to child connections)
```

### Testing Strategy

**For the CapabilityMap removal approach:**
- The `IBrokerConnectionContractTest.getCapabilityNeverReturnsNull()` and `getCapabilityReturnsPresentForKnownCapabilities()` tests directly validate the replacement
- Add a test: `getCapability(portClass).get() == connection.portMethod()` — verifying consistency
- Run the full broker test suite for all 3 connections

### Phase 2 Success Criteria (Preferred approach)
- [ ] `CapabilityMap.java` removed (or deprecated)
- [ ] All broker connections use direct delegation in `getCapability()`
- [ ] No builder pattern for capability registration anywhere
- [ ] All unit + component tests pass
- [ ] Lines removed: ~100+ (the builder calls × 4 copies + CapabilityMap.java)
- [ ] `DhanBrokerConnection` 16-parameter constructor simplified (one fewer thing being built)

---

## Phase 3 — Fix `MarketDataProvider.capabilities()` null return

**Files to change:**
```
broker/api/src/main/java/.../port/MarketDataProvider.java    — Return type changes
```

### What changes?

The `capabilities()` default method returns `null`. All callers must null-check. This is the null-that-could-be-Optional pattern.

**Option A: Return `Optional<HistoricalDataCapabilities>`**
```java
default Optional<HistoricalDataCapabilities> capabilities() {
    return Optional.empty();
}
```

**Option B: Use a Null Object pattern**
```java
// In HistoricalDataCapabilities:
static final HistoricalDataCapabilities EMPTY = new HistoricalDataCapabilities(
    Set.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of());

// In MarketDataProvider:
default HistoricalDataCapabilities capabilities() {
    return HistoricalDataCapabilities.EMPTY;
}
```

**Recommendation: Option B (Null Object)** — avoids Optional in domain model fields (per Java architects' guidance), and all existing callers can remove null checks.

### Files affected by the change:

**Update null-checking callers to remove `null` checks:**
```
broker/api/.../port/MarketDataProvider.java                  — validateInterval(): remove null check
broker/api/.../testFixtures/IBrokerConnectionContractTest.java — remove assumeTrue(caps != null) guards
Lots of other callers — search for `capabilities() == null` or `capabilities() != null`
```

### Testing Strategy

- Search for all calls to `.capabilities()` and verify each handles the Null Object correctly
- The contract test `marketDataCapabilitiesIsNotNull` changes:
  - Current: `assumeTrue(caps != null, ...)` → new: remove assumeTrue, `assertNotNull(caps)` always passes
  - `marketDataCapabilitiesHasNonEmptySupportedIntervals`: currently guarded, now always runs
- Run: `./gradlew test componentTest`

### Phase 3 Success Criteria
- [ ] `MarketDataProvider.capabilities()` no longer returns null
- [ ] `HistoricalDataCapabilities.EMPTY` constant exists (or use Optional)
- [ ] All null guards removed from callers
- [ ] All tests pass

---

## Phase 4 — Simplify Broker Connection Constructors

**Files to change:**
```
broker/dhan/src/main/java/.../DhanBrokerConnection.java
broker/upstox/src/main/java/.../UpstoxBrokerConnection.java
broker/icici/src/main/java/.../IciciBrokerConnection.java
```

### What changes?

**Remove the "legacy" constructor from `DhanBrokerConnection`.**

Currently `DhanBrokerConnection` has TWO constructors:
1. **The 16-parameter multi-adapter constructor** — for DI
2. **The 3-parameter legacy constructor** — creates everything internally (~150 lines)

After Phase 2 (CapabilityMap removal), the DI constructor shrinks. The legacy constructor is dead code if all injection is done via Spring DI or manual DI.

**Verify:** Search for usages of the legacy constructor:
```bash
grep -rn "new DhanBrokerConnection(settings\|new DhanBrokerConnection(DhanConnectionSettings" --include='*.java'
```

If the legacy constructor is unused, delete it along with its inner adapter creation code (~150 lines). If it IS used (maybe in tests), add `@Deprecated(forRemoval = true)` and migrate the callers first.

**Simplify the multi-adapter constructor:**
- After Phase 2, the constructor no longer builds a `CapabilityMap` — that's a ~20-line savings per constructor
- Consider extracting a Builder if the parameter count is still > 8

### Testing Strategy

- If deleting the legacy constructor: verify no compilation errors
- If adding `@Deprecated(forRemoval=true)`: verify deprecated warnings, then plan removal
- Run: `./gradlew compileJava` across all broker modules
- Run: `./gradlew test` across all broker modules

### Phase 4 Success Criteria
- [ ] Legacy constructor removed or deprecated
- [ ] All broker test + production code compiles without legacy constructor
- [ ] All tests pass

---

## Phase 5 — Refactor `DhanWebSocketMultiplexer` (Split God Class)

**Files to change:**
```
broker/dhan/src/main/java/.../websocket/DhanWebSocketMultiplexer.java
    → SPLIT into multiple classes
```

### What changes?

`DhanWebSocketMultiplexer` (~400+ lines) does:
1. Connection lifecycle management
2. Reconnection with circuit breaker
3. Subscription state management
4. Depth client management
5. Token rotation handling
6. Listener wiring
7. Packet handling and normalization
8. Order/trade processing
9. Dedup filtering
10. Health monitoring

**Extract into focused classes:**

```java
// New files in broker/dhan/.../websocket/

DhanWebSocketConnectionManager
  - Owns: connect(), disconnect(), marketFeedClient, orderStreamClient
  - Owns: reconnectWithBackoff(), executeReconnect()
  - Owns: transportLock, reconnectScheduler

DhanWebSocketSubscriptionHandler
  - Owns: subscriptionManager, depthClient
  - Owns: subscribe(), unsubscribe(), resubscribeAll()
  - Owns: reconciliationScheduler

DhanWebSocketPacketRouter
  - Owns: handleFeedPacket(), handleOrderPayload(), handleTradePayload()
  - Owns: tickDedupFilter, latestOrderStatuses
  - Collaborates with: normalizer, resolver

DhanWebSocketEventPublisher
  - Owns: marketListeners, orderListeners (CopyOnWriteArrayList)
  - Owns: publishMarket(), publishOrder()
  - Owns: healthEvent(), brokerError() helpers
```

The original `DhanWebSocketMultiplexer` becomes a facade that delegates to these four:

```java
public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private final DhanWebSocketConnectionManager connectionMgr;
    private final DhanWebSocketSubscriptionHandler subHandler;
    private final DhanWebSocketPacketRouter packetRouter;
    private final DhanWebSocketEventPublisher eventPublisher;

    @Override
    public void connect() { connectionMgr.connect(); }

    @Override
    public void subscribe(...) {
        subHandler.subscribe(instruments, feedMode);
        connectionMgr.connectDepthIfNeeded();
    }

    @Override
    public void onMarketData(MarketDataListener listener) {
        eventPublisher.addMarketListener(listener);
    }

    // ... etc
}
```

### Testing Strategy

**This is the riskiest refactor.** Do not do it all at once.

**Step 1: Create the new classes as pure extractions** — no behavior change, just move code.

**Step 2: Verify all existing tests pass** before the old class is removed:
```bash
./gradlew :broker-dhan:test
```

**Step 3: Make `DhanWebSocketMultiplexer` delegate to the new classes** — the original file becomes a thin delegation layer.

**Step 4: Add new targeted tests for each extracted class:**
- `DhanWebSocketConnectionManagerTest` — test connect/disconnect/reconnect lifecycle with mocked WebSocket clients
- `DhanWebSocketSubscriptionHandlerTest` — test subscription add/remove/resubscribe in isolation
- `DhanWebSocketPacketRouterTest` — test packet handling with mock normalizer + resolver
- `DhanWebSocketEventPublisherTest` — test listener notification and event filtering

**Step 5: Run the full suite:**
```bash
./gradlew :broker-dhan:test :broker-dhan:componentTest
```

### Phase 5 Success Criteria
- [ ] `DhanWebSocketMultiplexer` reduced from ~400+ lines to < 80 lines (delegation facade)
- [ ] 4 new focused classes with clear single responsibilities
- [ ] New unit tests for each extracted class (at minimum: constructor + happy path)
- [ ] All existing tests pass
- [ ] No behavior change — verified by same test suite passing

---

## Phase 6 — Simplify Pipeline Layer

**Files to change:**
```
pipeline/core/src/.../compiler/GraphNormalizer.java   — Remove silent graph mutation
pipeline/core/.../runtime/BasePipelineNode.java        — Extract metrics into composition
pipeline/runtime/.../DagPipelineRuntimeService.java     — Consider simplification
pipeline/runtime/.../DagPipelineInstance.java           — Consider merging with GraphRuntime
```

### What changes?

#### 6a. Delete `GraphNormalizer`

**Problem:** `GraphNormalizer.normalize()` silently:
1. Infers execution mode from graph structure
2. Inserts implicit ingress nodes
3. Creates new `PipelineGraph` instances

**Fix:** Remove the normalizer. If the graph is invalid or incomplete, fail in the compiler/validator:
```java
// Before (in the pipeline compilation flow):
PipelineGraph normalized = GraphNormalizer.normalize(rawGraph);

// After:
if (!PipelineGraphValidator.isValid(rawGraph)) {
    throw new IllegalArgumentException("Invalid graph: " + validationErrors(rawGraph));
}
// Use rawGraph directly — no normalization needed
```

The downstream code (`GraphRuntime`, `ExecutionPlan`) already handles missing ingress — it finds zero-in-degree nodes on its own. The normalizer was adding artificial ingress nodes that serve no real purpose.

**Files also affected:**
- Any import of `GraphNormalizer` in pipeline compilation code
- Tests for `GraphNormalizer` — update or remove

#### 6b. Replace `BasePipelineNode` inheritance with composition

**Problem:** `BasePipelineNode` uses Template Method pattern (inheritance) for metrics tracking, lifecycle, state management. Every pipeline node must extend it.

**Fix:** Extract metrics into a separate composed object:

```java
// New class — measurable behavior as composition, not inheritance
public final class NodeMetricsTracker {
    private final AtomicLong processedCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong totalExecutionNs = new AtomicLong();

    public <T> T track(String operation, Supplier<T> fn) {
        long start = System.nanoTime();
        try {
            T result = fn.get();
            processedCount.incrementAndGet();
            return result;
        } catch (RuntimeException e) {
            errorCount.incrementAndGet();
            throw e;
        } finally {
            totalExecutionNs.addAndGet(System.nanoTime() - start);
        }
    }

    public NodeMetrics snapshot() { ... }
}

// Usage in any PipelineNode implementation:
public class MyAnalyticsNode implements PipelineNode {
    private final NodeMetricsTracker metrics = new NodeMetricsTracker();

    @Override
    public void onEvent(DomainEvent event) {
        metrics.track("process", () -> { doWork(event); return null; });
    }

    @Override
    public NodeMetrics getMetrics() { return metrics.snapshot(); }
}
```

Now any class can implement `PipelineNode` without extending `BasePipelineNode`:
- No inheritance coupling
- No abstract methods to override
- Testable in isolation (mock `NodeMetricsTracker`)

**Files to create/change:**
```
pipeline/core/src/.../runtime/NodeMetricsTracker.java      → NEW
pipeline/core/src/.../runtime/BasePipelineNode.java         → Deprecate, keep for backward compat
pipeline/core/src/.../runtime/ReactivePipelineNode.java     → Convert to composition
pipeline/core/src/.../runtime/PartitionedNode.java          → Convert to composition
```

### Testing Strategy

**For 6a (GraphNormalizer deletion):**
- Find all tests for GraphNormalizer
- If they test the normalization logic, move applicable assertions to `PipelineGraphValidator` tests
- If they test that downstream code handles unnormalized graphs, those are the most valuable — keep them as regression tests
- Run: `./gradlew :pipeline-core:test :pipeline-platform:test`

**For 6b (NodeMetricsTracker composition):**
- Write tests for `NodeMetricsTracker` in isolation:
  - Verify `track()` increments counters
  - Verify error tracking
  - Verify `snapshot()` returns correct values
- Update existing pipeline node tests to use the new composition
- Run: `./gradlew :pipeline-core:test :pipeline-runtime:test`

### Phase 6 Success Criteria
- [ ] `GraphNormalizer.java` deleted
- [ ] Pipeline compilation fails immediately on invalid graphs (no silent normalization)
- [ ] `NodeMetricsTracker` exists as a standalone utility class
- [ ] `BasePipelineNode` is deprecated (or converted to use composition internally)
- [ ] All pipeline tests pass

---

## Phase 7 — Stream-ify `DefaultPerformanceAnalytics.analyzeTrades()`

**Files to change:**
```
pipeline/analytics/trade-analytics/src/.../DefaultPerformanceAnalytics.java
```

### What changes?

Replace the `for` loop with mutable accumulators (winningTrades, losingTrades, totalReturn, etc.) with a stream-based approach using a custom collector or reduction:

```java
// Create an accumulator record:
private record TradeAccumulator(
    int totalTrades,
    int winningTrades,
    int losingTrades,
    BigDecimal totalReturn,
    BigDecimal totalPositiveReturn,
    BigDecimal totalNegativeReturn,
    List<BigDecimal> returns
) {
    static TradeAccumulator from(TradeRecord trade) {
        BigDecimal pnl = trade.pnl();
        BigDecimal retPct = trade.returnPct();
        return new TradeAccumulator(
            1,
            pnl.compareTo(ZERO) > 0 ? 1 : 0,
            pnl.compareTo(ZERO) < 0 ? 1 : 0,
            pnl,
            pnl.compareTo(ZERO) > 0 ? pnl : ZERO,
            pnl.compareTo(ZERO) < 0 ? pnl.abs() : ZERO,
            retPct != null ? List.of(retPct) : List.of()
        );
    }

    TradeAccumulator merge(TradeAccumulator other) {
        List<BigDecimal> mergedReturns = new ArrayList<>(this.returns);
        mergedReturns.addAll(other.returns);
        return new TradeAccumulator(
            this.totalTrades + other.totalTrades,
            this.winningTrades + other.winningTrades,
            this.losingTrades + other.losingTrades,
            this.totalReturn.add(other.totalReturn),
            this.totalPositiveReturn.add(other.totalPositiveReturn),
            this.totalNegativeReturn.add(other.totalNegativeReturn),
            mergedReturns
        );
    }
}

// Usage in analyzeTrades():
TradeAccumulator acc = trades.stream()
    .map(TradeAccumulator::from)
    .reduce(TradeAccumulator::merge)
    .orElseThrow();

// Then compute ratios from acc — same as before
```

**Alternative: use `Collectors.teeing()`** for a single-pass approach:

```java
var stats = trades.stream().collect(Collectors.teeing(
    // Downstream 1: count winning/losing
    Collectors.teeing(
        Collectors.counting(),
        Collectors.summingLong(t -> t.pnl().compareTo(ZERO) > 0 ? 1 : 0),
        (total, wins) -> Map.of("total", total, "wins", wins)
    ),
    // Downstream 2: sum returns
    Collectors.mapping(TradeRecord::returnPct, 
        Collectors.filtering(Objects::nonNull, Collectors.toList())),
    (counts, returns) -> new AggregatedResult(counts, returns)
));
```

**Recommendation:** Use the `reduce` approach — it's more readable and easier to test.

### Testing Strategy

- The existing `DefaultPerformanceAnalyticsTest` already tests `analyzeTrades()` with fixture data
- Run those tests — they validate the same behavior via the interface
- Add a direct test for `TradeAccumulator`:
  - `merge()` with two non-empty records
  - `from()` with a winning trade, losing trade, and flat trade
  - Empty list edge case
- Run: `./gradlew :trade-analytics:test`

### Phase 7 Success Criteria
- [ ] `analyzeTrades()` uses streams instead of for-loop with mutable accumulators
- [ ] New `TradeAccumulator` record (or equivalent) exists
- [ ] All existing analytics tests pass with identical numeric results
- [ ] No mutable state (int winningTrades, BigDecimal totalReturn) in the analytics computation

---

## Phase 8 — Simplify Load-Balancing / Failover Layer

**Files to consider:**
```
broker/core/src/main/java/.../routing/
├── LoadBalancedBrokerGateway.java        → Evaluate if needed
├── LoadBalancedMarketDataProvider.java   → Evaluate if needed
├── FailoverOrderCommand.java             → Evaluate if needed
├── FailoverWebSocketMultiplexer.java     → Evaluate if needed
└── FallbackMarketDataProvider.java       → Evaluate if needed
```

### What changes?

**Do NOT delete these immediately.** First, audit whether multi-broker failover is actually used in production or if it's a speculative abstraction.

**Step 1:** Search for references to these classes in:
- Spring configuration files (`application*.yml`)
- Production code paths (not tests)
- The `app` module's wiring

```bash
grep -rn "LoadBalancedBrokerGateway\|LoadBalancedMarketData\|FailoverOrderCommand\|FailoverWebSocket" \
    --include='*.java' --include='*.yml' --include='*.yaml' --include='*.properties' \
    | grep -v '/test/' | grep -v '/build/'
```

**Step 2:** If they're unused in production:
- Deprecate them with `@Deprecated(forRemoval=true)`
- Add a comment explaining the multi-broker scenario they were designed for
- Keep the tests (they validate the concept, may be useful later)

**Step 3:** If they ARE used in production:
- Simplify `LoadBalancedBrokerGateway` — many methods just delegate to `primary()`, so the wrapping layer adds minimal value
- Consider replacing with a simpler `PrimaryFailoverConnection` wrapper:
  ```java
  public final class PrimaryFailoverConnection implements IBrokerConnection {
      private final IBrokerConnection primary;
      private final IBrokerConnection standby;

      @Override
      public MarketDataProvider marketData() {
          try { return primary.marketData(); }
          catch (RuntimeException e) { return standby.marketData(); }
      }
      // ... same pattern for all methods
  }
  ```
  This replaces 5+ files with one focused class.

### Testing Strategy

- If deprecated: no test changes needed
- If simplified to `PrimaryFailoverConnection`: adapt existing `LoadBalancedBrokerGatewayTest`, `GatewayFailoverTest` tests
- Run: `./gradlew :broker-core:test`

### Phase 8 Success Criteria
- [ ] If unused: classes deprecated with comments explaining the intended use case
- [ ] If used: 5 routing classes simplified to at most 2
- [ ] All existing failover/gateway tests pass

---

## Phase 9 — Fix `DhanWebSocketMultiplexer` Executor Shutdown Bug

**Files to change:**
```
broker/dhan/src/main/java/.../websocket/DhanWebSocketMultiplexer.java
```

### What changes?

**Current code (bug):**
```java
@Override
public void disconnect() {
    // ... other cleanup ...
    reconnectScheduler.shutdown();
    try {
        if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            reconnectScheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        reconnectScheduler.shutdownNow();  // ← if this throws, reconciliationScheduler doesn't shut down
        Thread.currentThread().interrupt();
    }
    reconciliationScheduler.shutdown();
    // ...
}
```

**Fix:** Ensure `reconciliationScheduler` shutdown runs even if `reconnectScheduler` shutdown fails:

```java
@Override
public void disconnect() {
    // ... other cleanup ...
    shutdownScheduler(reconnectScheduler, "dhan-reconnect");
    shutdownScheduler(reconciliationScheduler, "dhan-sub-reconcile");
    // ...
}

private void shutdownScheduler(ScheduledExecutorService scheduler, String name) {
    scheduler.shutdown();
    try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduler.shutdownNow();
        }
    } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
    }
}
```

### Testing Strategy

- This is timing-sensitive: hard to unit test deterministically
- Add a `@Test` that calls `connect()` then `disconnect()` and verifies `reconnectScheduler.isShutdown()` and `reconciliationScheduler.isShutdown()`
- Run: `./gradlew :broker-dhan:test`

### Phase 9 Success Criteria
- [ ] `disconnect()` always shuts down both schedulers
- [ ] No `InterruptedException` can leave one scheduler running
- [ ] Existing tests pass

---

## Phase 10 — Configuration Consolidation

**Files to examine:**
```
app/src/main/resources/
├── application.yml                → main
├── application-dev.yml            → merge
├── application-dev-live.yml       → merge
├── application-prod.yml           → merge
├── application-test.yml           → merge
├── application-replay.yml         → merge
├── application-gateway.yml        → merge
├── application-upstox-dev.yml     → merge into application-dev.yml
├── application-upstox-prod.yml    → merge into application-prod.yml
├── application-upstox-analytics.yml → merge
├── application-icici-prod.yml     → merge into application-prod.yml
└── logback-spring.xml             → keep
```

### What changes?

**Goal:** Reduce from 12+ configuration files to at most 4:
```
application.yml                — common defaults
application-dev.yml             — dev mode (includes broker dev settings)
application-prod.yml            — prod mode (includes broker prod settings)
application-test.yml            — test mode
```

**Each broker's configuration should be in the broker's own module,** not in `app/src/main/resources`. The broker modules can provide `additional-spring-configuration-metadata.json` or use `@ConfigurationProperties`.

**Step 1:** Audit what's in each config file and identify duplicates

**Step 2:** Move broker-specific settings (Upstox API keys, ICICI endpoints, Dhan URLs) into each broker module's own configuration:
- `broker/upstox/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- `broker/dhan/src/main/resources/application-dhan.yml`
- `broker/icici/src/main/resources/application-icici.yml`

**Step 3:** Update `app/src/main/resources/` to only reference the common/cross-cutting configs.

### Phase 10 Success Criteria
- [ ] 12+ config files reduced to 4
- [ ] Broker-specific configs live in their own modules
- [ ] All app startup tests pass with new config structure
- [ ] `./gradlew :app:test` passes

---

## Phase 11 — `DomainEventVisitor` Removal (if applicable)

**Files to examine:**
```
core/src/main/java/.../event/DomainEventVisitor.java        → Check if used
core/src/main/java/.../event/DomainEvent.java               → Remove accept() if visitor removed
```

### What changes?

Search for all implementations of `DomainEventVisitor`:
```bash
grep -rn "implements DomainEventVisitor\|DomainEventVisitor" --include='*.java'
```

**If 0 or 1 implementations:** Remove the visitor pattern. Replace with pattern matching:

```java
// Before (visitor):
event.accept(new DomainEventVisitor() {
    @Override public void visit(MarketTickEvent e) { handleTick(e); }
    @Override public void visit(DepthUpdateEvent e) { handleDepth(e); }
    // 30+ visit methods for every event type
});

// After (Java 21 pattern matching switch):
switch (event) {
    case MarketTickEvent e -> handleTick(e);
    case DepthUpdateEvent e -> handleDepth(e);
    case OrderAccepted e -> handleOrderAccepted(e);
    default -> log.warn("Unhandled event: {}", event.getClass().getSimpleName());
}
```

### Phase 11 Success Criteria
- [ ] `DomainEventVisitor` interface deleted (if ≤ 1 implementation)
- [ ] `DomainEvent.accept()` removed from the interface
- [ ] Remaining visitor implementations migrated to pattern matching switch
- [ ] All tests pass

---

## Phase 12 — Cumulative Regression Run

**Final validation:** Run the full test suite across all changed modules:

```bash
./gradlew test componentTest
./gradlew fullRegressionTest  # If applicable
./gradlew coverageReportAll
```

### Compare coverage to baseline

Use the JaCoCo coverage audit to verify coverage hasn't decreased:
```bash
./gradlew coverageAuditAll coverageAuditSummary
```

### Phase 12 Success Criteria
- [ ] All tests pass across all 30+ Gradle subprojects
- [ ] Coverage is at or above the Phase 0 baseline
- [ ] No regression in spotbugs/checkstyle (run `./gradlew checkstyleMain checkstyleTest spotbugsMain spotbugsTest`)

---

## Summary: Phases at a Glance

| Phase | Change | Effort | Risk | Lines Changed | Primary Files |
|-------|--------|--------|------|---------------|---------------|
| 0 | Test baseline | Low | None | 0 | — |
| 1 | Delete empty marker interfaces | Low | Low | ~120 removed | 6 capability interfaces + 4 connections |
| 2 | Remove CapabilityMap | Medium | Medium | ~150 removed | CapabilityMap.java + 4 connections |
| 3 | Fix null return in capabilities() | Low | Low | ~20 | MarketDataProvider + callers |
| 4 | Simplify constructors | Medium | Low | ~200 removed | DhanBrokerConnection (2 ctors → 1) |
| 5 | Split DhanWebSocketMultiplexer | High | High | +4 files, -300 in one | DhanWebSocketMultiplexer → 5 files |
| 6 | Simplify pipeline layer | Medium | Medium | ~250 | GraphNormalizer, BasePipelineNode |
| 7 | Stream-ify analytics | Low | Low | ~80 | DefaultPerformanceAnalytics |
| 8 | Simplify failover layer | Medium | Medium | ~200 | 5 routing files → 1-2 |
| 9 | Fix executor shutdown bug | Low | Low | ~15 | DhanWebSocketMultiplexer |
| 10 | Consolidate configuration | Medium | Medium | ~8 files | application*.yml |
| 11 | Remove visitor pattern | Low | Low | ~50 | DomainEventVisitor |
| 12 | Cumulative regression | Low | None | 0 | — |

**Total estimated impact:**
- **Lines deleted:** ~1,200
- **Lines added:** ~800 (mostly new test code in Phase 5)
- **Files deleted:** ~8-10 (interfaces, normalizer, routing classes, CapabilityMap)
- **Files created:** ~5-6 (extracted classes, NodeMetricsTracker, PrimaryFailoverConnection)

---

## Recommended Execution Order

1. **Phase 0** (baseline) → **Phase 1** (marker interfaces) → **Phase 3** (null fix) → **Phase 9** (shutdown bug)
   — These are low-risk, high-confidence wins that can be merged quickly

2. **Phase 2** (CapabilityMap) → **Phase 4** (constructors)
   — Sequential dependency, changes to same files

3. **Phase 7** (streams) — Independent, any time

4. **Phase 11** (visitor) — Independent, low risk

5. **Phase 6a** (GraphNormalizer deletion) — Moderate risk, needs careful test validation

6. **Phase 8** (failover simplification) — Medium risk, requires production usage audit

7. **Phase 5** (WebSocket split) — Highest risk, do last with the most test coverage

8. **Phase 10** (configuration) — Can be done in parallel with other work

9. **Phase 12** (final regression) — Always last

---

## Testing Command Reference

```bash
# Module-specific tests (after any change to that module)
./gradlew :broker-api:test
./gradlew :broker-dhan:test
./gradlew :broker-icici:test
./gradlew :broker-upstox:test
./gradlew :broker-core:test
./gradlew :broker-gateway:test
./gradlew :pipeline-core:test
./gradlew :pipeline-runtime:test
./gradlew :trade-analytics:test
./gradlew :app:test

# Full validation
./gradlew test componentTest -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest
./gradlew coverageReportAll coverageAuditAll

# With static analysis
./gradlew build -x integrationTest
```
