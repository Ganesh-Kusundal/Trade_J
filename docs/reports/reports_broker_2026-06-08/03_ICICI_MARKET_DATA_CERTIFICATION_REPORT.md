# ICICI Market Data Certification Report
**Generated:** 2026-06-08  
**Broker:** ICICI Breeze  
**Scope:** ICICI Breeze WebSocket Market Feed (Socket.IO JSON)

## Phase 4 Certification Items

### Session Creation  
**PASS**  
- `BreezeSessionExchange.exchange()` POSTs to `CustomerDetails` with SessionToken + AppKey  
- Returns `BreezeSession` with encoded token, expiry at next midnight IST  
- URIs: `https://api.icicidirect.com/breezeapi/api/v1/customerdetails`

### Session Refresh  
**PARTIAL**  
- Session expires at next midnight IST (not configurable)  
- `BreezeTokenManager` (interface only shown) is expected to call `BreezeSessionExchange` on expiry  
- No automatic re-fetch of session data visible in `BreezeWebSocketMultiplexer`

### Session Recovery  
**PARTIAL**  
- Socket.IO auto-reconnect handles transport recovery  
- On reconnect: first connect → `resubscribeAll()`; subsequent → `reconnectRegistry.notifyReconnect()`  
- **Critical wiring gap:** Comment says "Reconnect re-subscription is handled by ReconnectListenerRegistry → SubscriptionCoordinator" but `SubscriptionCoordinator` is NOT wired into `BreezeWebSocketMultiplexer`. There is no `SubscriptionCoordinator` import or field. Reconnect via `reconnectRegistry` fires listeners but no listener in this class applies resubscription.  
- Result: socket.io reconnect does NOT resubscribe via `emitJoin`. Only the first-Socket.IO-connect path (`firstConnect.compareAndSet(true, false)`) calls `resubscribeAll()`.

### Tick Feed  
**PARTIAL**  
- `handleQuote()` parses: `last` or `ltp` → ltpPaisa, `ltq`, `ttq`  
- Captures: ltp, lastTradeQty, totalTradedQty  
- Missing fields: open, high, low, close, volume, OI — not extracted from JSON

### LTP  
**PARTIAL**  
- LTP extracted from `tick.optDouble("last", tick.optDouble("ltp", 0.0))` → `:267`  
- Segment hardcoded to `ExchangeSegment.NSE_EQ` regardless of actual exchange  

### Quote Feed  
**PARTIAL**  
- No OHLC in `handleQuote` — only ltp/ltq/ttq reach `MarketTickEvent`  
- `IciciMarketDataProvider.getOhlcSnapshot()` delegates to `getQuote()` via REST endpoint  
- **ICICI REST endpoint** provides OHLC; WebSocket does not deliver OHLC  

### OHLC Feed  
**FAIL — WebSocket**  
- `BreezeWebSocketMultiplexer.handleQuote()` does not parse OHLC fields  
- REST `BreezeDomainMapper.toQuote()` does parse OHLC but that's pull, not streaming  
- `BreezeApiEndpoints.LIVE_OHLC_STREAM_URL = "https://breezeapi.icicidirect.com"` is defined but **no WebSocket client** uses this URL  
- No OHLC streaming WebSocket implementation exists

### OHLC Streaming (Critical Certification Item)  
**FAIL**  
- OHLC streaming via WebSocket is NOT implemented  
- No code connects to `LIVE_OHLC_STREAM_URL`  
- No `BreezeOhlcWebSocketClient` or equivalent exists  
- No test for OHLC streaming exists

### Volume Feed  
**PARTIAL**  
- Volume fetched via REST (`ttq` field in quote JSON)  
- Not streamed via WebSocket

---

### Subscribe  
**PARTIAL**  
- `subscriptions.put()` adds entry  
- `emitJoin()` calls `quoteSocket.emit("join", new JSONArray(tokens))` only if `connected` is true  
- If `subscribe()` called before `connect()`, instruments stored but no emit sent until EVENT_CONNECT fires `resubscribeAll()`  
- Race condition: concurrent `subscribe()` + `connect()` may miss instruments if `connected` is already true but `resubscribeAll()` hasn't fired yet

### Unsubscribe  
**PARTIAL**  
- `subscriptions.remove()` + `quoteSocket.emit("leave", ...)`  
- If not connected, removes from map but no emit — acceptable

### Dynamic Add  
**PARTIAL**  
- `subscriptions.put()` + conditional `emitJoin()`  
- No join message emitted if socket is nil

### Dynamic Remove  
**PARTIAL**  
- `subscriptions.remove()` + conditional leave emit

### Resubscribe  
**PARTIAL**  
- On first Socket.IO connect: `resubscribeAll()` calls `emitJoin(subscriptions.keySet())`  
- On reconnect (after first): delegates to `reconnectRegistry.notifyReconnect()`  
- **As documented above, `reconnectRegistry.notifyReconnect()` does NOT resubscribe** because no `SubscriptionCoordinator` is wired. This is a **resubscribe failure on reconnect**.  

---

## Scaling Certification

### 100 Symbols  
**PASS**  
- `ConcurrentHashMap` + `JSONArray` of 100 tokens — feasible  
- Socket.IO client can handle

### 500 Symbols  
**PARTIAL**  
- `emitJoin("join", JSONArray(500))` — single socket.IO emit, should work  
- `scriptCodes()` iterates and resolves each instrument via `instrumentResolver.requireBreezeDefinition()` — O(n) blocking resolution before emit  
- Risk: burst resolution latency

### 1,000 Symbols  
**PARTIAL**  
- Larger `JSONArray` emit  
- `scriptCodes()` resolution becomes noticeable  
- No batching — single emit of 1,000 strings may hit Socket.IO message size limits

### Actual Sustainable Limit  
- Bound by Socket.IO message size and ICICI server-side subscription limits  
- No client-side enforcement — no `MAX_INSTRUMENTS_PER_SUBSCRIPTION`  
- No rate limiting on `emit("join", ...)` frequency  
- `MarketTickEvent` events are fired per JSON `stock`/`if` event with no timestamp validation

## Verdict: FAIL (for WebSocket market data)

| Capability | Status |
|------------|--------|
| Tick Feed | PARTIAL — ltp/ltq/ttq only |
| OHLC Streaming | FAIL — Not implemented |
| Depth Streaming | FAIL — Not implemented in WebSocket |
| Reconnect Resubscribe | FAIL — registry not wired |
| Segment Resolution | FAIL — hardcoded NSE_EQ |
| OHLC Accuracy | FAIL — No WebSocket OHLC path |
| Multi-symbol streaming | PARTIAL — unbounded, no enforcement |
| Session Refresh | PARTIAL — midnight expiry only |
| 1,000 symbol sustain | PARTIAL — possible but unverified |
