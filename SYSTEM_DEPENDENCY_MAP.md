# SYSTEM_DEPENDENCY_MAP.md

Generated: 2026-06-15 | Audit Type: Pre-Production End-to-End Verification (Updated Post-Fixes)

---

## Architecture Overview

```
User (Browser / CLI)
    ↓
Frontend (trade_j_frontend — React + TypeScript + Vite)
    ↓ HTTP/SSE/WebSocket
API Layer (app/ — Spring Boot REST Controllers — 29 controllers)
    ↓
Services (Application Services)
    ↓
Pipeline (Runtime Hot-Path — Disruptor Event Bus)
    ↓
Core Domain (Events, OMS, Risk, Portfolio)
    ↓
Broker Gateway (Load-Balanced Multi-Broker Router)
    ↓
Broker Adapters (Dhan / Upstox / ICICI / Simulation)
    ↓
Exchange (NSE / BSE / MCX / IDX)
```

---

## Module Map

### Backend Modules (Gradle Multi-Module)

| Module | Path | Purpose |
|--------|------|---------|
| **app** | `app/` | Spring Boot main application, controllers, config, startup orchestration |
| **core** | `core/` | Domain model, events, OMS state machine, runtime bus, concurrency |
| **broker-api** | `broker/api/` | Broker abstraction interfaces (IBrokerConnection, ports, adapters) |
| **broker-core** | `broker/core/` | Shared broker infrastructure (circuit breaker, rate limiter, reconnection, routing) |
| **broker-dhan** | `broker/dhan/` | Dhan broker adapter (REST, WebSocket, auth, depth, orders) |
| **broker-upstox** | `broker/upstox/` | Upstox broker adapter (OAuth, WebSocket, GTT orders) |
| **broker-icici** | `broker/icici/` | ICICI Breeze broker adapter (browser auth, WebSocket, session) |
| **broker-gateway** | `broker-gateway/` | Multi-broker gateway, load balancing, failover, paper trading |
| **trading-execution** | `trading/execution/` | Order execution, risk management, position tracking, circuit breaker, **ReadModelStore (CQRS read model with 8-field OrderView)** |
| **trading-strategy** | `trading/strategy/` | Strategy plugins, signal generation, portfolio engine, ML inference |
| **trading-scanner** | `trading/scanner/` | Market scanner, option liquidity scanner, scan engine |
| **trading-indicators** | `trading/indicators/` | Technical indicators (SMA, RSI, MACD, etc.) |
| **trading-simulation** | `trading/simulation/` | Paper trading matching engine, PnL ledger |
| **trading-options-analytics** | `trading/options-analytics/` | Black-Scholes, Max Pain, Greeks, option chain registry |
| **trading-institutional-scanner** | `trading/institutional-scanner/` | Institutional-grade scanner |
| **data-persistence** | `data/persistence/` | DuckDB event store, Chronicle audit log, replay service |
| **data-feature-store** | `data/feature-store/` | Feature store for ML/strategy |
| **data-historical-ingest** | `data/historical-ingest/` | Historical data download, Parquet/DuckDB warehouse |
| **data-analytics** | `data/analytics/` | DuckDB analytics engine, SQL interface |
| **gateway** | `gateway/` | WebSocket gateway for external consumers, topic routing |
| **replay-engine** | `replay/engine/` | Scenario replay engine, tick/candle replay sessions |
| **pipeline-core** | `pipeline/core/` | DAG pipeline framework, node definitions, state scope |
| **pipeline-runtime** | `pipeline/runtime/` | Pipeline execution runtime, node factory |
| **pipeline-platform** | `pipeline/platform/` | Pipeline configuration UI descriptors |
| **mcp-server** | `mcp-server/` | MCP (Model Context Protocol) server for AI integration |
| **cli** | `cli/` | Command-line interface (tradej CLI) |
| **architecture-test** | `architecture-test/` | ArchUnit architecture tests |

### Frontend

| Module | Path | Purpose |
|--------|------|---------|
| **trade_j_frontend** | `trade_j_frontend/` | React + TypeScript + Vite SPA trading terminal |
| | `src/api/readModelContracts.ts` | **NEW** — Typed SSE read-model contracts (10 interfaces) |
| | `src/api/backend-contracts.ts` | REST API contracts (Order, Symbol, LtpResponse, etc.) |
| | `src/store/readModelStream.ts` | SSE stream dispatcher (fully typed) |
| | `src/store/types.ts` | Frontend domain types (Order, Position, Signal, Fill, etc.) |

