# Testing Pyramid

The project uses a three-layer test pyramid aligned with the trading runtime:

- `unit`
  Fast, deterministic checks for pure logic such as fixed-point price math and circuit-breaker state transitions.
- `component`
  Real in-process composition tests across modules, with no mocks and no fake broker adapters. These validate candle aggregation, risk qualification, and similar domain workflows.
- `integration`
  Live Dhan connectivity and order lifecycle tests. These are credential-gated and only run when the required `DHAN_*` environment variables are present.

## Gradle Tasks

```bash
./gradlew test
./gradlew unitTest
./gradlew componentTest
./gradlew integrationTest
./gradlew brokerRestTest
./gradlew brokerWsTest
./gradlew brokerOrderTest
./gradlew runtimeE2eTest
./gradlew regressionPreflightTest
./gradlew crossLayerRegressionTest
./gradlew fullRegressionTest
```

`test` excludes `integration` by default so the regular build stays fast and safe.

## Spring profiles (runtime)

See [CONFIG.md](CONFIG.md) for the full profile matrix. Summary:

| Command | Profile | Broker |
|---------|---------|--------|
| `./gradlew :trade-app:bootRun` | `dev` (default) | Sandbox |
| `./gradlew :trade-app:bootRun --args='--spring.profiles.active=dev-live'` | `dev-live` | Live |
| `SPRING_PROFILES_ACTIVE=prod ./gradlew :trade-app:bootRun` | `prod` | Live |

Credential files are unchanged: `config/dhan-local.properties` (live) and `config/dhan-sandbox.properties` (sandbox). The `dev` profile imports **both**; default runtime uses sandbox keys for safe local order testing.

## Full-stack regression

One command runs unit → component → preflight → live broker read paths → sandbox orders → runtime E2E → cross-layer OMS/execution tests:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # if needed
./scripts/run-full-regression.sh
```

Requires both credential files:

- `config/dhan-local.properties` (live data / runtime)
- `config/dhan-sandbox.properties` (order mutations)

The script sets all `*_TEST_ENABLED=true` flags and runs `./gradlew fullRegressionTest --no-daemon`.

Mapping of tests to invariants: [REGRESSION_MANIFEST.md](REGRESSION_MANIFEST.md). Architecture sign-off checklist: [SYSTEM_ANALYSIS_CHECKLIST.md](SYSTEM_ANALYSIS_CHECKLIST.md).

Cross-layer tests (`DHAN_CROSS_LAYER_TEST_ENABLED=true`):

- `ExecutionToSandboxBrokerIntegrationTest`
- `OmsToExecutionSandboxIntegrationTest`

## Dual Environment Credentials

Live environment (`broker-rest`, `broker-ws`, runtime smoke):

- `DHAN_CLIENT_ID`
- `DHAN_ACCESS_TOKEN`
- `DHAN_TEST_SYMBOL`
- `DHAN_TEST_EXCHANGE`
- `DHAN_TEST_SECURITY_ID`
- `DHAN_TEST_SEGMENT`

Historical and broker REST:

- `DHAN_CLIENT_ID`
- `DHAN_ACCESS_TOKEN`
- `DHAN_HISTORICAL_SYMBOL` (optional, defaults to `NIFTY`)
- `DHAN_HISTORICAL_SECURITY_ID` (optional, defaults to `13`)
- `DHAN_HISTORICAL_SEGMENT` (optional, defaults to `IDX_I`)
- `DHAN_HISTORICAL_INSTRUMENT` (optional, defaults to `INDEX`)

Derivatives and runtime smoke:

- `DHAN_CLIENT_ID`
- `DHAN_ACCESS_TOKEN`
- `DHAN_RUNTIME_SYMBOL` (optional, defaults to `NIFTY`)
- `DHAN_RUNTIME_SEGMENT` (optional, defaults to `IDX_I`)

Runtime startup:

- `TRADE_INSTRUMENTS_CSV` or `TRADE_INSTRUMENTS_CACHE_DIR`
- `TRADE_INSTRUMENTS_AUTO_DOWNLOAD` (defaults to `true`)

Sandbox environment (`broker-order`):

- `DHAN_SANDBOX_CLIENT_ID` (default local config is `2505162156`)
- `DHAN_SANDBOX_ACCESS_TOKEN`
- `DHAN_SANDBOX_REST_BASE_URL` (optional, defaults to `https://sandbox.dhan.co/v2`)
- `DHAN_ORDER_TEST_ENABLED=true`
- `DHAN_TEST_ORDER_SYMBOL`
- `DHAN_TEST_ORDER_SECURITY_ID`
- `DHAN_TEST_ORDER_SEGMENT`
- `DHAN_TEST_ORDER_EXCHANGE`
- `DHAN_TEST_ORDER_PRICE_PAISE`
- `DHAN_TEST_ORDER_QUANTITY`

The live integration helpers also accept Gradle properties such as `-Pdhan.clientId=...` and `-Pdhan.accessToken=...`.

They read an optional local live file at `config/dhan-local.properties` (gitignored):

