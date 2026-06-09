# Trade-J Architecture Remediation Plan

> **Status**: Draft for Review  
> **Date**: 2026-06-07  
> **Scope**: Address Dhan-centric coupling, configuration leakage, and module boundary violations identified in `AUDIT_REPORT.md` and align with `ARCHITECTURE.md` engineering standards.

---

## 1. Current Violations Summary

| # | Violation | Severity | Location |
|---|-----------|----------|----------|
| V1 | Dhan is default/primary broker in core paths | P0 | `BrokerRuntimeModeResolver`, `CliConfig`, `TradingProperties`, `LoadBalancedBrokerGateway` |
| V2 | Broker-specific configs leak into `app` module | P0 | `BrokerConfiguration` (460 LOC), `UpstoxConfiguration` (420 LOC), `IciciConfiguration` (280 LOC) |
| V3 | Hardcoded broker conditionals (`instanceof DhanBrokerConnection`) | P0 | `LoadInstrumentCatalogStep`, `MarketDepthConfiguration`, `SubscriptionConfiguration` |
| V4 | Dhan-specific metric names hardcoded | P1 | `MicrometerConfiguration` |
| V5 | Dhan config paths reused by other brokers | P1 | `DhanConfigPaths` used in Upstox CLI code |
| V6 | No centralized cross-broker symbol mapping | P1 | `CrossBrokerSymbolMapper` exists but unused in runtime |
| V7 | Runtime duplication (`PipelineRuntimeService` vs `DagPipelineRuntimeService`) | P2 | `app/pipeline/` |
| V8 | Subscription management split across 3 classes | P2 | `SubscriptionCoordinator`, `SubscriptionManager`, `SubscriptionRecoveryManager` |

---

## 2. Remediation Strategy

### 2.1 Principle: Broker Modules Are Self-Contained

Each broker module (`broker-dhan`, `broker-upstox`, `broker-icici`) must:
- Own its Spring auto-configuration
- Export a single `BrokerConnectionFactory` bean
- Register itself via `spring.factories` (Spring Boot auto-configuration)
- Contain zero references to other broker modules

The `app` module must:
- Only reference `broker-api` and `broker-core`
- Discover brokers via `CapabilityRegistry` or `IBrokerConnection` beans
- Never import broker-specific classes

---

### 2.2 Phase 0: Immediate Neutralization (Week 1)

**Goal**: Remove Dhan as implicit default without breaking functionality.

#### 2.2.1 Neutralize Broker Runtime Mode Resolution

**Current**:
```java
// BrokerRuntimeModeResolver
public BrokerRuntimeMode resolve() {
    return BrokerRuntimeMode.DHAN_LIVE_WS; // hardcoded default
}
```

**Target**:
```java
public BrokerRuntimeMode resolve() {
    String brokerType = env.getProperty("trade.broker.type", "none");
    if ("none".equals(brokerType)) {
        // Return first available IBrokerConnection from context
        return context.getBeans(IBrokerConnection.class).values().stream()
            .findFirst()
            .map(conn -> BrokerRuntimeMode.fromConnection(conn))
            .orElseThrow(() -> new IllegalStateException("No broker configured"));
    }
    return BrokerRuntimeMode.valueOf(brokerType.toUpperCase());
}
```

**Files to change**:
- `app/src/main/java/com/tradej/app/config/BrokerRuntimeModeResolver.java`

#### 2.2.2 Neutralize CLI Default Broker

**Current**:
```java
// CliConfig.BrokerType
public static BrokerType parse(String value) {
    return BrokerType.DHAN; // implicit default
}
```

**Target**:
```java
public static BrokerType parse(String value) {
    if (value == null || value.isBlank()) {
        // Read from environment or config
        String configured = System.getenv("TRADE_BROKER_TYPE");
        if (configured != null) return BrokerType.valueOf(configured.toUpperCase());
        throw new IllegalArgumentException(
            "Broker type not specified. Set --broker or TRADE_BROKER_TYPE");
    }
    return BrokerType.valueOf(value.toUpperCase());
}
```

**Files to change**:
- `cli/src/main/java/com/tradej/cli/config/CliConfig.java`

#### 2.2.3 Remove Dhan-Required Constraint from TradingProperties

**Current**: `TradingProperties` requires `DhanProperties` (non-optional)

**Target**: Make all broker properties optional maps:
```java
@ConfigurationProperties(prefix = "trade")
public class TradingProperties {
    private Map<String, BrokerProfile> brokers = new HashMap<>();
    // ...
}
```

