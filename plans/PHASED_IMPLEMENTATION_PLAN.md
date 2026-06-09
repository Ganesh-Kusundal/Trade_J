# Trade-J Architecture Remediation: Phased Implementation Plan

**Date:** 2026-06-09
**Base:** Actual code review findings (Dr. Venkat Subramaniam perspective)
**Scope:** Three core findings + high-value follow-ups, ordered by risk/reward

---

## Pre-Flight — Baseline Status (Phase 0)

| Check | Status |
|-------|--------|
| **Marker interfaces removed** | ✅ Already done — 0 matches for `OptionsCapable`, `FuturesCapable`, etc. |
| **Full build compiles** | ❌ `broker-gateway` has pre-existing compilation error (`DhanBrokerConnection.create()` missing — **already fixed in prior step**) |
| **Test suite baseline** | ⚠️ Partial — 1 pre-existing ICICI test failure (`IciciTokenRefreshExpiryTest.tokenManagerCreatesWithTotpMode()`) |
| **Branch** | `all-broker-support-and-moduler` (ahead of origin) |

**Before starting Phase 1, run:**
```bash
./gradlew test -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest
```
Confirm all tests pass (except the known ICICI failure). If the ICICI test fails, document it as a known pre-existing issue.

---

## Phase 1 — Simplify `getCapability()` Chains (Low Risk, High Payoff)

### Problem

5 `IBrokerConnection` implementations each have a linear `if`-chain of type checks:

| Class | Line count of chain | Files |
|-------|--------------------|-------|
| `DhanBrokerConnection` | 16 if-statements | `broker/dhan/.../DhanBrokerConnection.java` |
| `UpstoxBrokerConnection` | 16 if-statements | `broker/upstox/.../UpstoxBrokerConnection.java` |
| `IciciBrokerConnection` | 13 if-statements | `broker/icici/.../IciciBrokerConnection.java` |
| `PaperBrokerConnection` | 9 if-statements | `broker-gateway/.../simulation/PaperBrokerConnection.java` |
| `BacktestBrokerConnection` | 6 if-statements | `broker-gateway/.../simulation/BacktestBrokerConnection.java` |

Each chain has the same pattern:
```java
if (marketDataProvider != null && capabilityClass.isInstance(marketDataProvider)) return Optional.of(...);
if (futuresProvider != null && capabilityClass.isInstance(futuresProvider)) return Optional.of(...);
// ...
```

### Solution

Replace the if-chain with a `Map<Class<?>, Object>` lookup built once in the constructor.

#### For `DhanBrokerConnection`, `UpstoxBrokerConnection`, `IciciBrokerConnection`:

**New code pattern** (add a field, initialize in constructor):
```java
private final Map<Class<?>, Object> capabilityMap;

// In the multi-adapter constructor:
this.capabilityMap = new HashMap<>();
putIfNotNull(capabilityMap, MarketDataProvider.class, marketDataProvider);
putIfNotNull(capabilityMap, OrderCommand.class, orderCommand);
putIfNotNull(capabilityMap, OrderQuery.class, orderQuery);
// ... etc.

// Replace getCapability:
@Override
public <T> Optional<T> getCapability(Class<T> capabilityClass) {
    if (capabilityClass == null) return Optional.empty();
    if (capabilityClass.isInstance(this)) return Optional.of(capabilityClass.cast(this));
    Object impl = capabilityMap.get(capabilityClass);
    if (impl != null && capabilityClass.isInstance(impl)) {
        return Optional.of(capabilityClass.cast(impl));
    }
    return Optional.empty();
}

private static void putIfNotNull(Map<Class<?>, Object> map, Class<?> type, Object impl) {
    if (impl != null) map.put(type, impl);
}
```

**Benefit:** 16 verbose `if` statements → 1 loop-like lookup. Adding a new capability means one line (`putIfNotNull(...)`) instead of 3 lines (null check + isInstance check + return).

#### For `PaperBrokerConnection` and `BacktestBrokerConnection`:

Same pattern, but simpler since they create all adapters inline. These are test/simulation classes so lower priority, but the same refactoring applies for consistency.

### Testing Strategy

