# Market Data WebSocket Remediation Implementation Plan
**Created:** 2026-06-08  
**Based on:** Trade_J Market Data Certification Reports (reports_broker_2026-06-08/)  
**Goal:** Dhan → Certified, Upstox → Certified, ICICI → Certified

## Executive Summary

The certification audit identified 19 risks across 3 brokers. This plan sequences remediation into 4 priority tiers (P0–P3) with specific design proposals, test requirements, and complexity estimates.

**Must-fix before production:**
1. ICICI WebSocket depth (missing)
2. ICICI exchange segment hardcoded to NSE_EQ
3. ICICI reconnect resubscribe failure
4. Upstox null instrument in MarketDepth
5. Dhan OI frame type 5 silently dropped

---

## P0 — Production Blockers

### P0-1: R-01 / R3 — ICICI Reconnect Resubscribe Failure
**Risk Level:** CRITICAL  
**Likelihood:** CERTAIN  
**Business Impact:** Total loss of market data subscription after any network hiccup. Strategy silently stops receiving ticks.  
**Trading Impact:** Positions opened without live data; stale signals; potential unlimited loss on fast markets.

**Root Cause:** `BreezeWebSocketMultiplexer.openSocket()` uses `firstConnect` AtomicBoolean to call `resubscribeAll()` only on the very first Socket.IO connect. On subsequent reconnects, it calls `reconnectRegistry.notifyReconnect()` but no listener is wired to re-emit join events.

**Target Architecture:**
```java
socket.on(Socket.EVENT_CONNECT, args -> {
    resubscribeAll();  // idempotent — safe to call every time
    if (reconnectRegistry != null) {
        wsExecutor.submit(() -> reconnectRegistry.notifyReconnect());
    }
});
```

**Required Refactoring:**
- Remove `AtomicBoolean firstConnect` field
- Call `resubscribeAll()` unconditionally in EVENT_CONNECT handler
- `resubscribeAll()` is already idempotent (emits join for all keys in `subscriptions` map)

**Required Tests:**
- `BreezeWebSocketReconnectTest.reconnectResubscribesAllSymbols()` — simulate disconnect then reconnect; verify `emit("join", ...)` called with correct tokens
- `BreezeWebSocketReconnectTest.doubleConnectDoesNotDuplicateSubscriptions()` — verify idempotency
- Integration: Socket.IO mock server that disconnects and reconnects; verify subscription recovery

**Regression Coverage:**
- Existing `BreezeWebSocketReconnectTest` (5 tests) — unaffected
- `BreezeWebSocketDedupTest` — unaffected

**Estimated Complexity:** LOW — 8 lines changed, 2 new unit tests

---

### P0-2: R-02 — ICICI OHLC Streaming Implementation
**Risk Level:** CRITICAL  
**Likelihood:** CERTAIN  
**Business Impact:** ICICI cannot support any strategy requiring OHLC (RSI, MACD, VWAP, SuperTrend). ICICI limited to LTP-only monitoring.

**Root Cause:** No WebSocket client connects to `BreezeApiEndpoints.LIVE_OHLC_STREAM_URL`. `handleQuote()` only extracts `last/ltp`, `ltq`, `ttq`.

**Proposed Design:**
- **Option A (preferred):** Extend `BreezeWebSocketMultiplexer` with a third Socket.IO connection (`ohlcSocket`) to `LIVE_OHLC_STREAM_URL` that emits `ohlc` events
- **Option B:** Add OHLC fields to existing `stock`/`if` events if broker sends them but code ignores them

**Required Refactoring:**
- Add `ohlcSocket` field to `BreezeWebSocketMultiplexer`
- Add `subscribeOhlc()`, `unsubscribeOhlc()`, `resubscribeOhlcAll()` methods
- Extend `MarketTickEvent` creation with OHLC fields when available
- Update `connect()` to open 3rd socket if OHLC subscription exists

**Required Tests:**
- `BreezeWebSocketMultiplexerTest.ohlcStreamParsesOhlcFields()` — mock Socket.IO server sends OHLC JSON; verify `MarketTickEvent` contains open/high/low/close
- `BreezeWebSocketMultiplexerTest.ohlcResubscribeAfterReconnect()` — verify OHLC subscription survives Socket.IO reconnect
- Integration: live market-hours test with RELIANCE OHLC stream

