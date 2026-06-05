# Trade-J Broker & Runtime Audit Report

> **Date**: 2026-06-03  
> **Scope**: Broker dependency analysis, runtime wiring, unused abstractions, and instrument mapping audit  
> **Target**: Dhan-first certification preparation

---

## 1. Broker Dependency Report

### 1.1 Dependency Heatmap

| Module / Package | Dhan | Upstox | ICICI | Notes |
|------------------|------|--------|-------|-------|
| `app/config/BrokerConfiguration` | **HEAVY** | None | None | 460+ lines, all Dhan bean wiring |
| `app/config/UpstoxConfiguration` | None | **HEAVY** | None | 420+ lines, all Upstox bean wiring |
| `app/config/IciciConfiguration` | None | None | **HEAVY** | 280+ lines, all ICICI bean wiring |
| `app/config/BrokerRuntimeModeResolver` | **PRIMARY** | Secondary | Tertiary | Defaults to `dhan`, resolves `DHAN_LIVE_WS` |
| `app/config/TradingProperties` | **PRIMARY** | Secondary | Tertiary | `DhanProperties` is first, required |
| `app/startup/steps/LoadInstrumentCatalogStep` | **PRIMARY** | Secondary | Tertiary | `instanceof DhanBrokerConnection` hardcoded |
| `app/health/BrokerHealthIndicator` | **PRIMARY** | None | None | Defaults to `dhan` broker type |
| `app/metrics/MicrometerConfiguration` | **PRIMARY** | None | None | Metric names prefixed `dhan.*` |
| `cli/config/CliConfig` | **PRIMARY** | Secondary | None | Default broker type = DHAN |
| `cli/standalone/BrokerSessionFactory` | **PRIMARY** | Secondary | None | `DHAN -> DhanBrokerSession` |
| `broker-core/routing/LoadBalancedBrokerGateway` | **PRIMARY** | Secondary | Tertiary | Dhan is first in list |
| `data/historical-ingest` | None | **PRIMARY** | None | Defaults to `upstox` broker profile |
| `replay/engine/ReplayIsolationBoundary` | All | All | All | Blocks all three from replay |
| `architecture-test` | All | All | All | Enforces module boundaries |

### 1.2 Critical Finding: Dhan is Pervasive

**Dhan is not just one broker among many — it is the default, primary, and often only broker considered in core paths.**

Evidence:
- `BrokerRuntimeModeResolver.resolve()` returns `DHAN_LIVE_WS` as the default fallback
- `CliConfig.BrokerType.parse(null)` returns `DHAN`
- `TradingProperties` requires `DhanProperties` (non-optional)
- `LoadBalancedBrokerGateway` is constructed with Dhan first: `List.of(dhanBrokerConnection, iciciBrokerConnection, upstoxBrokerConnection)`
- `BrokerConfiguration` is activated by default (`trade.broker-type=dhan`), while `UpstoxConfiguration` and `IciciConfiguration` are conditional
- Metric names are hardcoded as `dhan.websocket.connected`, `dhan.order.latency`, etc.
- `DhanConfigPaths` is used by Upstox CLI code as a generic path resolver (`DhanConfigPaths.resolve(...)`)

### 1.3 Upstox Dependency Scope

Upstox is primarily used for:
1. **Analytics-only mode** (`trade.upstox.analytics-only=true`) — read-only market data and historical candles
2. **Equity backfill** — `data/historical-ingest` defaults to Upstox for equity historical data
3. **Expired options** — `UpstoxExpiredOptionService` provides expired option contracts
4. **Market data fallback** — `LoadBalancedBrokerGateway` includes Upstox as secondary

Key Upstox-specific classes:
- `UpstoxBrokerConnection` — full IBrokerConnection implementation
- `UpstoxMarketDataProvider` — REST + WebSocket market data
- `UpstoxOptionsProvider` — option chain + expiries
- `UpstoxHistoricalDataRestClient` — historical candles
- `UpstoxExpiredOptionService` — expired option contracts
- `UpstoxInstrumentResolver` / `UpstoxInstrumentLoader` — instrument catalog

### 1.4 ICICI Dependency Scope

