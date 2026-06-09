# Upstox Market Data Certification Report
**Generated:** 2026-06-08  
**Broker:** Upstox  
**Scope:** Upstox V3 Market Feed WebSocket (Binary) + OAuth Authorization

## Phase 3 Certification Items

### OAuth Flow  
**PASS**  
- `UpstoxOAuthClient.exchangeCode()` — PKCE flow with `code_verifier` → `:36-47`  
- `UpstoxOAuthClient.refreshToken()` — standard refresh grant  
- `UpstoxTokenManager.doRefresh()` — persists refreshed state  
- `UpstoxTokenManager.performInteractiveOAuth()` — full interactive flow with browser redirect  
- `UpstoxRedirectServer` — local HTTP callback handler  
- Extended token support: `UpstoxExtendedTokenHolder` (1-year analytics, no refresh) → `:176-208`

### One-Time Feed Authorization  
**PASS**  
- `UpstoxFeedAuthorizer.authorize()` calls `/feed/market-data-feed/authorize`  
- Returns `AuthorizedFeed` with `wsUri` and `expiryEpochMs`  
- URI is one-time-use; new URI required per connection

### Token Refresh  
**PASS**  
- `UpstoxTokenManager` extends `DefaultTokenLifecycleService` which auto-refreshes on `ensureValid()`  
- Refresh buffer configurable via `settings.refreshBufferMs()`

---

### V3 Feed Handling

#### Message Type 1 — Market Status  
**FAIL**  
- `UpstoxBinaryParser` has no `FRAME_MARKET_STATUS` type.  
- Constants defined: TICK(1), QUOTE(2), DEPTH(3), FULL(4), OI(5), HEARTBEAT(100), DISCONNECT(200), ERROR(255)  
- Market status frames silently thrown as `UpstoxParserException("Unknown frame type: 1")` — **if** Upstox V3 sends market status as type=1, they collide with TICK.  
- Risk: frameType 1 ambiguity — parser Javadoc says "1 = TICK" but document may also use 1 for market status

#### Message Type 2 — Snapshot  
**PARTIAL**  
- `parseQuote()` implements quote snapshot: ltp, ltq, open, high, low, close, volume, OI, timestamp  
- No separate MARKET_STATUS snapshot; handled as quote if type=2

#### Message Type 3 — Incremental Updates  
**PARTIAL**  
- `parseTick()` is the incremental update: ltp, lastTradeQty, volume, timestamp  
- Depth updates are separate (type=3 DEPTH frame)

---

### LTP  
**PASS**  
- `parseTick()`: ltpPaisa from `toPaisa(buffer.getInt())`  
- `UpstoxStreamNormalizer.toMarketTick()` maps `frame.ltpPaisa()` to `MarketTickEvent`

### Quotes  
**PASS**  
- `parseQuote()`: ltp + OHLC + volume + OI  

### OHLC  
**PASS**  
- Open/high/low/close all extracted in `parseQuote()` and `parseFull()`  

### Volume  
**PASS**  
- `volume()` in `ParsedFeedFrame` from quote, tick, full frames  

### Equity Feed  
**PASS**  
- `UpstoxSegmentMapper` resolves NSE_EQ, BSE_EQ segments  

### Futures Feed  
**PASS**  
- `UpstoxFuturesProvider` available  
- NSE_FNO segment mapping

### Options Feed  
**PASS**  
- `UpstoxOptionsProvider`, `UpstoxOptionChainRestClient` available  

### Index Feed  
**PASS**  
- NSE_INDEX segment resolved by `UpstoxSegmentMapper`  

---

### Subscribe  
**PARTIAL**  
- `UpstoxWebSocketMultiplexer.subscribe()` adds to `subscriptions` ConcurrentHashMap  
- Does NOT send a broker subscribe message — the V3 feed uses implicit subscription via token authorization OR requires subscribe command not seen in code  
- `MarketFeedHandler.onOpen()` calls `ws.request(Long.MAX_VALUE)` — push model  
- No explicit broker-side subscription message is sent for market data (only portfolio stream has ordered subscription via `authorizePortfolioStream()`)

### Unsubscribe  
**PARTIAL**  
- `unsubscribe()` removes from local map  
- Same gap: no broker unsubscribe command observed

### Dynamic Add  
**PARTIAL**  
- Entry added to `subscriptions` map  
- If connected, no re-subscribe message sent (no broker command)  

### Dynamic Remove  
**PARTIAL**  
- Entry removed from map  
- No broker unsubscribe command  

### Bulk Subscribe  
**PARTIAL**  
- `subscriptions` is a flat `ConcurrentHashMap` — bulk adds supported locally  
- No broker-side bulk operation

---

## Scaling Certification

### Single Feed: 100 / 500 / 1,000 Symbols  
**PARTIAL**  
- `ConcurrentHashMap` scales well at these sizes  
- `findKey()` iterates `subscriptions.keySet()` linearly for every binary frame — O(n) per message  
- At 1,000 symbols with high tick frequency, `findKey()` becomes CPU hotspot

### Actual Supported Limits Determination  
- No broker-documented limit enforced in code  
- `findKey()` linear scan is the binding constraint  
- `UpstoxWebSocketMultiplexer` buffer: `ByteBuffer.allocate(8192)` per frame — adequate for typical frames  
- `sequenceCounter` is `AtomicLong` — contention under high throughput

### Parser Throughput  
- `UpstoxBinaryParser.parse()` allocates `ParsedFeedFrame` record per tick  
- Javadoc warns: "This is a best-effort implementation based on documented Upstox market feed structure. The exact binary format must be verified against real sandbox output."

## Verdict: PARTIAL
- OAuth, feed authorization, binary parser for Quote/Full/Depth/OI are structurally complete
- Parser is UNVERIFIED against real broker output (documented risk)
- Market status frame type collision risk (type 1 = TICK vs MARKET_STATUS)
- No explicit subscribe/unsubscribe commands — assumes implicit push
- `findKey()` linear scan is a scaling bottleneck at 1,000+ symbols
- Missing heart-beat send path
