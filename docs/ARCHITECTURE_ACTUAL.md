# Trade-J Architecture & System Documentation

*Generated from actual source code analysis — June 2026*

---

## 1. Executive Summary

### System Purpose

Trade-J is a **multi-broker algorithmic trading platform** for Indian equity and derivatives markets (NSE, BSE, MCX). It provides automated trading, real-time market data processing, historical analytics, and strategy execution through a unified architecture that abstracts over multiple broker APIs.

### Major Capabilities

- **Multi-Broker Support**: Dhan, Upstox, and ICICI Breeze — each with full adapter implementations
- **Real-Time Market Data**: WebSocket-based streaming with LTP, quotes, depth, and order flow
- **Historical Data Management**: Parquet-based storage with DuckDB analytics, incremental sync, gap recovery
- **Strategy Engine**: Graph-based pipeline runtime with pluggable strategy plugins (tick, depth, candle)
- **Order Execution**: Full OMS with risk checks, kill switch, margin enforcement, and reconciliation
- **Options Analytics**: Black-Scholes, Greeks, IV computation, max pain, gamma exposure
- **Scanning & Screening**: Configurable scan profiles with volume, price, and options criteria
- **Backtesting & Replay**: Historical replay engine with candle and tick replay sessions
- **CLI Tool**: Rich command-line interface with 70+ commands for trading, analytics, and operations
- **REST API**: Spring Boot web layer for programmatic access

### Supported Brokers

| Broker | Auth Mode | WebSocket | Options | Bracket Orders | GTT Orders | Slice Orders |
|--------|-----------|-----------|---------|----------------|------------|--------------|
| Dhan | Static / TOTP | ✅ | ✅ | ✅ | ✅ | ✅ |
| Upstox | OAuth / PKCE | ✅ | ✅ | ❌ | ✅ | ✅ |
| ICICI Breeze | Session / TOTP | ✅ | ✅ | Synthetic | Synthetic | Synthetic |

### Supported Workflows

1. **Live Trading**: Real-time market data → Strategy evaluation → Risk checks → Order execution
2. **Historical Analytics**: Data ingestion → Parquet storage → DuckDB queries → Analytics
3. **Backtesting**: Historical data → Replay engine → Strategy execution → Performance metrics
4. **Market Scanning**: Universe definition → Criterion evaluation → Signal generation
5. **Position Management**: Event-sourced positions → PnL calculation → Reconciliation

### Intended Users

- **Quantitative Traders**: Building and deploying automated strategies
- **Portfolio Managers**: Monitoring positions, PnL, and risk in real-time
- **Data Analysts**: Querying historical data and running analytics
- **Operations Teams**: Managing broker connections, health monitoring, and reconciliation

### Current Maturity Level

**Production-capable with active development.** The system has:
- 3 fully implemented broker adapters with comprehensive test suites
- 462+ test files across all modules
- Architecture tests enforcing module boundaries and design patterns
- Health monitoring with Slack/PagerDuty alerting
- Chronicle-based audit logging and dead letter queues

### High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLI / REST API                           │
│  (TradeCli, AnalyticsController, BacktestController, etc.)     │
├─────────────────────────────────────────────────────────────────┤
│                     Composition Layer                           │
│  (FullComposition → BrokerComposition, DataComposition,        │
│   ExecutionComposition, PipelineComposition, ClockComposition)  │
├─────────────────────────────────────────────────────────────────┤
│                     Application Layer (Spring)                   │
│  (41 Configuration classes, Startup strategies, Health)         │
├─────────────────────────────────────────────────────────────────┤
│                     Gateway Layer                                │
│  (BrokerGateway → BrokerHandle → BrokerCallSupport)            │
│  (GatewayEventBridge, GatewayTopicRouter, WebSocketTransport)   │
├─────────────────────────────────────────────────────────────────┤
│                     Pipeline Runtime                             │
│  (DAG Pipeline, Node Registry, ReactorBridge, VirtualClock)     │
├──────────────────────┬──────────────────────────────────────────┤
│   Event Infrastructure│        Trading Engine                    │
│  (DisruptorEventBus, │  (Strategy, Execution, Risk, Scanner)    │
│   HotPath Pipelines, │  (GraphStrategySandbox, OMS, KillSwitch) │
│   SimpleEventBus)    │                                          │
├──────────────────────┴──────────────────────────────────────────┤
│                     Core Domain                                  │
│  (Events, Models, Value Objects, OMS State Machine)             │
├─────────────────────────────────────────────────────────────────┤
│                     Broker Abstraction                            │
│  (IBrokerConnection → CapabilityMap → 18 Port Interfaces)       │
├───────────┬─────────────┬───────────────────────────────────────┤
│  Dhan     │  Upstox     │  ICICI Breeze                         │
│  (60+     │  (45+       │  (40+                                 │
│  files)   │  files)     │  files)                               │
├───────────┴─────────────┴───────────────────────────────────────┤
│                     Data Layer                                   │
│  (Parquet Storage, DuckDB Analytics, Chronicle WAL, FeatureStore│
│   Historical Ingest, Incremental Sync, Gap Recovery)            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Module Inventory

### Module: `core`

- **Gradle ID**: `core`

- **Responsibility**: Domain model, events, value objects, visitor pattern, OMS state machine
- **Package**: `com.tradej.core.domain`
- **Public APIs**: `DomainEvent`, `DomainEventVisitor`, `EventBus`, `EventMetadata`, `OrderStateMachine`, `TradingClock`
- **Dependencies**: None (leaf module)
- **Consumers**: All other modules
- **Status**: Stable, foundational

### Module: `broker-api`

- **Gradle ID**: `broker-api`

- **Responsibility**: Broker port interfaces — the SPI contract for broker implementations
- **Package**: `com.tradej.broker.api`
- **Public APIs**: `IBrokerConnection`, `MarketDataProvider`, `OrderCommand`, `OrderQuery`, `PortfolioProvider`, `OptionsProvider`, `FuturesProvider`, `InstrumentResolver`, `WebSocketMultiplexer`, `MarginProvider`, `SessionRiskProvider`, `ConditionalAlertProvider`, `BracketOrderProvider`, `GttOrderProvider`, `SliceOrderCommand`, `CoverOrderProvider`, `MarketStatusProvider`, `NewsProvider`
- **Dependencies**: `trade-core`
- **Consumers**: `broker-dhan`, `broker-upstox`, `broker-icici`, `broker-gateway`
- **Status**: Stable

### Module: `broker-core`

- **Gradle ID**: `broker-core`

- **Responsibility**: Shared broker infrastructure — rate limiting, routing, observability wrappers
- **Package**: `com.tradej.broker.core`
- **Public APIs**: `LoadBalancedBrokerGateway`, `ObservableMarketDataProvider`, `ObservableOrderCommand`, `MultiBucketRateLimiter`
- **Dependencies**: `trade-broker-api`, `trade-core`
- **Consumers**: Broker implementations, gateway
- **Status**: Stable

### Module: `broker-dhan`

- **Gradle ID**: `broker-dhan`

- **Responsibility**: Dhan broker implementation — 60+ source files covering all adapter, auth, WebSocket, instrument, options, and historical data capabilities
- **Package**: `com.tradej.broker.dhan`
- **Public APIs**: `DhanBrokerConnection`, `DhanTokenManager`, `DhanAuthClient`
- **Dependencies**: `trade-broker-api`, `trade-broker-core`, `trade-core`
- **Consumers**: `composition`, `app`
- **Status**: Production-ready, most complete broker

### Module: `broker-upstox`

- **Gradle ID**: `broker-upstox`

- **Responsibility**: Upstox broker implementation — 45+ files with OAuth/PKCE auth, binary WebSocket parsing
- **Package**: `com.tradej.broker.upstox`
- **Public APIs**: `UpstoxBrokerConnection`, `UpstoxTokenManager`, `UpstoxOAuthClient`
- **Dependencies**: `trade-broker-api`, `trade-broker-core`, `trade-core`
- **Consumers**: `composition`, `app`
- **Status**: Production-ready

### Module: `broker-icici`

- **Gradle ID**: `broker-icici`

