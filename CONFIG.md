# Configuration and Spring Profiles

## Spring profiles

| Profile | Active when | Credential files | Broker API | Storage root |
|---------|-------------|------------------|------------|--------------|
| `dev` (default) | Local `bootRun` | `config/dhan-local.properties` + `config/dhan-sandbox.properties` | SANDBOX | `runtime-dev/` |
| `dev-live` | Live market data locally | Same as `dev` | LIVE | `runtime-dev/` |
| `test` | Spring runtime in test profile | `config/dhan-sandbox.properties` (+ optional local for TOTP) | SANDBOX | `runtime-test/` |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | `config/dhan-local.properties` only | LIVE | `runtime-prod/` |
| `upstox-dev` | Upstox sandbox trading | `config/upstox-sandbox.properties` | Upstox sandbox | `runtime-dev/` |
| `upstox-prod` | Upstox live trading | `config/upstox-live.properties` | Upstox live | `runtime-prod/` |
| `upstox-analytics` | Upstox read-only data | `config/upstox-live.properties` | Upstox live (analytics token) | `runtime-dev/` |
| `icici-prod` | ICICI Direct live (read-only by default) | `config/icici-local.properties` | ICICI Breeze live | `runtime-prod/` |

Profile group `dev-live` loads `application-dev.yml` then `application-dev-live.yml` (live broker override plus the same `runtime-dev/` storage paths).

Set `trade.broker-type=upstox` via the Upstox profiles above (not the default Dhan `dev` profile).
Set `trade.broker-type=icici` via the `icici-prod` profile.

**Note:** Default `dev` (sandbox) starts Spring successfully but startup preflight may fail on portfolio/fund-limit calls that still use the live SDK path. Use `dev-live` for full runtime smoke (market data, WS, historical preflight).

## Credential files (gitignored)

| File | Used by |
|------|---------|
| [config/dhan-local.properties](config/dhan-local.properties) | Live broker, `dev-live`, `prod`, TOTP refresh |
| [config/dhan-sandbox.properties](config/dhan-sandbox.properties) | Sandbox orders, `dev`, `test` |
| [config/dhan-pin.txt](config/dhan-pin.txt) | TOTP live auth |
| [config/dhan-totp-secret.txt](config/dhan-totp-secret.txt) | TOTP live auth |
| [config/upstox-sandbox.properties](config/upstox-sandbox.properties) | Upstox sandbox OAuth + access token |
| [config/upstox-live.properties](config/upstox-live.properties) | Upstox live access + analytics tokens |
| [config/icici-local.properties](config/icici-local.properties) | ICICI Breeze AppKey/Secret |
| [config/icici-totp-secret.txt](config/icici-totp-secret.txt) | ICICI TOTP auth |

Copy from `config/dhan-local.properties.example`, [config/dhan-sandbox.properties.example](config/dhan-sandbox.properties.example), [config/upstox-sandbox.properties.example](config/upstox-sandbox.properties.example), [config/upstox-live.properties.example](config/upstox-live.properties.example), and [config/icici-local.properties.example](config/icici-local.properties.example).

### Upstox analytics token

The **analytics token** is a 1-year read-only credential from the Upstox Developer Apps → Analytics tab. It uses the same `Authorization: Bearer` header as the daily access token for **REST market data only** (quotes, LTP, historical candles, option chain). It cannot place orders, read portfolio/profile/funds, estimate margin, authorize the live WebSocket market feed, or call **Plus expired-instruments APIs**.

**Plus expired instruments** require the short-lived **algo/access token** (`upstox.live.accessToken`) with `isPlusPlan: true`. In `upstox-analytics` profile, keep both tokens configured: analytics for LTP/historical, access for `/api/v1/market/expired-options/*`.

Use profile `upstox-analytics` with `upstox.live.analyticsToken` set in `config/upstox-live.properties`. Trading and margin ports return `UnsupportedOperationException` in this mode. WebSocket connect is skipped at startup.

**Plus plan:** Expired instruments APIs (historical expired option contracts and candles) require an analytics token with Plus entitlements (`isPlusPlan: true` in the JWT). Configure the token via `upstox.live.analyticsToken` or `UPSTOX_ANALYTICS_TOKEN` — never commit the token.