**Regression Coverage:**
- Existing `IciciMarketFeedIntegrationTest` — should continue to pass (LTP path unchanged)
- `BreezeWebSocketReconnectTest` — unaffected

**Estimated Complexity:** MEDIUM — new Socket.IO client, ~150 lines, ~200 lines of tests

---

### P0-3: R-03 — ICICI Exchange Segment Resolution
**Risk Level:** HIGH  
**Likelihood:** CERTAIN  
**Business Impact:** All F&O, currency, and commodity ticks from ICICI are tagged `NSE_EQ`. Strategy portfolio attribution is wrong; multi-segment strategies (e.g., arbitrage across NSE_EQ + NSE_FNO) break silently.

**Root Cause:** `BreezeWebSocketMultiplexer.handleQuote()` hardcodes `ExchangeSegment.NSE_EQ` at line 273.

**Proposed Design:**
- Resolve segment from `instrumentResolver` using symbol from tick + subscription context
- Fallback to `NSE_EQ` only if resolution fails (with warning log)

**Required Refactoring:**
```java
// In handleQuote:
String symbol = tick.optString("stock_code", tick.optString("stock_name", "UNKNOWN"));
com.tradej.core.domain.value.ExchangeSegment segment = 
    resolveSegmentFromSymbol(symbol, request);
// where request is looked up from subscriptions map by matching symbol
```

**Required Tests:**
- `BreezeWebSocketMultiplexerTest.resolvesSegmentForNseFno()` — mock tick for NFO instrument; verify `NSE_FNO` segment
- `BreezeWebSocketMultiplexerTest.fallbackToNseEqForUnknownSymbol()` — verify fallback with warning

**Regression Coverage:**
- `IciciMarketFeedIntegrationTest` — passes with NSE_EQ for RELIANCE (unchanged behavior)

**Estimated Complexity:** LOW — ~20 lines changed, ~60 lines of tests

---

### P0-4: R-05 / R5 — Upstox Binary Parser Validation
**Risk Level:** HIGH  
**Likelihood:** LIKELY  
**Business Impact:** Silent market data corruption if broker changes binary format. Parser Javadoc explicitly states "must be verified against real sandbox output."

**Root Cause:** `UpstoxBinaryParser` is implemented from documentation only. No real-broker fixtures validate frame layout, field widths, or endianness.

**Proposed Design:**
1. Capture binary frames from Upstox V3 sandbox for each frame type (TICK, QUOTE, DEPTH, FULL, OI)
2. Store as binary fixture files in `broker/upstox/src/test/resources/fixtures/`
3. Add parser test that decodes each fixture and asserts exact field values
4. Add negative tests for malformed frames

**Required Refactoring:**
- No production code change required
- Add `UpstoxBinaryParserFixtureTest` with real payloads
- Add `UpstoxMarketDataProviderIntegrationTest` that validates decoded fields against known values

**Required Tests:**
- `parsesRealTickFrame()` — fixture from sandbox
- `parsesRealDepthFrame()` — 5-level depth fixture
- `parsesRealFullFrame()` — combined OHLC+depth fixture
- `parsesRealOiFrame()` — OI-only fixture

**Regression Coverage:**
- Existing `UpstoxBinaryParserTest` (5 tests) — should continue to pass

**Estimated Complexity:** MEDIUM — requires live sandbox access, test fixture creation, ~200 lines of test code

---

### P0-5: R-04 / R6 — Dhan Index Feed NPE
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Dhan index feed crash on null `indexValue`. Index data (NIFTY, BANKNIFTY) becomes unavailable.

**Root Cause:** `DhanPayloadNormalizer.normalizeFeedPacket(Index)` calls `PriceMath.toPaisa(index.indexValue())` without null guard.

**Required Refactoring:**
```java
case DhanMarketFeedPacket.Index index -> {
    String indexValue = index.indexValue();
    long indexPaisa = (indexValue == null || indexValue.isBlank()) 
        ? 0L : PriceMath.toPaisa(indexValue);
    // ... use indexPaisa
}
```

**Required Tests:**
- `DhanPayloadNormalizerTest.indexWithNullValueReturnsZero()` — verify no NPE
- `DhanPayloadNormalizerTest.indexWithValidValueReturnsPaisa()` — verify conversion

**Regression Coverage:**
- `DhanMarketFeedWebSocketIntegrationTest` — unaffected