- **Responsibility**: ICICI Breeze broker implementation — 40+ files with session-based auth, browser capture
- **Package**: `com.tradej.broker.icici`
- **Public APIs**: `IciciBrokerConnection`, `BreezeTokenManager`, `BreezeSession`
- **Dependencies**: `trade-broker-api`, `trade-broker-core`, `trade-core`
- **Consumers**: `composition`, `app`
- **Status**: Production-ready, limited advanced order support

### Module: `broker-gateway`

- **Gradle ID**: `broker-gateway`

- **Responsibility**: Unified broker access facade — `BrokerGateway` → `BrokerHandle` fluent API
- **Package**: `com.tradej.brokergateway`
- **Public APIs**: `BrokerGateway`, `BrokerHandle`, `BrokerProvider` (SPI), `BrokerRegistry`, `GatewayResult`
- **Dependencies**: `trade-broker-api`
- **Consumers**: `cli`, `gateway`, `app`
- **Status**: Stable

### Module: `gateway`

- **Gradle ID**: `gateway`
- **Responsibility**: WebSocket transport layer — bridges domain events to frontend clients via binary protocol. **Note**: This is distinct from `broker-gateway` (which provides the unified broker facade). The `gateway` module handles frontend-facing WebSocket communication.
- **Package**: `com.tradej.gateway`
- **Public APIs**: `GatewayEventBridge`, `GatewayTopicRouter`, `GatewayWebSocketHandler`, `GatewayBinaryCodec`
- **Dependencies**: `trade-core`, `runtime-disruptor`
- **Consumers**: `app`, frontend
- **Status**: Stable

### Module: `composition`

- **Gradle ID**: `composition`
- **Status**: ✅ DELETED (P3 simplification). Config classes migrated to `core/config/`, `app/config/`, and `broker-gateway/config/`. `BrokerComposition` moved to `broker-gateway/wiring/`. CLI now uses `BrokerComposition.create()` directly.

### Module: `pipeline-core`

- **Gradle ID**: `pipeline-core`

- **Responsibility**: DAG-based pipeline engine — node definitions, graph compilation, execution plans
- **Package**: `com.tradej.pipeline.core`
- **Public APIs**: `PipelineGraph`, `PipelineNode`, `PipelineEdge`, `ExecutionPlan`, `VirtualClock`, `InMemoryStateStore`
- **Dependencies**: `trade-core`
- **Consumers**: `pipeline-runtime`, `pipeline-platform`
- **Status**: Stable

### Module: `pipeline-runtime`

- **Gradle ID**: `pipeline-runtime`

- **Responsibility**: Runtime bridge between pipeline core and application — DAG execution services
- **Package**: `com.tradej.pipeline.service`
- **Public APIs**: `DagPipelineRuntimeService`, `PipelineNodeFactory`, `ReactorBridge`
- **Dependencies**: `pipeline-core`, `trade-core`
- **Consumers**: `app`
- **Status**: Stable

### Module: `trade-pipeline-platform`

- **Gradle ID**: `trade-pipeline-platform`
- **Responsibility**: Pipeline catalog, versioning, templates, and validation
- **Package**: `com.tradej.pipeline.platform`
- **Public APIs**: `PipelineCatalogService`, `PipelineDefinition`, `PipelineTemplate`
- **Dependencies**: `pipeline-core`
- **Consumers**: `app`, `cli`
- **Status**: Stable

### Module: `runtime-disruptor`

- **Gradle ID**: `runtime-disruptor`

- **Responsibility**: LMAX Disruptor-based event bus — high-throughput, low-latency event processing
- **Package**: `com.tradej.disruptor`
- **Public APIs**: `DisruptorEventBus`, `ShardedDisruptorEventBus`, `BrokerScopedEventBus`
- **Dependencies**: LMAX Disruptor, `trade-core`
- **Consumers**: `app`, `hotpath`
- **Status**: Production-ready with deduplication, backpressure, and DLQ

### Module: `runtime-hotpath`

- **Gradle ID**: `runtime-hotpath`

- **Responsibility**: Ultra-low-latency market data and order pipelines bypassing Spring
- **Package**: `com.tradej.hotpath`
- **Public APIs**: `MarketDataPipeline`, `OrderPipeline`, `TokenBucket` (rate limiting)
- **Dependencies**: `runtime-disruptor`, `trade-core`
- **Consumers**: `app` (broker WebSocket handlers)
- **Status**: Production-ready

### Module: `trading-strategy`

- **Gradle ID**: `trading-strategy`

- **Responsibility**: Strategy plugin framework — SPI, graph strategy sandbox, ML inference
- **Package**: `com.tradej.strategy`
- **Public APIs**: `GraphStrategyPlugin`, `StrategyPlugin`, `GraphStrategySandbox`, `MLStrategyPlugin`, `PositionSizer`, `PortfolioEngine`
- **Dependencies**: `trade-core`
- **Consumers**: `app`
- **Status**: Active development

### Module: `trading-execution`

- **Gradle ID**: `trading-execution`

- **Responsibility**: Order lifecycle, risk management, reconciliation, PnL, depth analytics
- **Package**: `com.tradej.execution`
- **Public APIs**: `ExecutionHandler`, `OrderManagementService`, `KillSwitchCoordinator`, `RiskCheckChain`, `PositionRiskHandler`, `OrderReconciler`, `DepthAnalyticsPipeline`
- **Dependencies**: `trade-broker-api`, `trade-core`
- **Consumers**: `app`, `composition`
- **Status**: Production-ready

### Module: `trading-indicators`

- **Gradle ID**: `trading-indicators`

- **Responsibility**: Technical indicator library — EMA, RSI, MACD, ATR, VWAP, Bollinger, Volume Profile
- **Package**: `com.tradej.indicators`
- **Public APIs**: `IndicatorProvider`, `IndicatorRegistry`, `BuiltInIndicatorProvider`
- **Dependencies**: `trade-core`
- **Consumers**: `strategy`, `scanner`
- **Status**: Stable

### Module: `trading-scanner`

- **Gradle ID**: `trading-scanner`

- **Responsibility**: Market scanning engine — configurable criteria, filtering, signal generation
- **Package**: `com.tradej.scanner`
- **Public APIs**: `ScanEngine`, `ScanCriterion`, `ScanProfile`, `ScanHit`
- **Dependencies**: `trade-core`, `trade-broker-api`
- **Consumers**: `app`, `cli`
- **Status**: Active

### Module: `trading-options-analytics`

- **Gradle ID**: `trading-options-analytics`

- **Responsibility**: Options pricing, Greeks, IV, max pain, gamma exposure
- **Package**: `com.tradej.options`
- **Public APIs**: `BlackScholesCalculator`, `ImpliedVolatilityCalculator`, `MaxPainCalculator`, `GreekComputer`
- **Dependencies**: `trade-core`
- **Consumers**: `scanner`, `strategy`
- **Status**: Stable

### Module: `trading-simulation`

- **Gradle ID**: `trading-simulation`
- **Responsibility**: Backtesting matching engine, slippage models, PnL ledger
- **Package**: `com.tradej.simulation`
- **Public APIs**: `MatchingEngine`, `SlippageModel`, `SimulatedPnlLedger`
- **Dependencies**: `trade-core`
- **Consumers**: `replay-engine`
- **Status**: Stable

### Module: `data-persistence`

- **Gradle ID**: `data-persistence`

- **Responsibility**: Storage abstractions — DuckDB, Chronicle WAL, DLQ, event sourcing
- **Package**: `com.tradej.persistence`
- **Public APIs**: `DuckDbEventStore`, `AsyncDuckDbEventStore`, `ChronicleAuditLogWriter`, `ChronicleDeadLetterQueue`, `EventSourcedOrderRepository`, `HistoricalCandleLoader`, `ReplayRunner`
- **Dependencies**: DuckDB, Chronicle, `trade-core`
- **Consumers**: `data-analytics`, `data-historical-ingest`, `app`
- **Status**: Production-ready

### Module: `data-analytics`

- **Gradle ID**: `data-analytics`

- **Responsibility**: DuckDB analytics engine, online metrics (Welford), historical queries
- **Package**: `com.tradej.analytics`
- **Public APIs**: `DuckDbAnalyticsEngine`, `DefaultHistoricalAnalyticsService`, `WelfordOnlineMetrics`, `FederatedHistoricalBarRepository`
- **Dependencies**: `data-persistence`, `trade-core`
- **Consumers**: `app`, `cli`
- **Status**: Stable