| Test | What to verify |
|------|---------------|
| Existing contract tests | `DhanBrokerConnectionContractTest`, `UpstoxBrokerConnectionContractTest`, `IciciBrokerConnectionContractTest` — already test capability resolution |
| New unit test | Write a focused test that calls `getCapability()` for each registered type and verifies the correct impl is returned |
| Null safety | Test `getCapability(null)` returns `Optional.empty()` |
| Unknown type | Test `getCapability(String.class)` returns `Optional.empty()` |

### Success Criteria

- [ ] `DhanBrokerConnection.getCapability(OrderCommand.class)` returns correct instance
- [ ] `DhanBrokerConnection.getCapability(null)` returns `Optional.empty()`
- [ ] `UpstoxBrokerConnection.getCapability(MarketDataProvider.class)` returns correct instance
- [ ] All 3 contract tests pass
- [ ] `DhanBrokerConnection.getCapability(String.class)` returns `Optional.empty()`

---

## Phase 2 — Extract DhanWebSocketMultiplexer God Class (Medium Risk, Highest Payoff)

### Problem

`DhanWebSocketMultiplexer` (~350 lines) manages all of these:

1. **Connection lifecycle**: creating, connecting, disconnecting market feed + order stream WebSocket clients
2. **Event normalization**: parsing feed packets into `MarketTickEvent`, handling order/trade payloads
3. **Dedup**: filtering duplicate ticks via `MarketTickDedupFilter`
4. **Listener management**: `CopyOnWriteArrayList<MarketDataListener>` and `CopyOnWriteArrayList<OrderUpdateListener>`
5. **Reconnection**: circuit breaker, backoff, scheduled reconnect
6. **Token rotation**: reacting to token refresh
7. **Subscription reconciliation**: periodic resubscription every 5 minutes
8. **Health monitoring**: delegated to `DhanWebSocketHealthMonitor` (already separate — good)
9. **Depth client management**: delegated to `DhanWebSocketSubscriptionManager` (already separate — good)

And crucially: **the private `wireListeners()` method and `handleFeedPacket()`, `handleOrderPayload()`, `handleTradePayload()` are untestable** because they're private and create real WebSocket clients internally.

### Solution: Extract 3 collaborators

#### Step 2a — Extract `DhanWebSocketConnectionManager`

**Purpose:** Owns the two WebSocket clients, their creation, connection, and disconnection.

**Move from multiplexer to new class:**
- `marketFeedClient` field
- `orderStreamClient` field
- `transportLock`
- `bindClientsLocked()`, `closeClientsLocked()`, `ensureClientsLocked()`
- `connectMarketFeedLocked()`, `connectOrderStreamLocked()`
- `connected` volatile flag

**New interface:**
```java
// broker/dhan/src/main/java/.../websocket/DhanWebSocketConnectionManager.java
public final class DhanWebSocketConnectionManager {
    interface LifecycleListener {
        void onConnected();
        void onDisconnected(int code, String reason);
        void onError(Throwable error);
        void onPacket(DhanMarketFeedPacket packet, FeedMode feedMode);
        void onOrderUpdate(JsonNode update);
        void onTradeUpdate(JsonNode update);
    }
    // ...
}
```

#### Step 2b — Extract `DhanMarketEventNormalizer`

**Purpose:** Feed packet → `MarketTickEvent` normalization, order/trade payload parsing.

**Move from multiplexer to new class:**
- `handleFeedPacket()` logic
- `handleOrderPayload()` logic
- `handleTradePayload()` logic
- `latestOrderStatuses` map
- `tickDedupFilter` reference

**New class makes these testable:**
```java
public final class DhanMarketEventNormalizer {
    public MarketTickEvent normalizeFeedPacket(DhanMarketFeedPacket packet, DhanInstrumentDefinition def, FeedMode mode) { ... }
    public Optional<DomainEvent> normalizeOrder(JsonNode update, OrderStatus previousStatus) { ... }
    public Optional<DomainEvent> normalizeTrade(JsonNode update) { ... }
}
```

Now you can unit-test packet normalization without creating WebSocket clients.

#### Step 2c — Extract `DhanReconnectController`

**Purpose:** Reconnection state machine with circuit breaker, backoff, and scheduling.

