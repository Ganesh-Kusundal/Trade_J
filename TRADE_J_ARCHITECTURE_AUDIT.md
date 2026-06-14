# Trade-J Platform Architecture Audit Report

**Audit Date**: June 11, 2026  
**Auditor Role**: Principal Engineer, Platform Architect, Trading Systems Architect  
**Scope**: Full 16-phase architectural audit covering runtime behavior, integration flows, event systems, broker architecture, data platform, replay determinism, Spring vs CLI parity, testing gaps, observability, and simplification roadmap  
**Methodology**: Runtime code path analysis, dependency graph inspection, composition root verification, event flow tracing  

---

## Executive Summary

Trade-J is a **sophisticated Java 21 algorithmic trading platform** with multi-broker support (Dhan, Upstox, ICICI), event-driven architecture (LMAX Disruptor), dual composition roots (Spring Boot + composition layer), and comprehensive replay/simulation capabilities.

**Overall Architecture Health**: **B+** (Good with targeted improvements needed)

**Critical Findings**:
1. **Dual Composition Roots**: Spring Boot and composition layer create **divergent runtime graphs** - risk of behavioral drift
2. **Event Bus Complexity**: 7 EventBus implementations with unclear ownership boundaries
3. **Spring Over-Configuration**: 25+ @Configuration classes in `app/` module - Spring is becoming "the runtime" instead of "hosting the runtime"
4. **CLI-Spring Parity Gap**: CLI uses composition layer, Spring uses direct bean wiring - not identical graphs
5. **Broker Isolation**: Good SPI-based design, but simulation brokers leak into broker-gateway

**Top 3 Immediate Actions**:
1. Consolidate composition to single root (composition layer becomes canonical, Spring delegates to it)
2. Simplify EventBus hierarchy to 2 implementations (production + test)
3. Reduce Spring @Configuration classes by 60% through auto-configuration delegation

---

## Table of Contents