### Module: `data-historical-ingest`

- **Gradle ID**: `data-historical-ingest`

- **Responsibility**: Historical data pipeline — download, canonicalize (Parquet), resample, sync, gap recovery
- **Package**: `com.tradej.ingest`
- **Public APIs**: `DownloadJobService`, `IncrementalSyncService`, `BackfillService`, `GapDetector`, `CandleResampler`, `ParquetHistoricalDataStore`, `Nifty500UniverseFetcher`
- **Dependencies**: `data-persistence`, `trade-broker-api`
- **Consumers**: `app`, `cli`
- **Status**: Production-ready

### Module: `data-feature-store`

- **Gradle ID**: `data-feature-store`

- **Responsibility**: Feature storage for ML strategies — DuckDB and in-memory variants
- **Package**: `com.tradej.feature`
- **Public APIs**: `DuckDbFeatureStore`, `InMemoryFeatureStore`, `OptionsAwareFeatureStore`
- **Dependencies**: `data-persistence`
- **Consumers**: `strategy`
- **Status**: Stable

### Module: `replay-engine`

- **Gradle ID**: `replay-engine`

- **Responsibility**: Historical replay and backtesting orchestration
- **Package**: `com.tradej.replay.engine`
- **Public APIs**: `ReplayOrchestrator`, `CandleReplaySession`, `TickReplaySession`, `BacktestExecutionService`, `ReplayController`
- **Dependencies**: `trade-core`, `pipeline-runtime`
- **Consumers**: `app`, `cli`
- **Status**: Stable

### Module: `cli`

- **Gradle ID**: `cli`

- **Responsibility**: Command-line interface — 75 source files, 48 test files, 40+ command classes
- **Package**: `com.tradej.cli`
- **Public APIs**: `TradeCli` (main), `CliContext`, `CliOperations`, `InteractiveShell`, `BrokerSession`
- **Dependencies**: `composition`, `broker-gateway`, all trading modules
- **Consumers**: End users
- **Status**: Production-ready

### Module: `app`

- **Gradle ID**: `app`

- **Responsibility**: Spring Boot application — 41 configuration classes, REST controllers, health monitoring
- **Package**: `com.tradej.app`
- **Public APIs**: `TradingApplication` (main), REST controllers under `/api/v1/*`, `BrokerHealthIndicator`
- **Dependencies**: All modules
- **Consumers**: End users, external systems
- **Status**: Production-ready

### Module: `trade-node-library`

- **Gradle ID**: `trade-node-library`
- **Status**: ✅ DELETED (P3 simplification — zero external references, no active implementations)

### Module: `trade-institutional-scanner`

- **Gradle ID**: `trading-institutional-scanner`
- **Responsibility**: Institutional-grade scanning — sector ranking, candidate selection, feature pipelines
- **Package**: `com.tradej.institutional`
- **Dependencies**: `trade-core`, `trade-broker-api`
- **Consumers**: `app`, `cli`
- **Status**: Active

### Module: `trade-analytics` (pipeline analytics)

- **Gradle ID**: `trade-analytics`
- **Responsibility**: Trade performance analytics — trade records, drawdown reports, return distributions
- **Package**: `com.tradej.pipeline.analytics`
- **Dependencies**: `pipeline-core`
- **Consumers**: `app`
- **Status**: Stable

### Module: `architecture-test`

- **Responsibility**: ArchUnit-based architectural rules enforcement
- **Package**: `com.tradej.architecture`
- **Public APIs**: 7 test classes enforcing boundaries, patterns, Spring-free zones
- **Dependencies**: ArchUnit, all modules
- **Consumers**: CI/CD
- **Status**: Active

### Conditional Modules (enabled with `-Presearch`)

| Module | Gradle ID | Responsibility |
|--------|-----------|----------------|
| `research-core` | `research-core` | Research framework core |
| `research-lab` | `research-lab` | Experimentation environment |
| `research-api` | `research-api` | Research REST API |
| `mcp-server` | `mcp-server` | Model Context Protocol server for AI integration |

### Other Modules

| Module | Gradle ID | Responsibility |
|--------|-----------|----------------|
| `conductor` | — | Orchestration/modernization (docs-only, no Java source) |

---

## 3. Architecture Overview

### Architectural Style

Trade-J employs a **hybrid architecture** combining several patterns:

#### 1. Hexagonal Architecture (Ports & Adapters)

The broker layer is the clearest example. `IBrokerConnection` defines the "port" with 18 capability interfaces. Each broker (Dhan, Upstox, ICICI) provides "adapters" implementing these ports. The `CapabilityMap` enables runtime capability resolution — consumers request capabilities by class, and the connection returns the appropriate implementation or throws `UnsupportedOperationException`.

**Why**: This allows adding new brokers without changing consumer code. The `BrokerProvider` SPI enables discovery via `ServiceLoader`.

#### 2. Event-Driven Architecture

The core communication mechanism is a typed event system:
- **Events**: Java `record` types implementing `DomainEvent`
- **Visitor Pattern**: `DomainEventVisitor` with 4 sub-visitors for type-safe dispatch
- **Event Bus**: `DisruptorEventBus` (production) or `SimpleEventBus` (testing)
- **Event Taxonomy**: 40+ event types across Market Data, Order Lifecycle, Trading, and System categories

**Why**: Decouples producers from consumers, enables replay, supports backtesting with `VirtualClock`, and allows concurrent processing via LMAX Disruptor.

#### 3. Plugin Architecture

Strategies are loaded via Java `ServiceLoader`:
- `StrategyPlugin` (legacy, candle-based)
- `GraphStrategyPlugin` (modern, multi-event)
- `IndicatorProvider` (technical indicators)
- `BrokerProvider` (broker implementations)

**Why**: Enables adding new strategies, indicators, and brokers without modifying core code.

#### 4. Graph/DAG Pipeline Architecture

The pipeline system defines computation as directed acyclic graphs:
- Nodes with typed input/output ports
- Graph compilation and execution plans
- Shared state store for inter-node communication
- ReactorBridge for async offloading

**Why**: Makes data flows explicit, testable, and composable. Supports both live and replay modes through `VirtualClock`.

#### 5. Composition Root Pattern

The `composition` module creates the entire object graph without Spring:
- `FullComposition` aggregates all sub-compositions
- `BrokerComposition`, `DataComposition`, `ExecutionComposition`, `PipelineComposition`, `ClockComposition`
- Used by CLI and can be used by any non-Spring entry point

**Why**: Enables the CLI to use the same code as the Spring app without Spring overhead. Provides a clean separation between object creation and object use.

### Current Architecture Diagram