---

## API Layer — REST Controllers (29 controllers)

| Controller | Path | Endpoints | Purpose |
|------------|------|-----------|---------|
| **OrderController** | `/api/v1/orders` | POST, GET, POST /cancel | Order placement, listing, cancellation |
| **MarketDataController** | `/api/v1/market` | LTP, depth, historical candles | Market data retrieval |
| **SymbolController** | `/api/v1/symbols` | GET | Instrument catalog |
| **ReadModelController** | `/api/v1/read-model`, `/stream/read-model` | GET, SSE | **8-field OrderView via snapshot + real-time streaming** |
| **ScanController** | `/api/v1/scan` | POST, GET | Market scan execution |
| **OptionScanController** | `/api/v1/option-scan` | POST | Option liquidity scanning |
| **OptionsAnalyticsController** | `/api/v1/options` | Chain, Greeks | Option chain analytics |
| **AnalyticsController** | `/api/v1/analytics` | SQL, equity bars | DuckDB analytics |
| **BacktestController** | `/api/v1/backtest` | POST | Strategy backtesting |
| **StudioController** | `/api/v1/studio` | Charting, options | Chart studio |
| **ReplayStudioController** | `/api/v1/replay` | Control, status | Replay engine control |
| **AdminController** | `/admin` | Runtime, kill-switch, reconcile, risk | Administrative operations |
| **HistoricalDownloadController** | `/admin/historical` | Download jobs | Historical data management |
| **AuthController** | `/api/v1/auth` | Login, logout, whoami, broker-session, ws-url | Authentication |
| **MarketSessionController** | `/api/v1/market-session` | GET | Market session state |
| **IndicatorController** | `/api/v1/indicators` | GET | Technical indicator computation |
| **PipelineController** | `/api/v1/pipeline` | Management | Pipeline control |
| **PortfolioAnalyticsController** | `/api/v1/portfolio` | Analytics | Portfolio metrics |
| **DepthAnalyticsController** | `/api/v1/depth` | Analytics | Market depth analytics |
| **NewsController** | `/api/v1/news` | GET | News feed |
| **SyncStatusController** | `/api/v1/sync` | Status | Historical sync status |
| **BrokerRegistryController** | `/api/v1/brokers` | GET | Registered brokers |
| **ExpiredOptionsController** | `/api/v1/expired-options` | GET | Expired option data |
| **ReconciliationController** | `/admin/reconciliation` | GET | **Reconciliation status with tolerance** |
| **DashboardRedirectController** | `/console` | Redirect | Console redirect |
| **McpServerController** | `/mcp` | Health | MCP health check |
| **UpstoxNotifierWebhookController** | `/upstox/webhook` | POST | Upstox notifications |
| **ReplayApiController** (research) | `/research/replay` | Control | Research replay |
| **ResearchAnalyticsApiController** | `/research/analytics` | Analytics | Research analytics |

---

## WebSocket Endpoints

| Endpoint | Protocol | Purpose |
|----------|----------|---------|
| `/ws/gateway` | Binary WebSocket | Multi-broker gateway feed (gateway profile) |
| `/api/v1/stream/read-model` | SSE | **Real-time read-model updates with 8-field OrderView** (ticks, orders, positions, PnL) |
| Dhan market feed | WebSocket (native) | Dhan tick/quote/full market data stream |
| Dhan order stream | WebSocket (native) | Dhan order update stream |
| Upstox market feed | WebSocket (native) | Upstox tick/quote stream |
| Upstox portfolio stream | WebSocket (native) | Upstox order/position updates |
| ICICI Breeze WebSocket | WebSocket (native) | ICICI market + portfolio stream |

---

## Scheduled Tasks / Background Jobs

| Scheduler | Cron/Interval | Purpose |
|-----------|---------------|---------|
| **ScanScheduler** | `0 15,30,45 9-15 * * MON-FRI` (IST) | Market scan during trading hours |
| **HistoricalSyncScheduler** | `0 0 16 * * MON-FRI` (UTC) | Daily historical data sync |
| **DailyRiskResetScheduler** | `0 30 3 * * MON-FRI` (UTC) | Reset kill switch + loss counters at market open |
| **UpstoxDailyTokenRefresh** | `0 0 4 * * *` (UTC) | Daily Upstox OAuth token refresh |
| **CircuitBreakerMetrics** | Every 30s | Expose circuit breaker states as Micrometer gauges |
| **MetricsLoggerHarness** | Every 5s | JVM + pipeline metrics logging |
| **ChronicleRetentionCleanup** | `0 0 3 * * *` | Chronicle queue retention cleanup |
| **OptionsChainPoller** | Every 60s | Option chain refresh |
| **GatewayHealthBroadcast** | Every 5s | Gateway health status broadcast |
| **TickReconciliation** | Every 300s | Broker vs. system tick reconciliation |