**Estimated Complexity:** LOW — 3 lines changed, ~30 lines of tests

---

## P1 — High Risk

### P1-1: R-04 — Dhan OI Frame Type 5 Parser
**Risk Level:** HIGH  
**Likelihood:** LIKELY  
**Business Impact:** Option strategies silently missing OI updates. PCR, Max Pain calculations produce wrong signals.

**Root Cause:** `DhanMarketFeedBinaryParser.parse()` does not handle `FEED_RESPONSE_OI = 5`. Falls into default branch and routes to error handler.

**Proposed Design:**
```java
case DhanProtocolConstants.FEED_RESPONSE_OI -> consumer.accept(parseOi(buffer));
```
Add `DhanMarketFeedPacket.Oi` record with `segment`, `securityId`, `openInterest`.

**Required Tests:**
- `DhanMarketFeedBinaryParserTest.parsesOiFrame()` — binary OI payload → verify OI value
- `DhanPayloadNormalizerTest.normalizesOiToMarketTickEvent()` — OI packet → `openInterest` field populated

**Estimated Complexity:** LOW — ~20 lines parser + ~10 lines normalizer + ~30 lines tests

---

### P1-2: R-08 / R11 — Dhan Depth Feed Reconnect & Health
**Risk Level:** MEDIUM  
**Likelihood:** LIKELY  
**Business Impact:** 30s blind spot on depth during silent disconnect. No depth reconnect without market feed reconnect.

**Root Cause:** `DhanTwentyDepthWebSocketClient` has no `DhanWebSocketHealthMonitor`. Stale depth detection relies solely on market feed health monitor triggering full reconnect.

**Proposed Design:**
- Attach a dedicated `DhanWebSocketHealthMonitor` to depth client in `DhanWebSocketMultiplexer`
- On stale: call `subscriptionManager.getDepthClient().resubscribeAll()` then `connectDepthClientIfNeeded()`

**Required Tests:**
- `DhanTwentyDepthWebSocketClientTest.reconnectsOnStaleFeed()` — mock stale detection; verify reconnect triggered
- `DhanWebSocketMultiplexerTest.depthClientHasIndependentHealthMonitor()` — verify monitor attached

**Estimated Complexity:** LOW — ~30 lines in multiplexer + ~50 lines of tests

---

### P1-3: R-09 — Upstox Null Instrument in MarketDepth
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Downstream `OrderBookEngine` or analytics NPE when processing depth with null instrument.

**Root Cause:** `UpstoxStreamNormalizer.toMarketTick()` constructs `MarketDepth(null, bids, asks, ...)` — passes `null` instrument because `instrumentResolver.resolveSegment()` may return null for unresolved tokens.

**Required Refactoring:**
```java
Instrument instrument = instrumentResolver.resolve(frame.instrumentToken());
if (instrument == null) {
    log.warn("Upstox instrument token {} not resolved — using placeholder", frame.instrumentToken());
    instrument = new Instrument("UNKNOWN", "UNKNOWN", null, ExchangeSegment.UNKNOWN, "", "", null, null, null, 1L, 5L);
}
depth = new MarketDepth(instrument, bids, asks, ...);
```

**Required Tests:**
- `UpstoxStreamNormalizerTest.unresolvedTokenUsesPlaceholderInstrument()` — verify no NPE, placeholder used
- `UpstoxStreamNormalizerTest.resolvedTokenUsesCorrectInstrument()`

**Estimated Complexity:** LOW — ~10 lines changed + ~40 lines tests

---

### P1-4: R-07 / R7 — Tick Deduplication
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Duplicate ticks inflate volume, trigger false strategy signals, corrupt VWAP/OBV calculations.

**Root Cause:** No broker implements market tick deduplication. Relying on broker-side dedup only.

**Proposed Design:**
Add broker-level dedup using `(symbol, segment, sequenceId)` composite key:
```java
private final ConcurrentHashMap<String, Long> lastProcessedSequence = new ConcurrentHashMap<>();
// Key: symbol + ":" + segment.name()
// If incoming sequenceId <= lastProcessedSequence.get(key): DROP
// Else: update and forward
```