**Move from multiplexer to new class:**
- `reconnectAttempts`, `circuitOpenUntilMs`
- `reconnectScheduler` executor
- `reconnectWithBackoff()`, `executeReconnect()`
- `resetReconnectCircuitLocked()`

**New class:**
```java
public final class DhanReconnectController {
    interface ReconnectHandler {
        void onReconnect();
    }
    // ...
}
```

#### Step 2d — Simplify `DhanWebSocketMultiplexer` to a Facade

After extracting the 3 collaborators, the multiplexer becomes a thin facade:

```java
public final class DhanWebSocketMultiplexer implements WebSocketMultiplexer {
    private final DhanWebSocketConnectionManager connectionManager;
    private final DhanMarketEventNormalizer eventNormalizer;
    private final DhanReconnectController reconnectController;
    private final DhanWebSocketSubscriptionManager subscriptionManager;
    private final DhanWebSocketHealthMonitor healthMonitor;
    // Listener lists (still owned by facade)
    
    public void connect() {
        // Delegates to connectionManager.connect(...)
        // Registers LifecycleListener that calls eventNormalizer
    }
    
    // subscribe/unsubscribe → delegates to connectionManager + subscriptionManager
    // onMarketData/onOrderUpdate → manages listener lists
    
    private void handleFeedPacket(...) { eventNormalizer.normalizeFeedPacket(...); }
}
```

### Testing Strategy

| Test | What to verify |
|------|---------------|
| `DhanMarketEventNormalizerTest` (new) | Feed packet → MarketTickEvent mapping for TICKER, QUOTE, FULL |
| `DhanMarketEventNormalizerTest` (new) | Order status dedup (rejected once → no duplicate) |
| `DhanMarketEventNormalizerTest` (new) | Trade payload → OrderFilled event |
| `DhanMarketEventNormalizerTest` (new) | Null/error handling |
| `DhanReconnectControllerTest` (new) | Backoff delay computation |
| `DhanReconnectControllerTest` (new) | Circuit breaker: open after N failures |
| `DhanReconnectControllerTest` (new) | Circuit breaker: reset after cooldown |
| `DhanWebSocketConnectionManagerTest` (new) | Client creation + connection lifecycle |
| Existing tests | `DhanWebSocketShutdownTest`, `DhanResubscribeUnitTest`, etc. — must still pass |
| Contract tests | `DhanBrokerConnectionContractTest` — end-to-end verification |

### Migration Safety

**No behavioral changes.** The extraction preserves:
- All constructor signatures (delegating to new classes)
- All `WebSocketMultiplexer` interface methods
- All event types emitted
- All logging and metrics calls
- Thread safety (same lock strategy)

### Success Criteria

- [ ] No production code changes (only class extraction + delegation)
- [ ] All existing tests pass unchanged
- [ ] New `DhanMarketEventNormalizer` has ≥90% branch coverage on its 3 methods
- [ ] New `DhanReconnectController` has ≥90% branch coverage
- [ ] `DhanWebSocketMultiplexer` reduced from ~350 lines to ~150 lines

---

## Phase 3 — Simplify `GatewayEventBridge` Dispatch (Medium Risk)

### Problem

`GatewayEventBridge.onDomainEvent()` has a 17-arm `switch` on event types with inline JSON payload construction:
```java
switch (event) {
    case MarketTickEvent tick -> router.publish(..., marketTickPayload(tick));
    case DepthUpdateEvent depth -> router.publish(..., depthPayload(depth));
    // ... 15 more cases
}
```

Adding a new event type requires changes to 3 places:
1. `register()` — add subscription to event bus
2. `onDomainEvent()` — add switch case
3. A payload builder method

### Solution: `EventPayloadSerializer` interface

Define a per-event-type serializer that can be registered in a map:

```java
@FunctionalInterface
interface EventPayloadSerializer<T extends DomainEvent> {
    byte[] serialize(T event, ObjectMapper mapper, InstrumentResolver resolver);
}
```

Register in a `Map<Class<?>, EventPayloadSerializer<?>>`:
```java
private static Map<Class<?>, EventPayloadSerializer<?>> buildSerializers() {
    Map<Class<?>, EventPayloadSerializer<?>> map = new HashMap<>();
    map.put(MarketTickEvent.class, (EventPayloadSerializer<MarketTickEvent>) BridgePayloads::marketTick);
    map.put(DepthUpdateEvent.class, (EventPayloadSerializer<DepthUpdateEvent>) BridgePayloads::depth);
    // ... one line per type
    return Map.copyOf(map);
}
```

