# Architecture Audit Report — Trade-J Codebase

**Generated:** 2026-06-16
**Scope:** All ~30 Gradle modules, ~300+ Java source files examined

---

## PHASE 1 — Codebase Mapping

### 1.1 Module Registry & Responsibility

| Module Name | Dir | Responsibility |
|---|---|---|
| `core` | `core/` | Domain events, value types (PriceMath, ExchangeSegment, Side), domain models (Order, Candle, Position), OMS state machine, event bus interface, time abstractions (Live/Replay clocks), ID generators, shared service/port interfaces |
| `broker-api` | `broker/api/` | IBrokerConnection facade, all port interfaces (MarketDataProvider, OrderCommand, PortfolioProvider, etc.), SPI/BrokerProvider plugin contract, BrokerCapabilities et al., WebSocket interfaces, auth interfaces |
| `broker-core` | `broker/core/` | Shared broker infrastructure: ReconnectManager, CircuitBreaker, rate limiters (token bucket, multi-bucket), LoadBalancedBrokerGateway, DefaultWebSocketSupervisor, metrics, observability wrappers, chaos testing, historical candle merging |
| `broker-dhan` | `broker/dhan/` | Dhan-specific broker connection; REST + WebSocket clients; depth client; TOTP auth; instrument loader; order, bracket, cover, GTT, alert, slice, margin adapters |
| `broker-upstox` | `broker/upstox/` | Upstox-specific broker connection; protobuf binary feed parser; REST market data; historical mapper; expired options; GTT adapter; analytics-only mode support |
| `broker-icici` | `broker/icici/` | ICICI Breeze-specific connection; Selenium browser-based login; Socket.IO WebSocket feed; Breeze REST adapter; all "not supported" adapters (bracket, cover, GTT, slice) |
| `broker-gateway` | `broker-gateway/` | Multi-broker load balancer, failover, certification framework, PaperBrokerConnection/SimulatedProvider for testing, health probes |
| `pipeline-core` | `pipeline/core/` | Pipeline graph definition (PipelineGraph), compiler (GraphCompiler), runtime (PipelineRuntime, GraphRuntime), node definition, state store, virtual clock, backtest fill model |
| `pipeline-runtime` | `pipeline/runtime/` | DAG pipeline lifecycle management: PipelineRuntimeService, DagPipelineRuntimeService, node factory, Reactor bridge metrics |
| `runtime-disruptor` | `runtime/disruptor/` | LMAX Disruptor event bus implementation (DisruptorEventBus, ShardedDisruptorEventBus), pipeline wiring (DisruptorPipelineBuilder), stage timing, broker-scoped event bus |
| `runtime-hotpath` | `runtime/hotpath/` | High-throughput hot path: MarketDataPipeline, OrderPipeline, DepthUpdateFactory, TokenBucket rate limiter, data integrity validator |
| `trading-strategy` | `trading/strategy/` | Strategy engine: GraphStrategySandbox, strategy plugins, portfolio engine (PortfolioEngine), example strategies (SmaCrossStrategy), YAML-based strategy registry |
| `trading-execution` | `trading/execution/` | Order execution: OrderManagementService, PositionRiskHandler, TradingCircuitBreaker, SubscriptionCoordinator/Manager/Recovery, ExecutionHandler |
| `trading-scanner` | `trading/scanner/` | Profile-based scanning: ScanEngine, ScanCriterion interface, scanner-bridge to strategy signals |
| `trading-institutional-scanner` | `trading/institutional-scanner/` | Institutional scan variants (depends on historical data) |
| `trading-indicators` | `trading/indicators/` | Technical indicator engine: IndicatorEngine, indicator implementations |
| `trading-simulation` | `trading/simulation/` | Order matching: MatchingEngine, SimulatedOrderService, P&L ledger |
| `trading-options-analytics` | `trading/options-analytics/` | Options pricing: BlackScholesCalculator, Greeks, option chain analytics |
| `data-persistence` | `data/persistence/` | Event-sourced persistence: DuckDbEventStore, Chronicle Queue OMS repository, replay query service, reconciliation metrics |
| `data-feature-store` | `data/feature-store/` | Feature store with DuckDB: FeatureStoreImpl, feature definitions for ML models |
| `data-historical-ingest` | `data/historical-ingest/` | Historical data pipeline: Parquet writer/query, canonical bar format, DuckDB warehouse, data gap scanning, ingestion scheduling |
| `data-analytics` | `data/analytics/` | Analytics engine: DuckDB federated query engine, equity/option analytics, SQL endpoint support |
| `app` | `app/` | Spring Boot composition root: controllers (10+), configuration classes (15+), startup orchestrator, health indicators, admin services, all dependency wiring |
| `gateway` | `gateway/` | WebSocket transport layer: Spring WebSocket handler, binary codec, topic router, event bridge, tick batcher |
| `cli` | `cli/` | Operator CLI: picocli interactive/non-interactive commands, standalone broker operations, attach client, analytics commands |
| `replay-engine` | `replay/engine/` | Time-series replay: TickReplaySession, ScenarioRunner, AdminReplayAdapter, replay scenario definitions |
| `mcp-server` | `mcp-server/` | Model Context Protocol server: tool definitions (sync tools, data tools), Spring AI MCP integration |
| `architecture-test` | `architecture-test/` | ArchUnit-based module dependency and layer enforcement tests |
| `test-fixtures` | `test-fixtures/` | Shared test doubles: FakeOrderCommand, NoOpMarketDataProvider, FakeMarketDataProvider, etc. |
| `trade-pipeline-platform` | `pipeline/platform/` | Pipeline catalog, definitions, execution tracking, template service |