**Required Tests:**
- `DhanWebSocketMultiplexerTest.dropsDuplicateTickerSequence()` — same sequenceId twice; second dropped
- `UpstoxWebSocketMultiplexerTest.dropsDuplicateSequence()` — same sequenceId twice
- `BreezeWebSocketMultiplexerTest.dropsDuplicateTick()` — same ltp+timestamp combo

**Estimated Complexity:** MEDIUM — dedup logic in each multiplexer + ~100 lines tests

---

### P1-5: R-10 / R10 — ICICI Health Monitor
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Silent Socket.IO disconnect goes undetected for minutes. Strategy continues running on stale data.

**Root Cause:** ICICI has no staleness detection. Socket.IO `EVENT_DISCONNECT` may not fire on all failure modes.

**Proposed Design:**
- Add `BreezeWebSocketHealthMonitor` similar to Dhan/Upstox pattern
- Track `lastMessageTimestampMs` from `handleQuote` / `handleOrder`
- Periodic check (5s interval, 30s threshold) → emit `StreamHealthChanged(STALE)`
- On stale: trigger resubscribe + potential socket reconnect

**Required Refactoring:**
- New class: `BreezeWebSocketHealthMonitor` (~80 LOC)
- Wire into `BreezeWebSocketMultiplexer`

**Required Tests:**
- `BreezeWebSocketHealthMonitorTest.detectsStaleFeedAfter30s()`
- `BreezeWebSocketHealthMonitorTest.resetsOnMessage()`

**Estimated Complexity:** LOW — ~120 LOC new class + ~60 LOC tests

---

## P2 — Scalability

### P2-1: R-05 / R4 — Upstox findKey() HashMap Replacement
**Risk Level:** MEDIUM  
**Likelihood:** LIKELY at 1,000+ symbols  
**Business Impact:** CPU hotspot at market open. 1,000+ symbol scans per tick.

**Root Cause:** `UpstoxWebSocketMultiplexer.findKey()` does `subscriptions.keySet().stream().filter()` — O(n) per binary frame.

**Required Refactoring:**
```java
private final ConcurrentHashMap<Long, MarketSubscriptionRequest> tokenToRequest = new ConcurrentHashMap<>();
// Populate on subscribe(), remove on unsubscribe()
// Lookup: tokenToRequest.get(frame.instrumentToken()) — O(1)
```

**Required Tests:**
- `UpstoxWebSocketMultiplexerTest.findKeyResolvesFromHashMap()` — 1,000 entries; verify O(1) lookup
- `UpstoxWebSocketMultiplexerTest.handlerUsesDirectTokenLookup()` — end-to-end through `handleBinaryFrame`

**Estimated Complexity:** MEDIUM — refactor subscribe/unsubscribe/resubscribe paths + ~80 LOC tests

---

### P2-2: R-09 — WebSocket Subscription Rate Limiting
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Burst of 5,000 instrument subscribe calls hits broker-side throttling. Temporary market data blackout.

**Root Cause:** No throttle on `socket.sendText()` or `quoteSocket.emit()` across any broker.

**Proposed Design:**
Add per-broker emit rate limiter:
```java
private final RateLimiter subscribeRateLimiter = RateLimiter.create(10.0); // 10 emits/sec
public void subscribe(...) {
    subscribeRateLimiter.acquire();
    // ... send to broker
}
```

**Required Tests:**
- `DhanWebSocketMultiplexerTest.throttlesBulkSubscribeTo100InstrumentsPerFrame()`
- `BreezeWebSocketMultiplexerTest.throttlesJoinEmits()`

**Estimated Complexity:** LOW — ~20 lines per broker + ~40 lines tests

---

### P2-3: R-19 — Shared Event Bus Coupling
**Risk Level:** MEDIUM  
**Likelihood:** POSSIBLE  
**Business Impact:** Slow consumer (e.g., DB-backed `MarketDataListener`) blocks entire WebSocket callback thread. Under market-open burst, this cascades into feed stalls across all brokers.

**Architecture Evaluation:**

| Option | Latency | Throughput | Complexity | Recommendation |
|--------|---------|-----------|-----------|----------------|
| Current (direct dispatch) | 0 overhead | 1:1 | LOW | ❌ Blocks callback thread |
| Per-broker `AsyncEventBus` (queue + single consumer) | ~1ms queue | 1:1 | MEDIUM | ✅ Good balance |
| LMAX Disruptor | <1µs | 10M+ msg/s | HIGH | ⚠️ Over-engineering for this scale |
| Reactor Flux | ~1ms | 1M+ msg/s | HIGH | ⚠️ Adds framework dependency |
| RxJava | ~1ms | 500K+ msg/s | HIGH | ⚠️ Same as Reactor |

