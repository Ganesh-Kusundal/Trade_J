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
| Orders | Order book / trades / fill queries | Supported | SDK | REST |
| Orders | Slice orders | Supported | SDK | REST |
| Orders | Super orders | Supported | SDK | REST (may 404) |
| Orders | Forever/GTT orders | Supported | SDK | REST (may 404) |
| Risk | Margin calculator | Supported | Yes | No |
| Risk | PnL based exit | Supported | Yes | Optional skip |
| Alerts | Conditional alert CRUD | Supported | Yes | Yes |
| App helper | Live PnL composite | Supported | Yes | No |
| App helper | Long-range historical chunking | Supported | Yes | No |
| Options | ATM/OTM/ITM strike selection | Supported (catalog-backed) | Yes | No |

## Environment routing

- **Live** (`api.dhan.co/v2`): data, WebSocket, margin, historical, options — uses Dhan Java SDK where applicable.
- **Sandbox** (`sandbox.dhan.co/v2`): order mutations and order/trade queries — uses `DhanRestOrderClient`; SDK blocked via `DhanClientHolder`.

### Sandbox limitations

| Capability | Live | Sandbox |
|---|---|---|
| `cancelAndSquareOffIntradayPositions` | Cancels open orders, then places market exits per position | Only calls `DELETE /orders` (cancel all); does **not** flatten positions |
| `setKillSwitch` | SDK `setKillSwitch` | **Not available** — no REST path; use live-only opt-in test |
| Market data / portfolio / options | Full SDK + REST | **Not routed** — sandbox credentials are order-only |
| `OrderQuery` | SDK | REST `GET /orders`, `GET /orders/{id}`, `GET /trades` |

## Per-method matrix (Tradehull → Trade-J)

Legend: **Impl** = implemented on broker port; **Test** = integration test in default or opt-in regression.

| Tradehull method | Trade-J surface | Impl | Test |
|---|---|:---:|:---:|
| `get_login` / token helpers | `DhanAuthClient`, `DhanTokenManager` | Yes | `DhanTokenLifecycleIntegrationTest`; drills: `brokerAuthDrillTest` |
| `get_instrument_file` | `loadDailyInstrumentCatalog` | Yes | Via derivatives/historical tests |
| `get_ltp_data` | `MarketDataProvider.getLtpBatch` | Yes | `DhanBatchQuoteIntegrationTest` |
| `get_quote_data` | `getQuote` / `getQuoteBatch` | Yes | `DhanBatchQuoteIntegrationTest` |
| `get_ohlc_data` | `getOhlcSnapshot` / `getOhlcBatch` | Yes | `DhanBatchQuoteIntegrationTest`, `DhanMarketDepthIntegrationTest` |
| `get_market_depth` | `getDepth` | Yes | `DhanMarketDepthIntegrationTest` |
| `get_historical_data` | `getCandles` | Yes | `DhanHistoricalDataIntegrationTest` |
| `get_long_term_historical_data` | `getCandles` + `HistoricalRangeService` | Yes | `HistoricalRangeIntegrationTest` |
| `order_placement` / `modify_order` / `cancel_order` | `OrderCommand` | Yes | `DhanOrderLifecycleIntegrationTest`, `DhanOrderModifyIntegrationTest` (opt-in) |
| `place_slice_order` | `SliceOrderCommand` | Yes | `DhanSliceOrderIntegrationTest` (opt-in) |
| `cancel_all_orders` | `cancelAllOpenOrders` | Yes | `DhanCancelAllIntegrationTest` (opt-in sandbox) |
| `kill_switch` | `setKillSwitch` | Yes (live) | `DhanKillSwitchIntegrationTest` (opt-in live) |
| `get_balance` / `get_holdings` / `get_positions` | `PortfolioProvider` | Yes | `DhanPortfolioIntegrationTest` |
| `get_orderbook` / `get_trade_book` / order detail helpers | `OrderQuery` | Yes | `DhanOrderQueryIntegrationTest` (opt-in sandbox) |
| `margin_calculator` | `MarginProvider` | Yes | `DhanMarginIntegrationTest` (opt-in) |
| `get_expiry_list` / `get_option_chain` / `get_option_greek` | `OptionsProvider` | Yes | `DhanDerivativesIntegrationTest` |
| `get_expired_option_data` | `getExpiredOptionHistory` | Yes | `DhanRollingOptionIntegrationTest` (opt-in) |
| `ATM_*` / `OTM_*` / `ITM_*` | `selectStrikePaisa` (catalog strikes) | Yes | `StrikeSelectionSupportTest` (unit); `DhanStrikeSelectionIntegrationTest` (live) |
| Super / forever orders | `BracketOrderProvider` / `GttOrderProvider` | Yes | Opt-in sandbox tests |
| Conditional triggers | `ConditionalAlertProvider` | Yes | `DhanAlertIntegrationTest` (opt-in) |
| `enable_pnl_based_exit` | `SessionRiskProvider` | Yes | `DhanSessionRiskIntegrationTest` (opt-in) |
| `get_live_pnl` | `LivePnlService` | Yes | `LivePnlIntegrationTest` |
| WS ticks / depth | `WebSocketMultiplexer` | Yes | `DhanMarketFeedIntegrationTest` |
| `resample_timeframe`, `heikin_ashi`, `renko_bricks` | — | Out of scope | — |
| `send_telegram_alert` | — | Out of scope | — |
| `convert_to_date_time`, `get_start_date`, `order_report` | App utilities | Out of scope | — |

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

Opt-in parity flags (see [TESTING.md](TESTING.md)):

- `DHAN_ORDER_QUERY_TEST_ENABLED=true` — sandbox order query after place
- `DHAN_ORDER_MODIFY_TEST_ENABLED=true` — sandbox modify limit order
- `DHAN_CANCEL_ALL_TEST_ENABLED=true` — sandbox cancel-all
- `DHAN_KILL_SWITCH_TEST_ENABLED=true` — live kill-switch toggle (restores off in teardown)
