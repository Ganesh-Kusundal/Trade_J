# Composition Root Decision — Trade-J

**Status:** SUPERSEDED 2026-06-12 (see "Supersession" section below). The 2026-06-11 recommendation in §5 is no longer the chosen direction.
**Scope:** `/Users/apple/Downloads/Trade_J/composition/` vs `/Users/apple/Downloads/Trade_J/app/src/main/java/com/tradej/app/config/`
**Date:** 2026-06-11 (analysis), 2026-06-12 (supersession)

---

## 0. Supersession (2026-06-12)

The recommendation in §5 below ("slim `composition/` down to the broker-only types, delete the 5 wrapper classes") was **rejected by the user** in favor of the opposite direction, captured in `docs/compose/plans/2026-06-12-platform-consolidation.md`.

**What was actually done on 2026-06-12:**
- Recreated the 5 wrapper classes the doc recommended deleting: `FullComposition`, `DataComposition`, `ExecutionComposition`, `PipelineComposition`, `ClockComposition` in `composition/src/main/java/com/tradej/composition/`.
- `BrokerComposition` (the load-bearing broker-only class) kept.
- `FullComposition` is wired as a Spring bean via a new `FullCompositionConfiguration` in `app/`.
- The 5 risk beans now owned by `ExecutionComposition` (RiskLimits, EventSourcedNetPositionProvider, MarginEnforcementHandler, KillSwitchCoordinator, PositionRiskHandler) were stripped from `TradingConfiguration.java` and exposed as shim `@Bean` methods in `FullCompositionConfiguration` that delegate to `fullComposition.executionComposition()`. 8+ existing consumers continue to work without changes.
- The NPE risk the doc flagged at §4.2 (`FullComposition.execution()` returning null on the legacy `create()` path) is mitigated: the legacy `create(BrokerProfile, StorageProfile, RiskProfile)` overload is intentionally **NOT** provided; `execution()` is a non-null field; `brokerOnly()` accessors for `data()`, `execution()`, `pipeline()` throw `IllegalStateException` with a clear message.

**What is still pending (per the plan file):**
- P2.9: `DhanBrokerConfiguration` dual-root (15 port beans) — the shim pattern does not simplify this because the beans are broker-type-conditional.
- P2.10: Same dual-root fix for `UpstoxBrokerConfiguration` and `BrokerAdapterConfiguration`.
- P2.11: 4 integration tests to migrate from `BrokerComposition.create()` direct calls to `FullComposition.createFull()` or `@SpringBootTest`.
- P2.12: Full test sweep across `:cli:test`, `:broker-gateway:test`, and `:architecture-test:test` (4 pre-existing failures, unrelated to this work).
- Consumer migrations: each Spring consumer of the 5 shim beans is migrated to inject `FullComposition` directly; once all migrated, the shims come out.

The doc's evidence in §1-§4 remains valid as historical analysis (it accurately described a state that no longer exists — those classes were deleted at some point between 2026-06-11 and 2026-06-12, likely by a prior session that did the work this plan re-does). The plan `docs/compose/plans/2026-06-12-platform-consolidation.md` is the source of truth for the chosen direction.

---

## 1. Current state

### 1.1 Spring `@Configuration` classes in `app/` (17 outer, 27 total including inner classes)