**Recommendation: Per-Broker AsyncEventBus**

```java
public final class BrokerEventBus {
    private final BlockingQueue<DomainEvent> queue = new ArrayBlockingQueue<>(10_000);
    private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(...);
    private final CopyOnWriteArrayList<MarketDataListener> listeners = new CopyOnWriteArrayList<>();
    
    public void publish(DomainEvent event) {
        // Drop oldest if full — never block WebSocket thread
        queue.offer(event); 
    }
    
    // Consumer thread:
    while (!shutdown) {
        DomainEvent event = queue.poll(100, TimeUnit.MILLISECONDS);
        if (event != null) dispatch(event);
    }
}
```

**Key Properties:**
- Decouples WebSocket callback from listener execution
- Bounded queue (10,000 events) prevents OOM
- Drop-oldest policy prevents head-of-line blocking
- Per-broker isolation: slow consumer on Dhan does NOT affect Upstox/ICICI

**Can a slow consumer affect Dhan, Upstox, ICICI simultaneously?**
- **Current:** No — each broker has its own WebSocket callback thread. A slow consumer on Dhan blocks Dhan's thread only. Upstox and ICICI continue independently. **However**, shared `HttpClient.newHttpClient()` thread pool means a slow HTTP response from any broker could starve the others' REST calls.
- **With per-broker BrokerEventBus:** No — even stronger isolation. Each broker's dispatch is independent.

**Required Refactoring:**
- New class: `BrokerEventBus` (~100 LOC)
- Update `DhanWebSocketMultiplexer.publishMarket()`, `UpstoxWebSocketMultiplexer` listener loop, `BreezeWebSocketMultiplexer` to use `BrokerEventBus.publish()` instead of direct iteration
- Add `BrokerEventBus` lifecycle to `connect()`/`disconnect()`

**Required Tests:**
- `BrokerEventBusTest.dropsOldestWhenQueueFull()` — verify bounded behavior
- `BrokerEventBusTest.isolationBetweenBrokers()` — slow consumer on broker A doesn't delay broker B
- `BrokerEventBusTest.dispatchesInOrder()` — FIFO guarantee

**Estimated Complexity:** MEDIUM — ~200 LOC new class + ~150 LOC refactoring per broker + ~100 LOC tests

---

## P3 — Observability

### P3-1: R-12 — Metrics & Observability
**Risk Level:** LOW  
**Likelihood:** CERTAIN  
**Business Impact:** Ops cannot detect feed issues, reconnect storms, or subscription drift without metrics.

**Proposed Design:**
Add Micrometer metrics to each broker WebSocket multiplexer:

| Metric | Type | Tags |
|--------|------|------|
| `broker.ws.connection.uptime` | Gauge | broker, state |
| `broker.ws.reconnect.count` | Counter | broker, outcome |
| `broker.ws.subscription.count` | Gauge | broker, feed_mode |
| `broker.ws.message.received` | Counter | broker, frame_type |
| `broker.ws.message.dropped` | Counter | broker, reason |
| `broker.ws.parse.error` | Counter | broker |
| `broker.ws.queue.depth` | Gauge | broker |

**Required Refactoring:**
- Add `MeterRegistry` field to each multiplexer (constructor injection)
- Increment counters in `onConnected`, `onDisconnected`, `onPacket`, `handleBinaryFrame`, `subscribe`, `unsubscribe`

**Estimated Complexity:** LOW — ~50 LOC per broker + ~30 LOC tests

---

## Implementation Sequence

```
Week 1 (P0):
  P0-1  ICICI resubscribe fix        [1 day]
  P0-2  ICICI OHLC streaming         [3 days]
  P0-3  ICICI segment resolution     [1 day]
  P0-4  Upstox parser validation     [2 days]  (blocked on sandbox access)
  P0-5  Dhan index NPE fix           [0.5 day]

Week 2-3 (P1):
  P1-1  Dhan OI parser               [1 day]
  P1-2  Dhan depth reconnect         [2 days]
  P1-3  Upstox null instrument       [0.5 day]
  P1-4  Tick deduplication           [2 days]
  P1-5  ICICI health monitor         [1 day]

Week 4 (P2):
  P2-1  Upstox HashMap lookup        [2 days]
  P2-2  Subscription rate limiter    [1 day]
  P2-3  Per-broker AsyncEventBus     [3 days]

Week 5 (P3 + hardening):
  P3-1  Metrics & observability      [2 days]
  Integration testing
  Soak testing (30 min per broker)
  Multi-broker chaos test
```

