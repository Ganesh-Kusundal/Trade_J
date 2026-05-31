# Trade-J Usage Guide

## Prerequisites

- **Java 21** (set `JAVA_HOME` to JDK 21)
- **Node.js 20+** (for frontend)
- **pnpm** (for frontend dependency management)

## 1. Setup Credentials

Copy the example credential files and fill in your broker credentials:

```bash
cp config/dhan-local.properties.example config/dhan-local.properties
cp config/dhan-sandbox.properties.example config/dhan-sandbox.properties
# Edit with your credentials
$EDITOR config/dhan-local.properties
$EDITOR config/dhan-sandbox.properties
```

**Never commit** `config/*.properties`, `config/*-pin.txt`, `config/*-totp-secret.txt`, or `config/*-password.txt`.

## 2. Running the Application

### Development (Dhan Sandbox — default)

```bash
./gradlew :app:bootRun
# Starts on http://localhost:8080
# Uses sandbox.dhan.co for order placement
```

### Live Market Data

```bash
./gradlew :app:bootRun --args='--spring.profiles.active=dev-live'
```

### Production

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew :app:bootRun
```

### Upstox Analytics-Only (1-year read-only token)

```bash
# Configure config/upstox-live.properties with analyticsToken
./gradlew :app:bootRun --args='--spring.profiles.active=upstox-analytics'
```

### ICICI Direct

```bash
# Configure config/icici-local.properties
./gradlew :app:bootRun --args='--spring.profiles.active=icici-prod'
```

## 3. Frontend

```bash
cd frontend
pnpm install
pnpm run dev
# Starts on http://localhost:5173 (proxies API to localhost:8080)
```

Build for production:

```bash
cd frontend && pnpm run build
# Output in frontend/dist/ — served by Spring Boot at /console/index.html
```

## 4. CLI (Operator Console)

```bash
# Interactive mode
./scripts/tradej interactive

# Attach to running app for status
./scripts/tradej status

# Standalone Dhan mode (no app needed)
./scripts/tradej quote NIFTY IDX_I

# Standalone Upstox analytics mode
./scripts/tradej --broker upstox ltp SBIN NSE_EQ

# Sandbox orders
./scripts/tradej --profile sandbox balance
```

## 5. Testing the API

```bash
# Start the app first, then:
./scripts/test-api.sh http://localhost:8080

# With verbose output:
./scripts/test-api.sh http://localhost:8080 --verbose
```

### Quick curl examples

```bash
# Health check
curl -s http://localhost:8080/actuator/health | jq .

# LTP
curl -s 'http://localhost:8080/api/v1/market/ltp?symbol=NIFTY&exchangeSegment=IDX_I' | jq .

# Options scan
curl -s -X POST 'http://localhost:8080/api/v1/options/scan?underlying=NIFTY&segment=IDX_I&top=5' | jq .

# Symbols catalog
curl -s 'http://localhost:8080/api/v1/symbols' | jq .

# Runtime status
curl -s 'http://localhost:8080/admin/runtime' | jq .

# Kill switch
curl -s -X POST 'http://localhost:8080/admin/risk/kill-switch/false' | jq .

# Read model snapshot
curl -s 'http://localhost:8080/api/v1/read-model' | jq .

# Analytics catalog
curl -s 'http://localhost:8080/api/v1/analytics/catalog' | jq .

# Custom SQL query
curl -s -X POST 'http://localhost:8080/api/v1/analytics/sql' \
  -H 'Content-Type: application/json' \
  -d '{"sql": "SELECT 1 as test"}' | jq .
```

## 6. Running Tests

```bash
# Unit + component tests (default)
./gradlew test

# Integration tests (requires live Dhan credentials)
./gradlew integrationTest

# Specific test suite
./gradlew :app:brokerRestTest --no-daemon
./gradlew :app:brokerWsTest --no-daemon
./gradlew :app:brokerOrderTest --no-daemon

# Full regression
./scripts/run-full-regression.sh
```

## 7. Data Download Jobs

```bash
# Download Nifty 500 equity 1m bars
curl -X POST http://localhost:8080/admin/download/jobs/equity \
  -H 'Content-Type: application/json' \
  -d '{
    "universe": "NIFTY_500",
    "exchangeSegment": "NSE_EQ",
    "from": "2026-01-01",
    "to": "2026-05-30",
    "interval": "1m"
  }'

# Download rolling option bars
curl -X POST http://localhost:8080/admin/download/jobs \
  -H 'Content-Type: application/json' \
  -d '{
    "symbols": "NIFTY,BANKNIFTY",
    "exchangeSegment": "NSE_FNO",
    "from": "2026-01-01",
    "to": "2026-05-30"
  }'

# Check job status
curl -s 'http://localhost:8080/admin/download/jobs?source=EQUITY_INTRADAY'

# Refresh Nifty 500 universe
curl -s -X POST 'http://localhost:8080/admin/universe/nifty500/refresh'
```

## 8. Historical Replay

```bash
# Replay ticks (blocked in LIVE mode — use replay profile)
curl -X POST 'http://localhost:8080/admin/historical/replay/ticks' \
  -d 'symbol=NIFTY&from=1700000000000&to=1760000000000'

# Replay candles
curl -X POST 'http://localhost:8080/admin/historical/replay/candles' \
  -d 'symbol=NIFTY&interval=5m&from=1700000000000&to=1760000000000'
```

## 9. WebSocket Gateway

The WebSocket gateway at `/ws/gateway` provides real-time event streaming:

- Market ticks and depth updates
- Candle development and close events
- Order lifecycle events (accepted, filled, rejected, cancelled)
- Position updates and PnL snapshots
- Signal events
- Scan results

Connect from frontend at `ws://localhost:8080/ws/gateway` using the binary protocol.

## 10. Architecture Overview

```
┌──────────┐   ┌──────────┐   ┌──────────┐
│  CLI     │   │ Frontend │   │ Gateway  │
│(picocli) │   │ (React)  │   │ (WS)     │
└────┬─────┘   └────┬─────┘   └────┬─────┘
     │              │              │
     └──────────────┴──────────────┘
                    │
              ┌─────┴─────┐
              │  App      │
              │ (Spring)  │
              └─────┬─────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────┴────┐  ┌─────┴─────┐  ┌─────┴────┐
│Strategy │  │Execution  │  │ Scanner  │
│Engine   │  │ OMS/Risk  │  │ Engine   │
└────┬────┘  └─────┬─────┘  └─────┬────┘
     │              │              │
     └──────────────┼──────────────┘
                    │
          ┌─────────┴─────────┐
          │  Disruptor Bus    │
          │  (Event Bus)      │
          └─────────┬─────────┘
                    │
     ┌──────────────┼──────────────┐
     │              │              │
┌────┴────┐  ┌─────┴─────┐  ┌─────┴────┐
│  Dhan   │  │  Upstox   │  │  ICICI   │
│ (SDK)   │  │ (REST+WS) │  │ (Breeze) │
└─────────┘  └───────────┘  └──────────┘
                    │
          ┌─────────┴─────────┐
          │  Data Layer       │
          │  DuckDB / Parquet │
          │  / Chronicle Q    │
          └───────────────────┘
```