| File | Outer? | Wires (key beans) |
|------|--------|-------------------|
| `app/src/main/java/com/tradej/app/TradingApplication.java:8-10` | No — entry point; uses `@SpringBootApplication`, `@ConfigurationPropertiesScan`, `@ComponentScan("com.tradej")` | Enables component scan across `com.tradej.*` |
| `app/src/main/java/com/tradej/app/config/RegistryConfiguration.java:13` | Yes | `EventRegistry` (40+ event types), `StrategyRegistry.discover()`, `ScannerRegistry.discover()`, `IndicatorRegistry.discover()`, `TransformationRegistry.discover()` (lines 17-81) |
| `app/src/main/java/com/tradej/app/config/TradingConfiguration.java:52` | Yes (+ inner `RiskEventBusSubscriber` at line 93) | `RiskLimits`, `MarkToMarketRiskMonitor`, `RiskEventBusSubscriber` (line 80, breaks cycle), `MatchingEngine`, `PnLLedger`, `SimulatedOrderService`, `PortfolioEngine`, `TradingCircuitBreaker`, `OrderIdentityRegistry`, `EventSourcedNetPositionProvider`, `OrderManagementService`, `KillSwitchCoordinator`, `MarginEnforcementHandler`, `PositionRiskHandler`, `CommandHandler`, `ExecutionHandler`, `ModelRegistry`, `ThresholdMLInferenceEngine`, `MLStrategyPlugin`, `TickPriceChangeStrategy`, `DepthImbalanceStrategy`, `OptionsContextStrategyPlugin`, `GraphStrategySandbox` |
| `app/src/main/java/com/tradej/app/config/BrokerConfiguration.java:30` | Yes (+ inner `BrokerMarketDataConfig` at line 77) | `BrokerRegistry` (line 33), `CaffeineIdempotencyCache` (line 41), `OrderIdentityRegistry` (`@Primary`, line 47), `BrokerCapabilities` (line 52), `LivePnlService` (line 57), `OptionStrikeResolver` (line 65), `MarketDepthOrchestrator` (line 70); inner class: `BrokerHistoricalQueryService` (line 82), `BrokerExpiredOptionQueryService` (line 88) |
| `app/src/main/java/com/tradej/app/config/BrokerAdapterConfiguration.java:55` | Yes (+ 3 inner: `DhanAdapterConfig` line 58, `IciciAdapterConfig` line 214, `SimulationAdapterConfig` line 353) | Dhan: `MultiBucketRateLimiter`, `DhanConnectionSettings`, `DhanTokenManager`, **`BrokerComposition.create(...)` (line 127)**, `brokerConnection` (line 137), `ObservableMarketDataProvider`/`OrderCommand`, all 14 broker ports. ICICI: parallel set with `iciciBrokerComposition()` (line 269). Simulation: parallel set with `simulationBrokerComposition()` (line 369). |
| `app/src/main/java/com/tradej/app/config/UpstoxBrokerConfiguration.java:40` | Yes | `UpstoxConnectionSettings` (line 45), `upstoxBrokerComposition` returning `BrokerComposition.create(profile)` (line 84), `UpstoxTokenManager`, `UpstoxBrokerConnection`, all 14 broker ports, `NewsProvider`, `UpstoxExpiredOptionService` |
| `app/src/main/java/com/tradej/app/config/GatewayBrokerConfiguration.java:43` | Yes (+ 4 inner: `GatewayConfig` line 46, `GatewayBeansConfig` line 66, `GatewayAppConfig` line 109, `GatewayWebSocketConfiguration` line 180) | `LoadBalancedBrokerGateway` (line 52), `BrokerGateway` accepting `ObjectProvider<BrokerComposition>` (line 70), `BrokerRouter`, `MarketGateway`, `GatewayTopicRouter`, `GatewayWebSocketHandler`, `GatewayEventBridge`, `GatewayPipelineHealthBroadcaster` |
| `app/src/main/java/com/tradej/app/config/DataConfiguration.java:91` | Yes (+ 3 inner: `OptionsAnalyticsConfig` line 337, `DepthAnalyticsConfig` line 583, `DhanDepthConfig` line 661) | `WorkspacePaths`, `ReadModelStore`, `DeadLetterQueue` (line 121), `ChronicleAuditLogWriter` (line 127), `DuckDbEventStore` (line 132), `AsyncDuckDbEventStore` (line 137), `EventSourcedOrderRepository` (line 144), `HistoricalRangeService` (line 151), `DuckDbPipelineGraphStore` (line 156), `ReplayRunner` (line 161), `ReplayStateManager` (line 166), `ReplayClock` (line 186, `@Profile("replay")`), `OrderIdentityRehydrator` runner (line 191), `ReplayableRegistry` (line 199), `ReplayOrchestrator` (line 212), `PositionStateRebuilder` (line 225), `OptionsAwareFeatureStore`/`FeatureStore` (lines 234, 240), `DuckDbFeatureStore` (line 245), `AsyncDuckDbWriter` (line 251), `DuckDbAnalyticsEngine` (line 270), `HistoricalDataCatalog`/`BarRepository`/`RollingOptionHistoricalRepository` (lines 295-307), `HistoricalAnalyticsService` (line 310), `OptionChainPollingService` (inner), `DuckDbHistoricalWarehouse` (line 409), `DownloadJobService` (line 414), `EquityDownloadJobService` (line 432), `HiveCacheEquityImporter`, `DownloadJobRegistry`, `historicalDownloadExecutor` (line 465), `TradingCalendarStore`, `CompositeHolidayCalendar`, `DataGapScanService`, `ParquetWriteService`, `GapDetector`, `RuntimeParquetExporter`, `SyncStatusStore`, `CanonicalBarQuery`, `MultiIntervalGenerator`, `HistoricalDataStore`, `ParquetReplayAdapter`, `IncrementalSyncService`, `BackfillService`, `OrderBookEngine` + 6 depth analytics beans (inner `DepthAnalyticsConfig`), `DhanMarketDepthProvider` (inner `DhanDepthConfig`) |
| `app/src/main/java/com/tradej/app/config/RuntimeAndStartupConfiguration.java:67` | Yes | `Clock` (line 74, `@Profile("!replay")`), `TradingClock` (live line 81, replay line 87), `EventMetadataFactory` (line 92), `RuntimeModeHolder` (line 99), `RuntimeBusHolder` (line 108), `EventBus` (line 121, `@Lazy @Primary`, chooses `DisruptorEventBus` vs `SimpleEventBus`), `dhanEventBus`/`upstoxEventBus`/`iciciEventBus` (`BrokerScopedEventBus`, lines 159-174), `DisruptorBusMetrics` (line 178), `MarketDataPipeline` (line 190), `OrderPipeline` (line 196), `MarketDataHealthIndicator` (line 201), `RuntimeHealthState` (line 212), `BrokerLifecycleManager` (line 217), `StartupDependencies` (line 222), `runtimeStarter` `ApplicationRunner` (line 268) |
| `app/src/main/java/com/tradej/app/pipeline/PipelineConfiguration.java:39` | Yes | `VirtualClock` (line 47), `CandleAggregationService` (line 52), `ReactorBridge` (line 57), `ReactorBridgeMetrics` (line 62), `NodeRegistry` (line 67) with 8 node-type descriptors, `PipelineNodeFactory` (line 175), `PipelineRuntimeService` (line 207), `pipelineGraphBootstrap` `ApplicationRunner` (line 218), `DagPipelineRuntimeService` (line 235), `dagPipelineIngressBridge` (line 244), `dagGraphBootstrap` `ApplicationRunner` (line 251) |
| `app/src/main/java/com/tradej/app/config/ScanConfiguration.java:42` | Yes | `InstitutionalScanEngine` (line 50), `IndicatorEngine` (line 55), `StudioChartService` (line 60), `CandleReplaySession` (line 75), `ReconnectListenerRegistry` (line 90), `SubscriptionCoordinator` (line 95), `SubscriptionManager` (line 102), `SubscriptionRecoveryManager` (line 110), `OptionScanService` (line 122), `ScanEngine`/`DuckDbScanStore`/`ScanDependencies`/`RuntimeSubscriptionManager`/`ScanService` (all `@ConditionalOnProperty("trade.scan.enabled"=true)`, lines 128-184) |
| `app/src/main/java/com/tradej/app/config/ObservabilityConfiguration.java:51` | Yes | (per audit; metrics & observability wiring) |
| `app/src/main/java/com/tradej/app/config/PrometheusConfiguration.java:20` | Yes | (Prometheus / actuator integration) |
| `app/src/main/java/com/tradej/app/config/WebConfiguration.java:35` | Yes | (web layer beans) |
| `app/src/main/java/com/tradej/app/config/VirtualThreadConfiguration.java:21` | Yes | (virtual-thread executor wiring) |
| `app/src/main/java/com/tradej/app/config/AdminConfiguration.java:30` | Yes (+ inner at line 95) | (admin / management endpoints) |
| `app/src/main/java/com/tradej/app/config/BrokerResilienceMetricsConfiguration.java:13` | Yes | (resilience metrics for brokers) |
| `app/src/main/java/com/tradej/app/security/SecurityConfiguration.java:16` | Yes | (security filter chain) |

### 1.2 Composition factory methods in `composition/` (6 classes, 12 signatures)