---

## Broker Integrations

### Dhan
- **Auth**: Static token, TOTP-generated, PIN-based
- **REST**: Orders, positions, margins, funds, kill switch
- **WebSocket**: Market feed (tick/quote/full/depth-20), order stream
- **Features**: Kill switch API, bracket orders, intraday square-off
- **Resilience**: Exponential backoff reconnect, circuit breaker, health monitor

### Upstox
- **Auth**: OAuth 2.0 with PKCE, daily token refresh
- **REST**: Orders, positions, holdings, margins, option chain
- **WebSocket**: Market feed, portfolio stream
- **Features**: GTT orders, analytics-only mode
- **Resilience**: Retry executor with rate limiter, circuit breaker
- **Limitation**: Kill switch is no-op (Upstox API does not provide)

### ICICI Breeze
- **Auth**: Browser-automated session capture, TOTP, session exchange
- **REST**: Orders, positions, margins, holdings
- **WebSocket**: Market + portfolio stream
- **Features**: No native bracket orders, no kill switch
- **Resilience**: Session refresh scheduler, health monitor, reconnect manager
- **Limitation**: No MARKET orders (throws UnsupportedOperationException)

---

## Internal Event Flow

```
Market Data (Tick/Depth/Candle)
    ↓
MarketDataPipeline (Disruptor Ring Buffer)
    ↓
IndicatorEngine → StrategyPlugin → SignalGenerated
    ↓
PositionRiskHandler (Kill Switch → Daily Loss → Position Limits → Margin → Portfolio)
    ↓
SignalPendingExecution
    ↓
ExecutionHandler (Partitioned Queues → Order Management Service)
    ↓
OrderCommand (Broker Adapter → Exchange)
    ↓
OrderFilled / OrderRejected → TradeOpened / TradeClosed → Position Update → PnL
    ↓
ReadModelStore → SSE → Frontend
    ↓   (8-field OrderView: orderId, symbol, status, quantity, side, pricePaisa, orderType, filledQuantity)
ChronicleAuditLogWriter → Audit Log
DuckDbEventStore → Persistent Storage
DeadLetterQueue → Failed Event Capture
```

---

## Data Storage

| Store | Technology | Purpose |
|-------|-----------|---------|
| **Event Store** | DuckDB | Domain event persistence |
| **Audit Log** | Chronicle Queue | Append-only audit trail |
| **Dead Letter Queue** | Chronicle Queue | Failed event capture |
| **Historical Warehouse** | DuckDB | Historical OHLCV data |
| **Instrument Cache** | File system | Instrument catalog cache |
| **Token State** | File system | Broker token persistence |
| **Runtime DB** | DuckDB | Runtime trade data |

---

## Audit-Cycle Changes Summary

### Frontend Type Safety
- **NEW:** `trade_j_frontend/src/api/readModelContracts.ts` — 10 typed interfaces
- **MODIFIED:** `trade_j_frontend/src/store/readModelStream.ts` — Fully typed dispatch
- **MODIFIED:** `trade_j_frontend/src/dashboard/widgetsExtra.tsx` — OptionChain widget typed
- **MODIFIED:** `trade_j_frontend/src/api/backend-contracts.ts` — `ReadModelPnl` extracted

### Backend Read Model
- **MODIFIED:** `trading/execution/.../ReadModelStore.java` — `OrderView` expanded from 4 to 8 fields

### Configuration
- **MODIFIED:** `app/src/main/resources/application.yml` — Reconciliation tolerance 0→1 with `TRADE_RECONCILIATION_MISMATCH_TOLERANCE_QTY` env var

### Test Fixes
- **MODIFIED:** `app/src/test/java/com/tradej/app/integration/StartupSmokeComponentTest.java` — `@TempDir` + `@DynamicPropertySource`
- **MODIFIED:** `app/src/test/java/com/tradej/app/integration/MarketDataValidationTest.java` — Live-data quality guards

### Dead Code Removed
- **DELETED:** `sample.html`
- **DELETED:** `EventSourcedNetPositionProvider.java-e`
- **DELETED:** `originOf()`, `ORIGIN_BY_KEY`, unused imports in `readModelStream.ts`