### 1.2 Dependency Graph (Module → Direct Module Dependencies)

```
core                          → (none — bottom of the dependency stack)
broker-api                    → core
broker-core                   → broker-api, core
broker-dhan                   → broker-api, broker-core
broker-upstox                 → broker-api, broker-core
broker-icici                  → broker-api, broker-core
broker-gateway                → core, broker-api, broker-core, broker-dhan, broker-upstox, broker-icici
pipeline-core                 → core
pipeline-runtime              → pipeline-core, broker-api
runtime-disruptor             → core, pipeline-core
runtime-hotpath               → core, pipeline-core, runtime-disruptor
trading-strategy              → core, pipeline-core, data-feature-store, trading-indicators, trading-institutional-scanner
trading-execution             → core, pipeline-core, broker-api, broker-core, data-persistence, trading-strategy, trading-simulation
trading-scanner               → core, pipeline-core, broker-api
trading-institutional-scanner → core, data-historical-ingest
trading-indicators            → core
trading-simulation            → core
trading-options-analytics     → core, pipeline-core, broker-api, trade-pipeline-platform
data-persistence              → core, pipeline-core
data-feature-store            → core, pipeline-core, data-persistence
data-historical-ingest        → core, broker-api, broker-dhan, data-persistence
data-analytics                → core, data-historical-ingest
gateway                       → core, broker-api, runtime-hotpath
cli                           → core, broker-api, broker-dhan, broker-upstox, broker-icici, trading-indicators, trading-scanner, trading-simulation, data-persistence, data-historical-ingest, data-analytics, broker-gateway
app                           → all modules except mcp-server
replay-engine                 → core, pipeline-core, broker-api, trading-strategy, trading-execution, trading-simulation, data-persistence, gateway, pipeline-runtime
mcp-server                    → core, replay-engine, data-analytics, data-persistence, data-historical-ingest
```

### 1.3 Shared Types & Constants (Key Distributions)

| Type/Value | Used In (files) | Centralized? |
|---|---|---|
| `"NSE_EQ"` default string | 30+ files: controllers, CLI, config, tests | ✗ Hardcoded everywhere |
| `"IDX_I"` default string | 20+ files: controllers, CLI, tests | ✗ Hardcoded everywhere |
| `ExchangeSegment` enum | 200+ files (tests + production) | ✓ Centralized in core, but string usage bypasses it |
| `PriceMath.toPaisa()` | 15+ files across dhan, upstox, core | ✓ Centralized — good pattern |
| Upstox private `toPaisa(int)` | 1 file (UpstoxBinaryParser) | ✗ Duplicates PriceMath logic |
| `Duration.ofSeconds(5)` timeout | 3 files (Slack, PagerDuty, Webhook channels) | ✗ Duplicated |
| `browserLoginTimeoutSeconds` default "120" | 5+ files: config, test support, CLI | ✗ Duplicated |
| `"RELIANCE"`, `"SBIN"`, `"TCS"`, `"NIFTY"` | 100+ test files | ✗ Hardcoded test symbols |
| `RECONNECT_BASE_DELAY_MS` | Upstox: local constant; Dhan: config-based | ✗ Different in each broker |

---

## PHASE 2 — Shotgun Surgery Detection

### [SMELL-01] **A — Scattered Constants:** Exchange segment default strings `"NSE_EQ"` and `"IDX_I"`
**Files:** `OptionScanController.java` (L38, L70), `SyncStatusController.java` (L119, L133, L152, L168), `AnalyticsController.java` (L55), `StudioController.java` (L43), `ReplayStudioController.java` (L33), `DepthAnalyticsController.java` (L37, L58), `OptionsAnalyticsController.java` (L26, L39), `TradingProperties.java` (L62, L300), `MarketStatusProvider.java` (L45), `DataConfiguration.java` (L598), `TradeCli.java` (~30 occurrences), `CliParquetCommands.java` (6), `CliAnalyticsCommands.java` (5), `CliBrokerGatewayCommands.java` (12), `CliCertifyCommand.java` (L49), `CliComputeCommand.java` (L40), `SymbolController.java` (L92), `BacktestController.java` (L41), `ScanConfiguration.java` (L39)
**Symbol/Value:** `"NSE_EQ"`, `"IDX_I"`, `"NSE_FNO"`
**Blast Radius:** 40+ files
**Impact:** **HIGH** — Changing a segment name (e.g., `NSE_EQ` → `NSE_CM`) would require editing 40+ files