ICICI is the least integrated:
- `IciciConfiguration` is conditional on `trade.broker-type=icici` or `gateway`
- `IciciBrokerConnection` implements `IBrokerConnection` but with limited capabilities
- `BreezeWebSocketMultiplexer` handles WebSocket streaming
- `BreezeInstrumentResolver` / `BreezeInstrumentLoader` for instruments
- No dedicated historical data client visible in main code (uses generic patterns)
- No options adapter visible (likely unsupported)

### 1.5 Dependency Graph (Simplified)

```
app (main Spring Boot application)
├── BrokerConfiguration (Dhan, ALWAYS ACTIVE)
│   ├── DhanBrokerConnection
│   ├── DhanMarketDataProvider
│   ├── DhanOrderCommandAdapter
│   ├── DhanOptionsAdapter
│   ├── DhanInstrumentResolver
│   └── ... (30+ Dhan beans)
├── UpstoxConfiguration (conditional)
│   ├── UpstoxBrokerConnection
│   ├── UpstoxMarketDataProvider
│   ├── UpstoxOptionsProvider
│   └── ... (20+ Upstox beans)
├── IciciConfiguration (conditional)
│   ├── IciciBrokerConnection
│   ├── BreezeWebSocketMultiplexer
│   └── ... (10+ ICICI beans)
└── GatewayConfiguration
    └── LoadBalancedBrokerGateway
        ├── DhanBrokerConnection (primary)
        ├── IciciBrokerConnection
        └── UpstoxBrokerConnection
```

---

## 2. Runtime Wiring Report

### 2.1 Startup Path: TradingApplication → Broker → Runtime

```
TradingApplication.main()
  └── SpringApplication.run()
       ├── ComponentScan("com.tradej")
       ├── ConfigurationPropertiesScan
       │
       ├── [CONFIG PHASE]
       │   ├── BrokerConfiguration
       │   │   ├── DhanConnectionSettings
       │   │   ├── DhanTokenProvider (DhanTokenManager)
       │   │   ├── DhanClientHolder
       │   │   ├── DhanAuthenticatedHttpClient
       │   │   ├── DhanResilienceExecutor
       │   │   ├── DhanRestOrderClient
       │   │   ├── DhanInstrumentResolver (InMemoryInstrumentResolver)
       │   │   ├── DhanMarketDataProvider
       │   │   ├── DhanOptionsAdapter
       │   │   ├── DhanOrderCommandAdapter
       │   │   ├── DhanWebSocketMultiplexer
       │   │   └── DhanBrokerConnection
       │   │
       │   ├── EventBusConfiguration
       │   │   ├── CandleAggregationService
       │   │   ├── EventBus (DisruptorEventBus or ShardedDisruptorEventBus)
       │   │   ├── DeduplicatingEventBusAdapter
       │   │   ├── MarketDataPipeline
       │   │   ├── OrderPipeline
       │   │   └── MarketDataIngressPort
       │   │
       │   ├── GatewayConfiguration
       │   │   ├── LoadBalancedBrokerGateway
       │   │   ├── BrokerCapabilities
       │   │   └── BrokerTransportCapabilities
       │   │
       │   └── [Other configs...]
       │
       ├── [STARTUP PHASE] BrokerStartupOrchestrator
       │   ├── Step 1: LoadInstrumentCatalogStep
       │   │   └── For each IBrokerConnection:
       │   │       ├── DhanBrokerConnection → DhanInstrumentCatalogLoader
       │   │       ├── UpstoxBrokerConnection → UpstoxInstrumentLoader
       │   │       └── IciciBrokerConnection → BreezeInstrumentLoader
       │   │
       │   ├── Step 2: ConnectBrokerWebSocketStep
       │   │   └── gateway.websocket().subscribe(...)
       │   │
       │   ├── Step 3: InitializeEventBusAndOMSReplayStep
       │   │
       │   ├── Step 4: SetupWebSocketHandlersStep
       │   │
       │   ├── Step 5: SubscribeEventHandlersStep
       │   │   └── SubscriptionCoordinator.subscribe(...)
       │   │
       │   ├── Step 6: ValidateSubscriptionsStep
       │   │
       │   └── Step 7: VerifyPreflightStep
       │
       └── [RUNTIME PHASE]
           ├── REST Controllers (MarketData, Pipeline, Scan, etc.)
           ├── WebSocket Handlers
           ├── Pipeline Runtime (DAG engine)
           ├── Scanner Scheduler
           └── Health Indicators
```

