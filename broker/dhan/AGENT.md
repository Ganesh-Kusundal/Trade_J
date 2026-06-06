# Dhan Broker Agent Instructions

Directive for AI agents maintaining the `broker/dhan` module.

## Module Boundaries

- `broker/dhan` is a pure Java library. It has **zero Spring** or other framework coupling.
- Spring wiring lives exclusively in the `app` module.
- Public entry API: `DhanBrokerConnection` implements `IBrokerConnection`.
- All Dhan-specific logic lives at or below `com.tradej.broker.dhan`.

## Required Conventions

1. **New classes** go in an existing package if semantically closer (auth, adapter, options, historical, etc.) — do not invent new top-level packages.
2. **Naming**: Dhan-specific types use `Dhan*` prefix (e.g., `DhanOrderCommandAdapter`, `DhanHistoricalDataClient`). Shared domain types live in `broker-core` or `broker-api` and use generic names.
3. **Auth**: always call `DhanAuthenticatedHttpClient` / `DhanTokenProvider`, never pass raw tokens directly.
4. **HTTP**: append to REST builder via `DhanAuthenticatedHttpClient.postJson(...)`. Use `DhanApiUrlResolver` for endpoint construction.
5. **Resilience**: decorate external calls with `DhanRetryExecutor.execute(ApiCategory, "op-name", () -> httpCall)`.
6. **Rate limit** requests per `ApiCategory` — choose the tightest matching bucket:
   - `ORDER`, `SLICE_ORDER`, `ADVANCED_ORDER` → 10 rps
   - `DATA` → 5 rps
   - `QUOTE` → 1 rps
   - `OPTION_CHAIN` → 1 rps
   - `NON_TRADING` → 20 rps
7. **Instrument resolution**: use `DhanInstrumentResolver` / `InMemoryInstrumentResolver`. Do not construct definition objects inline.
8. **WebSocket clients**: extend `DhanMarketFeedWebSocketClient` or `DhanOrderStreamWebSocketClient`; do not mix feed and order semantics in a single client.
9. **Logging**: SLF4J only, parameterized messages, no `System.out`.
10. **Exceptions**: use `DhanBrokerException` hierarchy. Throw `DhanValidationException` for rule violations. Throw `DhanAuthenticationException` / `DhanAuthRejectedException` for token errors.
11. **Tests**: JUnit 5, Mockito, test fixtures from `broker-api`. Place unit tests beside the class under `src/test/java/com/tradej/broker/dhan/...`. Mirror the source package structure.
12. **Fixtures**: JSON response fixtures in `src/test/resources/dhan-fixtures/`. Redact tokens / client IDs. Document purpose in `src/test/resources/dhan-fixtures/README.md`.
13. **Credentials**: never hardcode or log `clientId`, `accessToken`, `pin`, `totpSecret`. Read from `DhanConfigPaths` / environment. `config/dhan-local.properties` is gitignored.

## Quick-Start Patterns

### Initialize a client (unit / integration)
```java
DhanConnectionSettings settings = DhanConnectionSettings.liveWithDefaults(
        clientId,
        accessToken,
        DhanAuthMode.STATIC,
        DhanConfigPaths.resolve("config/dhan-pin.txt"),
        DhanConfigPaths.resolve("config/dhan-totp-secret.txt"),
        DhanConfigPaths.resolve("runtime/dhan-token-state.json"),
        10L);

DhanAuthenticatedHttpClient httpClient = new DhanAuthenticatedHttpClient(
        new DhanTokenManager(...), HttpClient.newHttpClient());

DhanApiUrlResolver resolver = new DhanApiUrlResolver(settings);
DhanRetryExecutor retry = new DhanRetryExecutor();
DhanOptionChainClient ocClient = new DhanOptionChainClient(httpClient, resolver, retry);
```

### Place an order (safety rules enforced)
```java
IBrokerConnection broker = DhanBrokerConnection.create(settings, ...);
OrderPreview preview = broker.previewOrder(request);          // validation + notional check
Order placed = broker.orders()
                     .placeOrder(enrichedRequest);            // throws DhanValidationException if strict-mode violation
```

## Safety Rules (non-negotiable)

These are enforced in `DhanOrderValidator`. Do not bypass:
1. Confirmation token required for live orders (when implemented).
2. Always show readable `OrderPreview` before execution.
3. Default to `LIMIT`; `MARKET` requires explicit confirmation.
4. Notional warning at ₹50,000 (configurable via `DhanProtocolConstants.MAX_NOTIONAL_PAISA`).
5. Lot-size validation for F&O/commodity/currency.
6. Reject `CNC` / `MTF` for F&O/commodity/currency.

See `broker/dhan/DHAN_SAFETY_RULES_PLAN.md` for implementation status.

## Running Tests

Unit / contract tests (no live credentials required for unit tests):
```bash
./gradlew :broker:dhan:test
```

Integration tests (requires `config/dhan-local.properties`):
```bash
./gradlew :broker:dhan:test -PincludeTags=integration
```

Live smoke test against Dhan production (no backend required):
```bash
bash scripts/dhan-smoke.sh
```

## Module Layout

```
broker/dhan/
├── DhanBrokerConnection.java              ← facade, DI entry
├── adapter/                               ← REST adapters per capability
├── auth/                                  ← token lifecycle + TOTP
├── client/                                ← DhanClientHolder (token + rotation)
├── config/                                ← ConnectionSettings, environment enums
├── constants/                             ← URLs, protocol constants
├── depth/                                 ← 20-depth WebSocket + REST
├── domain/                                ← Dhan-specific value records
├── exceptions/                            ← error hierarchy
├── historical/                            ← daily / intraday / rolling history
├── http/                                  ← authenticated HTTP client
├── instrument/                            ← security master + catalog
├── mapper/                                ← JSON/DTO mapping
├── options/                               ← option chain, expiry cache, rolling options
├── orders/                                ← raw REST order client
├── rate/                                  ← ApiCategory enum
├── resilience/                            ← retry / circuit breaker / rate limiter
├── validator/                             ← DhanOrderValidator + OrderPreview
├── websocket/                             ← live market feed, order stream, depth
│   └── feed/                              ← binary frame parsing
├── DHAN_SAFETY_RULES_PLAN.md
└── build.gradle
```

## Module Boundaries

- `broker/dhan` may import from: `broker-api`, `broker-core`, `com.eatthepath:java-otp`, `jackson-databind`, `slf4j-api`.
- It must **not** import from `app`, `trading`, `ui`, or any other consumer module.
- When adding a feature, prefer adding a new adapter or inner client class over modifying `DhanBrokerConnection` directly.

## Documentation

- `DHAN_SAFETY_RULES_PLAN.md` — safety-rule implementation tracker.
- User-facing overview and getting started: `README.md` (this directory).
- API reference for the option-chain / historical test endpoints is in scripts and skill files.