### [SMELL-02] **A — Scattered Constants:** Timeout/Duration constants
**Files:** `BreezeSessionExchange.java` (20s), `BreezeAuthenticatedHttpClient.java` (20s), `BreezeInstrumentLoader.java` (60s), `DhanAuthClient.java` (10s, 15s), `DhanTwentyDepthWebSocketClient.java` (15,000ms), `SlackAlertChannel.java` (5s), `PagerDutyAlertChannel.java` (5s), `WebhookAlertChannel.java` (5s), `GraphStrategySandbox.java` (5,000ms), `AttachClient.java` (5s, 60s, 120s)
**Symbol/Value:** Various `Duration.ofSeconds(N)` and `long` constants
**Blast Radius:** 15+ production files
**Impact:** **MEDIUM** — Each timeout is somewhat context-specific, but 5s is duplicated 3× for alert channels

### [SMELL-03] **A — Scattered Constants:** Reconnect parameters
**Files:** `DhanConnectionSettings.java` (config-driven), `DhanTwentyDepthWebSocketClient.java` (MAX_RECONNECT_ATTEMPTS=5), `UpstoxWebSocketMultiplexer.java` (MAX_RECONNECT_ATTEMPTS=8, RECONNECT_BASE_DELAY_MS, RECONNECT_MAX_DELAY_MS), `DhanWebSocketMultiplexer.java` (inline reconnect logic), `BreezeWebSocketMultiplexer.java` (uses ReconnectManager)
**Symbol/Value:** Max reconnect attempts, base/max delay
**Blast Radius:** 6+ files across 4 broker implementations
**Impact:** **HIGH** — Inconsistent reconnect behavior means tuning one broker doesn't apply to others

### [SMELL-04] **B — Duplicated Logic:** WebSocket reconnect implementations
**Files:** `DhanWebSocketMultiplexer.java` (L302 inline `shouldReconnect`), `UpstoxWebSocketMultiplexer.java` (L400 uses ReconnectManager), `BreezeWebSocketMultiplexer.java` (uses ReconnectManager), `DhanTwentyDepthWebSocketClient.java` (L142 inline reconnect), `LoadBalancedBrokerGateway.FailoverWebSocketMultiplexer.java` (failover wrapper), `DefaultWebSocketSupervisor.java` (shared supervisor)
**Symbol/Value:** Reconnect orchestration — some use `ReconnectManager` from broker-core, others have inline logic
**Blast Radius:** 6 files
**Impact:** **MEDIUM** — ReconnectManager exists but isn't uniformly adopted

### [SMELL-05] **B — Duplicated Logic:** ICICI "not supported" adapter boilerplate
**Files:** `IciciBracketOrderAdapter.java`, `IciciCoverOrderAdapter.java`, `IciciGttOrderAdapter.java`, `IciciSliceOrderAdapter.java` (+ `IciciConditionalAlertProvider` which is wired but unclear if real)
**Symbol/Value:** Each adapter implements the port interface and throws `UnsupportedOperationException`
**Blast Radius:** 5 identical pattern files
**Impact:** **LOW** — Simple boilerplate but adds 5× code that could be a single `UnsupportedProvider` fallback

### [SMELL-06] **B — Duplicated Logic:** Price conversion across brokers
**Files:** `UpstoxBinaryParser.java` (private `toPaisa(int)` L165), `UpstoxExpiredOptionMapper.java` (private `toPaisa(JsonNode)` L113), `DhanRestOrderClient.java` (repeated `PriceMath.fromPaisa(x).doubleValue()` pattern — 10+ occurrences)
**Symbol/Value:** `price * 100` logic and `PriceMath.fromPaisa(x).doubleValue()` call pattern
**Blast Radius:** 5+ files
**Impact:** **MEDIUM** — Upstox binary parser has its own toPaisa that could be validated against PriceMath

### [SMELL-07] **C — Cross-Module State Mutation:** Global runtime mode holders
**Files:** `RuntimeModeHolder.java` (core), `RuntimeBusHolder.java` (core), `BrokerStartupOrchestrator.java` (app), `PipelineRuntime.java` (pipeline-core)
**Symbol/Value:** Static singletons holding `RuntimeMode` and `RuntimeBus`
**Blast Radius:** 10+ files read these globals
**Impact:** **MEDIUM** — Static mutable state complicates testing and parallel execution

### [SMELL-08] **D — Implicit Coupling:** Raw exchange segment strings in controllers
**Files:** All files from SMELL-01 — controllers use `@RequestParam(defaultValue = "NSE_EQ") String segment` instead of `ExchangeSegment segment`
**Symbol/Value:** `"NSE_EQ"` as string vs. `ExchangeSegment.NSE_EQ`
**Blast Radius:** 20+ controller endpoints
**Impact:** **HIGH** — Bypasses type safety; any refactor of ExchangeSegment enum requires string search

### [SMELL-09] **E — Fragmented Feature Ownership:** Order lifecycle
**Files:** `core/domain/model/Order.java`, `core/domain/oms/OrderStateMachine.java` (+7 state event files), `broker/api/port/OrderCommand.java`, `broker/dhan/.../DhanRestOrderClient.java`, `broker/upstox/.../orders/`, `trading/execution/.../OrderManagementService.java`, `app/.../api/OrderController.java`, `app/.../service/OrderApplicationService.java`, `data/persistence/oms/EventSourcedOrderRepository.java`, `cli/.../command/CliBrokerCommands.java`
**Symbol/Value:** Order domain entity, state machine, command interface, broker REST client, execution service, REST controller, application service, persistence, CLI
**Blast Radius:** 20+ files across 6 modules
**Impact:** **HIGH** — Changing order behavior touches core, broker-api, broker-impl, trading, app, data, and cli

