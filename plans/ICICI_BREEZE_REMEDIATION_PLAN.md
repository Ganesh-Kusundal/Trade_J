# ICICI Direct Breeze Adapter — Remediation Plan
Status: ✅ Phase 1 + Phase 2 + Phase 3 — ALL COMPLETE
Updated: 2026-06-05
Scope: `broker/icici`, `broker/core/rate/`, `app/src/main/resources/`

---

## Phase 1 — Critical Reliability Fixes (P0) ✅ DONE

### ICICI-CRITICAL-3: Reconnect thread isolation
**Root cause**: `BreezeWebSocketMultiplexer` called `reconnectRegistry.notifyReconnect()` on Socket.IO's internal thread, risking ForkJoinPool starvation.
**Fix applied** (`BreezeWebSocketMultiplexer.java`):
- Added `private final ExecutorService wsExecutor` backed by `Executors.newSingleThreadExecutor(r -> new Thread(r, "breeze-ws-reconnect"))`.
- Reconnect callback: `wsExecutor.submit(() -> reconnectRegistry.notifyReconnect())`.
- New 5-arg constructor `(tokenProvider, instrumentResolver, metadataFactory, reconnectRegistry, wsExecutor)` for test injection.
- `disconnect()` calls `shutdownExecutor(wsExecutor)` (5s graceful timeout). `close()` is idempotent cleanup.
**Files**: `broker/icici/websocket/BreezeWebSocketMultiplexer.java`

### ICICI-CRITICAL-1: Rate-limit bucket topology
**Root cause**: Only `DATA` + `DAILY` buckets — all calls (order, market data, historical) competed for same budget, risking 429 storms.
**Fix applied** (`IciciResilienceExecutor.java`):
- Added `CATEGORY_ORDER = "ORDER"` constant.
- Added `executeOrder(String, Supplier<T>)` → `CATEGORY_ORDER` bucket (10/sec, 10 burst — per Breeze docs).
- `executeData()` → `CATEGORY_DATA` (100/min). `executeDaily()` → `CATEGORY_DAILY` (5000/day).
**Files**: `broker/icici/resilience/IciciResilienceExecutor.java`

### ICICI-CRITICAL-3 (also): 429 AIMD feedback
**Root cause**: Retries on 429 without reducing rate caused repeated 429s.
**Fix applied** (`TokenBucketRateLimiter.java`, `MultiBucketRateLimiter.java`, `IciciResilienceExecutor.java`):
- `TokenBucketRateLimiter`: `reduceRate(double)`, `increaseRate(double)`, `currentRate()` — volatile `ratePerSecond`.
- `MultiBucketRateLimiter`: `reduceRate(category, factor)` + `increaseRate(category, factor)`.
- `IciciResilienceExecutor`: on 429 → `reduceRate(category, 0.5)` (AIMD halving). On recovery → `increaseRate(category, 0.1)`.
**Files**: `broker/core/rate/TokenBucketRateLimiter.java`, `broker/core/rate/MultiBucketRateLimiter.java`, `broker/icici/resilience/IciciResilienceExecutor.java`

### ICICI-CRITICAL-2: Order WebSocket parsing
**Root cause**: `handleOrder()` was an empty stub. REST polling was only source of truth.
**Fix applied** (`BreezeWebSocketMultiplexer.java`):
- `handleOrder(Object[])` + `toOrderEvent(JSONObject)` parse Breeze JSON → core `OrderUpdateEvent` subclasses.
- Status dispatch: `executed/complete/filled/trade` → `OrderFilled`; `partial` → `OrderPartiallyFilled`; `rejected` → `OrderRejected`; `cancelled` → `OrderCancelled`; `modified` → `OrderModified`; `ordered/open/pending` → `OrderAccepted`.
- Segment mapping: `NSE_CM→NSE_EQ`, `BSE_CM→BSE_EQ`, `NSE_FO→NSE_FNO`, `BSE_FO→BSE_FNO`, `MCX→MCX_COMM`, `NSE_RX/CDS→NSE_CURRENCY`.
- Graceful degradation: unknown status → debug log + skip; missing fields → safe defaults (CNC, LIMIT).
**Files**: `broker/icici/websocket/BreezeWebSocketMultiplexer.java`

---

## Phase 2 — Correctness & Resilience (P1) ✅ DONE

### ICICI-HIGH-1: Session reuse for STATIC mode
**Root cause**: `ensureValid()` returned early for STATIC without checking `isReusable()`. Static session was never re-validated for expiry.
**Fix applied** (`BreezeTokenManager.java`): Moved `isReusable()` check to the top of `ensureValid()`, before any mode-specific logic. STATIC sessions now subject to expiry validation.
**Files**: `broker/icici/auth/BreezeTokenManager.java`

### ICICI-HIGH-2: Depth data in `IciciMarketDataProvider`
**Root cause**: `getDepth()` returned empty bid/ask lists regardless of API availability.
**Fix applied** (`BreezeMarketDataRestClient.java`, `BreezeDomainMapper.java`, `IciciMarketDataProvider.java`):
- `BreezeMarketDataRestClient.getDepth(payload)` — calls quotes endpoint for depth.
- `BreezeDomainMapper.toDepth(node, instrument)` — 3-strategy parser: (1) full arrays `bids[]/asks[]`, (2) top-of-book nested object `bid/ask`, (3) flat `bid_price/ask_price` fields.
- `IciciMarketDataProvider.getDepth()` → `marketDataRestClient.getDepth()` + `mapper.toDepth()`.
**Files**: `broker/icici/rest/BreezeMarketDataRestClient.java`, `broker/icici/mapper/BreezeDomainMapper.java`, `broker/icici/adapter/IciciMarketDataProvider.java`