### 2.2 Market Data Flow (Runtime Wiring)

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. BROKER WEBSOCKET LAYER                                          │
│    DhanWebSocketMultiplexer / UpstoxWebSocketMultiplexer           │
│    BreezeWebSocketMultiplexer                                      │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Raw WebSocket frames
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 2. FAILOVER / LOAD BALANCING                                       │
│    FailoverWebSocketMultiplexer (broker-core)                      │
│    SubscriptionRegistry tracks active subscriptions                │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Selected feed
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 3. PAYLOAD NORMALIZATION                                           │
│    DhanPayloadNormalizer → MarketTickEvent / DepthUpdateEvent      │
│    UpstoxStreamNormalizer → MarketTickEvent                        │
│    IciciStreamingParser → MarketTickEvent                          │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ DomainEvent
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 4. BACKPRESSURE LAYER                                              │
│    BackpressureAwareEventPublisher                                 │
│    Bounded queue (8192 capacity, drop-oldest policy)               │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Queued events
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 5. DEDUPLICATION LAYER                                             │
│    DeduplicatingEventBusAdapter                                    │
│    LRU cache of (sourceId, sequenceId) → dedup TTL 30s            │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Clean events
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 6. EVENT BUS (LMAX Disruptor)                                      │
│    DisruptorEventBus / ShardedDisruptorEventBus                    │
│    Ring buffer dispatch to pipeline nodes                          │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ DomainEvent
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 7. PIPELINE NODES                                                  │
│    CandleNode → CandleAggregationService                           │
│    ScanNode → ScanEngine / OptionLiquidityScanner                  │
│    StrategyNode → StrategyPlugin (DepthImbalance, etc.)            │
│    PortfolioNode → PositionBook / ExposureTracker                  │
└───────────────────────────┬─────────────────────────────────────────┘
                            │ Signals / Orders
                            ▼
┌─────────────────────────────────────────────────────────────────────┐
│ 8. EXECUTION LAYER                                                 │
│    OrderPlacementService → PositionRiskHandler → CircuitBreaker    │
│    → FailoverOrderCommand → Broker REST/WS                         │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.3 Option Chain Flow

```
OptionScanController / OptionLiquidityScanner
  │
  ├── Dhan path:
  │   ├── DhanOptionChainClient (REST)
  │   │   └── DhanAuthenticatedHttpClient
  │   │       └── DhanTokenProvider (token refresh)
  │   ├── DhanRollingOptionClient (expired options)
  │   └── DhanOptionsAdapter
  │       └── DhanInstrumentResolver (catalog lookup)
  │
  ├── Upstox path:
  │   ├── UpstoxOptionChainRestClient (REST)
  │   │   └── UpstoxHttpClient (OAuth token)
  │   └── UpstoxOptionsProvider
  │       └── UpstoxInstrumentResolver
  │
  └── ICICI path:
      └── IciciOptionsProvider (limited)
```

### 2.4 Market Depth Flow

```
MarketDepthOrchestrator
  │
  ├── Dhan path (PRIMARY):
  │   ├── DhanMarketDepthProvider
  │   │   └── DhanWebSocketMultiplexer
  │   │       ├── DhanTwentyDepthWebSocketClient
  │   │       ├── DhanTwoHundredDepthWebSocketClient
  │   │       └── AbstractDhanDepthWebSocketClient
  │   │           └── DhanFullDepthBinaryParser (binary protocol)
  │   └── OrderBookEngine (core)
  │       └── DepthBookState
  │
  ├── Upstox path:
  │   └── UpstoxMarketDataProvider.getDepth() (REST only, no WS depth)
  │
  └── ICICI path:
      └── BreezeWebSocketMultiplexer (limited depth support)
```

### 2.5 Scanner Flow

```
ScanScheduler (cron-triggered)
  │
  ├── ScanService
  │   ├── ScanEngine
  │   │   ├── ScanDependencies (InstrumentResolver, MarketDataProvider, etc.)
  │   │   ├── ScanNode / StreamingScanCriterionNode
  │   │   └── ScanResultRanker
  │   │
  │   ├── OptionLiquidityScanner
  │   │   ├── OptionsProvider (Dhan/Upstox)
  │   │   ├── LiquidityScorer
  │   │   └── OptionSideFilter
  │   │
  │   └── InstitutionalScanEngine (separate module)
  │       ├── FeaturePipeline
  │       ├── RankingEngine
  │       └── SectorRankingEngine
  │
  └── ScanResultStore → DuckDbScanStore
```