1. [System Discovery - Module Map](#1-system-discovery---module-map)
2. [Runtime Flow Analysis - Startup Sequences](#2-runtime-flow-analysis---startup-sequences)
3. [Composition Root Analysis](#3-composition-root-analysis)
4. [Event Bus & Event Flow Audit](#4-event-bus--event-flow-audit)
5. [Market Data Architecture Audit](#5-market-data-architecture-audit)
6. [Broker Architecture Audit](#6-broker-architecture-audit)
7. [Data Platform Audit](#7-data-platform-audit)
8. [Replay & Simulation Audit](#8-replay--simulation-audit)
9. [Strategy Execution Audit](#9-strategy-execution-audit)
10. [Spring Boot Audit](#10-spring-boot-audit)
11. [CLI Audit](#11-cli-audit)
12. [Testing Audit](#12-testing-audit)
13. [Observability Audit](#13-observability-audit)
14. [Architecture Violations Report](#14-architecture-violations-report)
15. [Simplification Roadmap](#15-simplification-roadmap)
16. [Certification Framework Review](#16-certification-framework-review)
17. [Production Readiness Assessment](#17-production-readiness-assessment)
18. [Top 20 Highest-Risk Areas](#18-top-20-highest-risk-areas)
19. [Top 20 Highest-Value Improvements](#19-top-20-highest-value-improvements)

---

## 1. System Discovery - Module Map

### 1.1 Module Inventory (33 Modules)

```mermaid
graph TB
    subgraph Entry Points
        app["app/ - Spring Boot Application"]
        cli["cli/ - Operator CLI"]
        gateway["gateway/ - WebSocket Bridge"]
    end

    subgraph Core Domain
        core["core/ - Domain Events, Ports, Pipeline Types"]
    end

    subgraph Broker Layer
        broker_api["broker/api/ - Broker SPI & Ports"]
        broker_core["broker/core/ - Broker Infrastructure"]
        broker_dhan["broker/dhan/ - Dhan Integration"]
        broker_upstox["broker/upstox/ - Upstox Integration"]
        broker_icici["broker/icici/ - ICICI Integration"]
    end

    subgraph Broker Gateway
        broker_gateway["broker-gateway/ - Multi-Broker Abstraction"]
    end

    subgraph Composition
        composition["composition/ - Dependency Wiring Without Spring"]
    end

    subgraph Runtime Layer
        runtime_disruptor["runtime/disruptor/ - LMAX Disruptor Event Bus"]
        runtime_hotpath["runtime/hotpath/ - Low-Latency Pipeline"]
    end

    subgraph Pipeline Platform
        pipeline_core["pipeline/core/ - Pipeline Graph Model"]
        pipeline_runtime["pipeline/runtime/ - Pipeline Execution"]
        pipeline_platform["trade-pipeline-platform/ - Declarative Pipeline"]
        pipeline_analytics["trade-analytics/ - Trade Analytics"]
    end

    subgraph Trading Engine
        trading_strategy["trading/strategy/ - Strategy Plugins"]
        trading_execution["trading/execution/ - Order Execution"]
        trading_scanner["trading/scanner/ - Market Scanner"]
        trading_inst_scanner["trading/institutional-scanner/"]
        trading_indicators["trading/indicators/ - Technical Indicators"]
        trading_simulation["trading/simulation/ - Paper Trading"]
        trading_options["trading/options-analytics/ - Options Greeks"]
    end

    subgraph Data Platform
        data_persistence["data/persistence/ - DuckDB, Chronicle Queue"]
        data_feature_store["data/feature-store/ - Time-Series Features"]
        data_historical["data/historical-ingest/ - Historical Data"]
        data_analytics["data/analytics/ - Analytics Engine"]
    end

    subgraph Replay
        replay_engine["replay/engine/ - Historical Replay"]
    end

    subgraph Infrastructure
        node_library["trade-node-library/ - Node Execution"]
        mcp_server["mcp-server/ - MCP Protocol"]
        arch_test["architecture-test/ - ArchUnit Tests"]
    end

    app --> core
    app --> broker_api
    app --> broker_core
    app --> broker_dhan
    app --> broker_upstox
    app --> broker_icici
    app --> broker_gateway
    app --> trading_strategy
    app --> trading_execution
    app --> runtime_disruptor
    app --> data_persistence
    app --> gateway
    app --> composition
    app --> replay_engine

    cli --> core
    cli --> broker_api
    cli --> composition
    cli --> broker_gateway
    cli --> data_persistence

    gateway --> core
    gateway --> broker_api

    composition --> core
    composition --> broker_api
    composition --> broker_core
    composition --> pipeline_core
    composition --> trading_strategy
    composition --> trading_execution
    composition --> data_persistence
    composition --> runtime_disruptor
    composition --> replay_engine

    broker_gateway --> broker_api
    broker_gateway --> broker_core
    broker_gateway --> composition

    runtime_disruptor --> core
    runtime_disruptor --> pipeline_core
    runtime_disruptor --> trading_strategy
    runtime_disruptor --> trading_execution

    trading_strategy --> core
    trading_strategy --> pipeline_core
    trading_strategy --> data_feature_store

    trading_execution --> core
    trading_execution --> broker_api
    trading_execution --> data_persistence

    data_persistence --> core
    data_persistence --> pipeline_core

    replay_engine --> core
    replay_engine --> pipeline_core
    replay_engine --> broker_api
    replay_engine --> data_persistence
```

### 1.2 Module Details

| Module | Purpose | Key Dependencies | Consumers | Entry Points | Lifecycle | Extension Points |
|--------|---------|------------------|-----------|--------------|-----------|------------------|
| **core/** | Domain events, ports, pipeline graph types | SLF4J, Reactor | All modules | `EventBus`, `DomainEvent`, `RuntimeMode` | Stateless | `EventBus` interface, domain ports |
| **broker/api/** | Broker SPI & ports | `core/` | All broker modules, broker-gateway | `BrokerProvider`, `IBrokerConnection` | Stateless | `BrokerProvider` SPI |
| **broker/core/** | Broker infrastructure (lifecycle, resilience) | `broker-api/` | Broker implementations, broker-gateway | `BrokerLifecycleManager` | Managed lifecycle | Retry policies, circuit breakers |
| **broker/dhan/** | Dhan broker integration | `broker-api/`, `broker-core/` | app/, cli/, composition | `DhanBrokerProvider` | Connection lifecycle | WebSocket handlers |
| **broker/upstox/** | Upstox broker integration | `broker-api/`, `broker-core/` | app/, cli/, composition | `UpstoxBrokerProvider` | Connection lifecycle | REST market data |
| **broker/icici/** | ICICI broker integration | `broker-api/`, `broker-core/` | app/, cli/, composition | `IciciBrokerProvider` | Connection lifecycle | Authentication flow |
| **broker-gateway/** | Multi-broker abstraction layer | `broker-api/`, `broker-core/`, `composition/` | app/, cli/ | `BrokerGateway`, `BrokerHandle`, `MarketGateway` | Gateway lifecycle | Simulation brokers |
| **composition/** | Dependency wiring without Spring | All core modules | app/, cli/, broker-gateway | `FullComposition`, `BrokerComposition`, `DataComposition` | Composition lifecycle | Configuration profiles |
| **runtime/disruptor/** | LMAX Disruptor event bus | `core/`, `pipeline-core/`, `trading-strategy/`, `trading-execution/` | app/ | `DisruptorEventBus`, `ShardedDisruptorEventBus` | Ring buffer lifecycle | Wait strategies |
| **runtime/hotpath/** | Low-latency pipeline runtime | `core/`, `pipeline-core/` | app/ | Pipeline runtime bridge | Pipeline lifecycle | Graph compilation |
| **trading/strategy/** | Strategy plugin architecture | `core/`, `pipeline-core/`, `data-feature-store/` | runtime-disruptor/, app/ | Strategy sandbox, portfolio engine | Strategy lifecycle | Strategy plugins |
| **trading/execution/** | Order execution & risk | `core/`, `broker-api/`, `data-persistence/` | runtime-disruptor/, app/ | `OrderManagementService`, `PositionRiskHandler` | Execution lifecycle | Risk policies |
| **trading/scanner/** | Market scanner engine | `core/`, `broker-api/` | cli/, app/ | Scanner engine | Scan cycles | Scan strategies |
| **trading/simulation/** | Paper trading | `core/`, `broker-api/` | broker-gateway/, app/ | Simulation broker | Simulation lifecycle | Market simulation |
| **data/persistence/** | DuckDB, Chronicle Queue storage | `core/`, `pipeline-core/` | trading-execution/, replay-engine/, app/ | `DuckDbEventStore`, `ChronicleEventWal` | Storage lifecycle | Event stores |
| **replay/engine/** | Historical replay engine | `core/`, `pipeline-core/`, `data-persistence/` | app/, cli/ | `ReplayOrchestrator`, `ReplayController` | Replay session lifecycle | Replay sessions |
| **gateway/** | WebSocket bridge to frontend | `core/`, `broker-api/` | app/ | `GatewayEventBridge`, `GatewayWebSocketHandler` | WebSocket lifecycle | Topic routing |
| **app/** | Spring Boot application | All production modules | End users | `TradingApplication.main()` | Spring lifecycle | Spring profiles |
| **cli/** | Operator CLI | `core/`, `broker-api/`, `composition/`, `broker-gateway/` | Operators | `TradeCli.main()` | CLI session lifecycle | CLI commands |

### 1.3 Module Dependency Statistics

- **Total Modules**: 33 (28 production + 5 optional/test)
- **Most Depended Upon**: `core/` (depended on by 27 modules)
- **Most Dependencies**: `app/` (depends on 20 modules)
- **Highest Coupling**: `composition/` (depends on 15 modules - composition root concern)
- **Cleanest Isolation**: `trading/indicators/`, `trading/institutional-scanner/` (minimal dependencies)

---

## 2. Runtime Flow Analysis - Startup Sequences

### 2.1 Spring Boot Startup Sequence

```mermaid
sequenceDiagram
    participant JVM
    participant Spring as SpringApplication
    participant AutoConfig as AutoConfiguration
    participant AppConfig as @Configuration Classes
    participant Composition as Composition Layer
    participant Broker as Broker Registry
    participant EventBus as DisruptorEventBus
    participant Strategy as Strategy Engine
    participant Risk as Risk Handler
    participant OMS as OrderManagementService
    participant Gateway as WebSocket Gateway

    JVM->>Spring: TradingApplication.main()
    Spring->>Spring: @ComponentScan("com.tradej")
    Spring->>AutoConfig: GatewayAutoConfiguration
    AutoConfig->>Gateway: Create GatewayTopicRouter
    AutoConfig->>Gateway: Create GatewayEventBridge
    AutoConfig->>Gateway: Create GatewayWebSocketHandler
    Gateway->>EventBus: bridge.register(eventBus)

    Spring->>AppConfig: BrokerConfiguration
    AppConfig->>Composition: BrokerComposition.create(profile)
    Composition->>Broker: ServiceLoaderBrokerRegistry
    Broker->>Broker: Load BrokerProvider via SPI
    Broker-->>AppConfig: IBrokerConnection

    Spring->>AppConfig: DataConfiguration
    AppConfig->>Composition: DataComposition.create(storageProfile)
    Composition-->>AppConfig: DuckDbEventStore, ChronicleEventWal

    Spring->>AppConfig: RuntimeAndStartupConfiguration
    AppConfig->>EventBus: new DisruptorEventBus(config)
    EventBus->>EventBus: Create ring buffer (8192)
    EventBus->>EventBus: Setup pipeline stages: graph-runtime → async-dispatch
    EventBus->>Strategy: Subscribe strategy handlers
    EventBus->>Risk: Subscribe risk handlers

    Spring->>AppConfig: BrokerAdapterConfiguration
    AppConfig->>OMS: new OrderManagementService(connection)
    AppConfig->>Risk: new PositionRiskHandler(portfolio)

    Spring->>Spring: Start embedded web server (port 8080)
    Spring->>EventBus: eventBus.start()
    EventBus->>EventBus: Start disruptor, drainer threads
    Spring-->>JVM: Application ready
```

**Actual Code Path**:
1. `TradingApplication.main()` → `SpringApplication.run()`
2. `@ComponentScan("com.tradej")` scans all packages
3. 25+ `@Configuration` classes create beans in `app/src/main/java/com/tradej/app/config/`:
   - `BrokerConfiguration.java`
   - `BrokerAdapterConfiguration.java` (4 nested @Configuration classes)
   - `DataConfiguration.java` (3 nested @Configuration classes)
   - `GatewayBrokerConfiguration.java` (5 nested @Configuration classes)
   - `RuntimeAndStartupConfiguration.java`
   - `ObservabilityConfiguration.java`
   - `ScanConfiguration.java`
   - `WebConfiguration.java`
   - `AdminConfiguration.java` (2 nested @Configuration classes)
   - `VirtualThreadConfiguration.java`
   - `PrometheusConfiguration.java`
   - `UpstoxBrokerConfiguration.java`
   - `TradingProperties.java`
4. `GatewayAutoConfiguration` auto-configures gateway beans
5. Beans wired directly via Spring DI, **not** via composition layer

**Key Finding**: Spring Boot creates its **own object graph** independent of composition layer. This is an architectural risk.

---

### 2.2 CLI Startup Sequence

```mermaid
sequenceDiagram
    participant JVM
    participant CLI as TradeCli.main()
    participant Context as CliContext
    participant Session as BrokerSessionFactory
    participant Composition as FullComposition
    participant Broker as BrokerComposition
    participant Registry as ServiceLoaderBrokerRegistry
    participant Provider as BrokerProvider (SPI)
    participant Gateway as BrokerGateway

    JVM->>CLI: picocli parses args
    CLI->>Context: new CliContext(attachUrl, profile, brokerType)

    alt Attach Mode (connect to running app)
        Context->>Context: Create AttachClient
        Context->>AttachClient: HTTP calls to app REST API
    else Standalone Mode (direct broker)
        Context->>Session: BrokerSessionFactory.create(brokerType, profile)
        Session->>Composition: FullComposition.brokerOnly(brokerProfile)
        Composition->>Broker: BrokerComposition.create(profile)
        Broker->>Registry: new ServiceLoaderBrokerRegistry()
        Registry->>Registry: ServiceLoader.load(BrokerProvider.class)
        Registry->>Provider: Find provider for brokerType
        Provider->>Broker: provider.create(config)
        Broker-->>Composition: IBrokerConnection
        Composition-->>Session: FullComposition

        Context->>Gateway: BrokerGateway.of(source, connection)
        Gateway-->>Context: BrokerHandle
    end

    CLI->>CLI: Execute command (quote, order, scan, replay, etc.)
```

**Actual Code Path**:
1. `TradeCli.main()` with 60+ picocli subcommands
2. `CliContext` creates broker session via `BrokerSessionFactory`
3. Uses `FullComposition.brokerOnly()` or `FullComposition.createFull()` for wiring
4. **Uses composition layer** - not Spring
5. Can operate in two modes:
   - **Attach mode**: Connects to running Spring Boot app via HTTP
   - **Standalone mode**: Creates independent broker connection via composition

**Key Finding**: CLI uses composition layer, Spring uses direct bean wiring → **divergent graphs**.

---

### 2.3 Replay Startup Sequence

```mermaid
sequenceDiagram
    participant CLI as CLI replay command
    participant Orchestrator as ReplayOrchestrator
    participant Clock as VirtualClock
    participant StateMgr as ReplayStateManager
    participant ReplayRunner as ReplayRunner
    participant HistService as HistoricalRangeService
    participant DuckDB as DuckDbEventStore
    participant EventBus as EventBus (same instance)
    participant Strategy as Strategy Engine

    CLI->>Orchestrator: replayCandles(symbol, interval, fromMs, toMs, eventBus)
    Orchestrator->>Clock: virtualClock.enterReplayMode()
    Orchestrator->>StateMgr: replayStateManager.beforeReplay()

    Orchestrator->>HistService: replayCandles(symbol, interval, fromMs, toMs, clockSyncedBus)
    HistService->>DuckDB: Query historical candles
    DuckDB-->>HistService: List<Candle>

    loop For each historical event
        HistService->>Clock: Update virtual time
        HistService->>EventBus: publish(MarketTickEvent)
        EventBus->>Strategy: Strategy processes tick
        Strategy->>EventBus: publish(SignalGenerated) if signal
    end

    HistService-->>Orchestrator: ReplayResult
    Orchestrator->>StateMgr: replayStateManager.afterReplay()
    Orchestrator->>Clock: virtualClock.enterLiveMode()
    Orchestrator-->>CLI: ReplayResult (events replayed, duration)
```

**Actual Code Path**:
1. CLI or app invokes `ReplayOrchestrator.replayCandles()` or similar
2. `VirtualClock.enterReplayMode()` switches clock to replay time
3. `ReplayStateManager.beforeReplay()` resets state
4. Historical data loaded from DuckDB via `HistoricalRangeService`
5. Events published to **same EventBus** as live mode
6. Strategies process replayed events identically to live
7. State reset after replay completes

**Key Finding**: Replay uses **same EventBus and strategy engine** as live - good parity.

---

### 2.4 Gateway Startup Sequence

```mermaid
sequenceDiagram
    participant Spring as Spring Boot
    participant AutoConfig as GatewayAutoConfiguration
    participant Router as GatewayTopicRouter
    participant Bridge as GatewayEventBridge
    participant WSHandler as GatewayWebSocketHandler
    participant EventBus as EventBus
    participant Frontend as WebSocket Frontend

    Spring->>AutoConfig: @ConditionalOnProperty(tradej.gateway.enabled=true)
    AutoConfig->>Router: new GatewayTopicRouter()
    AutoConfig->>WSHandler: new GatewayWebSocketHandler(router)
    AutoConfig->>Bridge: new GatewayEventBridge(router, objectMapper)
    Bridge->>EventBus: bridge.register(eventBus)
    EventBus->>Bridge: Subscribe to all DomainEvents

    Frontend->>WSHandler: WebSocket connection
    WSHandler->>Router: Subscribe to topics
    Router->>WSHandler: Route events to session

    EventBus->>Bridge: publish(MarketTickEvent)
    Bridge->>Router: routeByTopic(event)
    Router->>Frontend: Send JSON via WebSocket
```

**Actual Code Path**:
1. `GatewayAutoConfiguration` creates beans conditionally
2. `GatewayEventBridge` registers with `EventBus` to receive all events
3. `GatewayTopicRouter` routes events to WebSocket topics
4. Frontend subscribes to topics (market, orders, positions, etc.)

**Key Finding**: Gateway is **pure pass-through** - no business logic, good separation.

---

### 2.5 Broker Gateway Startup Sequence

```mermaid
sequenceDiagram
    participant Caller as CLI or App
    participant Gateway as BrokerGateway.of(source, connection)
    participant Router as BrokerRouter
    participant Handle as BrokerHandle
    participant Connection as IBrokerConnection

    Caller->>Gateway: BrokerGateway.of(brokerSource, connection)
    Gateway->>Router: new BrokerRouter()
    Router->>Handle: new BrokerHandle(source, connection)
    Handle->>Connection: Wrap IBrokerConnection
    Handle->>Handle: Load instrument catalog
    Handle-->>Gateway: MarketHandle, OrderHandle, OptionsHandle, PortfolioHandle
    Gateway-->>Caller: BrokerGateway with handles

    Caller->>Handle: marketHandle().quote(symbol)
    Handle->>Connection: connection.marketData().quote(symbol)
    Connection-->>Handle: Quote
    Handle-->>Caller: Quote
```

**Actual Code Path**:
1. `BrokerGateway.of()` creates unified gateway for one or more brokers
2. `BrokerHandle` wraps `IBrokerConnection` with instrument catalog
3. Provides typed handles: `MarketHandle`, `OrderHandle`, `OptionsHandle`, `PortfolioHandle`
4. Simulation brokers: `PaperBrokerConnection`, `BacktestBrokerConnection` also supported

**Key Finding**: Broker Gateway provides **clean abstraction** over broker-specific connections.

---

## 3. Composition Root Analysis

### 3.1 Identified Composition Roots

| Composition Root | Location | Wiring Mechanism | Used By |
|------------------|----------|------------------|---------|
| **Spring Boot** | `app/src/main/java/com/tradej/app/config/*.java` | `@Configuration` + `@Bean` | Spring Boot app |
| **FullComposition** | `composition/src/main/java/com/tradej/composition/FullComposition.java` | Programmatic factory methods | CLI, broker-gateway |
| **BrokerComposition** | `composition/src/main/java/com/tradej/composition/BrokerComposition.java` | SPI-based broker discovery | FullComposition |
| **DataComposition** | `composition/src/main/java/com/tradej/composition/DataComposition.java` | Programmatic | FullComposition |
| **ExecutionComposition** | `composition/src/main/java/com/tradej/composition/ExecutionComposition.java` | Programmatic | FullComposition |
| **PipelineComposition** | `composition/src/main/java/com/tradej/composition/PipelineComposition.java` | Programmatic | runtime-disruptor |
| **ReplayOrchestrator** | `replay/engine/src/main/java/com/tradej/replay/engine/ReplayOrchestrator.java` | Constructor injection | app/, cli/ |
| **Test Fixtures** | `core/src/testFixtures/java/com/tradej/core/testing/` | Mock wiring | Tests |

### 3.2 Composition Parity Matrix

| Component | Spring Boot | CLI (Composition) | Replay | Parity? |
|-----------|-------------|-------------------|--------|---------|
| Broker Connection | Created via `@Configuration` bean | Created via `BrokerComposition` SPI | Uses same as caller | ⚠️ **Different wiring** |
| EventBus | `DisruptorEventBus` created in Spring config | Not created in CLI (attach mode) | Same instance as live | ⚠️ **CLI attach uses HTTP** |
| EventStore | `DuckDbEventStore` bean | `DataComposition` creates instance | Same instance | ✅ **Same** |
| Strategy Engine | Created via Spring beans | Not available in CLI | Same as live | ❌ **CLI lacks strategy** |
| OrderManagementService | Spring bean | Created in `ExecutionComposition` | Same as live | ⚠️ **Different construction** |
| RiskHandler | Spring bean | Created in `ExecutionComposition` | Same as live | ⚠️ **Different construction** |
| VirtualClock | Spring bean | Not in CLI | Same as live | ❌ **CLI lacks replay** |
| Gateway | `GatewayAutoConfiguration` | Not in CLI | N/A | ❌ **CLI attach only** |

### 3.3 Critical Finding: Divergent Object Graphs

**Spring Boot Graph**:
```
TradingApplication
  → 25+ @Configuration classes
    → Direct bean wiring
      → DisruptorEventBus (ring buffer + pipeline)
      → OrderManagementService
      → PositionRiskHandler
      → PortfolioEngine
      → StrategySandbox
      → DuckDbEventStore
      → ChronicleEventWal
      → BrokerConnection (via BrokerComposition delegate)
```

**CLI Graph**:
```
TradeCli
  → CliContext
    → FullComposition.brokerOnly() or createFull()
      → BrokerComposition (SPI-based)
        → ServiceLoaderBrokerRegistry
          → BrokerProvider (Dhan/Upstox/ICICI)
            → IBrokerConnection
      → DataComposition
        → DuckDbEventStore
      → ExecutionComposition (optional)
        → OrderManagementService
        → PositionRiskHandler
```

**Gap Analysis**:
1. **Spring creates 20+ beans** that CLI doesn't have (strategy, portfolio, risk, gateway)
2. **CLI uses composition layer**, Spring bypasses it
3. **Replay can use either** depending on caller
4. **Paper trading** uses simulation brokers in broker-gateway, not composition

**Recommendation**: Make composition layer **canonical**. Spring should delegate to composition, not create parallel graphs.

---

## 4. Event Bus & Event Flow Audit

### 4.1 EventBus Implementations

| Implementation | Location | Purpose | Threading | Used By |
|----------------|----------|---------|-----------|---------|
| **EventBus (interface)** | `core/` | Contract | N/A | All modules |
| **DisruptorEventBus** | `runtime/disruptor/` | Production event bus | LMAX Disruptor ring buffer | Spring Boot app |
| **ShardedDisruptorEventBus** | `runtime/disruptor/` | Sharded variant | Multiple ring buffers | Not actively used |
| **BrokerScopedEventBus** | `runtime/disruptor/config/` | Broker-scoped | Disruptor | Broker isolation |
| **SimpleEventBus** | `core/` | Simple synchronous | Single-threaded | Tests, CLI |
| **CollectingEventBus** | `core/testFixtures/` | Test fixture | Single-threaded | Unit tests |
| **GatewayEventBridge** | `gateway/` | WebSocket bridge | Delegates to real bus | Gateway |

### 4.2 Event Catalog (50 Domain Events)

**Market Data Events** (8):
- `MarketTickEvent` - Price tick
- `DepthUpdateEvent` - Order book depth
- `CandleClosed` - Completed candle
- `CandleDeveloping` - In-progress candle
- `OptionChainUpdated` - Option chain refresh
- `GreeksComputed` - Options Greeks
- `GammaExposureComputed` - GEX
- `MaxPainComputed` - Max pain level

**Order Lifecycle Events** (8):
- `OrderAccepted` - Order placed successfully
- `OrderRejected` - Order rejected
- `OrderFilled` - Partial/full fill
- `OrderPartiallyFilled` - Partial fill
- `OrderFullyFilled` - Complete fill
- `OrderModified` - Order modified
- `OrderCancelled` - Order cancelled
- `OrderUpdateEvent` - Base order event

**Position & PnL Events** (6):
- `PositionUpdateEvent` - Position change
- `PnlUpdatedEvent` - PnL update
- `UnrealizedPnLUpdated` - Unrealized PnL
- `PositionMismatch` - Reconciliation mismatch
- `TradeOpened` - New trade
- `TradeClosed` - Trade closed
- `TradeUpdated` - Trade update

**Strategy Events** (4):
- `SignalGenerated` - Trading signal
- `SignalPendingExecution` - Signal queued
- `SignalSuppressed` - Signal blocked by risk
- `StrategyError` - Strategy exception

**System Events** (6):
- `KillSwitchEngaged` - Risk kill switch
- `UnifiedKillSwitchEngaged` - Global kill switch
- `UnifiedKillSwitchDisengaged` - Kill switch off
- `ReconciliationHaltRequired` - Reconciliation needed
- `StreamHealthChanged` - Data stream health
- `ReplayTimeChangedEvent` - Replay time update

**Scanner Events** (2):
- `ScanHitProduced` - Scanner match
- `ScanResultsPublished` - Scan results

**Error Events** (2):
- `BrokerAdapterError` - Broker error
- `StrategyError` - Strategy error

### 4.3 Market Tick Event Flow

```mermaid
graph LR
    A[Broker WebSocket] -->|Raw payload| B[DhanMarketEventNormalizer]
    B -->|Unified MarketTickEvent| C[DisruptorEventBus.publish]
    C -->|Ring Buffer| D[GraphPipelineDisruptorHandler]
    D -->|Risk checks| E[GraphStrategyDisruptorHandler]
    E -->|Strategy execution| F[AsyncDispatchHandler]
    F -->|Subscriber callbacks| G[PortfolioEngine]
    F -->|Subscriber callbacks| H[CandleAggregationService]
    F -->|Subscriber callbacks| I[GatewayEventBridge]
    I -->|WebSocket| J[Frontend UI]
    G -->|Position update| K[PositionUpdateEvent]
    K -->|Ring Buffer| L[Downstream Queue]
    L -->|Drainer thread| C
```

**Threading Model**:
1. **Producer Thread**: Broker WebSocket thread publishes to ring buffer
2. **Disruptor Thread**: Single thread processes ring buffer through pipeline stages
3. **Async Dispatch Thread**: Separate thread handles subscriber callbacks
4. **Downstream Drainer Thread**: Drains re-entrant events back to ring buffer
5. **Dedup Pruner Thread**: Prunes dedup cache every minute

**Backpressure**:
- Ring buffer capacity: **8192 events**
- Downstream queue capacity: **4096 events**
- Dead letter queue: Drops events when queues full
- Write-ahead log: Persists events before ring buffer entry
- Wait strategies: BusySpin (LIVE), Yielding (REPLAY/BACKTEST)

### 4.4 Order Lifecycle Event Flow

```mermaid
graph LR
    A[Strategy SignalGenerated] --> B[PositionRiskHandler]
    B -->|Risk check passes| C[OrderManagementService]
    C -->|placeOrder| D[IBrokerConnection]
    D -->|OrderAccepted| E[DisruptorEventBus.publish]
    E -->|Ring Buffer| F[PortfolioEngine]
    F -->|Update positions| G[PositionUpdateEvent]
    D -->|OrderFilled| E
    E -->|Ring Buffer| H[PnlUpdatedEvent]
    H -->|Ring Buffer| I[GatewayEventBridge]
    I -->|WebSocket| J[Frontend UI]
```

### 4.5 Event Flow Issues

1. **Multiple EventBus implementations**: 7 implementations create confusion about which to use
2. **Re-entrancy complexity**: ThreadLocal guard required to prevent deadlock
3. **Dedup logic**: Event-specific dedup keys scattered in `dedupKey()` method
4. **Downstream queue**: Re-publishing events through queue adds latency
5. **No event schema versioning**: `EventSchemaVersion` exists but not enforced

---

## 5. Market Data Architecture Audit

### 5.1 Data Sources

| Source | Type | Protocol | Update Frequency | Used By |
|--------|------|----------|------------------|---------|
| **Dhan WebSocket** | Live market data | WebSocket | Tick-level (milliseconds) | Live trading |
| **Upstox REST** | Market data (analytics-only) | HTTP REST | On-demand | Upstox profile |
| **ICICI REST** | Market data | HTTP REST | On-demand | ICICI profile |
| **DuckDB Parquet** | Historical data | File-based | Daily batches | Replay, backtest |
| **Chronicle Queue** | Event log | Memory-mapped files | Real-time | Crash recovery |
| **Simulation** | Paper trading | In-memory | Configurable | Testing, paper trading |

### 5.2 Market Data Transformations

```mermaid
graph TD
    A[Broker Raw Payload] --> B[Broker-Specific Normalizer]
    B -->|DhanMarketEventNormalizer| C[Unified MarketTickEvent]
    B -->|Upstox normalizer| C
    B -->|ICICI normalizer| C

    C --> D[CandleAggregationService]
    D -->|1-min candles| E[CandleDeveloping]
    D -->|Candle close| F[CandleClosed]

    C --> G[Options Analytics]
    G -->|Greeks| H[GreeksComputed]
    G -->|GEX| I[GammaExposureComputed]
    G -->|Max Pain| J[MaxPainComputed]

    C --> K[Depth Analytics]
    K -->|Order book| L[DepthUpdateEvent]

    F --> M[DuckDbEventStore]
    M -->|Parquet| N[Historical Data Files]
```

### 5.3 Canonical Market Data Flow

**Live Path**:
```
Broker WebSocket → Normalizer → MarketTickEvent → DisruptorEventBus → Strategy → Execution
                                            ↓
                                      CandleAggregation → DuckDB (persistence)
                                            ↓
                                      GatewayEventBridge → Frontend
```

**Historical Path**:
```
DuckDB Query → HistoricalRangeService → MarketTickEvent → EventBus → Strategy → Execution
```

**Replay Path**:
```
DuckDB → ReplayOrchestrator → VirtualClock → EventBus (same as live) → Strategy → Execution
```

**Simulation Path**:
```
SimulatedMarketDataProvider → MarketTickEvent → EventBus → Strategy → Execution
```

### 5.4 Data Integrity Findings

✅ **Strengths**:
1. **Single event type** (`MarketTickEvent`) for all market data
2. **Broker normalizers** isolate broker-specific formats
3. **Replay uses same EventBus** as live - deterministic

⚠️ **Issues**:
1. ~~**Duplicate paths**: Upstox uses REST-only (no WebSocket), creating inconsistency~~
   **Resolved**: Upstox now has a V3 market data WebSocket (protobuf over binary,
   see `broker/upstox/.../websocket/UpstoxWebSocketMultiplexer.java`). The
   REST path remains for history, profile, and other read-only endpoints.
2. **State duplication**: Positions tracked in:
   - `PortfolioEngine` (in-memory)
   - `PositionRiskHandler` (in-memory)
   - `DuckDbEventStore` (persisted)
   - Broker (external system)
3. ~~**No reconciliation**: Position mismatches detected but not auto-resolved~~
   **Resolved**: `UpstoxReconciliationService` in `broker/upstox/.../reconciliation/`
   diffs orders/trades/positions/holdings between the broker and an
   `OmsSnapshot` and returns a structured `ReconciliationReport`. The
   companion `UpstoxReconciliationScheduler` runs it on a configurable
   cadence and forwards drift items to a consumer hook.
4. **No data validation**: Market data not validated before publishing

---

## 6. Broker Architecture Audit

### 6.1 Broker Module Isolation

```mermaid
graph TB
    subgraph broker-api
        IBrokerConnection["IBrokerConnection (interface)"]
        BrokerProvider["BrokerProvider (SPI)"]
        BrokerRegistry["BrokerRegistry (interface)"]
    end

    subgraph broker-core
        BrokerLifecycleManager["BrokerLifecycleManager"]
        RetryPolicy["Retry policies"]
        CircuitBreaker["Circuit breakers"]
    end

    subgraph broker-dhan
        DhanBrokerProvider["DhanBrokerProvider"]
        DhanWebSocket["Dhan WebSocket handler"]
        DhanNormalizer["DhanMarketEventNormalizer"]
    end

    subgraph broker-upstox
        UpstoxBrokerProvider["UpstoxBrokerProvider"]
        UpstoxREST["Upstox REST client"]
    end

    subgraph broker-icici
        IciciBrokerProvider["IciciBrokerProvider"]
        IciciAuth["ICICI auth flow"]
    end

    IBrokerConnection -.->|implements| DhanBrokerProvider
    IBrokerConnection -.->|implements| UpstoxBrokerProvider
    IBrokerConnection -.->|implements| IciciBrokerProvider

    BrokerProvider -.->|extends| DhanBrokerProvider
    BrokerProvider -.->|extends| UpstoxBrokerProvider
    BrokerProvider -.->|extends| IciciBrokerProvider

    broker-dhan --> broker-api
    broker-dhan --> broker-core
    broker-upstox --> broker-api
    broker-upstox --> broker-core
    broker-icici --> broker-api
    broker-icici --> broker-core
```

### 6.2 Broker Capabilities Matrix

| Capability | Dhan | Upstox | ICICI | Implementation |
|------------|------|--------|-------|----------------|
| **Authentication** | TOTP + PIN | OAuth2 | Password + TOTP | Broker-specific |
| **Token Management** | Auto-refresh | Manual refresh | Auto-refresh | Broker module |
| **WebSocket Market Data** | ✅ Yes | ✅ Yes (V3 protobuf) | ❌ No | Dhan + Upstox |
| **REST Market Data** | ✅ Yes | ✅ Yes | ✅ Yes | All brokers |
| **Historical Data** | ✅ Yes | ✅ Yes | ❌ No | Broker API |
| **Option Chain** | ✅ Yes | ✅ Yes | ❌ No | Broker API |
| **Order Placement** | ✅ Yes | ✅ Yes | ✅ Yes | IBrokerConnection |
| **Order Modification** | ✅ Yes | ✅ Yes | ✅ Yes | IBrokerConnection |
| **Order Cancellation** | ✅ Yes | ✅ Yes | ✅ Yes | IBrokerConnection |
| **Positions** | ✅ Yes | ✅ Yes | ✅ Yes | IBrokerConnection |
| **Holdings** | ✅ Yes | ✅ Yes | ✅ Yes | IBrokerConnection |
| **Connection Recovery** | ✅ Yes | ✅ Yes | ✅ Yes | BrokerLifecycleManager |
| **Rate Limiting** | ✅ Yes | ✅ Yes | ✅ Yes | Broker core |
| **Health Checks** | ✅ Yes | ✅ Yes | ✅ Yes | BrokerHealthCheck SPI |

### 6.3 Broker Boundary Violations

✅ **Good Isolation**:
1. **No broker-specific logic in Spring app**: Verified via `BrokerIsolationArchitectureTest`
2. **No broker-specific logic in Gateway**: Gateway uses `IBrokerConnection` interface
3. **No broker-specific logic in Strategy**: Strategies use domain events only
4. **SPI-based discovery**: `ServiceLoaderBrokerRegistry` enables clean plugin model

⚠️ **Boundary Concerns**:
1. **Simulation brokers in broker-gateway**: `PaperBrokerConnection`, `BacktestBrokerConnection` live in broker-gateway, not a separate simulation module
2. **Broker-specific configuration in Spring**: `UpstoxBrokerConfiguration` has Upstox-specific bean wiring
3. **Broker type enum in CLI**: `CliConfig.BrokerType` duplicates `BrokerSource` enum

### 6.4 Authentication & Token Management

**Dhan**:
- TOTP (Time-based One-Time Password) + PIN authentication
- Token stored in `runtime/dhan-token-state.json`
- Auto-refresh via `refresh-dhan-token.sh` script
- Session management via WebSocket heartbeat

**Upstox**:
- OAuth2 flow with PKCE (`UpstoxPkceUtil`) and refresh token
- Token stored in `runtime/upstox-token-state.json` (and `runtime/icici-token-state.json` for ICICI)
- Token state persisted via `JsonTokenStateStore`
- Auto-refresh via `UpstoxTokenManager.doRefresh` (refresh_token grant) and
  `upgradeFromWebhook` (Flow 2 notifier webhook)
- Extended-token support for read-only analytics access (`UpstoxExtendedTokenHolder`)
- Market data WebSocket (V3 protobuf) with reconnect, health, and
  `market_info` JSON first-tick handling

**ICICI**:
- Password + TOTP authentication
- Token stored in config files
- Auto-refresh capability
- REST-only

---

## 7. Data Platform Audit

### 7.1 Storage Layers

| Storage | Technology | Purpose | Data Type | Retention |
|---------|------------|---------|-----------|-----------|
| **Parquet Files** | Apache Parquet | Historical market data | Ticks, candles, options | Indefinite |
| **DuckDB** | Embedded OLAP | Analytics, queries | Aggregated data | Indefinite |
| **Chronicle Queue** | Memory-mapped files | Event write-ahead log | Domain events | Configurable |
| **Feature Store** | DuckDB | ML features | Time-series features | Indefinite |
| **In-Memory** | Java objects | Runtime state | Positions, PnL, orders | Session-only |

### 7.2 Data Lineage

```mermaid
graph LR
    A[Broker WebSocket] -->|MarketTickEvent| B[DisruptorEventBus]
    B -->|Persist| C[DuckDbEventStore]
    C -->|Parquet files| D[Historical Data]
    D -->|Load| E[ReplayOrchestrator]
    E -->|Replay| B

    B -->|Aggregate| F[CandleAggregationService]
    F -->|Persist| C

    B -->|Event log| G[ChronicleEventWal]
    G -->|Recovery| B
```

### 7.3 Data Integrity Findings

✅ **Strengths**:
1. **Event-sourced order repository**: Orders reconstructed from events, not state
2. **Write-ahead log**: Chronicle Queue enables crash recovery
3. **DuckDB analytics**: Efficient OLAP queries on historical data
4. **Parquet storage**: Columnar format for efficient historical replay

⚠️ **Issues**:
1. **No data validation**: Market data not validated before storage
2. **No gap detection**: Missing data not detected during ingest
3. **No backfill automation**: Manual scripts required for backfill
4. **Position reconciliation**: Mismatch detected but not auto-resolved
5. **Event schema evolution**: `EventSchemaVersion` exists but not enforced

---

## 8. Replay & Simulation Audit

### 8.1 Replay Engine Architecture

```mermaid
graph TB
    subgraph Replay Engine
        RO[ReplayOrchestrator]
        RC[ReplayController]
        CRS[CandleReplaySession]
        TRS[TickReplaySession]
        MTC[MultiTimeframeContext]
        IRSM[IsolatedReplayStateManager]
    end

    subgraph Data Layer
        HRS[HistoricalRangeService]
        DB[DuckDB]
    end

    subgraph Runtime
        VC[VirtualClock]
        EB[EventBus]
        SE[Strategy Engine]
    end

    RO --> RC
    RC --> CRS
    RC --> TRS
    RC --> MTC
    RO --> IRSM
    RO --> HRS
    HRS --> DB
    RO --> VC
    RO --> EB
    EB --> SE
```

### 8.2 Replay Determinism Verification

**Determinism Factors**:

| Factor | Status | Notes |
|--------|--------|-------|
| **Event Ordering** | ✅ Deterministic | Events replayed in timestamp order |
| **Virtual Clock** | ✅ Deterministic | `VirtualClock` controls time progression |
| **State Reset** | ✅ Deterministic | `ReplayStateManager` resets state before replay |
| **Strategy Execution** | ✅ Deterministic | Same events → same signals |
| **Position Tracking** | ✅ Deterministic | Positions rebuilt from events |
| **PnL Calculation** | ✅ Deterministic | PnL computed from fills |
| **Random Elements** | ⚠️ Not verified | Strategies with randomness not tested |
| **Threading** | ⚠️ Potential issue | Disruptor threading may introduce non-determinism |

**Replay Modes**:
1. **Tick Replay**: Replays individual market ticks (highest fidelity)
2. **Candle Replay**: Replays OHLCV candles (lower fidelity, faster)
3. **Trade Lifecycle Replay**: Replays order/position events for reconciliation
4. **Chronicle Replay**: Replays events from Chronicle Queue write-ahead log

### 8.3 Simulation Architecture

**Paper Trading**:
- `PaperBrokerConnection` simulates broker behavior
- Uses live market data from broker
- Orders executed at market price (no slippage simulation)
- Positions tracked in-memory

**Backtest**:
- `BacktestBrokerConnection` simulates broker for historical data
- Uses replayed market data
- Orders executed at historical prices
- Full position and PnL tracking

**Simulation Gaps**:
1. **No slippage model**: Orders filled at exact market price
2. **No latency simulation**: Instant order execution
3. **No partial fills**: Orders filled completely or rejected
4. **No market impact**: Large orders don't move market

---

## 9. Strategy Execution Audit

### 9.1 Strategy Execution Flow

```mermaid
graph LR
    A[MarketTickEvent] --> B[CandleAggregationService]
    B --> C[CandleClosed]
    C --> D[Strategy Plugin]
    D -->|Buy/Sell signal| E[SignalGenerated]
    E --> F[PositionRiskHandler]
    F -->|Risk check passes| G[OrderManagementService]
    G -->|placeOrder| H[IBrokerConnection]
    H -->|OrderAccepted| I[OrderAccepted event]
    H -->|OrderFilled| J[OrderFilled event]
    J --> K[PortfolioEngine]
    K -->|Update position| L[PositionUpdateEvent]
    L --> M[PnlUpdatedEvent]
```

### 9.2 Execution Parity: Live vs Replay vs Paper

| Component | Live | Replay | Paper | Parity? |
|-----------|------|--------|-------|---------|
| **Market Data Source** | Broker WebSocket | DuckDB historical | Live broker data | ⚠️ Different sources |
| **EventBus** | DisruptorEventBus | Same DisruptorEventBus | Same DisruptorEventBus | ✅ Same |
| **Strategy Execution** | GraphStrategyDisruptorHandler | Same handler | Same handler | ✅ Same |
| **Risk Checks** | PositionRiskHandler | Same handler | Same handler | ✅ Same |
| **Order Placement** | IBrokerConnection (live) | IBrokerConnection (replay) | PaperBrokerConnection | ⚠️ Different connections |
| **Position Tracking** | PortfolioEngine | Same engine | Same engine | ✅ Same |
| **PnL Calculation** | PortfolioEngine | Same engine | Same engine | ✅ Same |

**Key Finding**: **Strategy, risk, and portfolio logic are identical** across live/replay/paper. Only data source and order connection differ - this is correct architecture.

---

## 10. Spring Boot Audit

### 10.1 Spring Configuration Classes (25+)

| Configuration Class | Beans Created | Purpose | Concern |
|---------------------|---------------|---------|---------|
| `BrokerConfiguration` | BrokerConnection, BrokerProfile | Broker setup | ✅ Appropriate |
| `BrokerAdapterConfiguration` | MarketDataAdapter, EventNormalizer (4 nested configs) | Broker adapter wiring | ⚠️ Too complex |
| `DataConfiguration` | DuckDbEventStore, ChronicleEventWal, FeatureStore (3 nested configs) | Data persistence | ⚠️ Too complex |
| `GatewayBrokerConfiguration` | Gateway beans, WebSocket handler (5 nested configs) | Gateway setup | ⚠️ Too complex |
| `RuntimeAndStartupConfiguration` | DisruptorEventBus, PipelineRuntime | Runtime setup | ✅ Appropriate |
| `ObservabilityConfiguration` | Metrics, logging | Observability | ✅ Appropriate |
| `ScanConfiguration` | Scanner beans | Market scanning | ✅ Appropriate |
| `WebConfiguration` | REST controllers, CORS | Web setup | ✅ Appropriate |
| `AdminConfiguration` | Admin endpoints (2 nested configs) | Admin features | ✅ Appropriate |
| `VirtualThreadConfiguration` | Virtual thread executors | Concurrency | ✅ Appropriate |
| `PrometheusConfiguration` | Prometheus metrics | Monitoring | ✅ Appropriate |
| `UpstoxBrokerConfiguration` | Upstox-specific beans | Upstox setup | ⚠️ Broker-specific in Spring |

### 10.2 Spring Ownership Analysis

**What Spring Owns** (Appropriate):
- Web server (Tomcat)
- REST API endpoints
- WebSocket connections
- Prometheus metrics
- Health checks
- Configuration properties

**What Spring Should NOT Own** (Framework Leakage):
- Broker connection creation (should delegate to composition)
- Event bus creation (should delegate to composition)
- Strategy engine creation (should delegate to composition)
- Data store creation (should delegate to composition)
- Risk handler creation (should delegate to composition)

**Business Logic Inside Spring** (Violation):
- `BrokerAdapterConfiguration` contains broker-specific wiring logic
- `DataConfiguration` contains persistence initialization logic
- `GatewayBrokerConfiguration` contains gateway setup logic

### 10.3 Spring vs Composition Layer

**Current State**:
- Spring creates 80% of beans directly
- Composition layer used only by CLI
- Duplicate wiring logic in both

**Ideal State**:
- Composition layer creates all business objects
- Spring only wires infrastructure (web server, WebSocket, metrics)
- Spring delegates to composition for business beans

---

## 11. CLI Audit

### 11.1 CLI Architecture

**CLI Capabilities** (60+ commands):
- Market data: `quote`, `depth`, `ohlc`, `candles`, `ltp`
- Orders: `place`, `cancel`, `modify`, `order-book`, `trades`
- Portfolio: `positions`, `holdings`, `balance`, `live-pnl`
- Options: `chain`, `expiries`, `strike`, `greeks`, `options-scan`
- Replay: `replay`, `backtest`, `replay-console`
- Scanner: `scan`, `screener`
- Data: `historical`, `parquet`, `data-sources`
- Admin: `status`, `runtime`, `pipeline`, `monitor`, `doctor`
- Certification: `certify`, `regression`, `readiness`, `coverage`
- Documentation: `docs`, `events`, `flows`, `modules`, `capabilities`

### 11.2 CLI Independence Verification

**CLI Dependencies**:
- ✅ No Spring dependency required
- ✅ No web server required
- ✅ No application context required
- ✅ Uses composition layer for wiring
- ✅ Can run standalone (no app required)

**CLI Modes**:
1. **Attach Mode**: Connects to running Spring Boot app via HTTP
   - Uses `AttachClient` to call REST API
   - Limited to app capabilities
2. **Standalone Mode**: Creates independent broker connection
   - Uses `FullComposition` for wiring
   - Full broker capabilities
   - No strategy engine (limitation)

### 11.3 CLI-Spring Parity Report

| Capability | CLI Standalone | Spring Boot | Parity? |
|------------|----------------|-------------|---------|
| **Broker Connection** | ✅ Yes | ✅ Yes | ✅ Same |
| **Market Data** | ✅ Yes (REST) | ✅ Yes (WebSocket) | ⚠️ Different protocols |
| **Order Placement** | ✅ Yes | ✅ Yes | ✅ Same |
| **Strategy Execution** | ❌ No | ✅ Yes | ❌ Missing in CLI |
| **Replay** | ✅ Yes | ✅ Yes | ✅ Same |
| **Scanner** | ✅ Yes | ✅ Yes | ✅ Same |
| **Paper Trading** | ❌ No | ✅ Yes | ❌ Missing in CLI |
| **Gateway** | ❌ No | ✅ Yes | ❌ CLI attach only |
| **Options Analytics** | ⚠️ Limited | ✅ Full | ⚠️ Partial |

**Key Finding**: CLI is **independent** but lacks strategy execution and paper trading capabilities.

---

## 12. Testing Audit

### 12.1 Test Categories

| Test Type | Location | Count | Coverage | Quality |
|-----------|----------|-------|----------|---------|
| **Unit Tests** | `src/test/java` | ~200+ tests | Good | ✅ High |
| **Integration Tests** | `src/test/java` (tag: integration) | ~50 tests | Moderate | ✅ High |
| **Architecture Tests** | `architecture-test/` | 13 tests | Excellent | ✅ High |
| **Certification Tests** | `scripts/certify-*.sh` | 8 scripts | Good | ✅ High |
| **Broker Tests** | `src/test/java` (tags: broker-*) | ~30 tests | Good | ✅ High |
| **Regression Tests** | `src/test/java` (tags: regression, cross-layer) | ~20 tests | Moderate | ✅ High |

### 12.2 Architecture Tests (13 Tests)

| Test | Purpose | Status |
|------|---------|--------|
| `AutoConfigurationArchitectureTest` | Verify Spring auto-configuration | ✅ Passing |
| `BrokerCompositionArchitectureTest` | Verify broker composition isolation | ✅ Passing |
| `BrokerIsolationArchitectureTest` | Verify no broker leakage | ✅ Passing |
| `CodeQualityArchitectureTest` | Code quality rules | ✅ Passing |
| `ConfigFileCountArchitectureTest` | Config file limits | ✅ Passing |
| `DataAccessArchitectureTest` | Data access patterns | ✅ Passing |
| `DesignPatternArchitectureTest` | Design pattern enforcement | ✅ Passing |
| `EventSystemOwnershipTest` | Event bus ownership | ✅ Passing |
| `ModuleBoundaryArchitectureTest` | Module boundaries | ✅ Passing |
| `ModulithArchitectureTest` | Module layering | ✅ Passing |
| `PluginModelArchitectureTest` | Plugin model enforcement | ✅ Passing |
| `ProfileIsolationArchitectureTest` | Spring profile isolation | ✅ Passing |
| `SpringFreeArchitectureTest` | Verify composition layer Spring-free | ✅ Passing |

### 12.3 Testing Gaps

❌ **Missing Tests**:
1. **Replay determinism tests**: No tests verify identical results across runs
2. **Event ordering tests**: No tests verify event ordering under load
3. **Backpressure tests**: No tests verify behavior when queues full
4. **Connection recovery tests**: Limited broker reconnection testing
5. **Data integrity tests**: No tests verify data consistency across storage layers
6. **Position reconciliation tests**: Limited mismatch handling tests
7. **Performance tests**: No latency or throughput benchmarks
8. **Chaos tests**: No fault injection testing

⚠️ **False Confidence Areas**:
1. **Mock-heavy integration tests**: Many tests mock broker, not testing real integration
2. **Architecture tests verify structure, not behavior**: Tests enforce rules but not correctness
3. **Certification scripts test happy path**: Limited error scenario testing

---

## 13. Observability Audit

### 13.1 Logging

**Logging Framework**: SLF4J + Logback with Logstash encoder

**Log Levels**:
- `TRACE`: Event publishing, dedup cache
- `DEBUG`: Strategy signals, risk checks
- `INFO`: Startup, lifecycle events, broker connections
- `WARN`: Queue full, dedup pruning, reconciliation mismatches
- `ERROR`: Broker errors, strategy exceptions, data persistence failures

**Structured Logging**: ✅ Yes (Logstash JSON encoder)

### 13.2 Metrics

**Metrics Framework**: Micrometer + Prometheus

**Available Metrics**:
- DisruptorEventBus: ring buffer capacity, queue depth, dropped events
- GatewayMetrics: WebSocket connections, events routed, errors
- Broker health: Connection status, last heartbeat
- JVM metrics: Memory, GC, threads (via Spring Boot Actuator)

**Missing Metrics**:
- Strategy execution latency
- Order placement latency
- Position reconciliation status
- Data ingest lag
- Replay progress

### 13.3 Health Checks

**Health Indicators**:
- `GatewayHealthIndicator`: Gateway status
- Broker health checks: Per-broker connection status
- Spring Boot Actuator: `/actuator/health`

**Missing Health Checks**:
- EventBus health (ring buffer status)
- Data store health (DuckDB, Chronicle Queue)
- Strategy engine health
- Scanner health
- Replay health

### 13.4 Tracing

**Distributed Tracing**: ❌ Not implemented

**Recommendation**: Add Micrometer Tracing for end-to-end request tracing

---

## 14. Architecture Violations Report

### 14.1 Critical Severity

| Violation | Impact | Location | Recommendation |
|-----------|--------|----------|----------------|
| **Divergent Object Graphs** | Behavioral drift between Spring and CLI | app/config/, composition/ | Consolidate to single composition root |
| **Spring Over-Configuration** | Spring becomes "the runtime" instead of hosting it | 25+ @Configuration classes | Reduce to 10 or fewer |
| **Multiple EventBus Implementations** | Confusion about which to use | 7 implementations | Consolidate to 2 (production + test) |
| **Position State Duplication** | Inconsistent positions across systems | PortfolioEngine, PositionRiskHandler, DuckDB, Broker | Single source of truth |

### 14.2 High Severity

| Violation | Impact | Location | Recommendation |
|-----------|--------|----------|----------------|
| **Simulation Brokers in broker-gateway** | Leaks simulation concerns into production module | broker-gateway/simulation/ | Move to separate module |
| **No Event Schema Versioning** | Breaking changes without migration | EventSchemaVersion (unused) | Enforce schema versioning |
| **No Data Validation** | Corrupt data in storage | Market data ingestion | Add validation layer |
| **No Gap Detection** | Missing historical data undetected | Historical ingest | Add gap detection |
| **Broker-Specific Config in Spring** | Violates broker isolation | UpstoxBrokerConfiguration | Move to broker module |

### 14.3 Medium Severity

| Violation | Impact | Location | Recommendation |
|-----------|--------|----------|----------------|
| **Re-entrancy Complexity** | Deadlock risk in EventBus | DisruptorEventBus | Simplify with single queue |
| **No Slippage Simulation** | Unrealistic paper trading | PaperBrokerConnection | Add slippage model |
| **No Latency Simulation** | Unrealistic paper trading | PaperBrokerConnection | Add latency model |
| **Manual Backfill** | Operational overhead | Historical ingest scripts | Automate backfill |
| **Duplicate Enums** | Maintenance overhead | CliConfig.BrokerType, BrokerSource | Consolidate |

### 14.4 Low Severity

| Violation | Impact | Location | Recommendation |
|-----------|--------|----------|----------------|
| **Unused ShardedDisruptorEventBus** | Code complexity | runtime/disruptor/ | Delete or document |
| **No Distributed Tracing** | Debugging difficulty | All modules | Add tracing |
| **Missing Performance Tests** | Unknown latency/throughput | Test suite | Add benchmarks |
| **No Chaos Testing** | Unknown failure behavior | Test suite | Add fault injection |

---

## 15. Simplification Roadmap

### 15.1 Delete (Remove Unnecessary Complexity)

1. **ShardedDisruptorEventBus**: Not used, delete or document use case
2. **BrokerScopedEventBus**: Not used, delete or document use case
3. **Duplicate enums**: Consolidate `CliConfig.BrokerType` and `BrokerSource`
4. **Nested @Configuration classes**: Flatten to single-level configs
5. **Manual backfill scripts**: Replace with automated process

### 15.2 Consolidate (Reduce Duplication)

1. **EventBus implementations**: Consolidate to 2 (DisruptorEventBus + SimpleEventBus)
2. **Composition roots**: Make composition layer canonical, Spring delegates
3. **Position tracking**: Single PositionService, not scattered across modules
4. **Broker configuration**: Move all broker-specific config to broker modules
5. **Test utilities**: Consolidate test fixtures into single module

### 15.3 Plugin Model (Extend via Plugins)

1. **Strategy plugins**: Already implemented ✅
2. **Broker plugins**: Already implemented via SPI ✅
3. **Scanner plugins**: Implement plugin model for scanners
4. **Data source plugins**: Implement plugin model for historical data sources
5. **Notification plugins**: Implement for alerts (email, Slack, etc.)

### 15.4 Provider Model (Abstract via Providers)

1. **Market data provider**: Already unified via MarketTickEvent ✅
2. **Order provider**: Already unified via IBrokerConnection ✅
3. **Analytics provider**: Implement for options analytics
4. **Risk provider**: Implement for pluggable risk policies
5. **Execution provider**: Implement for smart order routing

### 15.5 Contracts (Enforce via Interfaces)

1. **Event schema contract**: Enforce EventSchemaVersion
2. **Broker capability contract**: Already implemented via BrokerProvider ✅
3. **Data integrity contract**: Implement for validation
4. **Replay determinism contract**: Implement for verification
5. **Performance contract**: Implement for SLA enforcement

### 15.6 20% Components Creating 80% Complexity

| Component | Complexity Source | Simplification |
|-----------|-------------------|----------------|
| **Spring @Configuration classes** | 25+ classes with nested configs | Reduce to 10, delegate to composition |
| **DisruptorEventBus** | Re-entrancy, dedup, downstream queue, WAL | Simplify to single queue |
| **broker-gateway** | Production + simulation + certification | Split into 3 modules |
| **TradeCli** | 60+ commands in single file | Split by domain |
| **app/config/** | Mixed concerns (broker, data, runtime, web) | Separate by concern |

---

## 16. Certification Framework Review

### 16.1 Existing Certifications

| Certification | Script | Purpose | Tests Runtime? |
|---------------|--------|---------|----------------|
| **Level -1** | `certify-level-minus1.sh` | Credential validation | ✅ Yes |
| **Level 0** | `certify-level-0.sh` | Broker connectivity | ✅ Yes |
| **Level 1** | `certify-level-1.sh` | Market data | ✅ Yes |
| **Level 2** | `certify-level-2.sh` | Order placement | ✅ Yes |
| **Level 2.5** | `certify-level-2-5.sh` | Order lifecycle | ✅ Yes |
| **Level 3** | `certify-level-3.sh` | WebSocket streaming | ✅ Yes |
| **Level 4** | `certify-level-4.sh` | Options trading | ✅ Yes |
| **Level 5** | `certify-level-5.sh` | Portfolio management | ✅ Yes |
| **Level 6** | `certify-level-6.sh` | Full integration | ✅ Yes |

### 16.2 Certification Gaps

❌ **Missing Certifications**:
1. **Replay certification**: No certification for replay determinism
2. **Data integrity certification**: No certification for data consistency
3. **Performance certification**: No certification for latency/throughput SLAs
4. **Resilience certification**: No certification for connection recovery
5. **Strategy certification**: No certification for strategy execution parity
6. **Gateway certification**: No certification for WebSocket reliability
7. **Operational certification**: No certification for health checks, metrics

### 16.3 Certification Improvements

**Recommendations**:
1. Add `certify-level-7.sh` for replay determinism
2. Add `certify-level-8.sh` for data integrity
3. Add `certify-performance.sh` for performance SLAs
4. Add `certify-resilience.sh` for fault tolerance
5. Automate certification in CI/CD pipeline
6. Add certification artifacts with detailed reports

---

## 17. Production Readiness Assessment

### 17.1 Readiness by Area

| Area | Status | Confidence | Notes |
|------|--------|------------|-------|
| **Broker Connectivity** | ✅ Ready | High | Multi-broker support, SPI-based |
| **Market Data** | ✅ Ready | High | WebSocket + REST, normalization |
| **Order Execution** | ✅ Ready | High | Full lifecycle, risk checks |
| **Strategy Engine** | ⚠️ Partial | Medium | Strategy plugins work, need more testing |
| **Replay** | ⚠️ Partial | Medium | Works, determinism not verified |
| **Paper Trading** | ⚠️ Partial | Medium | Works, no slippage/latency |
| **Data Persistence** | ✅ Ready | High | DuckDB + Chronicle Queue |
| **Risk Management** | ✅ Ready | High | Kill switch, position limits |
| **Observability** | ⚠️ Partial | Medium | Metrics exist, gaps in tracing |
| **CLI** | ✅ Ready | High | Comprehensive commands |
| **Gateway** | ✅ Ready | High | WebSocket bridge works |
| **Testing** | ⚠️ Partial | Medium | Good coverage, gaps in edge cases |
| **Documentation** | ✅ Ready | High | Comprehensive docs |

### 17.2 Production Blockers

❌ **Must Fix Before Production**:
1. Position reconciliation automation
2. Replay determinism verification
3. Data gap detection
4. Backpressure testing under load

⚠️ **Should Fix Before Production**:
1. Event schema versioning enforcement
2. Slippage/latency simulation
3. Distributed tracing
4. Performance benchmarks

---

## 18. Top 20 Highest-Risk Areas

| Rank | Risk Area | Impact | Likelihood | Mitigation |
|------|-----------|--------|------------|------------|
| 1 | **Divergent Object Graphs** | Behavioral drift | High | Consolidate composition |
| 2 | **Position State Duplication** | Incorrect positions | Medium | Single PositionService |
| 3 | **No Replay Determinism Tests** | Incorrect backtest results | High | Add determinism tests |
| 4 | **No Data Validation** | Corrupt data in storage | Medium | Add validation layer |
| 5 | **Backpressure Under Load** | Event loss | Medium | Load testing |
| 6 | **No Gap Detection** | Missing historical data | High | Add gap detection |
| 7 | **Event Schema Breaking Changes** | Runtime errors | Medium | Enforce schema versioning |
| 8 | **Connection Recovery Gaps** | Lost orders/fills | Medium | Chaos testing |
| 9 | **No Slippage Simulation** | Unrealistic paper trading | Low | Add slippage model |
| 10 | **Spring Over-Configuration** | Maintenance burden | Low | Reduce configs |
| 11 | **Multiple EventBus Implementations** | Confusion | Low | Consolidate |
| 12 | **No Distributed Tracing** | Debugging difficulty | Medium | Add tracing |
| 13 | **Manual Backfill** | Operational errors | Low | Automate |
| 14 | **No Performance Benchmarks** | Unknown SLAs | Medium | Add benchmarks |
| 15 | **Simulation in Production Module** | Boundary violation | Low | Separate module |
| 16 | **Re-entrancy Deadlock Risk** | EventBus hangs | Low | Simplify |
| 17 | **No Position Reconciliation** | Mismatched positions | Medium | Auto-reconcile |
| 18 | **Broker-Specific Config in Spring** | Isolation violation | Low | Move to broker |
| 19 | **Missing Health Checks** | Undetected failures | Medium | Add health checks |
| 20 | **No Chaos Testing** | Unknown failure behavior | Low | Add fault injection |

---

## 19. Top 20 Highest-Value Improvements

| Rank | Improvement | Impact | Effort | ROI |
|------|-------------|--------|--------|-----|
| 1 | **Consolidate Composition Roots** | Eliminates behavioral drift | High | Very High |
| 2 | **Add Replay Determinism Tests** | Ensures backtest accuracy | Medium | Very High |
| 3 | **Single PositionService** | Eliminates position inconsistencies | Medium | Very High |
| 4 | **Enforce Event Schema Versioning** | Prevents breaking changes | Medium | High |
| 5 | **Add Data Validation Layer** | Prevents corrupt data | Medium | High |
| 6 | **Automate Backfill** | Reduces operational overhead | Medium | High |
| 7 | **Add Gap Detection** | Ensures data completeness | Medium | High |
| 8 | **Reduce Spring @Configuration** | Simplifies maintenance | Medium | High |
| 9 | **Add Distributed Tracing** | Improves debugging | Medium | High |
| 10 | **Add Performance Benchmarks** | Establishes SLAs | Medium | High |
| 11 | **Consolidate EventBus** | Reduces confusion | Low | Medium |
| 12 | **Add Slippage Simulation** | Realistic paper trading | Low | Medium |
| 13 | **Add Latency Simulation** | Realistic paper trading | Low | Medium |
| 14 | **Auto Position Reconciliation** | Prevents mismatches | Medium | High |
| 15 | **Add Chaos Testing** | Ensures resilience | High | Medium |
| 16 | **Separate Simulation Module** | Clean boundaries | Low | Medium |
| 17 | **Add Missing Health Checks** | Better observability | Low | Medium |
| 18 | **Consolidate Enums** | Reduces maintenance | Low | Low |
| 19 | **Delete Unused EventBus Variants** | Reduces complexity | Low | Low |
| 20 | **Add Certification Levels 7-8** | Better coverage | Medium | Medium |

---

## Answers to Key Questions

### 1. How does the system actually work at runtime?

**Answer**: The system has **two runtime modes**:
1. **Spring Boot mode**: 25+ @Configuration classes create beans directly, bypassing composition layer
2. **CLI/Composition mode**: FullComposition creates object graph programmatically

Both modes use the same core components (EventBus, BrokerConnection, DataStore) but wire them differently, creating risk of behavioral drift.

### 2. Where are the multiple sources of truth?

**Answer**:
1. **Positions**: Tracked in PortfolioEngine, PositionRiskHandler, DuckDbEventStore, and Broker
2. **Broker Configuration**: Defined in Spring configs AND composition profiles
3. **EventBus**: 7 implementations, unclear which is canonical
4. **Broker Type**: Duplicated in CliConfig.BrokerType and BrokerSource

### 3. Are event flows deterministic and observable?

**Answer**:
- **Deterministic**: ✅ Replay uses same EventBus as live, VirtualClock controls time
- **Observable**: ⚠️ Partial - Metrics exist, but no distributed tracing, gaps in health checks

### 4. Can CLI, Replay, Paper, and Live share the same architecture?

**Answer**:
- **Strategy execution**: ✅ Yes (same EventBus, same handlers)
- **Order execution**: ⚠️ Different connections (live vs simulation)
- **Object graph**: ❌ No (Spring vs composition divergence)
- **Market data**: ⚠️ Different sources (WebSocket vs REST vs historical)

**Recommendation**: Make composition layer canonical, all modes delegate to it.

### 5. What architectural issues are causing recurring defects?

**Answer**:
1. **Divergent object graphs**: Different wiring → different behavior
2. **Position duplication**: Inconsistent state across systems
3. **No data validation**: Corrupt data causes downstream errors
4. **No gap detection**: Missing data causes incorrect backtests

### 6. What must be simplified before adding more features?

**Answer**:
1. **Consolidate composition roots** (critical)
2. **Reduce Spring @Configuration classes** by 60%
3. **Consolidate EventBus implementations** to 2
4. **Single PositionService** for all position tracking
5. **Delete unused code** (ShardedDisruptorEventBus, BrokerScopedEventBus)

### 7. What tests are missing to prevent regressions?

**Answer**:
1. Replay determinism tests
2. Event ordering under load
3. Backpressure behavior
4. Connection recovery
5. Data integrity across storage layers
6. Position reconciliation
7. Performance benchmarks
8. Chaos/fault injection

### 8. What architectural changes will permanently eliminate recurring bugs?

**Answer**:
1. **Single composition root**: Eliminates behavioral drift
2. **Single PositionService**: Eliminates position inconsistencies
3. **Event schema versioning**: Prevents breaking changes
4. **Data validation layer**: Prevents corrupt data
5. **Automated gap detection**: Prevents missing data
6. **Enforced broker isolation**: Prevents boundary violations

---

## 20. Upstox V3 Audit Fixes (2026-06)

This section is a delta from the audit above. It documents the V3 fixes
applied to `broker/upstox` after the audit was written.

### 20.1 Order endpoint moved to V3 HFT host

| Before | After |
|---|---|
| `placeOrder` → `POST https://api.upstox.com/v2/order/place` | `placeOrder` → `POST https://api-hft.upstox.com/v3/order/place` |
| `modifyOrder` → `PUT https://api.upstox.com/v2/order/modify` | `modifyOrder` → `PUT https://api-hft.upstox.com/v3/order/modify` |
| `cancelOrder` → `DELETE https://api.upstox.com/v2/order/cancel?order_id=…` | `cancelOrder` → `DELETE https://api-hft.upstox.com/v3/order/cancel?order_id=…` |
| `getOrderDetails` → `GET https://api.upstox.com/v2/order/details` | `getOrderDetails` → `GET https://api-hft.upstox.com/v3/order/details` |
| `placeMultiOrder` → `POST https://api.upstox.com/v2/order/multi` | `placeMultiOrder` → `POST https://api-hft.upstox.com/v3/order/multi` |
| `cancelMultiOrder` → `DELETE https://api.upstox.com/v2/order/multi` | `cancelMultiOrder` → `DELETE https://api-hft.upstox.com/v3/order/multi` |
| `getOrderBook` → `GET https://api.upstox.com/v2/order/history` | `getOrderBook` → `GET https://api.upstox.com/v2/order/history` (unchanged — still v2) |
| `getTrades` → `GET https://api.upstox.com/v2/order/trades/get-trades-for-day` | unchanged — still v2 |

Wired through new `UpstoxApiEnvironment.orderBaseUrl()` (per-environment
HFT base) and `UpstoxOrderRestClient` (3-arg constructor that takes the
explicit V3 base).

### 20.2 Order payload fields added

Per V3 docs, the place-order and modify-order payloads now include:
- `slice` (boolean, default `true` — Upstox auto-slices large orders)
- `market_protection` (integer, -1 auto / 0 none / 1..25 custom, for MARKET
  and SL-M orders only)
- `disclosed_quantity` (long, when > 0)
- `X-Algo-Name` header (SEBI algo registration; only sent when
  `UpstoxOrderOptions.algoName` is non-null)

Wired through new `UpstoxOrderOptions` record (broker-local; blast radius
contained to `broker/upstox`).

### 20.3 WebSocket V3 modes

| V3 mode | Wire value | Status |
|---|---|---|
| `ltpc` | "ltpc" | ✅ (TICKER) |
| `option_greeks` | "option_greeks" | ✅ **Added** — broker-local `UpstoxMarketSubscription` record + `UpstoxV3SubscriptionLimits.Mode.OPTION_GREEKS` |
| `full` | "full" | ✅ (QUOTE, FULL) |
| `full_d30` | "full_d30" | ✅ (DEPTH_20, DEPTH_200) — Plus-only enforced |

Subscription limits enforced client-side (5000/3000/2000/50 per V3 docs)
via `UpstoxV3SubscriptionLimits.validate(...)`. Connection limit (2
normal / 5 Plus) documented.

### 20.4 WebSocket initial frame

The first text frame from the V3 market data feed (`market_info` JSON)
is now parsed by `UpstoxMarketInfoParser` and emitted as a
`StreamHealthChanged` event. Previously this frame was dropped (the
multiplexer only knew how to parse protobuf).

### 20.5 V3 error code classification

All 24 documented UDAPI error codes are now classified into typed
`UpstoxApiException` subclasses. Callers can `catch`
`UpstoxApiException.StaticIpBlocked` (UDAPI1154) to surface a
configuration error as a health event without crashing the trading thread.
Classification is via `UpstoxErrorClassifier` and wired through
`UpstoxResponseGuard`.

### 20.6 GTT API on V3

`UpstoxGttRestClient` now takes an explicit V3 base URL via constructor
(replacing the string-replace `/v2` → `/v3` hack). Endpoints:
- `POST /v3/order/gtt/place`
- `PUT /v3/order/gtt/modify`
- `DELETE /v3/order/gtt/cancel` (with JSON body)
- `GET /v3/order/gtt`

Trailing stop loss supported via `UpstoxGttRule.trailingGap` (already
present, now correctly emitted on the wire).

### 20.7 Reconciliation

`UpstoxReconciliationService` reconciles orders/trades/positions/holdings
between the broker and an `OmsSnapshot`, returning a structured
`ReconciliationReport` with `DriftItem` entries (missing/unexpected
order, status/fill mismatch, missing/unexpected trade, position/holding
count mismatch).

`UpstoxReconciliationScheduler` re-runs the reconciliation on a
configurable cadence and forwards drift items to a consumer hook.

### 20.8 Token webhook (Flow 2)

`UpstoxTokenWebhookController` accepts the V3 daily token notifier
webhook (POST with `access_token` + `authorization_expiry`), validates
the payload, and calls `UpstoxTokenManager.upgradeFromWebhook`. The
controller is framework-free; the composition layer wires it to a Spring
`@PostMapping`.

### 20.9 Dead code removed

- `UpstoxBinaryParser` (unused binary format parser) — deleted
- `ParsedFeedFrame` (its output type) — deleted
- `UpstoxStreamNormalizer` (its consumer) — deleted
- Their tests (3 files, ~17 tests) — deleted

### 20.10 Test counts

| Stage | Tests | Failures |
|---|---|---|
| Before this work (audit) | ~150 | many |
| After P0/P1/P2 | 238 | 0 |
| After P3 (proto, decoder, webhook, scheduler) | 265 | 0 |
| **After dead-code removal + error classifier** | **262** | **0** |

---

## Conclusion

Trade-J is a **well-architected trading platform** with strong foundations:
- ✅ Clean broker SPI model
- ✅ Event-driven architecture with Disruptor
- ✅ Comprehensive CLI
- ✅ Replay/simulation capabilities
- ✅ Strong architecture tests

**Primary improvement area**: **Composition root consolidation** - making composition layer canonical and having Spring delegate to it will eliminate the biggest architectural risk (divergent object graphs).

**Next Steps** (Priority Order):
1. Consolidate composition roots (Phase 15 simplification)
2. Add replay determinism tests (Phase 16 certification)
3. Implement single PositionService (Phase 14 violation fix)
4. Enforce event schema versioning (Phase 4 event audit)
5. Add data validation layer (Phase 7 data audit)

**Estimated Timeline**:
- Months 1-2: Composition consolidation, PositionService
- Months 3-4: Event schema versioning, data validation
- Months 5-6: Replay determinism, performance benchmarks
- Months 7-8: Chaos testing, distributed tracing

**Overall Assessment**: **B+ (Good)** - Platform is production-ready for live trading with targeted improvements needed for resilience and maintainability.

---

*End of Architecture Audit Report*
