# Dhan Client Module

Standalone Java library for DhanHQ broker integration. Connects to `api.dhan.co` for REST and `wss://api-feed.dhan.co` for live market data. No Spring or external framework required.

## Getting Started

### 1. Install

The module is part of the Trade-J monorepo. Outside the monorepo, compile against:

- `broker-api` — ports/interfaces (`IBrokerConnection`, `OrderCommand`, `PortfolioProvider`, etc.)
- `broker-core` — domain types, resilience, rate limiting
- `com.eatthepath:java-otp` — TOTP (only for TOTP auth mode)
- `jackson-databind`, `slf4j-api`

### 2. Configure credentials

Create `config/dhan-local.properties` (gitignored) at the project root:

```
dhan.clientId=<client-id>
dhan.accessToken=<access-token>
dhan.authMode=TOTP_GENERATED
dhan.pinFile=config/dhan-pin.txt
dhan.totpSecretFile=config/dhan-totp-secret.txt
dhan.tokenStateFile=runtime/dhan-token-state.json
dhan.refreshBufferMinutes=10
dhan.restBaseUrl=https://api.dhan.co/v2
```

- `authMode`: `STATIC` (single access token) or `TOTP_GENERATED` (auto-renews via TOTP).
- For `TOTP_GENERATED`, both `dhan.pinFile` and `dhan.totpSecretFile` must exist.
- Optional overrides: `DHAN_CLIENT_ID`, `DHAN_ACCESS_TOKEN`, `DHAN_AUTH_MODE` env vars.

### 3. Connect

```java
DhanConnectionSettings settings = DhanConnectionSettings.liveWithDefaults(
        clientId,
        accessToken,
        DhanAuthMode.STATIC,
        DhanConfigPaths.resolve("config/dhan-pin.txt"),
        DhanConfigPaths.resolve("config/dhan-totp-secret.txt"),
        DhanConfigPaths.resolve("runtime/dhan-token-state.json"),
        10L);

DhanBrokerConnection broker = DhanBrokerConnection.create(settings);
broker.connect();
```

### 4. Place an order

```java
OrderRequest request = new OrderRequest.Builder(symbol)
        .transactionType(BUY)
        .productType(INTRADAY)
        .orderType(LIMIT)
        .quantity(50)
        .price(23366.7)
        .build();

OrderPreview preview = broker.orders().previewOrder(request);
System.out.println(preview);

Order placed = broker.orders().placeOrder(request);
```

`previewOrder()` returns an `OrderPreview` that includes margin, notional, and any validation warnings before you place.

## Capabilities

| Capability | Adapter / Class |
|------------|-----------------|
| Place / modify / cancel orders | `DhanOrderCommandAdapter` |
| Order / trade history | `DhanOrderQueryAdapter` |
| Fund limits & margin | `DhanMarginProvider`, `DhanPortfolioProvider` |
| Daily / intraday history | `DhanHistoricalDataClient` |
| Option chain | `DhanOptionChainClient` |
| Rolling options | `DhanRollingOptionClient` |
| Live market feed | `DhanMarketFeedWebSocketClient` via `DhanWebSocketMultiplexer` |
| 20-level depth | `DhanTwentyDepthWebSocketClient`, `DhanMarketDepthProvider` |
| Order stream | `DhanOrderStreamWebSocketClient` |
| Instrument catalog | `DhanInstrumentCatalog`, `DhanInstrumentLoader` |
| Advanced orders | `DhanBracketOrderAdapter`, `DhanSliceOrderAdapter`, `DhanGttOrderAdapter` |
| Session risk / alerts | `DhanSessionRiskProvider`, `DhanConditionalAlertProvider` |

## Environment

Use `DhanApiEnvironment.SANDBOX` to point all requests at `https://sandbox.dhan.co/v2`.

```java
DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults(
        sandboxClientId,
        sandboxAccessToken,
        DhanApiEnvironment.SANDBOX,
        DhanAuthMode.STATIC,
        ...);
```

## Safety Rules

`DhanOrderValidator` enforces:

1. Confirmation required for live orders.
2. Readable `OrderPreview` before execution.
3. `LIMIT` default; explicit opt-in for `MARKET`.
4. ₹50,000 notional warning.
5. Lot-size enforcement for F&O / commodity / currency.
6. `CNC` / `MTF` rejected for F&O / commodity / currency / indexes.

Rollout is warn-only first, then strict via `dhan.validation.strict=true/false`.

## Testing

```bash
./gradlew :broker:dhan:test
```

For live smoke testing (no backend required):

```bash
bash scripts/dhan-smoke.sh
```

`scripts/dhan-smoke.sh` calls `api.dhan.co/v2` directly with credentials from `config/dhan-local.properties`. Backend / Spring Boot is **not** required.

## Key Endpoints

| Function | Endpoint |
|----------|----------|
| Fund limits | `GET /v2/fundlimit` |
| Daily history | `POST /v2/charts/historical` |
| Intraday history | `POST /v2/charts/intraday` |
| Option chain | `POST /v2/optionchain` |
| Expiry list | `POST /v2/optionchain/expirylist` |
| Ledger | `GET /v2/ledger` |
| Positions | `GET /v2/positions` |
| Holdings | `GET /v2/holdings` |
| Orders | `GET /v2/orders` |
| Trades | `GET /v2/trades` |
| Quote feed | `POST /v2/marketfeed/quote` |

Request payloads must use Dhan field names (e.g., `UnderlyingScrip`, `UnderlyingSeg`, `Expiry` for option chain).

## Design Notes

This module depends only on `broker-api`, `broker-core`, Jackson, SLF4J, and `java-otp`. It has zero Spring coupling. The host app wires it via `IBrokerConnection` and `BrokerConfiguration`. Token rotation is observable — `DhanClientHolder` notifies rotation listeners, which triggers live WebSocket reconnect.