**Files to change**:
- `app/src/main/java/com/tradej/app/config/TradingProperties.java`
- `app/src/main/java/com/tradej/app/config/TradingProperties.BrokerProfile.java` (new)

---

### 2.3 Phase 1: Broker Auto-Configuration Migration (Weeks 2-3)

**Goal**: Move all broker-specific Spring wiring from `app` into broker modules.

#### 2.3.1 Create `spring.factories` in Each Broker Module

**`broker/dhan/src/main/resources/META-INF/spring.factories`**:
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.tradej.broker.dhan.config.DhanAutoConfiguration
```

**`broker/upstox/src/main/resources/META-INF/spring.factories`**:
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.tradej.broker.upstox.config.UpstoxAutoConfiguration
```

**`broker/icici/src/main/resources/META-INF/spring.factories`**:
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.tradej.broker.icici.config.IciciAutoConfiguration
```

#### 2.3.2 Create Auto-Configuration Classes

**`broker/dhan/src/main/java/com/tradej/broker/dhan/config/DhanAutoConfiguration.java`**:
```java
@Configuration
@ConditionalOnProperty(prefix = "trade.broker.dhan", name = "enabled", havingValue = "true")
@Import({DhanConnectionSettings.class, DhanTokenProvider.class, ...})
public class DhanAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public IBrokerConnection dhanBrokerConnection(...) {
        return new DhanBrokerConnection(...);
    }
    // All 30+ Dhan beans move here
}
```

**Repeat for Upstox and ICICI**.

#### 2.3.3 Remove Broker Configs from `app`

Delete or deprecate:
- `app/src/main/java/com/tradej/app/config/BrokerConfiguration.java`
- `app/src/main/java/com/tradej/app/config/UpstoxConfiguration.java`
- `app/src/main/java/com/tradej/app/config/IciciConfiguration.java`
- `app/src/main/java/com/tradej/app/config/BrokerStartupConfiguration.java`
- `app/src/main/java/com/tradej/app/config/MarketDepthConfiguration.java` (move depth logic to `broker-core`)
- `app/src/main/java/com/tradej/app/config/SubscriptionConfiguration.java`

**Migration path**:
1. Create auto-config classes in broker modules
2. Add `@ConditionalOnProperty` guards
3. Update `application.yml` to use `trade.broker.dhan.enabled=true` instead of `trade.broker-type=dhan`
4. Run tests to verify no bean resolution failures
5. Delete old configs

---

### 2.4 Phase 2: Neutral Broker Routing (Weeks 4-5)

**Goal**: Eliminate Dhan-first ordering and hardcoded broker conditionals.

#### 2.4.1 Fix LoadBalancedBrokerGateway Ordering

**Current**:
```java
// broker-core LoadBalancedBrokerGateway
this.connections = new CopyOnWriteArrayList<>(List.of(
    dhanBrokerConnection,
    iciciBrokerConnection,
    upstoxBrokerConnection
));
```

**Target**:
```java
// Accept list from configuration or discovery order
public LoadBalancedBrokerGateway(List<IBrokerConnection> connections) {
    this.connections = new CopyOnWriteArrayList<>(connections);
    this.primary = connections.get(0); // first registered is primary
}
```

**Configuration**:
```yaml
trade:
  broker:
    gateway:
      order: dhan,upstox,icici  # or auto-discover
```

#### 2.4.2 Replace `instanceof` Checks with Polymorphism

**Current**:
```java
// LoadInstrumentCatalogStep
if (brokerConnection instanceof DhanBrokerConnection) {
    // Dhan-specific catalog loading
}
```

**Target**:
```java
// Add to IBrokerConnection or create InstrumentCatalogLoader port
public interface IBrokerConnection {
    InstrumentCatalogLoader instrumentCatalogLoader();
    // ...
}

