# Upstox Broker Module — Comprehensive API Gap Analysis

**Date:** 2026-06-05
**Target:** `broker/upstox/` module
**Reference:** [Upstox Open API Documentation](https://upstox.com/developer/api-documentation/open-api/)

---

## Coverage Summary

| Category | Endpoints | Implemented | Missing | Coverage |
|---|---|---|---|---|
| Authentication & Platform | ~5 | 3 | 2 | 60% |
| Instruments | ~3 | 1 | 2 | 33% |
| User & Account | ~5 | 1 | 4 | 20% |
| Funds & Margins | ~4 | 1 | 3 | 25% |
| Orders (core) | ~10 | 6 | 4 | 60% |
| GTT Orders | ~5 | 0 | 5 | **0%** |
| Multi Order / Slicing | ~2 | 0 | 2 | **0%** |
| Portfolio | ~4 | 3 | 1 | 75% |
| Mutual Fund | ~4 | 0 | 4 | **0%** |
| Trade Analytics / P&L | ~3 | 0 | 3 | **0%** |
| Market Quote / Data | ~7 | 6 | 1 | 86% |
| Market Information | ~4 | 1 | 3 | 25% |
| Options / Greeks | ~5 | 5 | 0 | **100%** |
| News | ~2 | 2 | 0 | **100%** |
| Expired Instruments | ~3 | 3 | 0 | **100%** |
| WebSocket — Market Feed | ~2 | 2 | 0 | 100% |
| WebSocket — Order Updates | ~1 | 0 | 1 | **0%** |
| Webhooks | ~3 | 0 | 3 | **0%** |
| **TOTAL (weighted)** | **~72** | **~34** | **~38** | **~47%** |

---

## 1. 🔴 Critical Gaps (P0 — Must Fix)

### 1.1 GTT Orders — Entirely Missing

**Documented endpoints:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/gtt/orders` | Place GTT order |
| PUT | `/v2/gtt/orders/{gtt_order_id}` | Modify GTT order |
| DELETE | `/v2/gtt/orders/{gtt_order_id}` | Cancel GTT order |
| GET | `/v2/gtt/orders` | Get all GTT orders |
| GET | `/v2/gtt/orders/{gtt_order_id}` | Get GTT order details |

**Status:** ❌ No GTT paths in `UpstoxEndpoints.java`, no REST client methods, no adapter.

**Impact:** Trading systems relying on Good-Till-Triggered (bracket/cover) orders cannot use Upstox.

**Remediation:**
- Add 5 endpoint constants to `UpstoxEndpoints.java`
- Create `UpstoxGttRestClient.java` with all 5 GET/POST/PUT/DELETE methods
- Create `UpstoxGttOrderAdapter.java` implementing `ConditionalAlertProvider` or similar port
- Add GTT domain records if needed

---

### 1.2 Order WebSocket (Portfolio Stream Feed) — Never Connected

**Documented endpoint:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/feed/portfolio-stream-feed/authorize` | Get authorized WS URI for order updates |

**WebSocket:** `wss://portfolio-stream.upstox.com` (or authorized redirect URI)

**Status:** ❌ `UpstoxWebSocketMultiplexer` has `orderUpdateListeners` list and an `orderWs` field, but `orderWs` is **never initialized**. The `connect()` method only connects `marketWs`. The portfolio stream authorize endpoint path is not defined.

**Impact:** No live order status updates, trade executions, rejections, or partial fills reach the system via WebSocket. The OMS must poll instead.

**Remediation:**
- Add `PORTFOLIO_STREAM_AUTHORIZE_PATH` to `UpstoxEndpoints.java`
- Implement portfolio stream authorization in `UpstoxFeedAuthorizer` or create a separate `UpstoxPortfolioFeedAuthorizer`
- Connect `orderWs` in `UpstoxWebSocketMultiplexer.connect()`
- Implement an order update message handler for the binary/text protocol
- Wire `OrderUpdateListener` notifications

---

### 1.3 Multi Order & Slicing — Entirely Missing

**Documented endpoints:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/order/multi` | Place multiple orders in one request (basket) |
| POST | `/v2/order/slicing` | Large order iceberg/slicing |

**Status:** ❌ Not defined in endpoints, no REST client methods.

**Impact:** Basket trading, portfolio rebalancing, and large order execution are unavailable.

**Remediation:**
- Add endpoint constants
- Add methods to `UpstoxOrderRestClient`
- Consider implementing through `OrderCommand` or a new `BasketOrderProvider` port

---

### 1.4 Convert Position — Missing

**Documented endpoint:**
| Method | Path | Purpose |
|---|---|---|
| PUT | `/v2/portfolio/convert-position` | Convert intraday to delivery or vice versa |

**Status:** ❌ Not defined in endpoints or adapters.

**Impact:** Cannot convert positions between product types (MIS → CNC) via API.

---

### 1.5 Set Kill Switch — Unimplemented Stub

**Adapter:** `UpstoxOrderCommandAdapter.setKillSwitch()`
**Status:** ❌ Throws `UnsupportedOperationException`

**Note:** It's unclear if Upstox has a kill switch API endpoint. May need to check.

---

### 1.6 Cancel & Square-off — Unimplemented Stub

**Adapter:** `UpstoxOrderCommandAdapter.cancelAndSquareOffIntradayPositions()`
**Status:** ❌ Throws `UnsupportedOperationException`

**Impact:** Cannot automatically square off intraday positions for risk management.

---

## 2. 🟡 Moderate Gaps (P1 — Should Fix)

### 2.1 Fund Addition & Status

**Documented endpoints:**
| Method | Path | Purpose |
|---|---|---|
| POST | `/v2/user/fund/add` | Add funds to trading account |
| GET | `/v2/user/fund/status` | Check fund addition status |

**Status:** ❌ Not implemented.

---

### 2.2 Charges / Brokerage

**Documented:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/charges/brokerage` | Calculate brokerage for an order |
| GET | `/v2/charges/transaction` | Transaction charges |

**Status:** ❌ Not implemented.

---

### 2.3 Trade Analytics / P&L

**Documented:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/trade/profit-loss` | Realized & unrealized P&L report |
| GET | `/v2/trade/reports` | Trade reports |

**Status:** ❌ Not implemented.

---

### 2.4 Market Information (Trading Holidays, Timings)

**Documented endpoints:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/market/status/{exchange}` | Exchange status (defined but unused) |
| GET | `/v2/market/trading-days` | Trading calendar |
| GET | `/v2/market/holidays/{exchange}` | Trading holidays |
| GET | `/v2/market/timings` | Market timings |

**Status:** ⚠️ Only `MARKET_STATUS_PATH` is defined (in endpoints), and it's only used by `UpstoxOAuthClient.validateReadOnlyToken()` — not exposed as a public adapter API. Trading holidays, days, and timings are entirely missing.

---

### 2.5 Instrument Search

**Documented:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/instruments/search` | Search instruments by symbol/name |

**Status:** ❌ Not implemented. Only full master download exists.

**Impact:** Cannot quickly look up a single instrument without downloading the entire 100MB+ master CSV.

---

### 2.6 User Profile — No Adapter

**Via:** `UpstoxOAuthClient.fetchProfile()` returns token expiry only.

**Status:** ⚠️ `USER_PROFILE_PATH` endpoint defined but only used at the OAuth client level. No adapter method returns full profile data (name, email, segments, etc.).

**Documented response fields:** `user_id`, `user_name`, `email`, `mobile`, `pan`, `products`, `segments`, `exchange_permissions`, `token_expiry`

---

### 2.7 Mutual Fund APIs

**Documented:**
| Method | Path | Purpose |
|---|---|---|
| GET | `/v2/mutual-funds/holdings` | MF Holdings |
| GET | `/v2/mutual-funds/orders` | MF Orders |
| GET | `/v2/mutual-funds/portfolio` | MF Portfolio |

**Status:** ❌ Not implemented (P3 priority — acceptable gap).

---

### 2.8 Webhooks

**Documented:**
| Type | Purpose |
|---|---|
| Order Updates | Real-time order status webhooks |
| GTT Updates | GTT trigger notifications |
| Execution Notifications | Trade execution callbacks |

**Status:** ❌ No webhook receiver, registration, or event handling implemented.

---

## 3. 🟢 Minor Gaps & Improvements (P2 — Nice to Have)

### 3.1 Sandbox Routing — Not Implemented

`UpstoxConnectionSettings` has `isSandbox` field but **no adapter or client uses it**. Unlike Dhan (which has separate sandbox/live URL resolution), Upstox has a single `baseUrl` in `UpstoxHttpClient` without sandbox awareness.

**Impact:** Cannot easily switch between sandbox and live environments for testing.

### 3.2 No Idempotency for Orders

Unlike Dhan (which has `DhanOrderCommandAdapter` with `IdempotencyCachePort` and correlation ID tracking), the Upstox adapter has **no idempotency** mechanism. Duplicate order placement is possible on retry.

### 3.3 Order Cancel Error Resilience

`UpstoxOrderCommandAdapter.cancelAllOpenOrders()` catches all exceptions silently:
```java
try { restClient.cancelOrder(orderId); cancelled.add(orderId); }
catch (Exception ignored) {}
```

This swallows errors that should at minimum be logged.

### 3.4 No Rate Limiter Configuration

Upstox documented rate limits:
- **Order APIs** (place, modify, cancel, GTT, multi-order): **10 req/s** (regular), **50 req/s** (SEBI-registered), **500 req/min**
- **Standard APIs** (holdings, positions, funds, historical): **50 req/s**, **500 req/min**

The `UpstoxRetryExecutor` uses `MultiBucketRateLimiter` from broker-core but there's **no Upstox-specific rate limiter configuration** matching these documented limits.

### 3.5 Missing Expired Futures Support

`UpstoxExpiredOptionService` covers expired **options** only. There is no expired **futures** support, though the endpoints may overlap.

### 3.6 Instrument Resolver — No Search

`UpstoxInstrumentResolver` only resolves already-loaded instruments. There's no `getBySymbol()` support for searching by partial name.

---

## 4. ✅ What's Well-Implemented

### 4.1 Order API (Core)
| Endpoint | Status | Notes |
|---|---|---|
| POST `/v2/order/place` | ✅ | Full implementation via `UpstoxOrderCommandAdapter` |
| PUT `/v2/order/modify` | ✅ | Full implementation with mapper |
| DELETE `/v2/order/cancel` | ✅ | Works via query parameter |
| GET `/v2/order/history` | ✅ | `getOrderBook()` |
| GET `/v2/order/details` | ✅ | `getOrder()` |
| GET `/v2/order/trades/get-trades-for-day` | ✅ | `getTradeBook()` |

### 4.2 Market Data API
| Endpoint | Status | Notes |
|---|---|---|
| GET `/v2/market-quote/ltp` | ✅ | Batch and single |
| GET `/v2/market-quote/quotes` | ✅ | Batch and single |
| GET `/v2/market-quote/ohlc` | ✅ | Batch and single |
| GET `/v2/market-quote/order-book` | ✅ | Depth |
| GET `/v2/historical-candle/{...}` | ✅ | With date windowing & dedup |
| GET `/v2/historical-candle/expired/{...}` | ✅ | Via expired service |

### 4.3 Options & Greeks
| Endpoint | Status | Notes |
|---|---|---|
| GET `/v2/option/contracts` | ✅ | |
| GET `/v2/option/chain` | ✅ | Full snapshot with strikes |
| GET `/v2/option/expiry` | ✅ | |
| GET `/v2/option/greeks` | ✅ | Delta, Gamma, Theta, Vega, IV |

### 4.4 Portfolio
| Endpoint | Status | Notes |
|---|---|---|
| GET `/v2/portfolio/short-term-positions` | ✅ | |
| GET `/v2/portfolio/long-term-holdings` | ✅ | |
| GET `/v2/user/get-funds-and-margin` | ✅ | Balance object |

### 4.5 WebSocket Market Feed
| Component | Status | Notes |
|---|---|---|
| Feed Authorization | ✅ | `UpstoxFeedAuthorizer` |
| Binary Parser | ✅ | Supports TICK, QUOTE, DEPTH, FULL, OI frames |
| Stream Normalizer | ✅ | Maps to `MarketTickEvent` |
| Reconnection | ✅ | Exponential backoff with jitter |
| Health Checks | ✅ | Stale detection at 30s |

### 4.6 News
| Endpoint | Status | Notes |
|---|---|---|
| GET `/v2/news` | ✅ | By instrument keys, positions, holdings |

### 4.7 Expired Instruments
| Endpoint | Status | Notes |
|---|---|---|
| GET `/v2/expired-instruments/expiries` | ✅ | |
| GET `/v2/expired-instruments/option/contract` | ✅ | |
| GET `/v2/expired-instruments/historical-candle/{...}` | ✅ | With retry policy |

---

## 5. Test Coverage Audit

**Total test files:** 21
**Recent test run:** ✅ Build successful (4 tests executed, 6 up-to-date)

| Test File | Tests | Coverage |
|---|---|---|
| `UpstoxOrderCommandAdapterTest` | ✅ | Order placement flow |
| `UpstoxBinaryParserTest` | ✅ | Binary frame parsing |
| `UpstoxHistoricalDataServiceTest` | ✅ | Date windowing, candle mapping |
| `UpstoxFeedAuthorizerTest` | ✅ | Feed authorization |
| `UpstoxDomainMapperTest` | ✅ | Order/trade mapping |
| `UpstoxInstrumentResolverTest` | ✅ | Key resolution |
| `UpstoxInstrumentKeyResolutionTest` | ✅ | Symbol resolution |
| `UpstoxSegmentMapperTest` | ✅ | Segment mapping |
| `UpstoxResponseGuardTest` | ✅ | Error handling |
| `UpstoxHttpClientFailureTest` | ✅ | HTTP failure modes |
| `UpstoxOAuthClientTest` | ✅ | Token exchange |
| `UpstoxPkceUtilTest` | ✅ | PKCE generation |
| `UpstoxAnalyticsTokenHolderTest` | ✅ | Token management |
| `UpstoxJwtExpiryTest` | ✅ | Expiry validation |
| `UpstoxTokenExpiryTest` | ✅ | Token expiry |
| `UpstoxTokenManagerTest` | ✅ | Token lifecycle |
| `UpstoxStaticTokenHolderTest` | ✅ | Static token |
| `UpstoxRedirectServerTest` | ✅ | OAuth redirect |
| `UpstoxExpiredOptionMapperTest` | ✅ | Expired option mapping |
| `UpstoxHttpClientLiveIntegrationTest` | ✅ | Live integration |
| `BrokerCapabilityUnitTest` | ✅ | Capability routing |

**Test coverage gaps:**
- ❌ No tests for `UpstoxMarketDataProvider` (quote parsing, LTP, depth)
- ❌ No tests for `UpstoxPortfolioProvider` (positions, holdings, balance parsing)
- ❌ No tests for `UpstoxOptionsProvider` (option chain, Greeks)
- ❌ No tests for `UpstoxMarginProvider` (margin estimation)
- ❌ No tests for `UpstoxWebSocketMultiplexer` (connection, reconnection, frame dispatch)
- ❌ No tests for `UpstoxNewsProvider` (news parsing)
- ❌ No tests for `UpstoxOrderQueryAdapter` (order query, trade book)
- ❌ No tests for `UpstoxFuturesProvider` (contract resolution)

---

## 6. Priority Remediation Roadmap

### Phase 1 — Critical (P0)
| # | Item | Effort | Dependencies |
|---|---|---|---|
| 1 | **GTT Orders** — Add constants, REST client, adapter | 2-3 days | API port definition |
| 2 | **Order WebSocket** — Connect portfolio stream, wire listeners | 2-3 days | WebSocket protocol research |
| 3 | **Multi Order / Slicing** — Basket order support | 1-2 days | None |
| 4 | **Convert Position** — Add PUT endpoint | 0.5 day | None |
| 5 | **Set Kill Switch** — Implement or confirm no API | 0.5 day | Upstox research |
| 6 | **Cancel & Square-off** — Real implementation | 1 day | None |

### Phase 2 — Moderate (P1)
| # | Item | Effort |
|---|---|---|
| 7 | Fund addition & status | 1 day |
| 8 | Brokerage & charges | 1 day |
| 9 | Trade Analytics / P&L | 1-2 days |
| 10 | Market holidays, timings, trading days | 1 day |
| 11 | Instrument search | 0.5 day |
| 12 | User profile adapter | 0.5 day |
| 13 | Mutual Fund APIs | 1-2 days |
| 14 | Webhooks infrastructure | 3-5 days |

### Phase 3 — Improvements (P2)
| # | Item | Effort |
|---|---|---|
| 15 | Sandbox routing | 0.5 day |
| 16 | Idempotency for orders | 1 day |
| 17 | Rate limiter configuration | 0.5 day |
| 18 | Error resilience in cancelAllOpenOrders | 0.5 day |
| 19 | Test coverage for untested adapters | 3-4 days |
| 20 | Smoketest script | 0.5 day |

---

## 7. Architecture Notes

### Current Architecture
```
UpstoxBrokerConnection
├── UpstoxMarketDataProvider      → UpstoxMarketDataRestClient
├── UpstoxOrderCommandAdapter     → UpstoxOrderRestClient
├── UpstoxOrderQueryAdapter       → UpstoxOrderRestClient
├── UpstoxPortfolioProvider       → UpstoxPortfolioRestClient
├── UpstoxMarginProvider          → UpstoxJsonHttpClient (direct)
├── UpstoxOptionsProvider         → UpstoxOptionChainRestClient
├── UpstoxFuturesProvider         → UpstoxInstrumentResolver (local)
├── UpstoxNewsProvider            → UpstoxNewsRestClient
├── UpstoxWebSocketMultiplexer    → UpstoxFeedAuthorizer
└── UpstoxInstrumentResolver      → UpstoxInstrumentLoader
```

### Issues
1. **No GTT/Bracket adapter** — `ConditionalAlertProvider` not implemented
2. **No basket order support** — `BasketOrderProvider` port doesn't exist
3. **Single REST client per domain** — Good pattern, just missing GTT/multi clients
4. **WebSocket multiplexer asymmetry** — Market feed works, order feed doesn't
5. **Resilience not yet wired into REST clients** — Only `UpstoxHistoricalDataRestClient` and `UpstoxExpiredInstrumentRestClient` use the retry executor; other REST clients go direct

---

## Appendix: UpstoxEndpoints.java — Complete vs Missing

```
DEFINED (23 paths):                          MISSING (~25+ paths):
─────────────────────────────────────        ─────────────────────────────────
AUTH_DIALOG_PATH                             GTT: /gtt/orders
AUTH_TOKEN_PATH                              GTT: /gtt/orders/{id}
LOGOUT_PATH                                  GTT: /gtt/orders/{id} (modify)
USER_PROFILE_PATH                            GTT: /gtt/orders/{id} (cancel)
MARKET_STATUS_PATH                           /order/multi
LTP_PATH                                     /order/slicing
QUOTE_PATH                                   /portfolio/convert-position
ORDER_BOOK_PATH                              /instrument/search
OHLC_PATH                                    /market/trading-days
HISTORICAL_CANDLE_PATH                       /market/holidays/{exchange}
FEED_AUTHORIZE_PATH                          /market/timings
PLACE_ORDER_PATH                             /user/fund/add
MODIFY_ORDER_PATH                            /user/fund/status
CANCEL_ORDER_PATH                            /charges/brokerage
ORDER_DETAILS_PATH                           /charges/transaction
ORDER_HISTORY_PATH                           /trade/profit-loss
TRADES_PATH                                  /trade/reports
POSITIONS_PATH                               /mutual-funds/holdings
HOLDINGS_PATH                                /mutual-funds/orders
FUNDS_PATH                                   /mutual-funds/portfolio
OPTION_CONTRACTS_PATH                        /mutual-funds/transactions
OPTION_CHAIN_PATH                            /feed/portfolio-stream-feed/authorize
OPTION_EXPIRY_PATH                           (webhooks endpoints)
OPTION_GREEKS_PATH                           /user/segments
EXPIRED_EXPIRIES_PATH                        /user/permissions
EXPIRED_OPTION_CONTRACT_PATH                 (sandbox-specific endpoints)
EXPIRED_HISTORICAL_CANDLE_PATH
MARGIN_REQUIREMENT_PATH
NEWS_PATH
INSTRUMENT_MASTER_PATH
```

---

*Report generated by automated codebase analysis against Upstox Open API documentation.*