```
                         ┌──────────────┐
                         │   Frontend   │
                         │  (Browser)   │
                         └──────┬───────┘
                                │ WebSocket
                         ┌──────┴───────┐
                         │   Gateway    │
                         │  WebSocket   │
                         │  Transport   │
                         └──────┬───────┘
                                │
                    ┌───────────┴───────────┐
                    │   GatewayEventBridge   │
                    │   GatewayTopicRouter   │
                    └───────────┬───────────┘
                                │
    ┌───────────────────────────┼───────────────────────────┐
    │                           │                           │
┌───┴────┐              ┌──────┴──────┐             ┌──────┴──────┐
│  CLI   │              │  REST API   │             │  WebSocket  │
│(TradeCli)│            │(Controllers)│             │  Clients    │
└───┬────┘              └──────┬──────┘             └──────┬──────┘
    │                          │                           │
    └──────────┬───────────────┴───────────┬───────────────┘
               │                           │
        ┌──────┴──────┐            ┌───────┴────────┐
        │ Composition │            │ Spring Config  │
        │    Layer    │            │  (41 classes)  │
        └──────┬──────┘            └───────┬────────┘
               │                           │
        ┌──────┴───────────────────────────┴──────┐
        │              BrokerGateway              │
        │    (BrokerHandle → BrokerCallSupport)   │
        └──────┬───────────────────────────┬──────┘
               │                           │
    ┌──────────┴───────────────────────────┴──────────┐
    │              IBrokerConnection                   │
    │         (CapabilityMap → 18 Ports)               │
    ├─────────────┬───────────────┬───────────────────┤
    │   Dhan      │   Upstox      │   ICICI Breeze    │
    │  (60+ src)  │  (45+ src)    │  (40+ src)        │
    └──────┬──────┴───────┬───────┴──────────┬────────┘
           │              │                  │
    ┌──────┴──────────────┴──────────────────┴──────┐
    │              LMAX Disruptor Event Bus          │
    │  (8192 ring buffer, sharded, broker-scoped)   │
    └──────┬───────────────────────────┬────────────┘
           │                           │
    ┌──────┴──────┐            ┌───────┴────────┐
    │  HotPath    │            │  Pipeline      │
    │  Pipelines  │            │  Runtime       │
    │(Market/Order)│           │  (DAG Engine)  │
    └──────┬──────┘            └───────┬────────┘
           │                           │
    ┌──────┴───────────────────────────┴──────┐
    │           Strategy Layer                  │
    │  (GraphStrategySandbox → Plugins)        │
    │  (Indicators, ML Inference, PositionSizer)│
    └──────┬───────────────────────────┬──────┘
           │                           │
    ┌──────┴──────┐            ┌───────┴────────┐
    │  Execution  │            │   Scanner      │
    │  (OMS, Risk,│            │  (Criteria,    │
    │   KillSw)   │            │   Screening)   │
    └──────┬──────┘            └───────┬────────┘
           │                           │
    ┌──────┴───────────────────────────┴──────┐
    │           Data Layer                      │
    │  Parquet │ DuckDB │ Chronicle │ Feature   │
    └─────────────────────────────────────────┘
```

---

## 4. Composition Layer

### Purpose

The composition layer creates and wires the entire object graph **without Spring dependency injection**. It serves two entry points:
1. **CLI** (`TradeCli`) — uses `FullComposition` directly
2. **Spring Boot** (`app`) — Spring's `@Configuration` classes delegate to composition or create equivalent beans

### Responsibilities

1. **Broker Creation**: Select and instantiate the correct broker based on `BrokerProfile.BrokerType`
2. **Data Infrastructure**: Initialize Chronicle (audit, DLQ) and DuckDB (event store, scan store, pipeline store)
3. **Execution Infrastructure**: Wire risk handlers, OMS, kill switch, position tracking
4. **Pipeline Infrastructure**: Create clock, reactor bridge, node registry, runtime services
5. **Clock Management**: Configure `VirtualClock` for LIVE or REPLAY mode

### Runtime Creation Flow

```
User Input (profile config)
        │
        ▼
┌─────────────────┐
│   ConfigLoader   │ ← Reads properties files
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  BrokerProfile   │ ← Validates broker config
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│  FullComposition     │ ← Master composition root
│  createFull(...)     │
└────────┬────────────┘
         │
    ┌────┼────────────────┬──────────────────┐
    │    │                │                  │
    ▼    ▼                ▼                  ▼
┌──────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│Broker│ │  Data    │ │Execution │ │Pipeline  │
│Comp. │ │  Comp.   │ │  Comp.   │ │  Comp.   │
└──┬───┘ └────┬─────┘ └────┬─────┘ └────┬─────┘
   │          │            │            │
   ▼          ▼            ▼            ▼
IBroker    DuckDb       RiskCheck    DAGRuntime
Connection Store        Chain        Service
```

### What May Be Created Here

- `IBrokerConnection` instances (Dhan, Upstox, ICICI)
- `DuckDbConnectionPool`, `DuckDbEventStore`, `AsyncDuckDbEventStore`
- `ChronicleAuditLogWriter`, `ChronicleDeadLetterQueue`
- `PositionRiskHandler`, `KillSwitchCoordinator`, `MarginEnforcementHandler`
- `VirtualClock`, `ReactorBridge`, `NodeRegistry`
- `DagPipelineRuntimeService`, `PipelineNodeFactory`

### What Must NOT Be Created Here

- Spring beans (use `@Configuration` classes instead)
- HTTP controllers or REST endpoints
- WebSocket handlers
- Health indicators or metrics

---

## 5. Gateway Architecture

### Purpose

The gateway provides a **unified, telemetry-instrumented API** for accessing any broker through a single fluent interface (`BrokerHandle`), regardless of the underlying broker implementation.

### Responsibilities

1. **Broker Registry**: Maintain a map of named broker connections
2. **Fluent API**: Provide typed methods for all broker operations
3. **Telemetry**: Wrap every call with latency tracking and `GatewayResult` metadata
4. **Instrument Resolution**: Translate symbols to broker-specific IDs
5. **WebSocket Transport**: Bridge domain events to frontend clients via binary protocol

### Key Classes

#### `BrokerGateway` (Interface)
```java
BrokerGateway gateway = BrokerGateway.from(composition);
BrokerHandle dhan = gateway.broker("dhan");
BrokerHandle upstox = gateway.broker("upstox");
```

#### `BrokerHandle` (Fluent API)
```java
GatewayResult<Quote> quote = dhan.quote("RELIANCE");
GatewayResult<Position[]> positions = dhan.positions();
GatewayResult<OrderResult> order = dhan.placeOrder(...);
```

#### `BrokerCallSupport` (Internal)
- Wraps every call with `System.nanoTime()` timing
- Returns `GatewayResult<T>` with `data()`, `latencyMs()`, `source()`, `timestamp()`
- Handles instrument resolution and segment parsing

### Capability Matrix

| Capability | Dhan | Upstox | ICICI |
|-----------|------|--------|-------|
| MarketDataProvider | ✅ | ✅ | ✅ |
| OrderCommand | ✅ | ✅ | ✅ |
| OrderQuery | ✅ | ✅ | ✅ |
| PortfolioProvider | ✅ | ✅ | ✅ |
| MarginProvider | ✅ | ✅ | ✅ |
| OptionsProvider | ✅ | ✅ | ✅ |
| FuturesProvider | ✅ | ✅ | ✅ |
| InstrumentResolver | ✅ | ✅ | ✅ |
| WebSocketMultiplexer | ✅ | ✅ | ✅ |
| BracketOrderProvider | ✅ | ❌ | Synthetic* |
| GttOrderProvider | ✅ | ✅ | Synthetic* |
| SliceOrderCommand | ✅ | ✅ | Synthetic* |
| CoverOrderProvider | ✅ | ✅ | ✅ |
| SessionRiskProvider | ✅ | ❌ | ❌ |
| ConditionalAlertProvider | ✅ | ✅ | ❌ |
| MarketStatusProvider | ✅ | ✅ | ✅ |
| NewsProvider | ❌ | ✅ | ❌ |

**Notes:**
- **Synthetic**: ICICI's `BrokerDescriptor` explicitly marks bracket/GTT/slice/cover orders, kill switch, and MARKET orders as **not supported**. The adapter classes (`IciciBracketOrderAdapter`, `IciciGttOrderAdapter`, etc.) exist but provide only limited synthetic implementations.
- **❌ (SessionRiskProvider/ConditionalAlertProvider for ICICI)**: `IciciBrokerConnection` throws `UnsupportedOperationException` for `sessionRisk()` and `alerts()`.

### Extension Model

To add a new capability to the gateway:
1. Define the port interface in `broker-api`
2. Implement it in each broker module
3. Register it in the broker's `CapabilityMap`
4. Add a delegation method to `BrokerHandle`

---

## 6. Broker Architecture

### Dhan

**Files**: 60+ source files across 12 packages

#### Authentication
- **Modes**: `STATIC` (config-provided token) or `TOTP` (dynamic generation)
- **Token Management**: `DhanTokenManager` implements `TokenLifecycleService`
  - `ensureValid()` — validates token, recommends refresh if within buffer
  - Thread-safe refresh via `ReentrantLock`
  - State persistence via `DhanTokenStateStore`
- **Auth Client**: `DhanAuthClient` calls `/generateToken` (TOTP), `/renewToken`, `/profile` (validation)

#### WebSocket
- **Binary Protocol**: Custom binary parser for market feeds
- **Multiplexer**: `DhanWebSocketMultiplexer` manages market data and order streams
- **Health Monitor**: Tracks connection state and auto-reconnects

#### Rate Limiting
- **Multi-Bucket Rate Limiter**: Configurable per-endpoint rate limits
- **Retry Executor**: `DhanRetryExecutor` with exponential backoff