And move all payload construction into a `BridgePayloads` utility class.

Then `onDomainEvent()` becomes:
```java
@SuppressWarnings("unchecked")
void onDomainEvent(DomainEvent event) {
    EventPayloadSerializer<DomainEvent> serializer = 
        (EventPayloadSerializer<DomainEvent>) serializers.get(event.getClass());
    if (serializer != null) {
        // Lookup topic from a parallel map
        router.publish(topicFor(event.getClass()), serializer.serialize(event, ...));
    }
}
```

Adding a new event type is then one line in `buildSerializers()` and one line in `topicFor()`.

### Testing Strategy

- Extract payload serializers into testable methods
- Write `BridgePayloadsTest` that verifies each serializer produces correct JSON
- No behavioral change to existing event routing

### Success Criteria

- [ ] `onDomainEvent()` switch replaced with map lookup
- [ ] Each serializer independently testable
- [ ] All existing integration tests pass

---

## Phase 4 — Clean Up Legacy/Deprecated Code (Low Risk)

### 4a — Remove `BasePipelineNode` (already `@Deprecated`)

- File: `pipeline/core/src/main/java/.../runtime/BasePipelineNode.java`
- Action: Delete the class
- Impact: Must ensure no production code still extends it. Search for `extends BasePipelineNode`.
- If found: Migrate to direct `PipelineNode` implementation with `NodeMetricsTracker` composition.

### 4b — Remove `@Deprecated` constructors and factory methods

Once all callers are migrated to the multi-adapter constructors:
- `DhanBrokerConnection(DhanConnectionSettings, MultiBucketRateLimiter, IdempotencyCachePort)` — legacy constructor
- `DhanBrokerConnection.create(DhanConnectionSettings, IdempotencyCachePort)` — factory
- Remove after confirming no callers remain

### 4c — Remove or replace `GraphNormalizer` references

The file doesn't exist on disk (`[FILE_DOES_NOT_EXIST]`). Remove any imports or references.

### Testing Strategy

- Compile check: `./gradlew compileJava` must pass
- Search for `BasePipelineNode` usage: ensure migration is complete

### Success Criteria

- [ ] `BasePipelineNode.java` deleted
- [ ] No compilation errors
- [ ] No dangling references to deleted classes

---

## Phase 5 — Stream-ify Performance Analytics (Low Risk)

### Problem

`DefaultPerformanceAnalytics.calculateSharpeRatio()` and `calculateSortinoRatio()` use imperative `for` loops for variance calculation:
```java
for (BigDecimal r : returns) {
    BigDecimal diff = r.subtract(mean);
    variance = variance.add(diff.multiply(diff));
}
```

### Solution

Replace with stream-based reduction:
```java
BigDecimal variance = returns.stream()
    .map(r -> r.subtract(mean))
    .map(diff -> diff.multiply(diff))
    .reduce(BigDecimal.ZERO, BigDecimal::add)
    .divide(BigDecimal.valueOf(returns.size()), MC);
```

### Testing Strategy

- Existing tests (`DefaultPerformanceAnalyticsTest`, `PerformanceReportTest`, `PerformanceReportUnitTest`) must produce identical results
- Use golden file comparison to verify numerical equivalence

### Success Criteria

- [ ] `calculateSharpeRatio()` returns same value before/after for golden test cases
- [ ] `calculateSortinoRatio()` returns same value before/after
- [ ] All existing analytics tests pass

---

## Phase 6 — Full Regression Suite (Validation)

After all phases are complete, run the full test suite:

```bash
# Clean build + all tests (exclude known-failing ICICI test)
./gradlew clean test -x spotbugsMain -x spotbugsTest -x checkstyleMain -x checkstyleTest --continue

# Module-specific (fast iteration during phases)
./gradlew :broker-dhan:test :broker-upstox:test :broker-icici:test --continue
./gradlew :pipeline-core:test :trade-analytics:test --continue
./gradlew :broker-gateway:test --continue
./gradlew :runtime-disruptor:test --continue
```

