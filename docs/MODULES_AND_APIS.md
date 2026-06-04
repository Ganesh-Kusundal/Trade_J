# Trade-J Platform: Modules & API Endpoints Report

> **Generated:** 31 May 2026  
> **Platform:** Java 21, Spring Boot 3.4.13, Gradle Multi-Module  
> **Brokers:** Dhan, Upstox, ICICI Direct (Breeze)  
> **Market:** NSE Equity, NSE F&O, BSE Equity, BSE F&O, MCX Commodity, Indices

---

## 1. Module Architecture (28 Subprojects)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        APPLICATION LAYER                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐               │
│  │  app     │  │  cli     │  │ gateway  │  │frontend │               │
│  │ (Spring) │  │ (picocli)│  │ (WS)     │  │ (React) │               │
│  └────┬─────┘  └──────────┘  └──────────┘  └──────────┘               │
│       │                                                                │
├───────┼─────────────────────────────────────────────────────────────────┤
│       │         TRADING LAYER                                          │
│  ┌────┴─────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│  │ strategy │ │execution │ │ scanner  │ │indicators│ │ simulation   │ │
│  │ (engine) │ │ (OMS)    │ │ (engine) │ │ (tech)   │ │ (matching)   │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────┘ │
│  ┌───────────────────┐ ┌─────────────────────┐                         │
│  │institutional-     │ │ trade-pipeline-     │                         │
│  │scanner            │ │ platform            │                         │
│  └───────────────────┘ └─────────────────────┘                         │
├─────────────────────────────────────────────────────────────────────────┤
│                      RUNTIME LAYER                                      │
│  ┌──────────────────┐ ┌──────────────────┐                              │
│  │ runtime-disruptor│ │ runtime-hotpath  │                              │
│  │ (Event Bus)      │ │ (Pipelines)      │                              │
│  └──────────────────┘ └──────────────────┘                              │
├─────────────────────────────────────────────────────────────────────────┤
│                      BROKER LAYER                                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐                   │
│  │ api      │ │ core     │ │ dhan     │ │ upstox   │                   │
│  │ (ports)  │ │ (shared) │ │ (SDK)    │ │ (REST+WS)│                   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘                   │
│  ┌──────────┐                                                          │
│  │ icici    │                                                          │
│  │ (Breeze) │                                                          │
│  └──────────┘                                                          │
├─────────────────────────────────────────────────────────────────────────┤
│                       DATA LAYER                                        │
│  ┌──────────┐ ┌────────────┐ ┌────────────┐ ┌──────────┐               │
│  │persist-  │ │feature-    │ │historical- │ │analytics │               │
│  │ence      │ │store       │ │ingest      │ │engine    │               │
│  │(DuckDB/  │ │(ML store)  │ │(Parquet)   │ │(DuckDB)  │               │
│  │Chronicle)│ │            │ │            │ │          │               │
│  └──────────┘ └────────────┘ └────────────┘ └──────────┘               │
├─────────────────────────────────────────────────────────────────────────┤
│                       CORE LAYER                                        │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │ core — Domain events, ports, value objects, pipeline graph types │   │
│  └──────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────┤
└─────────────────────────────────────────────────────────────────────────┘
```

### Module Responsibilities

| Module | Path | Purpose |
|--------|------|---------|
| `:core` | `core/` | Domain events, ports, value objects, pipeline graph types, clock, state store |
| `:broker-api` | `broker/api/` | `IBrokerConnection` + all broker port interfaces |
| `:broker-core` | `broker/core/` | Shared broker infra: WS supervisor, resilience, rate limiting, auth lifecycle |
| `:broker-dhan` | `broker/dhan/` | Dhan adapter (SDK-based: auth, market data, orders, options, portfolio, margin, etc.) |
| `:broker-upstox` | `broker/upstox/` | Upstox adapter (REST + WS: market data, orders, portfolio, options, historical) |
| `:broker-icici` | `broker/icici/` | ICICI Direct Breeze adapter (browser session + TOTP, market data, orders, WS) |
| `:runtime-disruptor` | `runtime/disruptor/` | LMAX Disruptor event bus, sharded bus, stage handlers |
| `:runtime-hotpath` | `runtime/hotpath/` | Market data pipeline, order pipeline, pipeline config, rate limiter |
| `:trading-strategy` | `trading/strategy/` | Strategy engine (plugin-based), portfolio engine, candle agg, ML inference |
| `:trading-execution` | `trading/execution/` | OMS, execution handler, risk handler, reconciliation, idempotency cache |
| `:trading-scanner` | `trading/scanner/` | Market scan engine: %change, volume spike, PCR, max OI, option liquidity |
| `:trading-institutional-scanner` | `trading/institutional-scanner/` | Sector ranking, feature pipeline, candidate selection |
| `:trading-indicators` | `trading/indicators/` | Technical indicators: HalfTrend, CVD, Bollinger Squeeze, Order Blocks |
| `:trading-simulation` | `trading/simulation/` | Order matching engine, simulated order service, PnL ledger |
| `:data-persistence` | `data/persistence/` | DuckDB event store, Chronicle audit log, replay engine, dead letter queue |
| `:data-feature-store` | `data/feature-store/` | ML feature store (DuckDB + in-memory), async writer |
| `:data-historical-ingest` | `data/historical-ingest/` | Historical download: equity 1m bars, rolling options, Parquet warehouse |
| `:data-analytics` | `data/analytics/` | Federated DuckDB analytics: equity candles, option bars, SQL queries |
| `:app` | `app/` | Spring Boot composition root: controllers, config, wiring, health |
| `:gateway` | `gateway/` | WebSocket gateway: binary protocol, topic pub/sub for frontend |
| `:cli` | `cli/` | Operator CLI (picocli): interactive shell, standalone broker mode |
| `:trade-pipeline-platform` | `pipeline/platform/` | Pipeline template/definition/catalog services, validation, versioning |
| `:trade-node-library` | `nodes/` | Reusable pipeline nodes: scanner, output, feature, historical |
| `:trade-analytics` | `pipeline/analytics/` | Performance analytics: equity curve, drawdown, trade records |

| `:architecture-test` | `architecture-test/` | ArchUnit tests enforcing module boundaries |

---

## 2. API Endpoints Reference

### 2.1 Market Data API (`/api/v1/market`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/market/ltp` | `symbol` (str), `exchangeSegment` (NSE_EQ/BSE_EQ/NSE_FNO/IDX_I) | Last traded price |
| GET | `/api/v1/market/historical/candles` | `symbol`, `exchangeSegment`, `interval` (default: `1d`), `from`, `to`, `source` (default: `broker`) | Historical candles from broker or local store |
| GET | `/api/v1/market/expired-options/expiries` | `symbol`, `exchangeSegment` | List expired option expiries (Upstox Plus) |
| GET | `/api/v1/market/expired-options/contracts` | `symbol`, `exchangeSegment`, `expiry` | List expired contracts for an expiry |
| GET | `/api/v1/market/expired-options/candles` | `expiredInstrumentKey`, `interval` (default: `5minute`), `from`, `to` | Historical candles for expired options |