### [SMELL-10] **E — Fragmented Feature Ownership:** Market data pipeline
**Files:** `core/domain/event/MarketTickEvent.java`, `core/domain/port/MarketDataIngressPort.java`, `broker/api/port/MarketDataProvider.java`, `broker/dhan/.../websocket/DhanWebSocketMultiplexer.java`, `broker/upstox/.../websocket/UpstoxWebSocketMultiplexer.java`, `broker/icici/.../websocket/BreezeWebSocketMultiplexer.java`, `runtime/hotpath/MarketDataPipeline.java`, `gateway/bridge/GatewayEventBridge.java`, `app/.../api/MarketDataController.java`, `app/.../service/MarketDataApplicationService.java`
**Symbol/Value:** Market tick → provider interface → WebSocket multiplexer → hotpath pipeline → gateway bridge → controller
**Blast Radius:** 15+ files across 7 modules
**Impact:** **HIGH** — Adding a new feed mode requires changes in most layers

### [SMELL-11] **F — Parallel Hierarchies:** Broker connection implementations
**Files:** `DhanBrokerConnection.java`, `UpstoxBrokerConnection.java`, `IciciBrokerConnection.java`, `PaperBrokerConnection.java` (in broker-gateway)
**Symbol/Value:** All implement `IBrokerConnection` with near-identical `putIfNotNull` capability map building, provider wiring, getCapability delegation
**Blast Radius:** 4 parallel implementations
**Impact:** **MEDIUM** — Any new IBrokerConnection method requires 4+ implementations to update

### [SMELL-12] **F — Parallel Hierarchies:** WebSocket multiplexer implementations
**Files:** `DhanWebSocketMultiplexer.java`, `UpstoxWebSocketMultiplexer.java`, `BreezeWebSocketMultiplexer.java`, `SimulatedWebSocketMultiplexer.java`, `FailoverWebSocketMultiplexer.java` (inner class)
**Symbol/Value:** WebSocketMultiplexer interface with subscribe/unsubscribe/publish lifecycle
**Blast Radius:** 5 implementations
**Impact:** **MEDIUM** — Each has unique reconnect/subscription management; no shared base class

### [SMELL-13] **G — Inconsistent Abstraction Levels:** Controller parameter types
**Files:** `SyncStatusController.java` (String segment → manual `ExchangeSegment.valueOf()`), `OptionScanController.java` (String segment → manual `ExchangeSegment.valueOf()`), `AnalyticsController.java` (ExchangeSegment exchangeSegment — properly typed), `OrderController.java` (proper domain types)
**Symbol/Value:** Some controllers accept typed enums, others accept raw strings
**Blast Radius:** 10+ controllers
**Impact:** **MEDIUM** — Inconsistent API contract; callers can't rely on type safety

### [SMELL-14] **H — Boundary Violation:** data-historical-ingest depends on broker-dhan
**Files:** `data/historical-ingest/build.gradle` (`implementation project(':broker-dhan')`)
**Symbol/Value:** Import of a broker implementation (Dhan) into a data/historical module
**Blast Radius:** Potentially all other broker implementations if they want historical data
**Impact:** **HIGH** — Data layer should be broker-agnostic; this creates a hard coupling to Dhan

### [SMELL-15] **B — Duplicated Logic:** Sub-second sleep/wait patterns
**Files:** `AttachClient.java` (`Thread.sleep(500)` repeated), `DhanTwentyDepthWebSocketClient.java` (`Thread.sleep(1000)`), `UpstoxWebSocketMultiplexer.java` (sleep patterns in reconnect), `TickReplaySession.java` (sleep for pacing), `BreezeWebSocketResubscribeTest.java` (`Thread.sleep(500)` in tests)
**Symbol/Value:** `Thread.sleep(N)` for synchronization
**Blast Radius:** 10+ files
**Impact:** **LOW** — Standard pattern but inconsistent timing values

### [SMELL-16] **E — Fragmented Feature Ownership:** Scanning feature
**Files:** `core/domain/scan/ScanHit.java`, `core/domain/scan/ScanResult.java`, `core/domain/scan/ScanRun.java`, `core/domain/event/ScanResultsPublished.java`, `trading/scanner/engine/ScanEngine.java`, `trading/scanner/criterion/ScanCriterion.java`, `trading/scanner/bridge/ScannerStrategyBridge.java`, `app/.../config/ScanConfiguration.java`, `app/.../api/ScanController.java`, `app/.../api/OptionScanController.java`, `app/.../service/ScanService.java`, `cli/.../CliOperations.java`, `cli/.../command/CliScannerCommands.java`
**Symbol/Value:** Scan domain → scanner engine → strategy bridge → API controller → CLI
**Blast Radius:** 15+ files across 5 modules
**Impact:** **MEDIUM** — Scan profile logic is spread across data models, engine, and configuration