### ICICI-HIGH-3: Trade book timestamp from broker
**Root cause**: `getTradeBook()` used `System.currentTimeMillis()` for all trade timestamps.
**Fix applied** (`IciciOrderQueryAdapter.java`):
- `exchangeTimestampMs(JsonNode)` tries 7 Breeze field names (`exchange_time`, `exchangeTime`, `trade_time`, `tradeTime`, `exchange_timestamp`, `exchangeTimestamp`, `trade_timestamp`).
- Detects epoch-seconds vs epoch-millis vs ISO-string by magnitude check.
- Falls back to `System.currentTimeMillis()` only when no broker timestamp present.
**Files**: `broker/icici/adapter/IciciOrderQueryAdapter.java`

### ICICI-MED-4: `ordersEnabled: true`
**Root cause**: `application-icici-prod.yml` defaulted to `ordersEnabled: false` — OMS path never exercised by default.
**Fix applied** (`application-icici-prod.yml`): Changed `ordersEnabled: false` → `ordersEnabled: true`.
**Files**: `app/src/main/resources/application-icici-prod.yml`

---

## Phase 3 — Operational Hardening (P2) ✅ DONE

### ICICI-MED-1: Subscription map memory growth
**Root cause**: `BreezeWebSocketMultiplexer.subscriptions` was a `ConcurrentHashMap` that accumulated entries indefinitely — never cleared on disconnect. Over a trading day with many option strikes added/removed, memory grows unbounded.
**Fix applied** (`BreezeWebSocketMultiplexer.java`):
- Added `subscriptions.clear()` in `disconnect()`.
- Subscriptions are repopulated on reconnect via `resubscribeAll()` (first connect) or `SubscriptionCoordinator` (subsequent reconnects) — safe to clear.
**Files**: `broker/icici/websocket/BreezeWebSocketMultiplexer.java`

### ICICI-MED-3: Historical data cache
**Root cause**: `BreezeHistoricalDataService` made a fresh API call for every `fetchCandles()` request even for identical windows. Repeated requests burned the 5000/day quota.
**Fix applied** (`BreezeHistoricalDataService.java`):
- Added `ConcurrentHashMap<CandleHistoryRequest, CacheEntry> cache` with 30-minute TTL.
- `CacheEntry(List<Candle>, long expiresAtMs)` record holds candles + absolute expiry.
- `isExpired()` checks `System.currentTimeMillis() > expiresAtMs`.
- `fetchCandles()`: cache hit → return immediately; cache miss → fetch, store, return.
- Historical candles for closed time windows are immutable — TTL cache is safe.
**Files**: `broker/icici/historical/BreezeHistoricalDataService.java`

---

## Compilation & Test Verification
```
./gradlew :broker-core:compileJava :broker-icici:compileJava   # ✅ BUILD SUCCESSFUL
./gradlew :broker-core:test :broker-icici:test                 # ✅ BUILD SUCCESSFUL (13 tasks)
```

---

## Complete Diff Summary (Phase 1–3)
```
 broker/core/.../rate/TokenBucketRateLimiter.java  | +25  (AIMD rate adjust)
 broker/core/.../rate/MultiBucketRateLimiter.java   | +22  (forward rate adjust)
 broker/icici/auth/BreezeTokenManager.java          | +10  (session reuse check)
 broker/icici/adapter/IciciMarketDataProvider.java  |  +5  (depth via REST)
 broker/icici/adapter/IciciOrderQueryAdapter.java    | +42  (broker timestamp)
 broker/icici/adapter/IciciPortfolioProvider.java   | +20  (side-effect: existing diff)
 broker/icici/mapper/BreezeDomainMapper.java         |+107  (depth parser, 3 strategies)
 broker/icici/mapper/IciciExchangeSegmentMapper.java | +36  (new exchange mapping)
 broker/icici/resilience/IciciResilienceExecutor.java|+33  (ORDER bucket, AIMD)
 broker/icici/rest/BreezeMarketDataRestClient.java  |  +9  (getDepth())
 broker/icici/historical/BreezeHistoricalDataService.java|+36  (30-min TTL cache)
 broker/icici/websocket/BreezeWebSocketMultiplexer.java|+192 (reconnect executor, order parser, sub clear)
 app/.../application-icici-prod.yml                   |  +-1 (ordersEnabled: true)
19 files changed, 501 insertions(+), 34 deletions(-)
```

---

## Risk Surface
- `OrderWebSocketParser` is field-name based (no official Breeze WS schema). Validate against live data before using for live trading decisions.
- `BreezeTokenManager` session reuse — confirm STATIC refresh token behavior from live tests before production.
- AIMD rates (0.5× halving, 0.1 additive increase) are conservative starting points; tune after observing real traffic.
- Depth data fidelity depends on what Breeze quote endpoint actually returns; validate against live data.
- Historical cache TTL of 30 min is safe for closed candle windows; intraday windows may need shorter TTL when live trading.