```properties
dhan.clientId=...
dhan.accessToken=...
dhan.authMode=TOTP_GENERATED
dhan.pinFile=config/dhan-pin.txt
dhan.totpSecretFile=config/dhan-totp-secret.txt
dhan.tokenStateFile=runtime/dhan-token-state.json
dhan.refreshBufferMinutes=10
dhan.testSymbol=NIFTY
dhan.testExchange=INDEX
dhan.testSecurityId=13
dhan.testSegment=IDX_I
```

Sandbox order tests read a separate gitignored file `config/dhan-sandbox.properties`:

```properties
dhan.sandbox.clientId=2505162156
dhan.sandbox.accessToken=...
dhan.sandbox.restBaseUrl=https://sandbox.dhan.co/v2
dhan.testOrderSymbol=TCS
dhan.testOrderSecurityId=11536
dhan.testOrderSegment=NSE_EQ
dhan.testOrderExchange=NSE
dhan.testOrderQuantity=1
dhan.testOrderPricePaise=10000
```

When `dhan.authMode=TOTP_GENERATED`, keep the secrets in separate gitignored files:

- `config/dhan-pin.txt`
- `config/dhan-totp-secret.txt`

The runtime persists the last known token state to `runtime/dhan-token-state.json` (resolved from the repository root even when Gradle starts the JVM from `trade-app/`). If the configured `dhan.accessToken` is still valid, the broker layer reuses it and records its expiry from `GET /profile`. It only calls Dhan's TOTP token generation endpoint when the token is missing, expired, or inside the refresh buffer.

Forced TOTP generation drill (live, will mint a new session token):

```bash
./gradlew :trade-app:brokerRestTest --tests '*DhanTokenForcedGenerationIntegrationTest'
```

Refresh production `runtime/dhan-token-state.json` when live preflight fails (clears stale cache, mints via TOTP):

```bash
./gradlew :trade-app:brokerRestTest --tests '*DhanRefreshProductionTokenIntegrationTest' --no-daemon
```

Live integration tests and preflight resolve tokens through `DhanTokenManager` when `dhan.authMode=TOTP_GENERATED`; a stale `dhan.accessToken` in properties no longer blocks regeneration.

Token reuse drill (must not rotate when still valid):

```bash
./gradlew :trade-app:brokerRestTest --tests '*DhanTokenLifecycleIntegrationTest'
```

When credentials are exported from the shell, prefer `--no-daemon` for live runs so the Gradle test worker sees the current environment.

### Run by environment

```bash
# Live data and read-only broker flows
./gradlew :trade-app:brokerRestTest --no-daemon
./gradlew :trade-app:brokerWsTest --no-daemon

# Sandbox order mutation flows
DHAN_ORDER_TEST_ENABLED=true \
DHAN_SLICE_ORDER_TEST_ENABLED=true \
DHAN_SUPER_ORDER_TEST_ENABLED=true \
DHAN_FOREVER_ORDER_TEST_ENABLED=true \
DHAN_SQUAREOFF_TEST_ENABLED=true \
./gradlew :trade-app:brokerOrderTest --no-daemon
```

Sandbox caveat: fills are simulated and some endpoints (for example `/pnlExit`) may be unavailable. Those tests skip when unsupported.

## Options (expiry list + option chain)

Live option endpoints use Dhan REST:

- `POST /v2/optionchain/expirylist` — `options().getExpiries()` (API only, cached in-memory by default for 5 minutes)
- `POST /v2/optionchain` — `options().getOptionChain()` and `options().getGreeks()`
- Instrument master — `options().getOptionContracts()` (tradable contracts for orders; requires catalog load)

Broker REST integration (credentials + daily instrument master download):

```bash
./gradlew :trade-app:brokerRestTest --tests '*DhanDerivativesIntegrationTest'
```

**Rate limit:** Dhan option-chain calls share the `OPTION_CHAIN` bucket (~1 request every 3 seconds). Space `getOptionChain` / `getGreeks` calls accordingly; reuse an `OptionChainSnapshot` when reading multiple strikes. Configure cache TTL via `trade.broker.option-expiry-cache-ttl-minutes` (default `5`).

## Tradehull parity suite

Run the full parity gate:

```bash
./gradlew brokerParityTest
```

Additional parity integration tests are credential-gated and opt-in for destructive/live broker actions:

- `DHAN_ROLLING_OPTION_TEST_ENABLED=true`
- `DHAN_MARGIN_TEST_ENABLED=true`
- `DHAN_ALERT_TEST_ENABLED=true`
- `DHAN_PNL_EXIT_TEST_ENABLED=true`
- `DHAN_SLICE_ORDER_TEST_ENABLED=true`
- `DHAN_SUPER_ORDER_TEST_ENABLED=true`
- `DHAN_FOREVER_ORDER_TEST_ENABLED=true`
- `DHAN_SQUAREOFF_TEST_ENABLED=true`

Parity-related test classes:

- `DhanMarginIntegrationTest`
- `DhanAlertIntegrationTest`
- `DhanRollingOptionIntegrationTest`
- `DhanBatchQuoteIntegrationTest`
- `DhanSliceOrderIntegrationTest`
- `DhanSuperOrderIntegrationTest`
- `DhanForeverOrderIntegrationTest`
- `DhanSquareOffIntegrationTest`
- `DhanSessionRiskIntegrationTest`
- `LivePnlIntegrationTest`
- `HistoricalRangeIntegrationTest`