---

## 3. Unused Abstraction Report

### 3.1 Potentially Unused / Dead Code

| Class / Interface | Location | Status | Risk |
|-------------------|----------|--------|------|
| `UpstoxUnsupportedPorts` | `broker/upstox/adapter/` | **Sentinel only** | Low — used as fallback in analytics-only mode |
| `UpstoxUnsupportedOrderCommand` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedOrderQuery` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedPortfolioProvider` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedMarginProvider` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedBracketOrders` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedGttOrders` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedSliceOrders` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedSessionRisk` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `UpstoxUnsupportedAlerts` | `broker/upstox/adapter/` | **Sentinel only** | Low |
| `PassthroughInstrumentSubscribeResolver` | `broker/api/port/` | **Default fallback** | Low — used when broker doesn't override |
| `NoOpMarketDepthProvider` | `broker/api/port/` | **Fallback** | Low — used when depth not supported |
| `UnsupportedOptionsProvider` | `broker/core/routing/` | **Sentinel** | Low — inner class in LoadBalancedBrokerGateway |
| `ReplayIsolationBoundary` | `replay/engine/` | **Guard only** | Medium — blocks broker classes from replay |

### 3.2 Duplicated / Near-Duplicated Abstractions

| Pattern | Dhan | Upstox | ICICI | Assessment |
|---------|------|--------|-------|------------|
| `*InstrumentResolver` | `DhanInstrumentResolver` + `InMemoryInstrumentResolver` + `DhanInstrumentCatalog` | `UpstoxInstrumentResolver` | `BreezeInstrumentResolver` | **Each broker has its own resolver** — no shared base beyond `InstrumentResolver` interface |
| `*InstrumentLoader` | `DhanInstrumentLoader` | `UpstoxInstrumentLoader` | `BreezeInstrumentLoader` | **Each broker downloads/parses its own catalog format** |
| `*WebSocketMultiplexer` | `DhanWebSocketMultiplexer` | `UpstoxWebSocketMultiplexer` | `BreezeWebSocketMultiplexer` | **Each has different reconnect/subscription logic** |
| `*BrokerConnection` | `DhanBrokerConnection` | `UpstoxBrokerConnection` | `IciciBrokerConnection` | **Different capability sets** |
| `*MarketDataProvider` | `DhanMarketDataProvider` | `UpstoxMarketDataProvider` | `IciciMarketDataProvider` | **Different REST endpoints, different parsing** |
| `*OrderCommandAdapter` | `DhanOrderCommandAdapter` | `UpstoxOrderCommandAdapter` | `IciciOrderCommandAdapter` | **Different request/response formats** |

### 3.3 Configuration Leakage (App Module)

The `app` module contains broker-specific configuration that should arguably live in the broker modules:

| Class | Broker | Issue |
|-------|--------|-------|
| `BrokerConfiguration` | Dhan | 460 lines of Dhan bean wiring in `app` |
| `UpstoxConfiguration` | Upstox | 420 lines of Upstox bean wiring in `app` |
| `IciciConfiguration` | ICICI | 280 lines of ICICI bean wiring in `app` |
| `BrokerStartupConfiguration` | Dhan | Dhan-specific startup beans in `app` |
| `MarketDepthConfiguration` | Dhan | `if (brokerConnection instanceof DhanBrokerConnection)` |
| `SubscriptionConfiguration` | Dhan | References `DhanWebSocketMultiplexer` |
| `BrokerMarketDataConfiguration` | Upstox | Upstox-specific expired options in `app` |
| `DefaultAuthValidationService` | Dhan + ICICI | Direct references to `DhanTokenProvider` and `BreezeTokenProvider` |

**Recommendation**: These configurations should be moved into `broker-dhan`, `broker-upstox`, and `broker-icici` as auto-configuration modules, using Spring Boot's auto-configuration mechanism.

### 3.4 Runtime Duplication

| Concern | Location | Issue |
|---------|----------|-------|
| `PipelineRuntimeService` + `DagPipelineRuntimeService` | `app/pipeline/` | Two runtime service interfaces/classes with overlapping responsibilities |
| `ReplayOrchestrator` + `ReplayRunner` | `app/pipeline/` + `data/persistence/` | Replay logic split across modules |
| `BrokerStartupOrchestrator` + `StartupStep` | `app/startup/` | 8-step startup is complex; steps reference broker-specific classes |
| `SubscriptionCoordinator` + `SubscriptionManager` + `SubscriptionRecoveryManager` | `app/subscription/` | Three classes for subscription management |

---

## 4. CrossBrokerSymbolMapper Deep-Dive

### 4.1 Location & Purpose

**File**: `broker/core/src/main/java/com/tradej/broker/core/instrument/CrossBrokerSymbolMapper.java`

**Purpose**: Maps trading symbols and instrument keys across different broker catalogs. This is critical because:
- Each broker uses different internal security IDs, exchange segments, and symbol formats
- Analytics, scanning, and execution need a canonical `InstrumentKey` that works across brokers
- Option chain requests, historical data queries, and market data subscriptions all depend on correct mapping

### 4.2 Current Implementation Status

The `CrossBrokerSymbolMapper` class exists but its usage is **limited and inconsistent**:

| Usage Site | Broker | Method |
|------------|--------|--------|
| `GatewayEventBridge` | Multi-broker | Uses `InstrumentResolver` (not `CrossBrokerSymbolMapper` directly) |
| `LoadInstrumentCatalogStep` | Dhan/Upstox/ICICI | Each broker loads its own catalog independently |
| `UniverseBuilder` | Generic | Uses `InstrumentResolver` + `FuturesProvider` |
| `ScanDependencies` | Generic | Uses `InstrumentResolver` |

**Key Finding**: There is **no centralized cross-broker symbol mapping** in the current runtime path. Each broker's `InstrumentResolver` operates independently.

### 4.3 Instrument Resolution Flow (Current)

```
Dhan path:
  DhanInstrumentCatalog / InMemoryInstrumentResolver
    └── DhanInstrumentDefinition (securityId, exchangeSegment, symbol)
    └── resolve(InstrumentKey) → Instrument