// Each broker returns its own loader
// DhanBrokerConnection returns DhanInstrumentCatalogLoader
// UpstoxBrokerConnection returns UpstoxInstrumentLoader
```

**Files to change**:
- `broker/api/src/main/java/com/tradej/broker/api/port/IBrokerConnection.java`
- `broker/dhan/src/main/java/com/tradej/broker/dhan/DhanBrokerConnection.java`
- `broker/upstox/src/main/java/com/tradej/broker/upstox/UpstoxBrokerConnection.java`
- `broker/icici/src/main/java/com/tradej/broker/icici/BreezeBrokerConnection.java`
- `app/src/main/java/com/tradej/app/startup/steps/LoadInstrumentCatalogStep.java`

#### 2.4.3 Neutralize Metric Naming

**Current**:
```java
// MicrometerConfiguration
counter("dhan.websocket.connected");
timer("dhan.order.latency");
```

**Target**:
```java
// Use broker tag
counter("trade.broker.websocket.connected", Tags.of("broker", brokerName));
timer("trade.broker.order.latency", Tags.of("broker", brokerName));
```

**Files to change**:
- `app/src/main/java/com/tradej/app/metrics/MicrometerConfiguration.java`
- All broker-specific metric classes

---

### 2.5 Phase 3: Cross-Broker Symbol Mapping (Weeks 6-7)

**Goal**: Centralize instrument resolution across brokers.

#### 2.5.1 Activate CrossBrokerSymbolMapper

**Current**: `CrossBrokerSymbolMapper` exists but is unused in runtime.

**Target**:
```java
// New central registry
public class InstrumentMappingRegistry {
    private final Map<InstrumentKey, CanonicalInstrument> canonicalMap = new ConcurrentHashMap<>();
    
    public void register(IBrokerConnection broker, List<Instrument> instruments) {
        for (Instrument instrument : instruments) {
            CanonicalInstrument canonical = toCanonical(instrument);
            canonicalMap.put(canonical.key(), canonical);
        }
    }
    
    public Instrument resolve(InstrumentKey key, BrokerCapability capability) {
        CanonicalInstrument canonical = canonicalMap.get(key);
        if (canonical == null) throw new InstrumentNotFoundException(key);
        return canonical.forBroker(capability.brokerType());
    }
}
```

**Integration points**:
- `GatewayEventBridge` uses `InstrumentMappingRegistry` instead of per-broker `InstrumentResolver`
- `ScanDependencies` uses registry for universe building
- `OptionScanController` uses registry for option chain requests

#### 2.5.2 Standardize Exchange Segment Codes

Create canonical enum:
```java
public enum CanonicalSegment {
    NSE_EQ, NSE_FNO, NSE_CD,
    BSE_EQ, BSE_FNO,
    MCX, NCDEX,
    IDX_I, IDX_F
}
```

Each broker's `SegmentMapper` maps to/from canonical:
- `DhanSegmentMapper` → `CanonicalSegment`
- `UpstoxSegmentMapper` → `CanonicalSegment`
- `DhanExchangeSegmentCodes` → `CanonicalSegment`

---

### 2.6 Phase 4: Module Boundary Hardening (Week 8)

**Goal**: Enforce architectural rules automatically.

#### 2.6.1 Enhance Architecture Tests

**Current**: `ModuleBoundaryArchitectureTest` exists but may not cover all violations.

**Add tests for**:
1. `app` module must not import any `broker.dhan.*`, `broker.upstox.*`, `broker.icici.*` classes
2. `app` module must not import `com.tradej.broker.dhan.config.*`
3. No `instanceof` checks against concrete broker classes in `app`, `broker-core`, `trading-*`
4. No hardcoded metric names with broker prefixes in `app`
5. All broker modules must have `META-INF/spring.factories`
6. All broker modules must export `IBrokerConnection` bean

**File**: `architecture-test/src/test/java/com/tradej/architecture/ModuleBoundaryArchitectureTest.java`

#### 2.6.2 Add ArchUnit Tests

Add ArchUnit dependency to `architecture-test/build.gradle`:
```gradle
testImplementation 'com.tngtech.archunit:archunit-junit5:1.3.0'
```

Create rules:
```java
@AnalyzeClasses(packages = "com.tradej")
class BrokerIsolationArchitectureTest {
    @Test
    void appModuleMustNotDependOnConcreteBrokers() {
        noClasses()
            .that().resideInAPackage("..app..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "..broker.dhan..",
                "..broker.upstox..",
                "..broker.icici.."
            )
            .check(importedClasses);
    }
    
    @Test
    void noInstanceofConcreteBrokers() {
        noMethods()
            .should().haveRawParameterTypes(
                DhanBrokerConnection.class,
                UpstoxBrokerConnection.class,
                BreezeBrokerConnection.class
            )
            .check(importedClasses);
    }
}
```

---

## 3. Detailed Migration Steps

### 3.1 Broker Auto-Configuration Migration

**Step 1**: Create `DhanAutoConfiguration` in `broker-dhan`
- Move all `@Bean` methods from `BrokerConfiguration` related to Dhan
- Add `@ConditionalOnProperty(prefix = "trade.broker.dhan", name = "enabled")`
- Add `@ConditionalOnMissingBean` for overrideability

**Step 2**: Create `UpstoxAutoConfiguration` in `broker-upstox`
- Same pattern as Dhan

**Step 3**: Create `IciciAutoConfiguration` in `broker-icici`
- Same pattern as Dhan

**Step 4**: Update `application.yml`
```yaml
trade:
  broker:
    dhan:
      enabled: true
      client-id: ${DHAN_CLIENT_ID}
      access-token: ${DHAN_ACCESS_TOKEN}
    upstox:
      enabled: false
    icici:
      enabled: false
  broker-type: auto  # instead of dhan