---

## Broker Certification Readiness Matrix

| Finding | Current State | Target State | Effort | Risk | Expected Outcome |
|---------|--------------|--------------|--------|------|------------------|
| R-01 ICICI resubscribe | FAIL | PASS | Low | Low | Reconnect restores all symbols in <1s |
| R-02 ICICI OHLC WebSocket | FAIL | PASS | Medium | Medium | OHLC streaming available via 3rd Socket.IO connection |
| R-03 ICICI segment resolution | FAIL | PASS | Low | Low | Correct exchange tag on all ICICI ticks |
| R-04 Dhan OI frame | FAIL | PASS | Low | Low | OI updates decoded and forwarded to strategies |
| R-05 Upstox parser validation | PARTIAL | PASS | Medium | Low | All frame types validated against real broker fixtures |
| R-06 Dhan index NPE | PARTIAL | PASS | Low | Low | Index feed robust to null/malformed values |
| R-07 Tick deduplication | FAIL | PASS | Medium | Low | Duplicate ticks filtered at broker boundary |
| R-08 Resubscribe tests | FAIL | PASS | Low | Low | 100% resubscribe path coverage across all brokers |
| R-09 WS sub rate limiting | FAIL | PASS | Low | Low | No burst exceeds broker-side throttle limits |
| R-10 ICICI health monitor | FAIL | PASS | Low | Low | Stale detection emits health event within 30s |
| R-11 Dhan depth reconnect | PARTIAL | PASS | Low | Low | Depth client auto-reconnects independently |
| R-12 Metrics & observability | PARTIAL | PASS | Low | Low | All required metrics exported to Micrometer |
| R-19 Shared event bus | PARTIAL | PASS | Medium | Low | Slow consumer on one broker does not affect others |

---

## Architecture Target State

```
                    ┌──────────────────┐
                    │   Application     │
                    │   Strategies      │
                    └────────┬─────────┘
                             │ MarketTickEvent / DepthUpdateEvent
                             ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ BrokerEventBus │  │ BrokerEventBus │  │ BrokerEventBus │
│    (Dhan)     │  │   (Upstox)    │  │   (ICICI)     │
│  10k queue    │  │  10k queue    │  │  10k queue    │
│  drop-oldest  │  │  drop-oldest  │  │  drop-oldest  │
└──────┬────────┘  └──────┬────────┘  └──────┬────────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ DhanWSMultiplexer│ │ UpstoxWSMult │ │ BreezeWSMult │
│ + HealthMonitor │ │ + HealthMon  │ │ + HealthMon  │
│ + TokenRot      │ │ + TokenRef   │ │ + SessionRef │
└──────────────────┘ └──────────────────┘ └──────────────────┘
       │                   │                   │
       ▼                   ▼                   ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ DhanMarket   │  │ Upstox      │  │ Breeze      │
│ FeedClient   │  │ FeedClient  │  │ FeedClient  │
│ (+DepthClient)│ │            │  │ (+OHLCClient)│
└─────────────┘  └─────────────┘  └─────────────┘
```

**Key invariants:**
1. Each broker owns its own `BrokerEventBus` with bounded queue
2. WebSocket callback thread never blocks on listener execution
3. Slow consumer on one broker cannot backpressure another
4. Per-broker `HttpClient` with dedicated executor eliminates shared thread pool contention
5. All three brokers can operate simultaneously without interference

---

## Certification Readiness Summary

| Broker | Current | After P0 | After P0+P1 | After P0+P1+P2+P3 |
|--------|---------|----------|-------------|-------------------|
| Dhan | PARTIAL | Certified | Certified | Certified |
| Upstox | PARTIAL | PARTIAL | Certified | Certified |
| ICICI | FAIL | PARTIAL | PARTIAL | Certified |

**Note:** ICICI cannot reach Certified until P0-2 (OHLC streaming) is complete, as OHLC streaming was a Critical certification item.