### 2.2 Analytics API (`/api/v1/analytics`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/analytics/catalog` | — | Federated analytics catalog snapshot |
| GET | `/api/v1/analytics/equity/candles` | `symbol`, `exchangeSegment` (default: `NSE_EQ`), `interval`, `from`, `to`, `limit` (5000) | Equity candles from parquet warehouse |
| GET | `/api/v1/analytics/equity/universe` | — | Nifty 500 universe listing |
| GET | `/api/v1/analytics/options/bars` | `underlying`, `expiryKind`, `expiryCode`, `strikeOffset`, `optionType`, `intervalMin` (5), `from`, `to`, `limit` (1000) | Rolling option bars |
| POST | `/api/v1/analytics/sql` | Body: `{"sql": "...", "limit": 100}` | Guarded read-only SQL |

### 2.3 Pipeline API (`/api/v1/pipeline`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/pipeline/templates` | — | Pipeline graph templates |
| GET | `/api/v1/pipeline/node-types` | — | All registered node types |
| GET | `/api/v1/pipeline/node-types/categories` | — | Node type categories |
| GET | `/api/v1/pipeline/node-types/category/{category}` | `category` (path) | Nodes by category |
| GET | `/api/v1/pipeline/graph` | — | Active pipeline graph |
| GET | `/api/v1/pipeline/dag/graphs` | — | Active DAG graphs |
| GET | `/api/v1/pipeline/dag/graph/{graphId}` | `graphId` (path) | Specific DAG graph |
| GET | `/api/v1/pipeline/history/{graphId}` | `graphId` (path) | Graph version history |
| GET | `/api/v1/pipeline/history/{graphId}/{version}` | `graphId`, `version` (path) | Specific graph version |
| POST | `/api/v1/pipeline/persist` | — | Persist active graph |
| POST | `/api/v1/pipeline/compile` | Body: PipelineGraph JSON | Compile and reload a graph |
| POST | `/api/v1/pipeline/dag/compile` | Body: PipelineGraph JSON | Compile DAG graph |
| POST | `/api/v1/pipeline/restore/{graphId}/{version}` | `graphId`, `version` (path) | Restore graph version |
| GET | `/api/v1/pipeline/stream/metrics` | — | SSE stream of pipeline metrics |