### Market data HTTP APIs (broker REST)

| Endpoint | Source |
|----------|--------|
| `GET /api/v1/market/ltp?symbol=SBIN&exchangeSegment=NSE_EQ` | Upstox REST via active broker |
| `GET /api/v1/market/historical/candles?symbol=SBIN&exchangeSegment=NSE_EQ&interval=1d&from=2025-01-01&to=2025-01-31` | Upstox REST via active broker |
| `GET /api/v1/market/expired-options/expiries?symbol=NIFTY&exchangeSegment=IDX_I` | Upstox expired instruments (Plus; `upstox-analytics` profile) |
| `GET /api/v1/market/expired-options/contracts?symbol=NIFTY&exchangeSegment=IDX_I&expiry=2024-11-27` | Upstox expired instruments |
| `GET /api/v1/market/expired-options/candles?expiredInstrumentKey=NSE_FO%7C...&interval=5minute&from=2024-11-20&to=2024-11-27` | Upstox expired historical candles (OHLCV + OI; no IV/spot) |

### Local historical APIs (DuckDB)

| Endpoint | Source |
|----------|--------|
| `GET /admin/historical/candles` (epoch `from`/`to` ms) | Locally persisted DuckDB feature store |
| `GET /admin/historical/ticks`, `/orders`, `/fills`, etc. | DuckDB / replay journal |

## Scan and options liquidity

Set `trade.scan.enabled=true` in `application-*.yml` to enable profile-based scans (`ScanService`, DuckDB persistence, `POST /api/v1/scans/run`).

Options contract ranking is always available when the app is running (via `OptionScanService`):

- `POST /api/v1/options/scan?underlying=NIFTY&segment=IDX_I&top=10`
- `GET /api/v1/options/scan/expiries?underlying=NIFTY&segment=IDX_I`

CLI: `./scripts/tradej options-scan NIFTY IDX_I` (standalone broker) or attach to app. Profile `option-liquidity` in [config/scan-profiles.json](config/scan-profiles.json) runs multi-underlying scans when scan is enabled.

## Run commands

```bash
# Sandbox broker (default)
./gradlew :app:bootRun

# Live broker (market data, WS)
./gradlew :app:bootRun --args='--spring.profiles.active=dev-live'

# Production
SPRING_PROFILES_ACTIVE=prod ./gradlew :app:bootRun

# Upstox analytics-only (market data, no trading)
./gradlew :app:bootRun --args='--spring.profiles.active=upstox-analytics'
```

Verify Upstox credentials:

```bash
./gradlew :app:upstoxPreflightTest
```

## Naming clarifications

- **`trade.broker.environment`** (`LIVE` / `SANDBOX`) — which Dhan API host the single runtime broker uses (`api.dhan.co` vs `sandbox.dhan.co`).
- **Spring profile `test`** — Spring Boot config for isolated storage paths; not the same as Gradle `@Tag("integration")` or JUnit tags.
- **Gradle integration tests** — still load credential files via [LiveDhanTestSupport](app/src/test/java/com/tradej/app/integration/LiveDhanTestSupport.java), independent of `spring.profiles.active`.

## Property binding

All `trade.*` settings bind to [TradingProperties](app/src/main/java/com/tradej/app/config/TradingProperties.java). Profile YAML maps sandbox or live keys from the `dhan.*` property files into `trade.broker.*`.

## Historical analytics (`trade.analytics`)

Federated DuckDB engine over equity Parquet + options DuckDB. See [docs/contracts/ANALYTICS_CATALOG.md](docs/contracts/ANALYTICS_CATALOG.md).

| Property | Default | Purpose |
|----------|---------|---------|
| `trade.analytics.equity-root` | `data/historical-equity` | Nifty 500 parquet lake |
| `trade.analytics.options-warehouse` | `runtime-dev/historical.duckdb` | Rolling options bars |
| `trade.analytics.attach-runtime-db` | `false` | ATTACH `trade.duckdb` read-only for replay joins |
| `trade.analytics.sql-enabled` | `true` | Enable `POST /api/v1/analytics/sql` |

Public API: `/api/v1/analytics/catalog`, `/equity/candles`, `/options/bars`, `/sql`.