#### Key Adapters
- `DhanMarketDataProvider` — LTP, quotes, depth, candles
- `DhanOrderCommandAdapter` — place, modify, cancel
- `DhanOptionsProvider` — option chains, Greeks
- `DhanBracketOrderAdapter`, `DhanGttOrderAdapter`, `DhanSliceOrderAdapter`

### Upstox

**Files**: 45+ source files across 15 packages

#### Authentication
- **Modes**: OAuth 2.0 with PKCE or Static token
- **Token Manager**: `UpstoxTokenManager` with JWT expiry detection
- **OAuth Flow**: `UpstoxOAuthClient` → `UpstoxRedirectServer` → callback parsing
- **Special**: `UpstoxAnalyticsTokenHolder` for data services API

#### WebSocket
- **Binary Parser**: `UpstoxBinaryParser` for proprietary feed format
- **Feed Authorizer**: `UpstoxFeedAuthorizer` for authenticated feeds
- **Portfolio Stream**: `UpstoxPortfolioStreamParser` for position updates

#### Key Adapters
- `UpstoxMarketDataProvider` — market data with price parsing
- `UpstoxOrderCommandAdapter` — order management
- `UpstoxOptionsProvider` — option chains including expired options
- `UpstoxNewsProvider` — market news (unique to Upstox)
- `UpstoxHistoricalDataService` — candle data retrieval

### ICICI Breeze

**Files**: 40+ source files across 12 packages

#### Authentication
- **Modes**: Session-based or TOTP
- **Session Capture**: `BreezeBrowserSessionCapture` — headless browser for session tokens
- **API Session**: `BreezeApiSessionRedirectServer` for OAuth-like flow
- **Token Manager**: `BreezeTokenManager` with state persistence

#### WebSocket
- **Multiplexer**: `BreezeWebSocketMultiplexer` for market feeds
- **Health Monitor**: `BreezeWebSocketHealthMonitor`

#### Limitations (per `BrokerDescriptor`)
- No MARKET orders
- No kill switch integration
- No square-off batching
- No bracket/GTT/slice orders (synthetic adapters provide limited support)

---

## 7. Event Architecture

### Events

All events implement `DomainEvent` (a Java `record` with `EventMetadata`). The system defines **40+ event types** organized into 4 categories:

#### MarketDataVisitor (8 events)
| Event | Description |
|-------|-------------|
| `MarketTickEvent` | Real-time tick with LTP, volume |
| `DepthUpdateEvent` | Order book depth with bids/asks |
| `CandleClosed` | Completed candle (OHLCV) |
| `CandleDeveloping` | In-progress candle update |
| `OptionChainUpdated` | Full option chain snapshot |
| `GreeksComputed` | Computed option Greeks |
| `GammaExposureComputed` | Portfolio gamma exposure |
| `MaxPainComputed` | Max pain strike calculation |

#### OrderLifecycleVisitor (8 events)
| Event | Description |
|-------|-------------|
| `OrderAccepted` | Broker accepted order |
| `OrderFilled` | Order fully filled |
| `OrderRejected` | Broker rejected order |
| `OrderCancelled` | Order cancelled |
| `OrderModified` | Order modified |
| `OrderPartiallyFilled` | Partial fill |
| `OrderFullyFilled` | All legs filled |
| `OrderUpdateEvent` | Generic order update |

#### TradingVisitor (14 events)
| Event | Description |
|-------|-------------|
| `SignalGenerated` | Strategy produced signal |
| `SignalPendingExecution` | Signal queued for execution |
| `SignalSuppressed` | Signal blocked by risk |
| `TradeOpened` | New trade position |
| `TradeClosed` | Position closed |
| `TradeUpdated` | Position updated |
| `KillSwitchEngaged` | Emergency stop activated |
| `UnifiedKillSwitchEngaged` | Cross-broker kill switch |
| `UnifiedKillSwitchDisengaged` | Kill switch released |
| `UnrealizedPnLUpdated` | PnL mark-to-market |
| `PnlUpdatedEvent` | Realized PnL update |
| `PositionUpdateEvent` | Position state change |
| `PositionMismatch` | Reconciliation mismatch |
| `TradeExecutionEvent` | Execution detail |

#### SystemVisitor (8 events)
| Event | Description |
|-------|-------------|
| `ReconciliationHaltRequired` | Halt for reconciliation |
| `StreamHealthChanged` | WebSocket health change |
| `EventBusBackpressure` | Event bus overloaded |
| `BrokerAdapterError` | Broker adapter failure |
| `StrategyError` | Strategy execution error |
| `ScanHitProduced` | Scanner found match |
| `ScanResultsPublished` | Scan batch complete |
| `ReplayTimeChangedEvent` | Replay clock advanced |

### Event Bus

#### `DisruptorEventBus` (Production)
- **Ring Buffer**: 8192 capacity, `ProducerType.MULTI`
- **Pipeline**: Ring Buffer → GraphStrategyStage → AsyncDispatch
- **Re-entrancy Guard**: `ThreadLocal<Boolean>` prevents deadlocks from re-entrant publishes
- **Downstream Queue**: `ArrayBlockingQueue(4096)` for re-entrant events
- **Dead Letter Queue**: Failed events stored for later analysis
- **Deduplication**: `ScheduledExecutorService` prunes seen events periodically

#### `SimpleEventBus` (Testing)
- Thread-safe `ConcurrentHashMap` + `CopyOnWriteArrayList`
- Synchronous dispatch on caller thread
- Supports catch-all subscribers on `DomainEvent.class`

### Event Flow

```
Producer (WebSocket/Strategy/Scanner)
    │
    ▼
DisruptorEventBus.publish(DomainEvent)
    │
    ├── Ring Buffer (8192 slots)
    │       │
    │       ▼
    │   GraphStrategyDisruptorHandler
    │       │ (runs GraphStrategySandbox)
    │       ▼
    │   AsyncDispatchHandler
    │       │ (routes to subscribers)
    │       ▼
    │   DomainEventHandler<T>.onEvent()
    │
    └── [If re-entrant] → downstreamQueue → DeadLetterQueue (if full)
```

---

## 8. Data Flow Documentation

### Historical Data Flow

```
Broker REST API
    │
    ▼
DownloadJobService
    │ (EquityHistoricalDownloadPlanner / RollingOptionDownloadPlanner)
    ▼
ParquetWriteService
    │ (canonical Parquet format)
    ▼
ParquetHistoricalDataStore
    │ (Hive-partitioned: segment=NSE_EQ/symbol=RELIANCE/data_0.parquet)
    ▼
DuckDbHistoricalWarehouse
    │ (registers Parquet files in DuckDB for SQL queries)
    ▼
DuckDbAnalyticsEngine
    │ (SQL queries, aggregation, analytics)
    ▼
REST API / CLI
```

### Market Data Flow

```
Broker WebSocket
    │
    ▼
MarketDataPipeline (HotPath)
    │ (TokenBucket rate limiting, EMA tick rate tracking)
    ▼
DisruptorEventBus
    │
    ├── GraphStrategySandbox
    │       │ (strategy evaluation)
    │       ▼
    │   SignalGenerated event
    │
    ├── DepthAnalyticsPipeline
    │       │ (imbalance, absorption, iceberg detection)
    │       ▼
    │   DepthAnalyticsEvents
    │
    ├── CandleAggregationService
    │       │ (tick → candle aggregation)
    │       ▼
    │   CandleClosed event
    │
    └── GatewayEventBridge
            │ (binary encoding)
            ▼
        Frontend WebSocket
```

### Option Chain Flow

```
OptionsProvider.optionChain(symbol, expiry)
    │
    ▼
Broker-specific REST client
    │ (DhanOptionChainClient / UpstoxOptionChainRestClient / BreezeOptionChainRestClient)
    ▼
Raw option chain data
    │
    ▼
DomainMapper → OptionChainSnapshot
    │
    ├── OptionChainUpdated event
    │
    └── GreekComputer
            │ (Black-Scholes IV computation)
            ▼
        GreeksComputed event
```

### Scanner Flow