```

**Step 5**: Remove old configs from `app`
- Delete `BrokerConfiguration.java`
- Delete `UpstoxConfiguration.java`
- Delete `IciciConfiguration.java`
- Delete `BrokerStartupConfiguration.java`

**Step 6**: Update `BrokerRuntimeModeResolver`
- Use `trade.broker-type=auto` to discover first available broker
- Fall back to explicit broker type if specified

**Step 7**: Run architecture tests
```bash
./gradlew :architecture-test:test
```

---

### 3.2 Cross-Broker Symbol Mapping Migration

**Step 1**: Create `CanonicalInstrument` record
```java
public record CanonicalInstrument(
    InstrumentKey key,
    String symbol,
    CanonicalSegment segment,
    Map<BrokerType, Instrument> brokerSpecific
) {
    public Instrument forBroker(BrokerType broker) {
        return brokerSpecific.get(broker);
    }
}
```

**Step 2**: Create `InstrumentMappingRegistry`
- Singleton managed by Spring
- Populated at startup from all `IBrokerConnection` instrument catalogs
- Thread-safe reads via `ConcurrentHashMap`

**Step 3**: Update `GatewayEventBridge`
- Replace direct `InstrumentResolver` calls with `InstrumentMappingRegistry`
- Fall back to per-broker resolver if registry miss

**Step 4**: Update `ScanDependencies`
- Use registry for universe building
- Ensure option chain requests go through registry

**Step 5**: Add registry warmup to startup
- New `StartupStep`: `WarmInstrumentMappingStep`
- Runs after `LoadInstrumentCatalogStep`

---

### 3.3 Neutral Routing Migration

**Step 1**: Update `LoadBalancedBrokerGateway` constructor
- Accept `List<IBrokerConnection>` from Spring context
- Primary = first in list (configurable via `trade.broker.gateway.order`)

**Step 2**: Update `FailoverWebSocketMultiplexer`
- Remove Dhan-specific assumptions
- Use `SubscriptionRegistry` for all brokers uniformly

**Step 3**: Update `MarketDepthConfiguration`
- Remove `instanceof DhanBrokerConnection` check
- Use `BrokerCapabilities.depthSupported()` instead

**Step 4**: Update `SubscriptionConfiguration`
- Remove `DhanWebSocketMultiplexer` references
- Use `WebSocketMultiplexer` port from `IBrokerConnection`

---

### 3.4 Metric Neutralization

**Step 1**: Create `BrokerMetrics` interface in `broker-core`
```java
public interface BrokerMetrics {
    void recordWebSocketConnected(String brokerName);
    void recordOrderLatency(String brokerName, long latencyMs);
    void recordOrderPlaced(String brokerName);
    void recordOrderRejected(String brokerName, String reason);
}
```

**Step 2**: Implement `BrokerMetrics` in each broker module
- `DhanBrokerMetrics` in `broker-dhan`
- `UpstoxBrokerMetrics` in `broker-upstox`
- `IciciBrokerMetrics` in `broker-icici`

**Step 3**: Update `MicrometerConfiguration`
- Inject `BrokerMetrics` instead of hardcoded Dhan metrics
- Use `broker` tag in all meter names

---

## 4. Testing Strategy

### 4.1 Unit Tests
- Test each auto-configuration class in isolation
- Test `InstrumentMappingRegistry` with mock brokers
- Test `BrokerRuntimeModeResolver` with various config scenarios

### 4.2 Component Tests
- Test Spring context loads with only Dhan enabled
- Test Spring context loads with only Upstox enabled
- Test Spring context loads with multiple brokers enabled
- Test `LoadBalancedBrokerGateway` with different broker orderings

### 4.3 Architecture Tests
- Verify no `app` → `broker-dhan` dependency
- Verify no `instanceof` concrete broker classes
- Verify all broker modules have `spring.factories`
- Verify metric names are neutral

### 4.4 Integration Tests
- Test full startup with each broker profile
- Test instrument catalog loading for each broker
- Test WebSocket connection lifecycle for each broker
- Test failover between brokers

---

## 5. Rollback Plan

Each phase is independently reversible:

**Phase 0 Rollback**: Revert `BrokerRuntimeModeResolver` and `CliConfig` changes. Dhan becomes default again.

**Phase 1 Rollback**: Keep old configs in `app` alongside new auto-configs. Use `@ConditionalOnProperty` to switch between old/new.

**Phase 2 Rollback**: Keep `instanceof` checks behind feature flag `trade.broker.polymorphism.enabled`.

**Phase 3 Rollback**: Keep `CrossBrokerSymbolMapper` optional; fall back to per-broker resolution.

**Phase 4 Rollback**: Architecture tests are additive; removing them has no runtime impact.

---

## 6. Success Criteria

| Criterion | Measurement |
|-----------|-------------|
| Zero Dhan imports in `app` module | `grep -r "broker.dhan" app/src/main/java` returns empty |
| Zero `instanceof` broker checks | ArchUnit test passes |
| All brokers auto-configured | Each broker module has `spring.factories` |
| Neutral metric names | No `dhan.*` or `upstox.*` metric prefixes in code |
| Centralized symbol mapping | `CrossBrokerSymbolMapper` used in all resolution paths |
| Architecture tests pass | `./gradlew :architecture-test:test` green |

---

## 7. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Spring context fails to load after config migration | Medium | High | Run context load tests in isolation before deleting old configs |
| Broker auto-config conflicts with manual bean definitions | Medium | Medium | Use `@ConditionalOnMissingBean` everywhere |
| Instrument mapping registry causes startup slowdown | Low | Medium | Load catalogs asynchronously; warm registry in background |
| Failover behavior changes with neutral ordering | Low | High | Test failover scenarios explicitly; keep Dhan-first as configurable option |
| Metrics dashboards break with new naming | Medium | Low | Run both old and new metrics in parallel during transition |

---

## 8. Timeline

| Week | Phase | Deliverable |
|------|-------|-------------|
| 1 | Phase 0 | Neutralized broker resolution; no Dhan default |
| 2-3 | Phase 1 | Broker auto-configuration; `app` module broker-agnostic |
| 4-5 | Phase 2 | Neutral routing; no `instanceof` checks |
| 6-7 | Phase 3 | Centralized symbol mapping; canonical segments |
| 8 | Phase 4 | Architecture tests; module boundary enforcement |
| 9 | Validation | Full regression suite; Dhan certification readiness |

---

## 9. Appendix: Key Files Reference

### Files to Create
- `broker/dhan/src/main/java/com/tradej/broker/dhan/config/DhanAutoConfiguration.java`
- `broker/upstox/src/main/java/com/tradej/broker/upstox/config/UpstoxAutoConfiguration.java`
- `broker/icici/src/main/java/com/tradej/broker/icici/config/IciciAutoConfiguration.java`
- `broker/dhan/src/main/resources/META-INF/spring.factories`
- `broker/upstox/src/main/resources/META-INF/spring.factories`
- `broker/icici/src/main/resources/META-INF/spring.factories`
- `core/src/main/java/com/tradej/core/instrument/InstrumentMappingRegistry.java`
- `core/src/main/java/com/tradej/core/instrument/CanonicalInstrument.java`
- `core/src/main/java/com/tradej/core/instrument/CanonicalSegment.java`
- `broker-core/src/main/java/com/tradej/broker/core/metrics/BrokerMetrics.java`

### Files to Modify
- `app/src/main/java/com/tradej/app/config/BrokerRuntimeModeResolver.java`
- `app/src/main/java/com/tradej/app/config/TradingProperties.java`
- `cli/src/main/java/com/tradej/cli/config/CliConfig.java`
- `broker-core/src/main/java/com/tradej/broker/core/routing/LoadBalancedBrokerGateway.java`
- `broker/api/src/main/java/com/tradej/broker/api/port/IBrokerConnection.java`
- `app/src/main/java/com/tradej/app/metrics/MicrometerConfiguration.java`
- `app/src/main/java/com/tradej/app/startup/steps/LoadInstrumentCatalogStep.java`
- `architecture-test/src/test/java/com/tradej/architecture/ModuleBoundaryArchitectureTest.java`

### Files to Delete (after migration)
- `app/src/main/java/com/tradej/app/config/BrokerConfiguration.java`
- `app/src/main/java/com/tradej/app/config/UpstoxConfiguration.java`
- `app/src/main/java/com/tradej/app/config/IciciConfiguration.java`
- `app/src/main/java/com/tradej/app/config/BrokerStartupConfiguration.java`
- `app/src/main/java/com/tradej/app/config/MarketDepthConfiguration.java`
- `app/src/main/java/com/tradej/app/config/SubscriptionConfiguration.java`