### Architecture Tests

```bash
# ArchUnit tests enforce module boundaries
./gradlew :architecture-test:test
```

---

## Execution Order Recommendation

| Order | Phase | Risk | Effort | Value |
|-------|-------|------|--------|-------|
| **1** | Phase 4a — Remove `BasePipelineNode` | Low | 1 file | Cleanup |
| **2** | Phase 4c — Remove `GraphNormalizer` refs | Low | 1 search | Cleanup |
| **3** | Phase 1 — Simplify `getCapability()` chains | Low | 5 files | **High** — eliminates 45+ lines of copy-paste |
| **4** | Phase 5 — Stream-ify analytics | Low | 1 file | Medium — code quality |
| **5** | Phase 2 — Extract `DhanWebSocketMultiplexer` | **Medium** | 6 files | **Highest** — unlocks testability |
| **6** | Phase 3 — Simplify `GatewayEventBridge` | Medium | 3 files | Medium — reduces shotgun surgery |
| **7** | Phase 4b — Remove legacy constructors | Low | 1 file | Cleanup (after confirming no callers) |
| **8** | Phase 6 — Full regression | Low | — | Validation |

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| WebSocket extraction breaks runtime behavior | Extract in multiple small commits; run smoke tests (`dhan-smoke.sh`) after each |
| `getCapability()` refactor breaks capability lookup | Contract tests verify exact types; add explicit mapping tests |
| `BasePipelineNode` removal breaks unknown subclass | Search codebase thoroughly before deleting |
| Stream refactor changes float rounding | Use golden test comparison; maintain same `MathContext` |
| Parallel efforts cause merge conflicts | Execute phases sequentially, not in parallel |

---

## Estimated Timeline

| Phase | Estimated effort | Dependencies |
|-------|-----------------|-------------|
| Phase 0 (baseline) | 30 min | None |
| Phase 4a/4c (quick cleanup) | 15 min | Phase 0 |
| Phase 1 (getCapability) | 2 hours | Phase 0 |
| Phase 5 (stream analytics) | 30 min | Phase 1 |
| Phase 2 (WebSocket extraction) | 4 hours | Phase 1 |
| Phase 3 (Gateway bridge) | 2 hours | Phase 2 |
| Phase 4b (legacy cleanup) | 15 min | All above |
| Phase 6 (regression) | 1 hour | All above |

**Total estimated: ~10 hours of implementation + 2 hours testing**

---

## Appendix: File Change Summary

| File | Phase | Action |
|------|-------|--------|
| `broker/dhan/.../DhanBrokerConnection.java` | 1 | Replace if-chain with Map lookup |
| `broker/upstox/.../UpstoxBrokerConnection.java` | 1 | Replace if-chain with Map lookup |
| `broker/icici/.../IciciBrokerConnection.java` | 1 | Replace if-chain with Map lookup |
| `broker-gateway/.../PaperBrokerConnection.java` | 1 | Replace if-chain with Map lookup |
| `broker-gateway/.../BacktestBrokerConnection.java` | 1 | Replace if-chain with Map lookup |
| `broker/dhan/.../DhanWebSocketMultiplexer.java` | 2 | Extract → reduce to facade |
| `broker/dhan/.../DhanWebSocketConnectionManager.java` | 2 | **New file** |
| `broker/dhan/.../DhanMarketEventNormalizer.java` | 2 | **New file** |
| `broker/dhan/.../DhanReconnectController.java` | 2 | **New file** |
| `broker/dhan/.../DhanMarketEventNormalizerTest.java` | 2 | **New test** |
| `broker/dhan/.../DhanReconnectControllerTest.java` | 2 | **New test** |
| `gateway/.../GatewayEventBridge.java` | 3 | Replace switch with serializer map |
| `gateway/.../BridgePayloads.java` | 3 | **New file** — extracted serializers |
| `gateway/.../BridgePayloadsTest.java` | 3 | **New test** |
| `pipeline/core/.../BasePipelineNode.java` | 4a | **Delete** |
| `pipeline/analytics/.../DefaultPerformanceAnalytics.java` | 5 | Replace loops with stream reduce |

**Totals:** ~10 files modified, ~5 new files, ~1 file deleted, ~3 new test files