```
ScanProfile (JSON config)
    │
    ▼
ScanEngine
    │
    ├── FetchService → MarketDataProvider (quotes, depth)
    │
    ├── Criterion evaluation:
    │   ├── VolumeSpikeCriterion
    │   ├── PriceChangeCriterion
    │   ├── OptionLiquidityCriterion
    │   └── ...
    │
    ▼
ScanHit / ScanHitProduced event
    │
    ▼
ScanResultsPublished event → GatewayEventBridge → Frontend
```

### Replay Flow

```
ReplayOrchestrator.startCandleReplay(symbol, interval, speed)
    │
    ▼
VirtualClock.switchToReplay()
    │
    ▼
CandleReplaySession
    │ (ScheduledExecutorService, play/pause/step/stop)
    ▼
HistoricalCandleLoader.loadCandles()
    │ (from Parquet via DuckDB)
    ▼
CandleClosed events emitted at replay timestamps
    │
    ▼
ClockSyncedEventBus
    │ (advances VirtualClock to match event timestamps)
    ▼
All subscribers receive events as if live
```

---

## 9. Data Storage Architecture

### Storage Technologies

| Technology | Usage | Path |
|-----------|-------|------|
| **Parquet** | Historical candle data, option bars | `data/historical/bars/segment=NSE_EQ/symbol=RELIANCE/data_0.parquet` |
| **DuckDB** | Event store, analytics queries, scan results, pipeline graphs | `runtime-dev/duckdb/` |
| **Chronicle Queue** | Audit log, dead letter queue, event WAL | `runtime-dev/chronicle/` |
| **In-Memory** | Feature store (alternative mode), state store | JVM heap |

### Partitioning Strategy

**Parquet** (Hive-style partitioning):
```
data/historical/bars/
├── segment=NSE_EQ/
│   ├── symbol=RELIANCE/
│   │   └── data_0.parquet
│   ├── symbol=TCS/
│   │   └── data_0.parquet
├── segment=NSE_FNO/
│   └── ...
```

**DuckDB** tables:
- `events` — domain event store
- `scans` — scan results
- `pipeline_graphs` — pipeline definitions
- Historical data accessed via `read_parquet()` with glob patterns

### Sync Strategy

1. **Incremental Sync**: `IncrementalSyncService` compares last known date with current date
2. **Gap Detection**: `GapDetector` identifies missing date ranges
3. **Backfill**: `BackfillService` fills detected gaps
4. **Universe Refresh**: `Nifty500UniverseFetcher` updates constituent list

### Retention Strategy

- Parquet files are partitioned by segment/symbol, allowing manual cleanup
- DuckDB event store can be pruned via SQL
- Chronicle queues have built-in TTL support
- No automated retention policies currently enforced

---

## 10. Analytics & Market Utils

### Indicators (`trading-indicators`)

The indicator system uses an SPI pattern:

```
IndicatorProvider (SPI)
    │
    ├── BuiltInIndicatorProvider
    │   ├── EMA (Exponential Moving Average)
    │   ├── RSI (Relative Strength Index)
    │   ├── MACD (Moving Average Convergence Divergence)
    │   ├── ATR (Average True Range)
    │   ├── Bollinger Bands
    │   ├── Bollinger Squeeze
    │   ├── VWAP (Volume Weighted Average Price)
    │   ├── Volume Profile
    │   └── Heikin Ashi
    │
    └── CustomIndicatorProvider (extensible)
```

### Analytics Engine (`data-analytics`)

- **`DuckDbAnalyticsEngine`**: Executes SQL queries against DuckDB for historical analytics
- **`WelfordOnlineMetrics`**: Computes running mean, variance, and standard deviation in O(1) per update
- **`FederatedHistoricalBarRepository`**: Aggregates data from multiple sources (broker, local Parquet, DuckDB)
- **`AnalyticsSqlGuard`**: Validates and sanitizes SQL queries for safety

### Options Analytics (`trading-options-analytics`)

| Component | Purpose |
|-----------|---------|
| `BlackScholesCalculator` | Theoretical option pricing |
| `ImpliedVolatilityCalculator` | Newton-Raphson IV computation |
| `GreekComputer` | Delta, Gamma, Theta, Vega, Rho |
| `MaxPainCalculator` | Max pain strike identification |
| `GammaExposureCalculator` | Portfolio-level gamma exposure |
| `OptionStrikeResolver` | Strike price selection logic |

### How to Add New Analytics

1. **Indicator**: Implement `IndicatorProvider` interface, register via `ServiceLoader`
2. **Options Calculator**: Add class in `trading-options-analytics`, expose via `OptionsProvider`
3. **SQL Analytics**: Write SQL query, expose via `DuckDbAnalyticsEngine.query()`
4. **Feature**: Implement `FeatureNode` in `data-feature-store`

---

## 11. CLI Architecture

### Command Structure

The CLI is organized into 40+ command classes under `com.tradej.cli.command`:

| Command | Responsibility |
|---------|---------------|
| `CliBrokerCommands` | Broker status, connections |
| `CliBrokerGatewayCommands` | Gateway operations |
| `CliMarketCommands` | Market data, quotes |
| `CliTradingCommands` | Order placement, positions |
| `CliPortfolioCommands` | Portfolio, holdings |
| `CliScanCommands` | Market scanning |
| `CliHistoricalCommands` | Historical data queries |
| `CliDataCommands` | Data management |
| `CliDownloadCommands` | Historical data download |
| `CliAnalyticsCommands` | Analytics queries |
| `CliBacktestCommands` | Backtesting |
| `CliReplayCommands` | Historical replay |
| `CliEventsCommand` | Event inspection |
| `CliIndicatorsCommand` | Indicator computation |
| `CliOptionsAnalyticsCommands` | Options analytics |
| `CliDoctorCommand` | System diagnostics |
| `CliCertifyCommand` | Broker certification |
| `CliRegressionCommand` | Regression testing |
| `CliModulesCommand` | Module dependency graph |
| `CliArchitectureCommand` | Architecture analysis |
| `CliDashboardCommand` | Real-time dashboard |
| `CliMonitorCommand` | Live monitoring |

### Execution Flow

```
TradeCli.main(args)
    │
    ▼
CliContext (shared state: config, broker sessions, output formatter)
    │
    ▼
InteractiveShell (JLine3-based REPL)
    │ (command parsing, history, tab completion, aliases, macros)
    ▼
CliCommand.execute(args)
    │
    ├── Direct mode: uses BrokerSession (standalone, no Spring)
    │   └── BrokerSessionFactory → DhanBrokerSession / UpstoxBrokerSession / IciciBrokerSession
    │
    └── Attached mode: connects to running Spring Boot app via HTTP
        └── AttachClient → REST API
```

### CLI ↔ Composition Usage

The CLI creates a lightweight object graph via `FullComposition`:
```java
FullComposition composition = FullComposition.createFull(brokerProfile, storageProfile, riskProfile);
BrokerHandle broker = BrokerGateway.from(composition).broker("dhan");
```

This avoids Spring overhead while reusing the same broker, data, and execution code.

---

## 12. Spring Boot Architecture

### Configuration

The application uses **41 configuration classes** under `com.tradej.app.config`:

| Category | Classes | Purpose |
|----------|---------|---------|
| Broker | `DhanBrokerConfiguration`, `IciciConfiguration`, `UpstoxConfiguration` | Broker-specific Spring beans |
| Core | `EventBusConfiguration`, `RuntimeConfiguration`, `TimeConfiguration` | Core infrastructure |
| Trading | `StrategyConfiguration`, `RiskConfiguration`, `ScanConfiguration` | Trading logic |
| Data | `PersistenceConfiguration`, `AnalyticsConfiguration`, `FeatureStoreConfiguration` | Data layer |
| Gateway | `GatewayConfiguration`, `GatewayWebSocketConfig`, `GatewayBeansConfiguration` | WebSocket transport |
| Health | `AlertConfiguration` | Slack, PagerDuty, Webhook alerts |
| Pipeline | `PipelineConfiguration` | DAG pipeline wiring |
| Ops | `SchedulingConfiguration`, `TracingConfiguration`, `MicrometerConfiguration` | Operations |

### Bean Creation Strategy

The Spring configuration classes create beans that mirror the composition layer:
- `BrokerConfiguration` → port beans from `IBrokerConnection`
- `PersistenceConfiguration` → DuckDB and Chronicle beans
- `ExecutionBeansConfiguration` → risk handlers, OMS
- `PipelineConfiguration` → DAG runtime, node registry