### 2.4 Scans API (`/api/v1/scans`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/scans/latest` | `profile` (str) | Latest scan result by profile |
| GET | `/api/v1/scans/{runId}` | `runId` (path) | Specific scan run |
| GET | `/api/v1/scans` | `profile`, `limit` (default: 10) | List recent scan runs |
| POST | `/api/v1/scans/run` | `profile` (optional) | Trigger a scan run |

### 2.5 Options Scan API (`/api/v1/options/scan`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| POST | `/api/v1/options/scan` | `underlying`, `segment` (IDX_I), `expiry`, `expiryDate`, `side` (both), `top` (10), `minOi` (1000), `minVolume` (0), `maxSpreadBps` (300), `strictSpread` (false) | Rank option contracts by liquidity |
| GET | `/api/v1/options/scan/expiries` | `underlying`, `segment` (IDX_I) | List available expiries |

### 2.6 Studio API (`/api/v1/studio`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/studio/startup-candidates` | `date` (optional), `topN` (default: 3) | Startup candidates for charting |
| GET | `/api/v1/studio/chart` | `symbol`, `exchangeSegment` (NSE_EQ), `interval` (5m), `from`, `to`, optional overlay params | Chart data (candles + option overlay) |

### 2.7 Symbols API (`/api/v1/symbols`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/symbols` | `refresh` (default: false) | All available trading symbols |

### 2.8 Read Model API (`/api/v1`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/api/v1/read-model` | — | Snapshot of orders, positions, ticks, depths, candles, signals, PnL |
| GET | `/api/v1/stream/read-model` | — | SSE stream of read model |

### 2.9 Admin API (`/admin`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| GET | `/admin/runtime` | — | Runtime status (WS, circuit breaker, catalog) |
| POST | `/admin/risk/kill-switch/{enabled}` | `enabled` (path: true/false) | Toggle kill switch |
| GET | `/admin/pipeline` | — | Pipeline metrics |
| GET | `/admin/strategies` | — | Plugin names |
| GET | `/admin/summary` | — | Aggregated runtime summary |
| GET | `/admin/rate-limit` | — | Rate limit metrics |
| POST | `/admin/reconcile` | Body: `{"SYMBOL": quantity}` | Trigger order reconciliation |
| GET | `/admin/historical/candles` | `symbol`, `interval`, `from`, `to`, `limit` | Historical candles (local) |
| GET | `/admin/historical/ticks` | `symbol`, `from`, `to`, `limit` | Historical ticks |
| GET | `/admin/historical/orders` | `symbol`, `from`, `to`, `limit` | Historical orders |
| GET | `/admin/historical/fills` | `symbol`, `from`, `to`, `limit` | Historical fills |
| GET | `/admin/historical/fill-events` | `symbol`, `from`, `to`, `limit` | Historical fill events |
| GET | `/admin/historical/stats` | `symbol`, `from`, `to` | Historical data stats |
| POST | `/admin/historical/replay/ticks` | `symbol`, `from`, `to` | Replay ticks |
| POST | `/admin/historical/replay/candles` | `symbol`, `interval`, `from`, `to` | Replay candles |
| POST | `/admin/historical/replay/fills` | `symbol`, `from`, `to` | Replay fill events |
| POST | `/admin/historical/replay/orders` | `symbol`, `from`, `to` | Replay orders |
| POST | `/admin/chronicle/replay` | `eventType` | Replay from Chronicle audit log |

### 2.10 Historical Download API (`/admin`)

| Method | Path | Parameters | Description |
|--------|------|------------|-------------|
| POST | `/admin/download/jobs` | Body: RollingOptionJobRequest | Start rolling option download job |
| POST | `/admin/download/jobs/equity` | Body: EquityJobRequest | Start equity download job |
| POST | `/admin/universe/nifty500/refresh` | — | Refresh Nifty 500 universe |
| GET | `/admin/download/jobs` | `source`, `limit` (20) | List download jobs |
| GET | `/admin/download/jobs/{jobId}` | `jobId` (path) | Job status |
| POST | `/admin/download/jobs/{jobId}/resume` | `jobId` (path), `async` (true) | Resume job |
| POST | `/admin/historical/equity/import-hive` | Body: ImportHiveRequest | Import from Hive cache |

