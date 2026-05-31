# Dhan fixture captures

Sanitized JSON fixtures for `@Tag("unit")` mapper tests (`DhanRestOrderClientFixtureTest`).

## Bundled fixtures

| File | Purpose |
|------|---------|
| `place-order-response.json` | Sandbox place-order shape |
| `cancel-order-response.json` | Cancel acknowledgement |
| `optionchain-response.json` | Option chain nested `data` |
| `historical-daily-response.json` | OHLC series arrays |

## One-time capture commands

```bash
# Live historical (redact symbols if needed)
curl -s -X POST https://api.dhan.co/v2/charts/historical \
  -H "client-id: $DHAN_CLIENT_ID" -H "access-token: $DHAN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"securityId":"13","exchangeSegment":"IDX_I","instrument":"INDEX","fromDate":"2024-01-01","toDate":"2024-01-31"}' \
  > historical-daily-response.json

# After a sandbox order test, save the place response body (redact tokens/ids)
```

Guidelines:

- Capture only real payloads from live or sandbox broker calls.
- Remove/replace sensitive account identifiers.
- Keep the original shape and field naming.
- Integration tests remain credential-gated (`broker-rest`, `broker-order`, `broker-ws`).
