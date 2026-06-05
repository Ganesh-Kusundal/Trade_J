# Trade-J API Documentation

Java 21 algorithmic trading platform with live market data, OMS, risk management, strategy plugins, and broker adapters (Dhan, Upstox, ICICI).

## Base URLs

| Environment | URL |
|-------------|-----|
| Local Development | `http://localhost:8080` |
| Production | `https://api.tradej.example.com` |

## Authentication

API uses Spring Security with session-based authentication. Admin endpoints require appropriate permissions.

---

## API Endpoints

### Market Data

#### Get Last Traded Price
```
GET /api/v1/market/ltp
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Trading symbol (e.g., SBIN) |
| exchangeSegment | string | Yes | Exchange segment (NSE_EQ, NSE_FNO, etc.) |

**Response:**
```json
{
  "symbol": "SBIN",
  "exchangeSegment": "NSE_EQ",
  "ltpPaisa": 75000,
  "exchangeTimestampMs": 1234567890000
}
```

#### Get Historical Candles
```
GET /api/v1/market/historical/candles
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| exchangeSegment | string | Yes | - | Exchange segment |
| interval | string | No | 1d | Candle interval (1m, 3m, 5m, 15m, 1h, 1d) |
| from | string | Yes | - | Start date (YYYY-MM-DD) |
| to | string | Yes | - | End date (YYYY-MM-DD) |
| source | string | No | broker | Data source: `broker` or `parquet` |

**Response:**
```json
{
  "symbol": "SBIN",
  "exchangeSegment": "NSE_EQ",
  "interval": "5m",
  "from": "2024-01-01",
  "to": "2024-01-31",
  "count": 8000,
  "candles": [
    {
      "symbol": "SBIN",
      "startTimeMs": 1704067200000,
      "endTimeMs": 1704067500000,
      "openPaisa": 74500,
      "highPaisa": 74600,
      "lowPaisa": 74400,
      "closePaisa": 74550,
      "volume": 150000
    }
  ]
}
```

#### Expired Options - List Expiries
```
GET /api/v1/market/expired-options/expiries
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Underlying symbol |
| exchangeSegment | string | Yes | Exchange segment |

#### Expired Options - List Contracts
```
GET /api/v1/market/expired-options/contracts
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Underlying symbol |
| exchangeSegment | string | Yes | Exchange segment |
| expiry | string | Yes | Expiry date (YYYY-MM-DD) |

#### Expired Options - Get Candles
```
GET /api/v1/market/expired-options/candles
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| expiredInstrumentKey | string | Yes | - | Broker instrument key |
| interval | string | No | 5minute | Candle interval |
| from | string | Yes | - | Start date |
| to | string | Yes | - | End date |

---

### Analytics

#### Analytics Catalog Snapshot
```
GET /api/v1/analytics/catalog
```

Returns metadata about the analytics data warehouse.

#### Equity Candles from Parquet
```
GET /api/v1/analytics/equity/candles
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| exchangeSegment | string | No | NSE_EQ | Exchange segment |
| interval | string | Yes | - | Candle interval |
| from | string | Yes | - | Start date |
| to | string | Yes | - | End date |
| limit | integer | No | 5000 | Max candles to return |

#### Nifty 500 Universe
```
GET /api/v1/analytics/equity/universe
```

Returns the Nifty 500 universe listing.

#### Rolling Option Bars
```
GET /api/v1/analytics/options/bars
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| underlying | string | Yes | Underlying symbol |
| expiryKind | string | Yes | Expiry kind |
| expiryCode | integer | Yes | Expiry code |
| strikeOffset | integer | Yes | Strike offset |
| optionType | string | Yes | CALL, PUT, or UNKNOWN |
| intervalMin | integer | No | 5 | Interval in minutes |
| from | integer | Yes | Epoch milliseconds |
| to | integer | Yes | Epoch milliseconds |
| limit | integer | No | 1000 | Max bars |

#### Run Guarded SQL
```
POST /api/v1/analytics/sql
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| sql | string | Yes | Read-only SQL query |
| limit | integer | No | 100 | Max rows |