### 2.11 Spring Actuator

| Method | Path | Description |
|--------|------|-------------|
| GET | `/actuator/health` | Health (liveness + readiness probes) |
| GET | `/actuator/info` | App info |
| GET | `/actuator/prometheus` | Prometheus metrics |

### 2.12 Dashboard

| Method | Path | Description |
|--------|------|-------------|
| GET | `/`, `/console`, `/console/` | Redirect to React SPA |

### 2.13 WebSocket Gateway

| Path | Protocol | Description |
|------|----------|-------------|
| `/ws/gateway` | Binary WebSocket | Real-time event streaming (ticks, candles, orders, positions, signals, PnL) |

---

## 3. Backend Storage

| Storage | Technology | Purpose |
|---------|------------|---------|
| Event Store | DuckDB | Domain event persistence for replay |
| Feature Store | DuckDB + In-Memory | ML feature storage |
| Historical Warehouse | DuckDB | Rolling option bars |
| Historical Equity | Parquet files | Nifty 500 equity 1m bars |
| Audit Log | Chronicle Queue | Off-heap low-latency trade audit |
| Scan Store | DuckDB | Scan result persistence |
| Pipeline Graph Store | DuckDB | Pipeline graph versioning |
| Position Tracking | In-Memory | Event-sourced net positions |
| Idempotency Cache | Caffeine | Order idempotency |

---

## 4. Supported Exchanges / Venues

| Venue | Feed Modes | Session |
|-------|-----------|---------|
| NSE_EQ | TICKER, QUOTE, FULL, DEPTH_20 | 09:15-15:30 |
| NSE_FNO | TICKER, QUOTE, FULL, DEPTH_20 | 09:15-15:30 |
| BSE_EQ | TICKER, QUOTE, FULL, DEPTH_20 | 09:15-15:30 |
| BSE_FNO | TICKER, QUOTE, FULL, DEPTH_20 | 09:15-15:30 |
| MCX_COMM | TICKER, QUOTE, FULL, DEPTH_20 | 09:00-23:30 |
| IDX_I | TICKER, QUOTE | 09:15-15:30 |

---

## 5. Spring Profiles

| Profile | Broker | Use Case |
|---------|--------|----------|
| `dev` (default) | Dhan Sandbox | Local development |
| `dev-live` | Dhan Live | Live market data locally |
| `prod` | Dhan Live | Production |
| `upstox-dev` | Upstox Sandbox | Upstox dev trading |
| `upstox-prod` | Upstox Live | Upstox live trading |
| `upstox-analytics` | Upstox Live (read-only) | Market data only (1yr token) |
| `icici-prod` | ICICI Breeze Live | ICICI live trading |
| `replay` | None | Historical replay |

---

## 6. Quick Start Commands

```bash
# Sandbox broker (default)
./gradlew :app:bootRun

# Live market data
./gradlew :app:bootRun --args='--spring.profiles.active=dev-live'

# Upstox analytics-only
./gradlew :app:bootRun --args='--spring.profiles.active=upstox-analytics'

# Production
SPRING_PROFILES_ACTIVE=prod ./gradlew :app:bootRun

# CLI interactive
./scripts/tradej interactive

# Frontend dev server
cd frontend && npm run dev

# Run tests
./gradlew test              # unit + component
./gradlew integrationTest   # integration tests
./scripts/run-full-regression.sh
```

---

## 7. End-to-End Data Flow

```
Broker (Dhan/Upstox/ICICI)
  │
  ├── Market Data WS ──► MarketDataPipeline ──► Disruptor Event Bus
  │                                                │
  ├── REST API ◄── Controller Layer (Spring)       │
  │                                                │
  └── Order REST ◄── OMS ──► Execution Handler ───┘
                          │
                          ▼
                    Risk Handler (Circuit Breaker)
                          │
                          ▼
                    Broker REST ──► Exchange
                          │
                          ▼
                    Event Store (DuckDB) / Audit Log (Chronicle)
                          │
                          ▼
                    WebSocket Gateway ──► React Frontend / CLI
```