### [SMELL-17] **A — Scattered Constants:** Large test symbol duplication
**Files:** 100+ test files across all modules
**Symbol/Value:** `"RELIANCE"`, `"SBIN"`, `"TCS"`, `"NIFTY"`, `"BANKNIFTY"`, `"INFY"`, `"CRUDEOIL"`, `"GOLD"`
**Blast Radius:** 100+ test files
**Impact:** **LOW** (for production) but **MEDIUM** (for test maintenance)

### [SMELL-18] **E — Fragmented Feature Ownership:** Replay engine
**Files:** `core/domain/time/ReplayTradingClock.java`, `replay/engine/.../TickReplaySession.java`, `replay/engine/.../ScenarioRunner.java`, `replay/engine/.../AdminReplayAdapter.java`, `pipeline/core/.../clock/VirtualClock.java`, `replay/engine/.../scenario/Scenario.java`, `data/persistence/.../replay/HistoricalQueryService.java`, `app/.../api/ReplayController.java`, `app/.../api/ReplayStudioController.java`, `gateway/.../websocket/GatewayReplayCommandProcessor.java`
**Symbol/Value:** Replay clocks, sessions, scenarios, admin adapters, query services, controllers
**Blast Radius:** 12+ files across 6 modules
**Impact:** **MEDIUM** — Replay mode has special-case logic throughout

---

## PHASE 3 — Root Cause Classification

### RC1: Missing Shared Vocabulary Layer (Constants, Types, Enums Not Centralized)
- **SMELL-01** (`"NSE_EQ"` / `"IDX_I"` defaults) — no `DefaultSegments` or `CommonDefaults` class
- **SMELL-02** (timeout constants) — no `TimeoutDefaults` or `StandardTimeouts` class
- **SMELL-03** (reconnect params) — no `ReconnectDefaults` shared between brokers
- **SMELL-17** (test symbols) — no `TestSymbols` or `TestData` constants file for tests

### RC2: Missing Service / Use-Case Layer (Business Logic Leaking into I/O or UI)
- **SMELL-09** (order lifecycle) — application service (`OrderApplicationService`) exists but execution/broker details leak up to controllers
- **SMELL-10** (market data) — `MarketDataApplicationService` exists but controllers still handle SSE streaming directly
- **SMELL-16** (scanning) — scan configuration is split across YAML, Java config, and DB

### RC3: Missing Domain Model (Raw Dicts/Primitives Instead of Typed Entities)
- **SMELL-08** (string segments in controllers) — bypasses `ExchangeSegment` enum
- **SMELL-13** (mixed controller param types) — some use String, some use Enum

### RC4: Boundary Violations (Modules Importing Across Layer Boundaries)
- **SMELL-14** (data-historical-ingest → broker-dhan) — hard layer violation
- **SMELL-07** (static holders) — mutable globals create hidden coupling

### RC5: Premature File Splitting (One Concept Split Without Unifying Interface)
- **SMELL-09** (order across 6 modules) — legitimate separation but without a clear aggregate boundary
- **SMELL-10** (market data across 7 modules) — similar issue

### RC6: Absent or Inconsistent Coding Standards
- **SMELL-06** (price conversion) — some use PriceMath, others private methods
- **SMELL-13** (controller param types) — inconsistent use of typed vs. untyped
- **SMELL-15** (sleep patterns) — inconsistent wait/sleep durations

---

## PHASE 4 — Refactoring Plan

### REF-01: Extract Default Exchange Segment Constants
- **Root Cause:** RC1 (Missing shared vocabulary)
- **Action:** Extract — Create `DefaultSegments` class in core with `DEFAULT_EQUITY_SEGMENT = "NSE_EQ"`, `DEFAULT_INDEX_SEGMENT = "IDX_I"`, etc.
- **From:** 40+ files with hardcoded `"NSE_EQ"` / `"IDX_I"` / `"NSE_FNO"`
- **To:** `core/src/main/java/.../config/DefaultSegments.java`
- **Touches:** Controllers: `OptionScanController`, `SyncStatusController`, `AnalyticsController`, `StudioController`, `ReplayStudioController`, `DepthAnalyticsController`, `OptionsAnalyticsController`, `TradingProperties`, `MarketStatusProvider`, `DataConfiguration`, `TradeCli`, `CliParquetCommands`, `CliAnalyticsCommands`, `CliBrokerGatewayCommands`, `CliCertifyCommand`, `CliComputeCommand`, `CliBrokerCommands`, `SymbolController`, `BacktestController`
- **Test Strategy:** Unit test for DefaultSegments; compilation check for all references
- **Sequencing Note:** None (foundational)

