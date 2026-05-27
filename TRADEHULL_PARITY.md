# Tradehull Parity Matrix

This document tracks Tradehull capability parity against Trade-J broker abstraction and Dhan adapters.

## Current parity snapshot

| Domain | Capability | Status | Live | Sandbox |
|---|---|---|---|---|
| Auth | Access token + TOTP + renew | Supported | Yes | Static token |
| Market data | LTP/Quote/Depth single instrument | Supported | Yes | No |
| Market data | Batch LTP/Quote/OHLC | Supported | Yes | No |
| Historical | Daily/intraday candles | Supported | Yes | No |
| Historical | Rolling option history | Supported | Yes | No |
| Options | Expiry list + chain + greeks + contracts | Supported | Yes | No |
| Orders | Place/modify/cancel/cancel-all/kill-switch | Supported | SDK | REST |
| Orders | Slice orders | Supported | SDK | REST |
| Orders | Super orders | Supported | SDK | REST (may 404) |
| Orders | Forever/GTT orders | Supported | SDK | REST (may 404) |
| Risk | Margin calculator | Supported | Yes | No |
| Risk | PnL based exit | Supported | Yes | Optional skip |
| Alerts | Conditional alert CRUD | Supported | Yes | Yes |
| App helper | Live PnL composite | Supported | Yes | No |
| App helper | Long-range historical chunking | Supported | Yes | No |

## Environment routing

- **Live** (`api.dhan.co/v2`): data, WebSocket, margin, historical, options — uses Dhan Java SDK where applicable.
- **Sandbox** (`sandbox.dhan.co/v2`): order mutations only — uses `DhanRestOrderClient`; SDK blocked via `DhanClientHolder`.

## Fixture workflow

Unit mappers use fixture payloads under `trade-broker-dhan/src/test/resources/dhan-fixtures/`.

Capture (one-time, redact secrets):

```bash
# Live fund limit (preflight shape)
curl -s -H "client-id: $DHAN_CLIENT_ID" -H "access-token: $DHAN_ACCESS_TOKEN" \
  https://api.dhan.co/v2/fundlimit > fundlimit-live.json

# Sandbox place order response (after DHAN_ORDER_TEST_ENABLED run)
# Save response body to place-order-response.json with orderId/correlationId redacted
```

Rules:

1. Capture from real responses only.
2. Remove sensitive account identifiers.
3. Keep representative payload shape for parser coverage.
4. Integration tests remain live and credential-gated; fixtures are for `@Tag("unit")` only.

## Regression

See [REGRESSION_MANIFEST.md](REGRESSION_MANIFEST.md) and run `./scripts/run-full-regression.sh` for the full-stack gate.