---

### Pipeline

#### Pipeline Graph Templates
```
GET /api/v1/pipeline/templates
```

Returns available pipeline graph templates.

#### Get All Node Types
```
GET /api/v1/pipeline/node-types
```

Returns all registered pipeline node types with their descriptors.

#### Get Node Type Categories
```
GET /api/v1/pipeline/node-types/categories
```

Returns list of node type categories.

#### Get Node Types by Category
```
GET /api/v1/pipeline/node-types/category/{category}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| category | string | Yes | Category name |

#### Get Active Pipeline Graph
```
GET /api/v1/pipeline/graph
```

Returns the currently active pipeline graph.

#### Get Active DAG Graphs
```
GET /api/v1/pipeline/dag/graphs
```

Returns all active DAG graphs.

#### Get Specific DAG Graph
```
GET /api/v1/pipeline/dag/graph/{graphId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| graphId | string | Yes | Graph identifier |

#### List Graph Versions
```
GET /api/v1/pipeline/history/{graphId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| graphId | string | Yes | Graph identifier |

#### Load Graph Version
```
GET /api/v1/pipeline/history/{graphId}/{version}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| graphId | string | Yes | Graph identifier |
| version | integer | Yes | Version number |

#### Persist Active Graph
```
POST /api/v1/pipeline/persist
```

Persists the current active graph to storage.

#### Compile Pipeline Graph
```
POST /api/v1/pipeline/compile
```

Compiles and reloads a pipeline graph.

**Request Body:**
```json
{
  "id": "graph-id",
  "name": "Graph Name",
  "version": 1,
  "nodes": [...],
  "edges": [...],
  "executionMode": "HOT_PATH"
}
```

#### Compile DAG Graph
```
POST /api/v1/pipeline/dag/compile
```

Compiles a DAG-specific pipeline graph.

#### Restore Graph Version
```
POST /api/v1/pipeline/restore/{graphId}/{version}
```

Restores a specific graph version.

#### Stream Pipeline Metrics (SSE)
```
GET /api/v1/pipeline/stream/metrics
```

Server-Sent Events stream of pipeline metrics.

---

### Scans

#### Get Latest Scan Result
```
GET /api/v1/scans/latest
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| profile | string | Yes | Scan profile name |

#### Get Scan by Run ID
```
GET /api/v1/scans/{runId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| runId | string | Yes | Scan run identifier |

#### List Recent Scan Runs
```
GET /api/v1/scans
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| profile | string | Yes | - | Scan profile name |
| limit | integer | No | 10 | Max runs to return |

#### Trigger Scan Run
```
POST /api/v1/scans/run
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| profile | string | No | Scan profile (uses default if omitted) |

---

### Options Scan

#### Rank Option Contracts
```
POST /api/v1/options/scan
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| underlying | string | Yes | - | Underlying symbol |
| segment | string | No | IDX_I | Exchange segment |
| expiry | string | No | - | Expiry date |
| expiryDate | string | No | - | Expiry date (YYYY-MM-DD) |
| side | string | No | both | CE, PE, or both |
| top | integer | No | 10 | Top N contracts |
| minOi | integer | No | 1000 | Minimum open interest |
| minVolume | integer | No | 0 | Minimum volume |
| maxSpreadBps | number | No | 300 | Max spread in basis points |
| strictSpread | boolean | No | false | Strict spread filter |

#### List Available Expiries
```
GET /api/v1/options/scan/expiries
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| underlying | string | Yes | Underlying symbol |
| segment | string | No | IDX_I | Exchange segment |

---

### Studio

#### Get Startup Candidates
```
GET /api/v1/studio/startup-candidates
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| date | string | No | Today | Date (YYYY-MM-DD) |
| topN | integer | No | 3 | Number of candidates |

Returns ranked symbols for initial chart display.

#### Get Chart Data
```
GET /api/v1/studio/chart
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| exchangeSegment | string | No | NSE_EQ | Exchange segment |
| interval | string | No | 5m | Candle interval |
| from | string | Yes | - | Start date |
| to | string | Yes | - | End date |
| optionUnderlying | string | No | - | Option underlying |
| optionExpiryKind | string | No | - | Option expiry kind |
| optionExpiryCode | integer | No | - | Option expiry code |
| optionStrikeOffset | integer | No | - | Strike offset |
| optionType | string | No | - | CALL or PUT |
| optionIntervalMin | integer | No | 5 | Option interval |

**Response:**
```json
{
  "symbol": "SBIN",
  "exchangeSegment": "NSE_EQ",
  "interval": "5m",
  "from": "2024-01-01",
  "to": "2024-01-31",
  "count": 8000,
  "candles": [...],
  "halfTrend": [...],
  "cvd": [...],
  "markers": [...],
  "structureBreaks": [...],
  "orderBlockZones": [...]
}
```

---

### Symbols

#### Get All Symbols
```
GET /api/v1/symbols
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| refresh | boolean | No | false | Force catalog refresh |

Returns all available trading symbols with metadata.

---

### Read Model

#### Get Read Model Snapshot
```
GET /api/v1/read-model
```

Returns snapshot of runtime state including orders, positions, ticks, depths, candles, signals, and PnL.

#### Stream Read Model (SSE)
```
GET /api/v1/stream/read-model
```

Server-Sent Events stream of read model updates.

---

### Admin - Runtime

#### Get Runtime Status
```
GET /admin/runtime
```

Returns runtime health information.

**Response:**
```json
{
  "websocketConnected": true,
  "circuitBreakerOpen": false,
  "subscriptions": 50,
  "catalogLoaded": true,
  "catalogSize": 2500,
  "brokerPreflightPassed": true,
  "startupCompleted": true
}
```

#### Toggle Kill Switch
```
POST /admin/risk/kill-switch/{enabled}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| enabled | boolean | Yes | Enable/disable kill switch |

#### Get Pipeline Metrics
```
GET /admin/pipeline
```

Returns detailed pipeline metrics including disruptor stats, execution queue, and market data pipeline.

#### Get Strategies
```
GET /admin/strategies
```

Returns list of loaded strategy plugins.

#### Get Summary
```
GET /admin/summary
```

Returns aggregated runtime summary.

#### Get Rate Limit Metrics
```
GET /admin/rate-limit
```

Returns rate limit token bucket state.

#### Trigger Reconciliation
```
POST /admin/reconcile
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| (symbol: quantity pairs) | object | Yes | Expected net positions |

---

### Admin - Historical

#### Get Historical Candles
```
GET /admin/historical/candles
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| interval | string | No | 5m | Candle interval |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |
| limit | integer | No | 1000 | Max candles |

#### Get Historical Ticks
```
GET /admin/historical/ticks
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |
| limit | integer | No | 5000 | Max ticks |

#### Get Historical Orders
```
GET /admin/historical/orders
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | No | "" | Filter by symbol |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |
| limit | integer | No | 500 | Max orders |

#### Get Historical Fills
```
GET /admin/historical/fills
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | No | "" | Filter by symbol |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |
| limit | integer | No | 500 | Max fills |

#### Get Historical Fill Events
```
GET /admin/historical/fill-events
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | No | "" | Filter by symbol |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |
| limit | integer | No | 500 | Max events |

#### Get Historical Stats
```
GET /admin/historical/stats
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Trading symbol |
| from | integer | Yes | Epoch milliseconds |
| to | integer | Yes | Epoch milliseconds |

---

### Admin - Replay

#### Replay Ticks
```
POST /admin/historical/replay/ticks
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Trading symbol |
| from | integer | Yes | Epoch milliseconds |
| to | integer | Yes | Epoch milliseconds |

#### Replay Candles
```
POST /admin/historical/replay/candles
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| symbol | string | Yes | - | Trading symbol |
| interval | string | No | 5m | Candle interval |
| from | integer | Yes | - | Epoch milliseconds |
| to | integer | Yes | - | Epoch milliseconds |

#### Replay Fill Events
```
POST /admin/historical/replay/fills
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | No | Filter by symbol |
| from | integer | Yes | Epoch milliseconds |
| to | integer | Yes | Epoch milliseconds |

#### Replay Orders
```
POST /admin/historical/replay/orders
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | No | Filter by symbol |
| from | integer | Yes | Epoch milliseconds |
| to | integer | Yes | Epoch milliseconds |

#### Replay from Chronicle
```
POST /admin/chronicle/replay
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| eventType | string | Yes | Fully qualified domain event class name |

---

### Admin - Download Jobs

#### Start Rolling Option Download
```
POST /admin/download/jobs
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| symbols | string | Yes | Comma-separated symbols |
| exchangeSegment | string | Yes | Exchange segment |
| from | string | Yes | Start date |
| to | string | Yes | End date |
| intervals | string | Yes | Comma-separated intervals |
| expiries | string | Yes | Comma-separated expiries |
| strikes | string | Yes | Comma-separated strikes |
| optionTypes | string | Yes | Comma-separated option types |
| delayMs | integer | No | Delay between requests |
| resume | boolean | No | Resume from checkpoint |
| runAsync | boolean | No | Run asynchronously |

#### List Download Jobs
```
GET /admin/download/jobs
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| source | string | Yes | - | ROLLING_OPTION or EQUITY_INTRADAY |
| limit | integer | No | 20 | Max jobs |

#### Get Download Job Status
```
GET /admin/download/jobs/{jobId}
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| jobId | string | Yes | Job identifier |

#### Resume Download Job
```
POST /admin/download/jobs/{jobId}/resume
```

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| jobId | string | Yes | - | Job identifier |
| async | boolean | No | true | Run asynchronously |

#### Refresh Nifty 500 Universe
```
POST /admin/universe/nifty500/refresh
```

#### Import from Hive
```
POST /admin/historical/equity/import-hive
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| sourceHive | string | Yes | Source Hive path |
| universeCsv | string | Yes | Universe CSV path |
| industryParquet | string | Yes | Industry parquet path |
| rootPath | string | Yes | Target root path |
| fromMonth | string | Yes | From month |
| toMonth | string | Yes | To month |
| force | boolean | No | Force import |
| symbols | string | No | Filter symbols |
| skipUniverseImport | boolean | No | Skip universe |
| runAsync | boolean | No | Run asynchronously |

---

### Research API

#### Get Replay Status
```
GET /api/research/replay/status
```

Returns current replay controller state.

#### Start Replay
```
POST /api/research/replay/start
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| symbol | string | Yes | Trading symbol |
| exchange | string | Yes | Exchange segment |
| fromMs | integer | Yes | Start epoch ms |
| toMs | integer | Yes | End epoch ms |

#### Control Replay
```
POST /api/research/replay/play
POST /api/research/replay/pause
POST /api/research/replay/step
POST /api/research/replay/stop
POST /api/research/replay/speed?multiplier={speed}
```

#### List Experiments
```
GET /api/research/experiments
```

#### Create Experiment
```
POST /api/research/experiments
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| name | string | Yes | Experiment name |
| hypothesis | string | Yes | Hypothesis |
| pipelineType | string | Yes | REPLAY, SCANNER, or STRATEGY |
| owner | string | Yes | Owner name |

#### Get Experiment
```
GET /api/research/experiments/{id}
```

#### Get Experiment Runs
```
GET /api/research/experiments/{experimentId}/runs
```

#### Start Experiment Run
```
POST /api/research/experiments/{experimentId}/runs
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| (parameters) | object | Yes | Run parameters |

#### Get Run Results
```
GET /api/research/analytics/run-results
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| sessionId | string | No | Filter by session |

#### Get Trade Log
```
GET /api/research/analytics/trade-log
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| runId | string | Yes | Run identifier |

#### Get Sessions
```
GET /api/research/analytics/sessions
```

#### Run Parameter Sweep
```
POST /api/research/lab/sweep
```

| Body | Type | Required | Description |
|------|------|----------|-------------|
| sessionId | string | Yes | Session identifier |
| strategyName | string | Yes | Strategy name |
| strategyVersion | string | Yes | Strategy version |
| symbols | array | Yes | Target symbols |
| fromMs | integer | Yes | Start epoch ms |
| toMs | integer | Yes | End epoch ms |
| parameterRanges | object | Yes | Parameter ranges |
| objectiveMetric | string | No | Objective metric |
| maximize | boolean | No | Maximize objective |
| slippageBps | number | No | Slippage in bps |
| commissionPaisa | integer | No | Commission in paisa |
| fillRatio | number | No | Fill ratio |
| maxConfigs | integer | No | Max configurations |

---

### MCP (Model Context Protocol)

#### MCP SSE Connection
```
GET /mcp/sse
```

Establishes SSE channel for MCP JSON-RPC communication.

#### MCP Message
```
POST /mcp/message
```

Handles JSON-RPC requests. Available tools:

| Tool | Description |
|------|-------------|
| `list_active_scanners` | List active scanner profiles |
| `get_scanner_hits` | Get scanner hits from DuckDB |
| `get_scanner_status` | Get scanner aggregate status |
| `run_scanner_dryrun` | Run read-only scanner dryrun |
| `list_strategies` | List strategy configurations |
| `get_strategy_config` | Get strategy by config hash |
| `get_strategy_run_results` | Get strategy run results |
| `get_strategy_trade_log` | Get trade log for run |
| `list_replay_sessions` | List replay sessions |
| `get_replay_status` | Get replay controller status |
| `get_replay_candles` | Get historical candles |
| `get_replay_indicators` | Get indicator values |
| `get_performance_metrics` | Get Sharpe, Sortino, win rates |
| `get_drawdown_stats` | Get drawdown statistics |
| `get_mae_mfe_scatter` | Get MAE/MFE excursions |
| `get_execution_quality` | Get execution quality metrics |

---

### Actuator

#### Health Check
```
GET /actuator/health
```

Returns liveness and readiness status.

#### Application Info
```
GET /actuator/info
```

Returns application information.

#### Prometheus Metrics
```
GET /actuator/prometheus
```

Returns Prometheus scrape endpoint.

---

### Dashboard

#### Root Redirect
```
GET /
```

Redirects to `/console/index.html`.

#### Console
```
GET /console
GET /console/index.html
```

Serves the React SPA for the operator console.

---

## Swagger UI

The API includes integrated Swagger UI for interactive documentation.

### Access Swagger UI
```
GET /swagger-ui.html
```

Or access the API docs directly:
```
GET /v3/api-docs
```

### Swagger UI Configuration
- **Path**: `/swagger-ui.html`
- **API Docs**: `/v3/api-docs`
- **Operations Sorter**: Method
- **Tags Sorter**: Alpha
- **Actuator Endpoints**: Included

---

## WebSocket Gateway

```
WebSocket /ws/gateway
```

Real-time market data and event streaming.

**Message Format:**
```json
{
  "topic": "CANDLE_CLOSED",
  "payload": {
    "symbol": "SBIN",
    "startTimeMs": 1704067200000,
    "openPaisa": 74500,
    "highPaisa": 74600,
    "lowPaisa": 74400,
    "closePaisa": 74550,
    "volume": 150000
  }
}
```

**Topics:**
- `CANDLE_CLOSED` - Completed candle
- `CANDLE_DEVELOPING` - Live updating candle
- `MARKET_TICK` - Live market tick
- `POSITION_UPDATE` - Position changes
- `ORDER_UPDATE` - Order updates
- `PNL_UPDATE` - PnL updates

---

## Error Responses

All endpoints return standard HTTP status codes. Error responses follow:

```json
{
  "error": "Error message",
  "hint": "Optional hint for resolution"
}
```

Common status codes:
- `200` - Success
- `400` - Bad request
- `401` - Unauthorized
- `404` - Not found
- `409` - Conflict (e.g., replay blocked in LIVE mode)
- `500` - Internal server error
- `503` - Service unavailable (catalog not loaded)