### REF-02: Standardize Controller Parameters to ExchangeSegment Enum
- **Root Cause:** RC3 (Missing domain model), RC6 (Inconsistent standards)
- **Action:** Replace `@RequestParam(defaultValue = "NSE_EQ") String segment` with `@RequestParam(defaultValue = "NSE_EQ") ExchangeSegment segment` in all controllers that accept segment as string
- **From:** 10+ controller endpoints with `String segment`
- **To:** Type-safe `ExchangeSegment segment` (already works via Spring's StringToEnumConverterFactory)
- **Touches:** `OptionScanController`, `SyncStatusController`, `ReplayStudioController`, `DepthAnalyticsController`, `OptionsAnalyticsController`, `AnalyticsController`, `StudioController`
- **Test Strategy:** Existing integration tests verify endpoint behavior post-refactor
- **Sequencing Note:** After REF-01

### REF-03: Extract Test Symbol Constants
- **Root Cause:** RC1 (Missing shared vocabulary)
- **Action:** Extract — Create `TestSymbols` and `TestSegments` constants in test-fixtures module
- **From:** 100+ test files with `"RELIANCE"`, `"SBIN"`, `"TCS"`, `"NIFTY"`, `"BANKNIFTY"`
- **To:** `test-fixtures/src/main/java/.../TestSymbols.java`
- **Touches:** All test files in app, broker-*, trading-*, data-*, gateway, cli, replay-engine
- **Test Strategy:** No functional change; refactoring with IDE rename + compilation check
- **Sequencing Note:** None (foundational)

### REF-04: Fix Layer Violation in data-historical-ingest
- **Root Cause:** RC4 (Boundary violations)
- **Action:** Extract Dhan-specific historical functionality from `data-historical-ingest` into broker-dhan, and keep only broker-agnostic historical code in data module; or create a broker-api historical port that broker-dhan implements
- **From:** `data/historical-ingest/build.gradle` (`implementation project(':broker-dhan')`)
- **To:** Move Dhan-specific historical adapters to `broker/dhan/.../historical/` that implement interfaces from `data-historical-ingest` SPI
- **Touches:** `data/historical-ingest/build.gradle`, Dhan historical files in ingest module, possibly new SPI port in broker-api
- **Test Strategy:** ArchUnit boundary test (already exists in architecture-test), integration tests still pass
- **Sequencing Note:** None — independent fix

### REF-05: Consolidate Reconnect Configuration
- **Root Cause:** RC1 (Missing shared vocabulary)
- **Action:** Extract — Create `ReconnectDefaults` with standard values: `DEFAULT_MAX_ATTEMPTS = 8`, `DEFAULT_BASE_DELAY_MS = 1000`, `DEFAULT_MAX_DELAY_MS = 30000` in broker-core
- **From:** `UpstoxWebSocketMultiplexer.java` (local constants), `DhanTwentyDepthWebSocketClient.java` (local constants), `DhanConnectionSettings.java` (config-based), `BreezeWebSocketMultiplexer.java` (uses ReconnectManager directly)
- **To:** `broker/core/src/main/java/.../reconnect/ReconnectDefaults.java`
- **Touches:** `UpstoxWebSocketMultiplexer`, `DhanTwentyDepthWebSocketClient`, `DhanWebSocketMultiplexer`, `DhanConnectionSettings`
- **Test Strategy:** ReconnectManager tests (already exist); no functional change expected
- **Sequencing Note:** None

### REF-06: Consolidate Alert Channel Timeouts
- **Root Cause:** RC1 (Missing shared vocabulary)
- **Action:** Extract — Create `AlertChannelDefaults` class with `DEFAULT_TIMEOUT = Duration.ofSeconds(5)`
- **From:** `SlackAlertChannel.java`, `PagerDutyAlertChannel.java`, `WebhookAlertChannel.java`
- **To:** Shared constant in `app/.../health/AlertChannelDefaults.java`
- **Touches:** 3 alert channel files
- **Test Strategy:** Unit test for constant value; verify no behavioral change
- **Sequencing Note:** None

### REF-07: Introduce UnsupportedProvider Fallback
- **Root Cause:** RC6 (Inconsistent standards)
- **Action:** Merge — Replace 5 "not supported" ICICI adapters with a single `UnsupportedPortProvider` that uses `java.lang.reflect.Proxy` or a factory to create UnsupportedOperationException-throwing adapters for any port interface
- **From:** `IciciBracketOrderAdapter`, `IciciCoverOrderAdapter`, `IciciGttOrderAdapter`, `IciciSliceOrderAdapter`, possibly `IciciConditionalAlertProvider`
- **To:** `broker-core/src/main/java/.../UnsupportedPortProvider.java`
- **Touches:** 5 ICICI adapter files (delete), IciciBrokerConnection (update wiring), IciciBrokerDescriptor (update capabilities)
- **Test Strategy:** Unit test for Proxy-based UnsupportedProvider; existing ICICI tests still pass
- **Sequencing Note:** None

### REF-08: Uniform MarketDataIngressPort for All Feed Modes
- **Root Cause:** RC2 (Missing service layer), RC5 (Premature file splitting)
- **Action:** Introduce abstraction — Define a `FeedBridge` interface that decouples WebSocket multiplexers from the hotpath pipeline; each broker provides a `FeedBridge`, and the hotpath consumes it uniformly
- **From:** Dhan/Upstox/ICICI WebSocket multiplexers directly producing `MarketTickEvent` and `DepthUpdateEvent`
- **To:** `FeedBridge` interface in `broker-api`; implementation per broker in broker-x; hotpath consumes via `MarketDataIngressPort`
- **Touches:** `broker-api` (new interface), broker-{dhan,upstox,icici} (implement FeedBridge), `runtime/hotpath/MarketDataPipeline.java` (consume via interface)
- **Test Strategy:** Component test with mock FeedBridge; existing integration tests as regression
- **Sequencing Note:** After REF-09 (broker-api stability)

### REF-09: IBrokerConnection Template Method
- **Root Cause:** RC6 (Parallel hierarchies)
- **Action:** Introduce abstraction — Create `AbstractBrokerConnection` base class in broker-core that handles capability map building, `putIfNotNull`, `getCapability`, and `requireCapability` delegation
- **From:** 4 broker connections (Dhan, Upstox, ICICI, Paper) with duplicated capability wiring
- **To:** `broker/core/src/main/java/.../AbstractBrokerConnection.java`
- **Touches:** All 4 BrokerConnection classes, their providers
- **Test Strategy:** BrokerPluginContractTest (already in broker-api) provides contract testing
- **Sequencing Note:** None — can proceed independently

### REF-10: RuntimeMode as Explicit Dependency Instead of Static Holder
- **Root Cause:** RC4 (Boundary violations — hidden coupling via globals)
- **Action:** Refactor — Thread RuntimeMode through PipelineRuntime as a constructor parameter; deprecate RuntimeModeHolder; replace all static reads with dependency injection
- **From:** `RuntimeModeHolder.get()` (core), `RuntimeModeHolder.set()` (app's startup), `PipelineRuntime` (reads holder)
- **To:** Constructor-injected `RuntimeMode` in all consumers
- **Touches:** `RuntimeModeHolder` (deprecate), `RuntimeMode` (no change), `PipelineRuntime`, `PipelineRuntimeService`, `BrokerStartupOrchestrator`, all callers of `RuntimeModeHolder.get()`
- **Test Strategy:** Existing tests that use `RuntimeModeHolder.set()` need updating; compile-time safety after refactor
- **Sequencing Note:** After stabilizing core (REF-01, REF-03)

---

## PHASE 5 — Structural Recommendations

### 5.1 Proposed Directory Structure

```
trade-j/
├── core/                          # Domain kernel — no infra dependencies
│   ├── domain/
│   │   ├── value/                 # ExchangeSegment, Side, PriceMath, Symbol, etc.
│   │   ├── model/                 # Order, Candle, Position, Trade, Quote...
│   │   ├── event/                 # DomainEvent, MarketTickEvent, etc.
│   │   ├── oms/                   # OrderStateMachine, OrderEvent hierarchy
│   │   ├── scan/                  # ScanHit, ScanResult, ScanRun
│   │   ├── time/                  # TradingClock, ExchangeCalendar
│   │   ├── instrument/            # ContractSymbolNormalizer, IndexSymbols
│   │   ├── port/                  # Domain ports (EventBus, MarketDataIngressPort, etc.)
│   │   ├── config/                # NEW: DefaultSegments, ReconnectDefaults
│   │   └── id/                    # IdGenerator, DeterministicIdGenerator
│   ├── infrastructure/            # WorkspacePaths, config loaders
│   └── service/                   # MarketDataService, HistoricalDataService
│
├── broker/
│   ├── api/                       # IBrokerConnection, all port interfaces, SPI
│   ├── core/                      # AbstractBrokerConnection (NEW), ReconnectManager,
│   │   #                            CircuitBreaker, rate limiting, LoadBalancedGateway,
│   │   #                            DefaultWebSocketSupervisor, UnsupportedPortProvider
│   ├── dhan/                      # Dhan-specific adapters, clients
│   ├── upstox/                    # Upstox-specific adapters, protobuf parser
│   ├── icici/                     # ICICI Breeze-specific adapters, Selenium login
│   └── template/                  # (commented out) Broker implementation template
│
├── broker-gateway/                # Multi-broker routing, failover, certification
│
├── trading/
│   ├── strategy/                  # GraphStrategySandbox, PortfolioEngine, plugins
│   ├── execution/                 # OrderManagementService, PositionRiskHandler, subscription
│   ├── scanner/                   # ScanEngine, ScanCriterion, ScannerStrategyBridge
│   ├── institutional-scanner/     # Institutional scan variants
│   ├── indicators/                # IndicatorEngine
│   ├── simulation/                # MatchingEngine, SimulatedOrderService
│   └── options-analytics/         # BlackScholesCalculator, Greeks
│
├── pipeline/
│   ├── core/                      # PipelineGraph, GraphCompiler, PipelineRuntime
│   ├── runtime/                   # PipelineRuntimeService, DagPipelineRuntimeService
│   └── platform/                  # Pipeline catalog, definitions, execution tracking
│
├── runtime/
│   ├── disruptor/                 # DisruptorEventBus, pipeline wiring
│   └── hotpath/                   # MarketDataPipeline, OrderPipeline
│
├── data/
│   ├── persistence/               # DuckDbEventStore, Chronicle Queue, OMS repo
│   ├── feature-store/             # FeatureStore with DuckDB
│   ├── historical-ingest/         # Parquet writer/query, canonical bar format
│   └── analytics/                 # Federated DuckDB analytics engine
│
├── gateway/                       # WebSocket transport, topic router, binary codec
├── app/                           # Spring Boot composition root
├── cli/                           # Operator CLI
├── replay/engine/                 # TickReplaySession, ScenarioRunner
├── mcp-server/                    # MCP server
├── architecture-test/             # ArchUnit module dependency enforcement
├── test-fixtures/                 # Shared test doubles + TestSymbols (NEW)
└── scripts/                       # Shell scripts for operations, certification
```

### 5.2 Boundary Rules

| # | Rule | Enforced By |
|---|---|---|
| B1 | **`domain/` must never import from `infrastructure/`** | ArchUnit `checkLayer` |
| B2 | **`broker-api/` must NOT depend on any broker implementation** | build.gradle dependency graph |
| B3 | **`data/` modules must NOT depend on broker implementations** | ArchUnit — enforce via `slice.check` |
| B4 | **`app/` is the ONLY module that may depend on all others** | ArchUnit exception list |
| B5 | **`pipeline-core/` must NOT depend on `broker-api/`** | ArchUnit slice rule |
| B6 | **No static mutable state holders** (`RuntimeModeHolder`, `RuntimeBusHolder`) | SpotBugs `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` + code review |
| B7 | **All controller parameters representing segments must use `ExchangeSegment` enum, not `String`** | Checkstyle/custom rule or ArchUnit |

### 5.3 Coding Standards to Enforce

1. **Price values: ALWAYS use `long paisa` via `PriceMath.toPaisa()` / `fromPaisa()`** — never `float`/`double` for price fields. Broker-specific parsers that need raw integer scaling must delegate to `PriceMath`.

2. **Exchange segments: ALWAYS use `ExchangeSegment` enum** — never `String` for segment parameters in APIs, domain models, or configuration. `@RequestParam ExchangeSegment segment` is preferred; the string form `"NSE_EQ"` is only acceptable in `DefaultSegments` constants.

3. **Reconnect policies: ALL brokers must use `ReconnectManager` from `broker-core`** — no inline reconnect loops. Parameters (maxAttempts, baseDelay, maxDelay) must be configurable through `DhanConnectionSettings`/equivalent, not hardcoded.

4. **Alert channel timeouts: Use `AlertChannelDefaults.DEFAULT_TIMEOUT`** — no `Duration.ofSeconds(X)` with varying values.

5. **Thread.sleep() for synchronization: Forbidden in production code** — use `CountDownLatch`, `CompletableFuture`, or `Awaitility` for test synchronization.

6. **Controller layer: Must use domain types for all model parameters** — no `Map<String, Object>` response bodies; use typed records for serialization.

7. **Module dependency direction: Data flow is always `domain → use case → infrastructure`** — never the reverse. `data/` must not depend on `broker-dhan`.

### 5.4 Guardrails to Prevent Recurrence

| Guardrail | Tool/Technique | Prevents |
|---|---|---|
| **Module dependency tests** | ArchUnit (`architecture-test`) — run in CI | Boundary violations (B1–B7, SMELL-14) |
| **Dependency lock file diff** | `gradle.lockfile` in CI diff — detect new inter-module deps | Accidental `implementation project(':broker-dhan')` in data module |
| **Checkstyle constant name rule** | Custom Checkstyle check: `^[A-Z][A-Z0-9_]+$` for `static final` non-strings → `^DEFAULT_.*$` | SMELL-01: scattered magic values |
| **ADR templates** | One-page Markdown per new feature/inter-module dependency | Prevents fragmentation before it starts (SMELL-09, SMELL-10) |
| **Module `exports` / `opens`** | Java 21 module-info.java (when ready) to enforce public API boundaries | Accidental access to internal classes across modules |
| **Pre-commit hook** | `./gradlew :architecture-test:test` + `./gradlew checkstyleMain` | Catches violations before commit |
| **Abstract base class enforcement** | `AbstractBrokerConnection` with `final` on capability-wiring methods; subclasses may only override business methods | SMELL-11: parallel connection hierarchies |
| **Code review checklist item** | "Does this change touch more than 2 modules for a single logical feature?" | SMELL-09/10: fragmented feature ownership |

---

## Summary Metrics

| Metric | Count |
|---|---|
| Total modules | ~30 |
| Production Java files examined | ~250+ |
| Shotgun surgery findings (Phase 2) | 18 |
| High-impact findings | 5 |
| Medium-impact findings | 9 |
| Low-impact findings | 4 |
| Refactoring tasks (Phase 4) | 10 |
| Boundary rules (Phase 5) | 7 |
| Coding standards | 7 |
| Guardrails | 8 |

**Top 3 priorities for immediate action:**

1. **SMELL-14** (Boundary violation: data-historical-ingest → broker-dhan) — breaks the entire layer architecture
2. **SMELL-01** (Scattered `"NSE_EQ"` / `"IDX_I"` across 40+ files) — high blast radius, easy to fix
3. **SMELL-07** (Static global holders) — prevents scalable testing and parallel execution