| Class | File | Static method | Wires |
|-------|------|---------------|-------|
| `FullComposition` | `composition/src/main/java/com/tradej/composition/FullComposition.java` | `create(BrokerProfile, StorageProfile, RiskProfile)` — line 40 | `BrokerComposition.create(profile)` + `DataComposition.create(storageProfile)`, returns `FullComposition(broker, data, null)` — **execution left null** |
| `FullComposition` | same | `createFull(BrokerProfile, StorageProfile, RiskProfile, PortfolioEngine, OrderManagementService)` — line 62 | Same as `create()` plus `ExecutionComposition.create(riskProfile, portfolio, connection, oms)` |
| `FullComposition` | same | `createFull(BrokerProfile, StorageProfile, RiskProfile)` — line 87 | Convenience overload; calls 5-arg with `null, null` |
| `FullComposition` | same | `brokerOnly(BrokerProfile)` — line 95 | `BrokerComposition.create(profile)`, returns `FullComposition(broker, null, null)` |
| `BrokerComposition` | `composition/src/main/java/com/tradej/composition/BrokerComposition.java` | `create(BrokerProfile)` — line 30 | `ServiceLoaderBrokerRegistry.provider(source)`, `provider.create(profile.toGenericConfig())`, `IBrokerConnection` |
| `BrokerComposition` | same | `create(BrokerProfile, IdempotencyCachePort)` — line 34 | Same, plus idempotency cache (note: the idempotency arg is **not** used in the body — see [Section 4](#4-the-npe-risk)) |
| `DataComposition` | `composition/src/main/java/com/tradej/composition/DataComposition.java` | `create(StorageProfile)` — line 55 | `ChronicleDeadLetterQueue`, `ChronicleAuditLogWriter`, `DuckDbConnectionPool`, `DuckDbEventStore`, `AsyncDuckDbEventStore`, `DuckDbPipelineGraphStore`, `DuckDbScanStore` |
| `ExecutionComposition` | `composition/src/main/java/com/tradej/composition/ExecutionComposition.java` | `create(RiskProfile, PortfolioEngine, IBrokerConnection, OrderManagementService)` — line 42 | `EventSourcedNetPositionProvider`, `CaffeineIdempotencyCache`, `MarginEnforcementHandler`, `KillSwitchCoordinator`, `PositionRiskHandler` |
| `PipelineComposition` | `composition/src/main/java/com/tradej/composition/PipelineComposition.java` | `create(PositionRiskHandler, CandleAggregationService, GraphStrategySandbox, ExecutionHandler, PortfolioEngine, FeatureStore, DuckDbPipelineGraphStore, ScanEngine, Map<String, ScanProfile>)` — line 48 | `VirtualClock`, `ReactorBridge`, `ReactorBridgeMetrics`, `NodeRegistry`, `PipelineNodeFactory`, `PipelineRuntimeService`, `DagPipelineRuntimeService`, `DagPipelineIngressBridge` |
| `ClockComposition` | `composition/src/main/java/com/tradej/composition/ClockComposition.java` | `live()` — line 28 | `Clock.systemDefaultZone()`, `LiveTradingClock(clock)`, `EventMetadataFactory(tradingClock)` |
| `ClockComposition` | same | `replay()` — line 34 | `Clock.fixed(Instant.EPOCH, ZoneId("Asia/Kolkata"))`, `ReplayTradingClock(Instant.EPOCH)`, `EventMetadataFactory(tradingClock)` |
| `ClockComposition` | same | `live(Clock)` — line 40 | Caller-supplied `Clock` + `LiveTradingClock(clock)` + `EventMetadataFactory` |

Plus supporting records: `BrokerProfile` (`composition/src/main/java/com/tradej/composition/config/BrokerProfile.java`), `RiskProfile` (`composition/src/main/java/com/tradej/composition/config/RiskProfile.java`), `StorageProfile` (`composition/src/main/java/com/tradej/composition/config/StorageProfile.java`), `ConfigLoader` (`composition/src/main/java/com/tradej/composition/config/ConfigLoader.java`), `ScanProperties` (`composition/src/main/java/com/tradej/composition/config/ScanProperties.java`).

### 1.3 Side-by-side: same dependency wired in both roots?

| Dependency | Wired in `composition/`? | Wired in `app/`? | Same object? |
|---|---|---|---|
| `BrokerComposition` (SPI broker) | Yes — `BrokerComposition.create()` | Yes — 4 `@Bean` methods call `BrokerComposition.create()` from `BrokerAdapterConfiguration` and `UpstoxBrokerConfiguration` | **Yes — `app/` already delegates to `composition/` for this.** |
| `DuckDbConnectionPool` + `DuckDbEventStore` + `AsyncDuckDbEventStore` | Yes — `DataComposition` | Yes — `DataConfiguration` (lines 132, 137) | Different objects. `DataComposition` uses a **shared** pool; `DataConfiguration` constructs `DuckDbEventStore` from a path. |
| `ChronicleAuditLogWriter` + `ChronicleDeadLetterQueue` | Yes — `DataComposition` (lines 59-60) | Yes — `DataConfiguration` (lines 121, 127) | Different objects. Same `ChronicleDeadLetterQueue` impl, different paths. |
| `DuckDbPipelineGraphStore` | Yes — `DataComposition` | Yes — `DataConfiguration` line 156 | Different objects. |
| `DuckDbScanStore` | Yes — `DataComposition` | Yes — `ScanConfiguration` line 136 (`@ConditionalOnProperty`) | Different objects, conditional. |
| `EventSourcedNetPositionProvider` | Yes — `ExecutionComposition` | Yes — `TradingConfiguration` line 147 | Different objects. |
| `PositionRiskHandler` | Yes — `ExecutionComposition` | Yes — `TradingConfiguration` line 208 | Different objects. |
| `MarginEnforcementHandler` | Yes — `ExecutionComposition` | Yes — `TradingConfiguration` line 182 | Different objects. |
| `KillSwitchCoordinator` | Yes — `ExecutionComposition` | Yes — `TradingConfiguration` line 171 | Different objects. |
| `OrderManagementService` | Yes — `ExecutionComposition.create()` | Yes — `TradingConfiguration` line 152 | Different objects. |
| `CaffeineIdempotencyCache` | Yes — `ExecutionComposition` | Yes — `BrokerConfiguration` line 41 | Different objects, both `new CaffeineIdempotencyCache()`. |
| `VirtualClock` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` line 47 | Both `new VirtualClock(Mode.LIVE)`. |
| `ReactorBridge` + `ReactorBridgeMetrics` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` lines 57, 62 | Both `new`. |
| `NodeRegistry` | Yes — `PipelineComposition` (empty) | Yes — `PipelineConfiguration` line 67 (with 8 descriptors) | **Drift:** `composition/` registers nothing, `app/` registers 8. |
| `PipelineNodeFactory` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` line 175 | Different objects. |
| `PipelineRuntimeService` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` line 207 | Different objects. |
| `DagPipelineRuntimeService` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` line 235 | Different objects. |
| `DagPipelineIngressBridge` | Yes — `PipelineComposition` | Yes — `PipelineConfiguration` line 244 | Different objects. |
| `Clock` + `TradingClock` + `EventMetadataFactory` | Yes — `ClockComposition` | Yes — `RuntimeAndStartupConfiguration` lines 74, 81, 92 | Different objects. |

**Summary:** `app/` **delegates broker creation to `BrokerComposition`** (4 `@Bean` methods). For everything else, the two roots construct **separate instances** of the same concrete types. The two roots do not share a single bean.

---

## 2. Overlap analysis

### 2.1 Wired in BOTH roots

1. **The `BrokerComposition` class itself is wired in both, and `app/` already delegates the call.** This is the **only** true overlap. The four `@Bean` methods in `app/.../config/` (`BrokerAdapterConfiguration` lines 110/249/367 and `UpstoxBrokerConfiguration` line 67) all invoke `BrokerComposition.create(profile)`. So the `composition/` module is **already** the broker-creation root, and `app/` consumes its result.
2. Concrete types are also wired in both — but as **separate instances** in each root, not as shared beans. This is *redundancy* not *overlap* in the strict sense.

### 2.2 Wired in ONLY `composition/`

| Class | Concrete types created | Production callers outside `composition/` |
|---|---|---|
| `DataComposition` | `ChronicleDeadLetterQueue`, `ChronicleAuditLogWriter`, `DuckDbConnectionPool`, `DuckDbEventStore`, `AsyncDuckDbEventStore`, `DuckDbPipelineGraphStore`, `DuckDbScanStore` | **None** (only `FullComposition.create()` / `createFull()` references it, and those are not called from production code — see [Section 3](#3-use-cases)) |
| `ExecutionComposition` | `EventSourcedNetPositionProvider`, `CaffeineIdempotencyCache`, `MarginEnforcementHandler`, `KillSwitchCoordinator`, `PositionRiskHandler` | **None** (only `FullComposition.createFull()` references it) |
| `PipelineComposition` | `VirtualClock`, `ReactorBridge`, `ReactorBridgeMetrics`, `NodeRegistry`, `PipelineNodeFactory`, `PipelineRuntimeService`, `DagPipelineRuntimeService`, `DagPipelineIngressBridge` | **None** |
| `ClockComposition` | `Clock`, `LiveTradingClock`/`ReplayTradingClock`, `EventMetadataFactory` | **None** (only docs reference it) |
| `FullComposition` (the aggregator) | combines the above | **Only** `cli/src/main/java/com/tradej/cli/standalone/BrokerSession.java` and 3 CLI sessions, all calling `FullComposition.brokerOnly()` |
| `ConfigLoader` | `Properties` from files / env | **None** |
| `StorageProfile`, `RiskProfile` (records) | data containers | Only the dead `create()`/`createFull()` paths |
| `ScanProperties` | data container | Spring `@ConfigurationProperties` (consumed by `app/`) — only metadata, not wiring |

### 2.3 Wired in ONLY `app/`

Everything else. Highlights that have no counterpart in `composition/`:

- `EventBus` (line 121) — `DisruptorEventBus` or `SimpleEventBus` selection
- `RuntimeModeHolder` / `RuntimeBusHolder` (lines 99, 108)
- `PipelineRuntimeBridge` (referenced from `eventBus` bean)
- `BrokerScopedEventBus` (3 named beans, lines 159-174)
- `MarketDataPipeline` / `OrderPipeline` / `MarketDataHealthIndicator` (lines 190-201)
- `RuntimeHealthState`, `StartupDependencies`, `BrokerStartupOrchestrator.runStartup()` (lines 212-283)
- `DhanTokenManager`/`DhanTokenProvider`/`DhanTokenRevalidator` (in `BrokerAdapterConfiguration` lines 93-107)
- `BreezeTokenManager`/`BreezeTokenProvider` (in `BrokerAdapterConfiguration` lines 244-246)
- `UpstoxTokenManager`/`UpstoxOAuthClient` (in `UpstoxBrokerConfiguration` lines 88-97)
- `UpstoxExpiredOptionService`, `UpstoxExpiredInstrumentRestClient` (in `UpstoxBrokerConfiguration` lines 208-215)
- `LivePnlService`, `OptionStrikeResolver`, `MarketDepthOrchestrator` (in `BrokerConfiguration` lines 57-71)
- `OrderBookEngine` + 6 depth analytics beans + `DhanMarketDepthProvider` (in `DataConfiguration` inner classes)
- `OptionChainPollingService` (`DataConfiguration` inner class, lines 363-403)
- `DuckDbAnalyticsEngine`, `HistoricalDataCatalog`, `FederatedHistoricalBarRepository` (in `DataConfiguration`)
- `DuckDbFeatureStore`, `AsyncDuckDbWriter` (in `DataConfiguration` lines 245-256)
- `ReconnectListenerRegistry`, `SubscriptionCoordinator`, `SubscriptionManager`, `SubscriptionRecoveryManager`, `RuntimeSubscriptionManager` (in `ScanConfiguration` lines 90-117, 153-159)
- `InstitutionalScanEngine`, `IndicatorEngine`, `StudioChartService`, `CandleReplaySession` (in `ScanConfiguration` lines 50-85)
- All `EventRegistry`/`StrategyRegistry`/`ScannerRegistry`/`IndicatorRegistry`/`TransformationRegistry` SPI discovery beans (in `RegistryConfiguration`)
- All `ModelRegistry`/`MLStrategyPlugin`/`TickPriceChangeStrategy`/`DepthImbalanceStrategy`/`OptionsContextStrategyPlugin` strategy beans (in `TradingConfiguration` lines 255-291)

---

## 3. Use cases

### 3.1 What is `composition/` actually used for today?

A `ripgrep` for `FullComposition\.|BrokerComposition\.|DataComposition\.|ExecutionComposition\.|PipelineComposition\.|ClockComposition\.` across the entire repo (excluding `composition/` and `.qoder/` doc cache) returns the following **non-test, non-doc** callers:

| Caller (file) | Method called | Purpose |
|---|---|---|
| `cli/src/main/java/com/tradej/cli/standalone/BrokerSession.java:5,14` | Declares `FullComposition fullComposition()` in the interface | The CLI's broker-session contract. |
| `cli/src/main/java/com/tradej/cli/standalone/DhanBrokerSession.java:6,60` | `FullComposition.brokerOnly(brokerProfile)` | Lazy-create Dhan broker connection. |
| `cli/src/main/java/com/tradej/cli/standalone/UpstoxBrokerSession.java:6,60` | `FullComposition.brokerOnly(brokerProfile)` | Lazy-create Upstox broker connection. |
| `cli/src/main/java/com/tradej/cli/standalone/IciciBrokerSession.java:5,41` | `FullComposition.brokerOnly(brokerProfile)` | Lazy-create ICICI broker connection. |
| `app/src/main/java/com/tradej/app/config/BrokerAdapterConfiguration.java:34,127,249,369` | `BrokerComposition.create(profile, ...)` | Spring beans for Dhan/ICICI/Simulation broker compositions. |
| `app/src/main/java/com/tradej/app/config/UpstoxBrokerConfiguration.java:32,84` | `BrokerComposition.create(profile)` | Spring bean for Upstox broker composition. |
| `app/src/main/java/com/tradej/app/config/GatewayBrokerConfiguration.java:14,70,76,100,103` | `ObjectProvider<BrokerComposition>` + `composition.profile().brokerType()` | Reads the active profile from whichever `BrokerComposition` bean is registered. |
| `broker-gateway/src/main/java/com/tradej/brokergateway/DefaultBrokerGateway.java:5` | imports `BrokerComposition` (used to construct gateway node) | Multi-broker gateway wiring. |
| `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerGateway.java:5` | imports `BrokerComposition` (factory method `BrokerGateway.of(source, connection)`) | Static factory. |

**Test-only callers (5 files):**

| Test | Usage |
|---|---|
| `app/src/test/java/com/tradej/app/config/GatewayBrokerConfigurationTest.java:12` | Constructs `BrokerComposition` for Spring tests. |
| `app/src/test/java/com/tradej/app/integration/UpstoxOrderLifecycleIntegrationTest.java:5` | Real-broker Upstox lifecycle test. |
| `app/src/test/java/com/tradej/app/integration/IciciMarginIntegrationTest.java:5` | Real-broker ICICI margin test. |
| `app/src/test/java/com/tradej/app/integration/UpstoxPortfolioIntegrationTest.java:5` | Real-broker Upstox portfolio test. |
| `app/src/test/java/com/tradej/app/integration/IciciOptionChainIntegrationTest.java:5` | Real-broker ICICI option-chain test. |
| `composition/src/test/java/com/tradej/composition/FullCompositionTest.java:44,52,63` | Exercises `FullComposition.brokerOnly()`, `createFull()`, `create()`. |
| `composition/src/test/java/com/tradej/composition/CompositionParityTest.java:51,58,68,80,97` | Parity assertions between `FullComposition` and Spring. |

**Surprising finding:** `DataComposition.create`, `ExecutionComposition.create`, `PipelineComposition.create`, `ClockComposition.live/replay` have **zero callers in production code or in tests outside the composition module itself.** They are reached only transitively through `FullComposition.createFull()`, which in turn has **zero callers in production code** — only `CompositionParityTest` and the Javadoc example on `FullComposition.java:19` reference it.

### 3.2 What is `app/` used for?

`app/src/main/java/com/tradej/app/TradingApplication.java:15-17` is the Spring Boot entry point that boots the entire production runtime (everything except the CLI and broker-gateway module's own runtime). `app/src/main/java/com/tradej/app/config/*` defines all production beans.

### 3.3 Verdict

`composition/` is **partially dead code**:
- `BrokerComposition` is live and load-bearing (used by `app/`, `cli/`, `broker-gateway/`, plus 4 integration tests).
- `FullComposition` is live but **only via the `brokerOnly()` path** (3 CLI sessions).
- `DataComposition`, `ExecutionComposition`, `PipelineComposition`, `ClockComposition` are **dead code in production** — only documentation and one parity test reference them.
- `ConfigLoader` is **dead code** — no callers.

---

## 4. The NPE risk

### 4.1 The exact code

`composition/src/main/java/com/tradej/composition/FullComposition.java:40-49`:

```java
public static FullComposition create(
        BrokerProfile brokerProfile,
        StorageProfile storageProfile,
        RiskProfile riskProfile
) {
    BrokerComposition broker = BrokerComposition.create(brokerProfile);
    DataComposition data = DataComposition.create(storageProfile);

    return new FullComposition(broker, data, null);
}
```

The private constructor at lines 30-38 is:

```java
private FullComposition(
        BrokerComposition broker,
        DataComposition data,
        ExecutionComposition execution
) { ... }
```

The accessor at lines 108-110:

```java
public ExecutionComposition execution() {
    return execution;
}
```

### 4.2 Why this is risky

`create()` is the "legacy" factory and explicitly hands `null` to the `execution` slot. `FullComposition#execution()` returns the field verbatim with no `Objects.requireNonNullElse(...)` guard, no `@Nullable` annotation, and no `IllegalStateException` for the null case.

`FullCompositionTest.create_preservesBackwardCompatibility` (line 62-68) *asserts* the null behaviour:

```java
assertNull(system.execution(), "Legacy create() should not wire execution (backward compat)");
```

The `Javadoc` on the class (line 19) shows `FullComposition.create(brokerProfile, storageProfile, riskProfile)` as a recommended usage example, **without warning that `execution()` will return null on that path**. Any caller who follows the Javadoc example and then accesses `system.execution().positionRiskHandler()` will NPE.

### 4.3 Secondary risk: unused argument in `BrokerComposition.create(BrokerProfile, IdempotencyCachePort)`

`BrokerComposition.java:34-52`:

```java
public static BrokerComposition create(BrokerProfile profile, IdempotencyCachePort idempotencyCache) {
    profile.validate();
    BrokerRegistry registry = new ServiceLoaderBrokerRegistry();
    BrokerSource source = BrokerSource.parse(profile.brokerType().name());
    BrokerProvider provider = registry.provider(source).orElseThrow(...);
    Map<String, Object> config = profile.toGenericConfig();
    IBrokerConnection connection = provider.create(config);
    log.info(...);
    return new BrokerComposition(profile, connection);
}
```

The `idempotencyCache` parameter is **never read** inside the body. Yet `BrokerAdapterConfiguration.java:110` (Dhan) calls it as `BrokerComposition.create(profile, idempotencyCache)`, intending to thread the cache through. This is a silent "argument is ignored" bug — the caller's intent is captured, the effect is dropped.

### 4.4 Plain English

Anyone reading `FullComposition`'s Javadoc example and calling `FullComposition.create(...)` will get a `FullComposition` whose `execution()` accessor returns `null` with no diagnostic. The test suite documents this as "intentional backward compatibility" but the API gives no signal to the caller that they are on the broken-by-design path. `BrokerComposition.create(profile, cache)` accepts a cache it silently throws away.

---

## 5. Recommendation

### 5.1 The choice

**Recommendation: Keep `app/.../config/` as the production root, and slim `composition/` down to the broker-only types that are still load-bearing. Concretely: keep `BrokerComposition`, keep the `BrokerProfile`/`RiskProfile`/`StorageProfile` records, keep the SPI/`ServiceLoaderBrokerRegistry`; delete `FullComposition`, `DataComposition`, `ExecutionComposition`, `PipelineComposition`, `ClockComposition`, and `ConfigLoader`.**

This is **not** "delete the composition module" — `BrokerComposition` is the only piece of `composition/` that is genuinely used outside its own tests, and the `BrokerProfile` record is the canonical config DTO that `app/`, `cli/`, and `broker-gateway/` already consume. But the wrapper compositions, the clock factory, the config loader, and the aggregator are dead code that hides the NPE risk documented in [Section 4](#4-the-npe-risk) and creates a misleading second wiring path.

### 5.2 Justification (with concrete evidence)

1. **`BrokerComposition` is the only `composition/` class with multiple production callers in different modules.** Used by `app/` (4 `@Bean` methods), `cli/` (3 sessions + 1 interface), and `broker-gateway/` (2 files) — see [Section 3.1](#31-what-is-composition-actually-used-for-today). The other 5 composition classes have **no** production callers outside the composition module.

2. **`app/` is structurally much larger and does work that `composition/` cannot replace.** 17 outer `@Configuration` classes, ~70 `@Bean` methods, profile-conditional wiring, the Disruptor/Simple event-bus selection (`RuntimeAndStartupConfiguration.java:121-156`), token managers, replay lifecycle, scan subscription recovery, observability. None of this has a counterpart in `composition/`. Deleting `app/` is non-starter.

3. **`FullComposition.create()` and `FullComposition.createFull()` are the ONLY entry points that pull in `DataComposition`/`ExecutionComposition`/`PipelineComposition`.** Since those entry points have no production callers (see [Section 3.3](#33-verdict)), the downstream composition classes are unreachable from production. Keeping them is paying maintenance cost for zero benefit.

4. **The NPE at `FullComposition.java:48` is the "diagnostic that exists to mask an architectural smell" that the user rules call out.** The class is a god-object aggregator for a wiring path nobody uses. Removing the class removes the NPE surface entirely. The CLI sessions that currently call `FullComposition.brokerOnly(profile)` can be updated to call `BrokerComposition.create(profile)` directly — `BrokerComposition` returns an `IBrokerConnection` via `.brokerConnection()` and a `BrokerLifecycleManager` via `.lifecycleManager()` (`BrokerComposition.java:58, 62`), which is everything the three CLI sessions actually use (see `BrokerSession.java:16-18` — the default `connection()` method only calls `fullComposition().brokerConnection()`).

5. **`BrokerComposition.create(profile, cache)` silently dropping its second argument is a separate "implicit behaviour" violation that should be fixed in the same pass** — either by threading the cache into the provider, or by deleting the overload. After this recommendation, only the no-cache `create(profile)` form would remain, and the integration tests that pass the cache (`BrokerAdapterConfiguration.java:110`) would be updated to drop the arg.

6. **The integration tests already use `BrokerComposition` directly**, not `FullComposition` (see [Section 3.1](#31-what-is-composition-actually-used-for-today) test list). So consolidating the CLI on `BrokerComposition` brings the CLI into parity with the integration-test path.

7. **`ScanProperties` (`composition/src/main/java/com/tradej/composition/config/ScanProperties.java:13`) is consumed by `app/.../config/ScanConfiguration.java:164` and by `RuntimeAndStartupConfiguration.java:225` (as `ObjectProvider<ScanProperties>`).** It must stay in `composition/` (or be moved to a different module — but that's a separate refactor).

### 5.3 Effort estimate

| Step | Effort |
|---|---|
| Update 3 CLI session classes to call `BrokerComposition.create(profile)` instead of `FullComposition.brokerOnly(profile)` (≈ 3 file edits, each 2-3 line changes, see `UpstoxBrokerSession.java:60`, `IciciBrokerSession.java:41`, `DhanBrokerSession.java:60`) | 30 min |
| Update `BrokerSession.java:5,14,16-18` interface to expose `BrokerComposition` (or just `IBrokerConnection` + a `close()` callback) | 30 min |
| Update 2 composition unit tests (`FullCompositionTest.java`, `CompositionParityTest.java`) — `FullCompositionTest` collapses to broker-only tests, `CompositionParityTest` continues unchanged | 1 h |
| Delete `composition/src/main/java/com/tradej/composition/FullComposition.java`, `DataComposition.java`, `ExecutionComposition.java`, `PipelineComposition.java`, `ClockComposition.java`, `config/ConfigLoader.java` | 15 min |
| Drop the unused `idempotencyCache` arg from `BrokerComposition.create(profile, cache)` → keep only `create(profile)`; update 1 caller in `BrokerAdapterConfiguration.java:110` and the matching bean wiring | 30 min |
| Remove `composition/src/main/java/com/tradej/composition/config/RiskProfile.java` and `StorageProfile.java` if no remaining caller (verify) | 15 min |
| Run `:composition:test`, `:cli:test`, `:app:test`, `:broker-gateway:test` to confirm no breakage | 1 h |
| Run runtime verification scripts (`scripts/run-runtime-verification.sh`, `runtime-verification/scripts/phase1-composition-verification.sh`) which grep for `FullComposition` and would need updating | 30 min |
| Total | **~4.5 hours** |

Files touched: ~12 source files + 2 shell scripts + `composition/build.gradle` (only if classes move; otherwise unchanged).

### 5.4 Top 3 risks of executing this recommendation

1. **Hidden caller of `FullComposition` outside the CLI.** The grep did not find any production Java caller other than the 3 CLI sessions and the `BrokerSession` interface, but there are documentation strings, .sh verification scripts, and audit markdown files (see `TRADE_J_ARCHITECTURE_AUDIT.md:337`, `RUNTIME_PROOF_CERTIFICATION.md`, `phase1-composition-verification.sh:30,33`) that reference the old factory. These would rot quickly and mislead the next reader.

2. **Behavioural drift in the CLI** — the 3 CLI sessions currently use `FullComposition.brokerOnly()` which constructs the same `BrokerComposition` as the 1-arg `BrokerComposition.create(profile)` (because `brokerOnly()` delegates to `BrokerComposition.create(profile)` at line 96). So the swap is behaviourally identical **only if** the 1-arg `create(profile)` is used. If a future refactor routes through `create(profile, idempotencyCache)` and the cache starts being honoured, the behaviour changes. The "silent argument drop" risk in [Section 4.3](#43-secondary-risk-unused-argument-in-brokercompositioncreatebrokerprofile-idempotencycacheport) becomes a real behaviour change at that point.

3. **Dead `composition/build.gradle` entries / compile errors.** The `composition/build.gradle` declares `api project(':trading-execution')` and similar — used by the deleted compositions. If we delete only the Java files, the Gradle module continues to compile and pull transitive deps that nothing in the trimmed `composition/` module actually uses. Either delete the unused module deps in the same pass, or accept a bloat. Deleting them is mechanical; not deleting them is silent tech-debt accumulation.

### 5.5 Top 3 things that could break if the recommendation is wrong

1. **The CLI's `FullComposition` return type is part of the public CLI surface.** External scripts or operators may invoke CLI commands and inspect the returned `BrokerSession.fullComposition()` object. Removing `FullComposition` from `BrokerSession` is a **breaking change to the CLI's public contract**. If there are any internal users of the CLI other than the listed 3 sessions, they will need to be updated.

2. **`PipelineComposition` and `DataComposition` are wired by no production code today, but the `architecture-test` module and the certification tests in `docs/architecture/ADR-SINGLE-COMPOSITION-ROOT.md:165-178` reference them as the "single composition root" target.** Removing the dead compositions is consistent with the ADR's intent, but if the architecture-test suite has assertions that grep for `PipelineComposition.create` or `DataComposition.create` (it appears to, per the test class name `BrokerCompositionArchitectureTest` referenced in the ADR), the tests will start passing for the wrong reason.

3. **The broker-gateway module (`broker-gateway/src/main/java/com/tradej/brokergateway/DefaultBrokerGateway.java:5` and `BrokerGateway.java:5`) imports `BrokerComposition`.** If a future refactor decides to **promote** `BrokerComposition` into `broker-api` (per the ADR's "Option 1.5" plan, line 30), then deleting the wrapper compositions and trimming `composition/` to just `BrokerComposition` is the right intermediate state. If instead the team decides to abandon the SPI approach and inline broker creation into Spring `@Configuration` classes (reverting the work that `BrokerAdapterConfiguration` already does), then **`BrokerComposition` is also dead code** and the entire `composition/` module should be deleted in a follow-up. The current recommendation is the minimal safe step toward either outcome — keeping broker creation in one place while eliminating the false overlap.

---

## 6. Migration outline (since the recommendation is to delete parts of `composition/`)

### 6.1 Files to delete

- `composition/src/main/java/com/tradej/composition/FullComposition.java`
- `composition/src/main/java/com/tradej/composition/DataComposition.java`
- `composition/src/main/java/com/tradej/composition/ExecutionComposition.java`
- `composition/src/main/java/com/tradej/composition/PipelineComposition.java`
- `composition/src/main/java/com/tradej/composition/ClockComposition.java`
- `composition/src/main/java/com/tradej/composition/config/ConfigLoader.java`
- `composition/src/main/java/com/tradej/composition/config/RiskProfile.java` (verify no caller first)
- `composition/src/main/java/com/tradej/composition/config/StorageProfile.java` (verify no caller first)

### 6.2 Files to update

| File | Change |
|---|---|
| `cli/src/main/java/com/tradej/cli/standalone/BrokerSession.java:5,14,16-18` | Change return type from `FullComposition` to `BrokerComposition`; simplify `connection()` default to `fullComposition().brokerConnection()`. |
| `cli/src/main/java/com/tradej/cli/standalone/UpstoxBrokerSession.java:6,18,42,60,112` | Replace `FullComposition brokerOnly(...)` with `BrokerComposition.create(profile)`; field type changes to `BrokerComposition`; `close()` calls `composition.brokerConnection().disconnect()`. |
| `cli/src/main/java/com/tradej/cli/standalone/IciciBrokerSession.java:5,17,37,41,78` | Same change as UpstoxBrokerSession. |
| `cli/src/main/java/com/tradej/cli/standalone/DhanBrokerSession.java:6,19,43,60,110` | Same change as UpstoxBrokerSession. |
| `app/src/main/java/com/tradej/app/config/BrokerAdapterConfiguration.java:110,127` | Drop the `idempotencyCache` argument: `BrokerComposition.create(profile)`. |
| `composition/src/main/java/com/tradej/composition/BrokerComposition.java:34-52` | Delete the `(profile, idempotencyCache)` overload; keep only `create(BrokerProfile)`. |
| `composition/src/main/java/com/tradej/composition/config/BrokerProfile.java` | No code change needed (still required). Verify the file is still referenced. |
| `composition/src/test/java/com/tradej/composition/FullCompositionTest.java` | Reduce to broker-only tests using `BrokerComposition` directly. |
| `composition/src/test/java/com/tradej/composition/CompositionParityTest.java` | Likely unchanged (it already calls `FullComposition.brokerOnly(...)`; update to `BrokerComposition.create(profile)`). |
| `composition/build.gradle:8-21` | Remove `api project(':trading-execution')` and other transitive deps that the deleted compositions needed but the remaining `BrokerComposition` does not. Audit the dependency list. |
| `scripts/run-runtime-verification.sh:88-89` | Update the test description (it greps for "FullComposition Creation" — change to "BrokerComposition Creation"). |
| `runtime-verification/scripts/phase1-composition-verification.sh:19,30,32,33` | Replace `FullComposition` references with `BrokerComposition`. |
| `docs/architecture/ADR-SINGLE-COMPOSITION-ROOT.md:30,33,40-50,75-77,84-88,etc.` | Update the ADR's architecture diagram and migration checklist to reflect the new state. |
| `TRADE_J_ARCHITECTURE_AUDIT.md:337,474-478,1110,1467` | Audit doc — mark references as historical. |
| `docs/ARCHITECTURE_ACTUAL.md:66,197,498,586,614,1172,1174,1241,1582` | Update references from "FullComposition" to "BrokerComposition" for broker wiring. |
| `docs/TRADE-J-COMPLETE-DOCUMENTATION.md:72,529,531-534,586,1172,1582` | Same. |

### 6.3 Tests to update

| Test | Update |
|---|---|
| `composition/src/test/java/com/tradej/composition/FullCompositionTest.java` | Reduce to `BrokerComposition`-only assertions. Current 4 tests: `brokerProfileGenericConfigFiltersNullOptionalValues` (line 29) stays as-is, `brokerOnly_executionIsNull` (line 43) becomes a no-op (delete — `BrokerComposition` has no `data`/`execution`), `createFull_wiresExecutionComposition` (line 51) becomes a test that verifies SPI + profile validation, `create_preservesBackwardCompatibility` (line 62) **deletes** (this is the test that codified the NPE — its removal is the point). |
| `composition/src/test/java/com/tradej/composition/CompositionParityTest.java` | Replace `FullComposition.brokerOnly(profile)` calls with `BrokerComposition.create(profile)`. The 5 tests in this file are otherwise unaffected. |
| `app/src/test/java/com/tradej/app/config/GatewayBrokerConfigurationTest.java` | No code change expected (uses `BrokerComposition` directly). |
| `app/src/test/java/com/tradej/app/integration/UpstoxOrderLifecycleIntegrationTest.java` | No code change expected. |
| `app/src/test/java/com/tradej/app/integration/IciciMarginIntegrationTest.java` | No code change expected. |
| `app/src/test/java/com/tradej/app/integration/UpstoxPortfolioIntegrationTest.java` | No code change expected. |
| `app/src/test/java/com/tradej/app/integration/IciciOptionChainIntegrationTest.java` | No code change expected. |

### 6.4 Test-coverage delta

| Currently-covered dependency | Tests | Post-delete coverage |
|---|---|---|
| `BrokerComposition` (broker-only) | 9 tests across `FullCompositionTest` (4) + `CompositionParityTest` (5) + 4 app integration tests + 1 app config test = **14** | Same — all callers and assertions survive. **No delta.** |
| `FullComposition` (aggregator) | 4 tests in `FullCompositionTest` (lines 28-68) — `brokerProfileGenericConfigFiltersNullOptionalValues` (still valid), `brokerOnly_executionIsNull`, `createFull_wiresExecutionComposition`, `create_preservesBackwardCompatibility` | **Lost**: 3 tests are about aggregation behaviour that no longer exists. The fourth (broker-profile generic-config) survives as a `BrokerProfileTest`. **−3 tests, +0** (the `BrokerProfileTest` would be the only new artefact). |
| `DataComposition` | 0 | 0 |
| `ExecutionComposition` | 0 | 0 |
| `PipelineComposition` | 0 | 0 |
| `ClockComposition` | 0 | 0 |
| `ConfigLoader` | 0 | 0 |

**Net:** 3 unit tests become obsolete and are removed. 11 unit tests are preserved (renamed/rewired). 0 production code paths lose coverage because the deleted classes had no production callers.

---

## Appendix A — Counter-factual: why NOT the other two options?

### A.1 "Keep `composition/`, delete `app/.../config/`"

This is infeasible. `app/.../config/` has **70+ beans** that `composition/` does not model at all (event-bus selection, token managers, replay state, scan subscription, observability, security, web, admin, scan conditional beans, ML strategy plugins, depth analytics, options analytics, Parquet writers, etc.). Re-implementing these in pure Java would require duplicating Spring's conditional-bean, profile-conditional, environment-property-binding, and `@PostConstruct`/`@PreDestroy` lifecycle machinery. The CLI would not benefit either — it does not need 90% of those beans.

### A.2 "Keep both, but make `composition/` a thin pass-through that delegates to `app/.../config/` beans"

This is also infeasible. The CLI does not run Spring, so it cannot consume Spring beans. The only way to share between CLI and `app/` is to either (a) keep the framework-agnostic Java factories (the current `composition/` approach, which is what we are partially recommending to keep) or (b) have the CLI bootstrap a Spring `ApplicationContext` (which defeats the purpose of a "lightweight CLI"). Option (a) is exactly the trimmed `BrokerComposition`-only version recommended in [Section 5.1](#51-the-choice).

---

## Appendix B — Is this recommendation consistent with the existing ADR?

`docs/architecture/ADR-SINGLE-COMPOSITION-ROOT.md:30` ("Option 1.5: SPI + Composition Root") says Spring should *consume* `IBrokerConnection` from `BrokerComposition`, not *create* it. **This ADR's intent is already partially implemented** — `BrokerAdapterConfiguration` and `UpstoxBrokerConfiguration` do exactly that. The current recommendation extends the ADR's intent to the **CLI**: today the CLI's `BrokerSession` aggregates 3 wrapper classes that all just call `FullComposition.brokerOnly(profile)` which calls `BrokerComposition.create(profile)` — the wrappers add no value and the NPE risk documented in [Section 4](#4-the-npe-risk) is a symptom of the dead-aggregation-layer pattern. Removing the wrapper makes the CLI a clean consumer of `BrokerComposition`, which is exactly the architecture the ADR prescribes.

The ADR's Phases 4-6 ("Simplify Spring Configuration", "Simplify CLI", "Clean Up") are **already partially done** — `BrokerConfiguration.java` no longer contains `DhanAdapterConfig` or `IciciAdapterConfig` (those moved to `BrokerAdapterConfiguration.java` lines 58-352), and the CLI's 3 sessions all use `BrokerComposition`. The current recommendation is the final step of those phases: delete the dead aggregator (`FullComposition`) and the dead sub-compositions.