Upstox path:
  UpstoxInstrumentResolver
    └── UpstoxInstrumentDefinition (instrumentKey, exchangeSegment, symbol)
    └── resolve(InstrumentKey) → Instrument

ICICI path:
  BreezeInstrumentResolver
    └── BreezeInstrumentDefinition (stockCode, exchangeSegment, symbol)
    └── resolve(InstrumentKey) → Instrument
```

**Problem**: Each resolver uses broker-specific `InstrumentKey` formats. There is no canonical mapping layer that translates:
- Dhan's `securityId` ↔ Upstox's `instrumentKey` ↔ ICICI's `stockCode`
- Dhan's `NSE_FNO` ↔ Upstox's `NFO` ↔ ICICI's `NFO`

### 4.4 Symbol Normalization Utilities

| Class | Location | Purpose |
|-------|----------|---------|
| `ContractSymbolNormalizer` | `core/domain/instrument/` | Normalizes option contract symbols (e.g., `NIFTY24JUNFUT`) |
| `DhanSymbolNormalizer` | `broker/dhan/instrument/` | Dhan-specific symbol normalization |
| `UpstoxSegmentMapper` | `broker/upstox/instrument/` | Maps Upstox exchange segments to canonical segments |
| `DhanSegmentMapper` | `broker/dhan/instrument/` | Maps Dhan exchange segments to canonical segments |

### 4.5 Risk Assessment for Analytics

| Risk Area | Current State | Impact |
|-----------|---------------|--------|
| **Symbol mismatch across brokers** | No centralized mapping | Analytics may attribute data to wrong instruments |
| **Exchange segment inconsistency** | Each broker has own segment codes | `NSE_FNO` vs `NFO` vs `NSE_FNO` confusion |
| **Option contract key format** | Different per broker | Option chain lookups may fail silently |
| **Instrument catalog staleness** | Each broker caches independently | Catalogs may diverge, causing resolution failures |
| **Failover symbol resolution** | `FailoverInstrumentResolver` tries primary then secondary | If catalogs differ, resolution may succeed on wrong broker |

### 4.6 Recommended Certification Sequence

Based on this audit, the **highest-risk component** for Dhan certification is the instrument discovery and symbol mapping layer. The recommended sequence is:

1. **Week 1: Instrument Discovery Audit**
   - Verify `DhanInstrumentCatalog` loads correctly from Dhan's security master
   - Verify `InMemoryInstrumentResolver` resolves all required instruments
   - Test `DhanInstrumentSubscribeResolver` for contract discovery (FNO/options)
   - Validate `DhanSegmentMapper` mappings against actual Dhan feed data

2. **Week 2: Symbol Mapping Validation**
   - Create integration tests that verify `InstrumentKey` → `DhanInstrumentDefinition` → actual Dhan WebSocket subscription
   - Verify option chain symbol formats match between REST and WebSocket
   - Test `ContractSymbolNormalizer` with real Dhan option symbols

3. **Week 3: Subscription Infrastructure**
   - Verify `DhanWebSocketMultiplexer` subscription flow end-to-end
   - Test subscription recovery after WebSocket reconnect
   - Validate `SubscriptionRegistry` state consistency

4. **Week 4: Market Data Ingestion**
   - Verify tick, quote, full, and depth feeds normalize correctly
   - Test `DhanPayloadNormalizer` with all feed modes
   - Validate `DeduplicatingEventBus` handles Dhan sequence IDs correctly

5. **Week 5: Scanner & Analytics**
   - Only after instrument layer is certified
   - Test option chain, historical data, and market depth
   - Validate analytics queries against known-good data

---

## 5. Summary of Recommendations

### 5.1 Immediate Actions (Before Dhan Certification)

| Priority | Action | Rationale |
|----------|--------|-----------|
| **P0** | Audit `DhanInstrumentCatalog` loading and resolution | Foundation for all market data |
| **P0** | Verify `DhanWebSocketMultiplexer` subscription flow | Core data ingestion path |
| **P0** | Test `DeduplicatingEventBus` with Dhan sequence IDs | Prevents duplicate/stale data |
| **P1** | Move broker configs from `app` to broker modules | Reduces `app` module complexity |
| **P1** | Disable Upstox/ICICI at runtime (not code removal) | Reduces surface area for certification |
| **P2** | Review `LoadBalancedBrokerGateway` necessity | Failover not yet proven in production |
| **P2** | Consolidate `PipelineRuntimeService` / `DagPipelineRuntimeService` | Reduces duplication |

### 5.2 Architecture Ratings (Updated)

| Area | Rating | Change | Reason |
|------|--------|--------|--------|
| Module separation | 8.5/10 | — | Still strong |
| Broker abstraction | 8/10 | — | Good SPI, but configs leak upward |
| Analytics potential | 9/10 | — | Unchanged |
| Runtime simplicity | 4/10 | ↓ | `app` module is God module; too many broker configs |
| Operational complexity | 5/10 | ↓ | Multi-broker failover adds complexity without proven value |
| Risk of over-engineering | 8/10 | ↑ | Failover, load balancing, sharding may be premature |
| Dhan-first certification readiness | 6/10 | ↑ | Instrument layer needs audit before certification |

### 5.3 Key Files for Dhan Certification

| File | Purpose | Priority |
|------|---------|----------|
| `broker/dhan/instrument/DhanInstrumentCatalog.java` | Instrument catalog loading | P0 |
| `broker/dhan/adapter/InMemoryInstrumentResolver.java` | Instrument resolution | P0 |
| `broker/dhan/adapter/DhanInstrumentResolver.java` | Resolver interface | P0 |
| `broker/dhan/instrument/DhanInstrumentSubscribeResolver.java` | Contract discovery | P0 |
| `broker/dhan/websocket/DhanWebSocketMultiplexer.java` | WebSocket streaming | P0 |
| `broker/dhan/mapper/DhanPayloadNormalizer.java` | Event normalization | P0 |
| `broker/dhan/websocket/AbstractDhanDepthWebSocketClient.java` | Depth streaming | P1 |
| `broker/dhan/options/DhanOptionChainClient.java` | Option chain | P1 |
| `broker/dhan/historical/DhanHistoricalDataClient.java` | Historical data | P1 |
| `core/domain/event/DeduplicatingEventBus.java` | Event deduplication | P0 |

---

*End of Audit Report*