### Profiles

| Profile | Purpose |
|---------|---------|
| `dev` | Dhan sandbox, local storage |
| `dev-live` | Dhan production with local properties |
| `upstox-dev` | Upstox sandbox |
| `upstox-prod` | Upstox production |
| `icici-dev` | ICICI sandbox |

### Health Checks

- **`BrokerHealthIndicator`**: Reports `UP` only if WebSocket connected AND circuit breaker closed
- Monitors: broker type, WS status, circuit breaker, active subscriptions, error counts
- Triggers critical alerts via `AlertManager` on failure

---

## 13. Testing Strategy

### Test Distribution

| Module | Test Files | Focus |
|--------|-----------|-------|
| `app` | 122 | Integration, component, contract tests |
| `broker` (all) | 112 | Broker adapters, auth, WebSocket |
| `trading` (all) | 79 | Strategy, execution, indicators, scanner |
| `cli` | 48 | Command execution, output formatting |
| `data` | 27 | Persistence, analytics, ingest |
| `pipeline` | 24 | DAG compilation, execution |
| `core` | 21 | Domain events, OMS state machine |
| `runtime` | 16 | Disruptor, hotpath stress tests |
| `gateway` | 9 | Event bridge, topic router |
| `replay` | 3 | Candle/tick replay sessions |
| `composition` | 1 | FullComposition wiring |
| **Total** | **462+** | |

### Testing Pyramid

```
        ╱╲
       ╱  ╲
      ╱ E2E╲         Broker certification tests
     ╱──────╲        (live broker connectivity)
    ╱  Live  ╲       Integration tests with real broker sandboxes
   ╱──────────╲
  ╱ Integration╲     Component tests, contract tests
 ╱──────────────╲   (mocked brokers, real Spring context)
╱   Component    ╲
╱──────────────────╲
╱     Unit Tests     ╲  Pure logic tests
╱────────────────────╲ (indicators, mappers, parsers, state machines)
```

### Test Types

1. **Unit Tests**: Pure logic — indicators, mappers, parsers, state machines, algorithms
2. **Component Tests**: Spring context with mocked external dependencies
3. **Contract Tests**: Verify broker API contracts (e.g., `InstrumentResolverContractTest`)
4. **Integration Tests**: Real broker sandbox connections (e.g., `DhanOrderLifecycleIntegrationTest`)
5. **Architecture Tests**: ArchUnit rules — module boundaries, Spring-free zones, design patterns
6. **Stress Tests**: Concurrency, backpressure, deduplication (e.g., `DisruptorEventBusStressTest`)
7. **Certification Tests**: Live broker feature verification

### Architecture Tests (7 classes)

| Test | Enforces |
|------|----------|
| `BrokerIsolationArchitectureTest` | Broker modules don't cross-reference each other |
| `ModuleBoundaryArchitectureTest` | Strict module dependency rules |
| `SpringFreeArchitectureTest` | Core modules don't import Spring |
| `ProfileIsolationArchitectureTest` | Profile-specific beans are isolated |
| `DataAccessArchitectureTest` | Data access layer encapsulation |
| `DesignPatternArchitectureTest` | Consistent use of patterns |
| `CodeQualityArchitectureTest` | Coding standards |

---

## 14. Observability

### Logging

- **SLF4J** with Logback (configured via `application.yml`)
- **Chronicle Audit Log**: `ChronicleAuditLogWriter` persists all events to Chronicle queue for post-mortem analysis
- **Dead Letter Queue**: `ChronicleDeadLetterQueue` captures failed events

### Metrics

- **Micrometer** with Prometheus endpoint (`/actuator/prometheus`)
- **`DisruptorBusMetrics`**: Ring buffer utilization, throughput, latency
- **`ReactorBridgeMetrics`**: Bridge processing metrics
- **`StageTimings`**: Per-stage pipeline timing
- **Broker latency**: Every `BrokerHandle` call records latency via `GatewayResult`

### Health Checks

- **Spring Actuator**: `/actuator/health` (liveness + readiness probes)
- **`BrokerHealthIndicator`**: WebSocket + circuit breaker + subscription count + error tracking
- **Broker-specific health**: `DhanHealthCheck` verifies connectivity by fetching RELIANCE LTP

### Alerting

- **`AlertManager`** with configurable channels:
  - `LoggingAlertChannel` — SLF4J
  - `WebhookAlertChannel` — HTTP POST
  - `SlackAlertChannel` — Slack webhook
  - `PagerDutyAlertChannel` — PagerDuty API
- **Triggered by**: Health check failures, kill switch events, reconciliation mismatches

### Tracing

- **`TracingConfiguration`** enables distributed tracing
- **`BrokerCallSupport.timed()`** wraps every gateway call with timing
- **Correlation IDs**: `EventMetadata.correlationId` links events across the system

---

## 15. Extension Model

### How to Add a New Broker

1. **Create module**: `broker/<name>/src/main/java/com/tradej/broker/<name>/`
2. **Implement `IBrokerConnection`**: Create `<Name>BrokerConnection` with `CapabilityMap`
3. **Implement port adapters**: One class per port interface (MarketDataProvider, OrderCommand, etc.)
4. **Implement `BrokerProvider`**: Register via `META-INF/services/com.tradej.brokergateway.spi.BrokerProvider`
5. **Add to `BrokerProfile`**: Add `BrokerType` enum value and config record
6. **Add to `BrokerComposition`**: Add creation logic
7. **Add Spring configuration**: Create `@Configuration` class in `app`
8. **Write architecture test**: Verify module isolation

### How to Add a New Strategy

1. **Implement `GraphStrategyPlugin`**:
   ```java
   public class MyStrategy implements GraphStrategyPlugin {
       public Set<Class<? extends DomainEvent>> subscribedEventTypes() {
           return Set.of(MarketTickEvent.class, CandleClosed.class);
       }
       public void onDomainEvent(DomainEvent event) { /* strategy logic */ }
   }
   ```
2. **Register as `@Bean`** in `StrategyConfiguration`
3. **Auto-discovered** by `GraphStrategySandbox` via `ServiceLoader`

### How to Add a New Indicator

1. **Implement `IndicatorProvider`** in `trading-indicators/spi/`
2. **Register** via `META-INF/services/com.tradej.indicators.spi.IndicatorProvider`
3. **Available** to all strategies and scanners

### How to Add a New Scanner Criterion

1. **Implement `ScanCriterion`** in `trading-scanner/criterion/`
2. **Add to scan profile JSON** in `config/scan-profiles.json`
3. **Evaluated** by `ScanEngine` during scan execution

### How to Add a New Pipeline Node

1. **Implement `NodeExecutor`** in `trading/strategy/` or `trading/execution/` (the `trade-node-library` was deleted)
2. **Define `NodeDescriptor`** with ports and properties
3. **Register** in `PipelineConfiguration.nodeRegistry()`
4. **Available** in DAG pipeline composition

### How to Add a New CLI Command

1. **Create class** extending `CliCommandSupport` in `cli/command/`
2. **Annotate** with command metadata
3. **Register** in `CliContext`
4. **Auto-discovered** by `InteractiveShell`

---

## 16. Runtime Flows

### Startup Sequence

```
TradingApplication.main()
    │
    ▼
SpringApplication.run()
    │
    ▼
Configuration classes create beans:
    1. TimeConfiguration → VirtualClock (LIVE mode)
    2. EventBusConfiguration → DisruptorEventBus
    3. BrokerConfiguration → IBrokerConnection (via BrokerComposition)
    4. PersistenceConfiguration → DuckDb, Chronicle
    5. ExecutionBeansConfiguration → Risk handlers, OMS
    6. StrategyConfiguration → GraphStrategySandbox + plugins
    7. PipelineConfiguration → DAG runtime, node registry
    8. GatewayConfiguration → WebSocket transport
    9. AlertConfiguration → AlertManager
    │
    ▼
ApplicationRunner (PipelineGraphBootstrap):
    Load persisted pipeline graphs from DuckDbPipelineGraphStore
    │
    ▼
StartupConfiguration orchestration:
    1. Load instrument catalog
    2. Connect broker WebSocket
    3. Subscribe to market data feeds
    4. Start scan profiles
    5. Start reconciliation scheduler
    6. Start health monitoring
```

### Token Refresh Flow (Dhan)

```
DhanTokenManager.ensureValid()
    │
    ├── Token valid? → return immediately
    │
    └── Token expired or within refresh buffer?
        │
        ▼
    ReentrantLock.lock()
        │
        ├── AuthMode.STATIC? → throw (no refresh possible)
        │
        └── AuthMode.TOTP?
            │
            ▼
        DhanAuthClient.generateViaTotp(clientId, pin, totp)
            │
            ├── Success → DhanTokenStateStore.save(newState)
            │
            └── DhanAuthRejectedException?
                │ (rate limit, invalid TOTP)
                ▼
            Log error, emit BrokerAdapterError event
```

### Order Execution Flow

```
SignalGenerated event
    │
    ▼
ExecutionHandler.onSignal(signal)
    │
    ▼
RiskCheckChain.findRejection(context)
    │ ├── KillSwitchRiskCheck → REJECT if engaged
    │ ├── DailyLossRiskCheck → REJECT if limit exceeded
    │ └── PositionLimitRiskCheck → REJECT if positions full
    │
    ▼
[All checks pass]
    │
    ▼
ExecutionHandler queues command
    │ (partitioned BlockingQueue)
    ▼
ExecutionHandler worker thread:
    │
    ▼
OrderManagementService.placeOrder(request)
    │
    ├── SimulatedOrderService (if simulation mode)
    │
    └── IBrokerConnection.orders().placeOrder(...)
        │
        ▼
    OrderStateMachine.transition(NEW → SUBMITTED)
        │
        ▼
    Broker REST API call
        │
        ▼
    Response → OrderStateMachine.transition(SUBMITTED → ACCEPTED/REJECTED)
        │
        ▼
    OrderAccepted / OrderRejected event emitted
```

---

## 17. Known Limitations

### Current Technical Debt

1. **41 Configuration Classes**: The Spring configuration layer is large and could benefit from consolidation
2. **Deprecated `BrokerConfiguration`**: Still exists alongside newer configurations
3. **Two Event Bus Implementations**: `SimpleEventBus` (test) and `DisruptorEventBus` (prod) with different semantics
4. **Composition vs Spring Duality**: Both mechanisms exist, creating potential for divergence
5. **ICICI Limitations**: No native bracket/GTT/slice orders, no kill switch, no market orders

### Known Risks

1. **No automated retention**: Parquet and DuckDB data grows without cleanup policies
2. **Single-threaded replay**: `CandleReplaySession` uses a single scheduled executor
3. **Chronicle queue growth**: Audit log and DLQ can grow unbounded
4. **No circuit breaker on DuckDB**: Heavy analytics queries could impact hot path

### Known Gaps

1. **No BSE implementation**: Only NSE segments fully supported
2. **No MCX implementation**: Commodity markets not yet integrated
3. **No portfolio-level risk model**: Risk checks are position-level only
4. **No real-time Greeks streaming**: Greeks computed on-demand, not per-tick
5. **Limited frontend**: WebSocket transport exists but console assets are being removed/rebuilt

### Future Work

1. **Multi-broker routing**: Load balance orders across multiple broker accounts
2. **Event sourcing complete migration**: Full event-sourced architecture for positions
3. **ML strategy framework**: Expand beyond threshold-based ML to neural network strategies
4. **Automated backtesting pipeline**: CI/CD integration for strategy validation
5. **Production hardening**: Circuit breakers, graceful degradation, chaos testing

---

## 18. Architecture Decision Records

### ADR-001: Why Gateway Exists

**Decision**: Introduce `BrokerGateway` / `BrokerHandle` as a unified broker access layer.

**Rationale**: Direct use of `IBrokerConnection` requires knowing which broker is active and casting capabilities. `BrokerHandle` provides a fluent, broker-agnostic API with built-in telemetry. This simplifies CLI commands, REST controllers, and gateway transports.

### ADR-002: Why Composition Layer Exists

**Decision**: Create a DI-free composition layer alongside Spring.

**Rationale**: The CLI needs to use broker and data infrastructure without Spring Boot overhead. Composition roots create the same object graph as Spring but without framework dependency. This enables fast CLI startup and testability.

### ADR-003: Why LMAX Disruptor Event Bus

**Decision**: Use LMAX Disruptor for production event bus.

**Rationale**: The trading hot path requires ultra-low-latency, lock-free event processing. Disruptor's ring buffer with pre-allocated objects eliminates GC pressure. The sharded variant provides per-broker isolation. Re-entrancy guards prevent deadlocks from strategy-triggered events.

### ADR-004: Why Parquet + DuckDB

**Decision**: Parquet for storage, DuckDB for analytics.

**Rationale**: Parquet provides columnar, compressed, partitioned storage ideal for time-series financial data. DuckDB provides SQL analytics directly on Parquet files without data movement. This combination gives both efficient storage and powerful querying.

### ADR-005: Why Plugin Architecture for Strategies

**Decision**: Use Java ServiceLoader for strategy discovery.

**Rationale**: Strategies should be independently developed and deployed. ServiceLoader enables adding new strategies by adding JARs to the classpath. The `GraphStrategySandbox` provides isolation (virtual threads, timeouts) to prevent faulty strategies from impacting the system.

### ADR-006: Why Capability-Based Broker Abstraction

**Decision**: Use `CapabilityMap` with class-based lookup instead of interface inheritance.

**Rationale**: Not all brokers support all features. A flat interface would require throwing `UnsupportedOperationException` everywhere. Capability-based lookup allows runtime feature detection and graceful degradation.

---

## 19. Production Readiness

### Operational Procedures

- **Health Monitoring**: Spring Actuator + custom `BrokerHealthIndicator`
- **Alerting**: Slack, PagerDuty, Webhook channels via `AlertManager`
- **Audit**: Chronicle-based event logging for post-mortem analysis
- **Reconciliation**: `OrderReconciler` and `TickReconciler` verify broker vs. local state

### Recovery Procedures

1. **Token Expiry**: `DhanTokenManager` / `UpstoxTokenManager` auto-refresh tokens
2. **WebSocket Disconnect**: Auto-reconnect via broker-specific health monitors
3. **Circuit Breaker**: `TradingCircuitBreaker` blocks orders during broker outages
4. **Kill Switch**: `KillSwitchCoordinator` halts all trading on risk breach
5. **Dead Letter Queue**: Failed events captured for manual inspection

### Deployment Model

- **Single JVM**: Spring Boot application with embedded Tomcat
- **CLI**: Standalone Java application using `FullComposition`
- **Frontend**: Separate WebSocket client (browser-based)

### Scaling Model

- **Single-instance**: Currently designed for single-broker, single-user operation
- **Per-broker sharding**: `BrokerScopedEventBus` isolates events per broker
- **Symbol sharding**: `SymbolShardRouter` distributes load across pipeline partitions

---

## 20. Appendices

### Module Dependency Diagram

```
core (leaf — no dependencies)
    ↑
broker-api
    ↑
broker-core
    ↑
broker-dhan, broker-upstox, broker-icici
    ↑
composition, broker-gateway
    ↑
gateway, cli
    ↑
app (Spring Boot — depends on all modules)
```

### Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `trade.broker-type` | `dhan` | Active broker (dhan/upstox/icici) |
| `trade.broker.client-id` | — | Broker client ID |
| `trade.broker.access-token` | — | Broker access token |
| `trade.broker.environment` | `SANDBOX` | SANDBOX or PRODUCTION |
| `trade.risk.max-daily-loss-paisa` | — | Daily loss limit |
| `trade.risk.max-open-positions` | — | Max concurrent positions |
| `trade.analytics.sql-enabled` | `false` | Enable SQL query endpoint |
| `management.endpoints.web.exposure.include` | `health,info,prometheus` | Actuator endpoints |

### Test Count Summary

| Category | Count |
|----------|-------|
| Unit Tests | ~250 |
| Integration Tests | ~120 |
| Contract Tests | ~30 |
| Architecture Tests | 7 |
| Stress/Concurrency Tests | ~15 |
| Broker Certification | ~40 |
| **Total** | **462+** |
