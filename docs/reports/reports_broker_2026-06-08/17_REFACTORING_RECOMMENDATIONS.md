# Refactoring Recommendations
**Generated:** 2026-06-08  
**Priority:** P0 (Critical) / P1 (High) / P2 (Medium) / P3 (Low)

## Dhan Refactoring

### P0: Fix DhanMarketFeedBinaryParser OI Handling
```java
// DhanMarketFeedBinaryParser.parse() — add:
case DhanProtocolConstants.FEED_RESPONSE_OI -> consumer.accept(parseOi(buffer));
```
```java
private static DhanMarketFeedPacket.Oi parseOi(ByteBuffer buffer) {
    Header header = parseHeader(buffer);
    long oi = buffer.getInt() & 0xFFFFFFFFL;
    return new DhanMarketFeedPacket.Oi(header.segment(), header.securityId(), oi);
}
```

### P0: Add reconnectScheduler shutdown
```java
// In DhanWebSocketMultiplexer.disconnect():
reconnectScheduler.shutdown();
try {
    if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        reconnectScheduler.shutdownNow();
    }
} catch (InterruptedException e) {
    reconnectScheduler.shutdownNow();
    Thread.currentThread().interrupt();
}
```

### P1: Add depth client health monitor
```java
// In DhanWebSocketMultiplexer constructor:
DhanWebSocketHealthMonitor depthHealthMonitor = new DhanWebSocketHealthMonitor(
    clientHolder,
    idleMs -> connectDepthClientIfNeeded(),  // trigger reconnect
    this::publishMarket,
    metadataFactory,
    "dhan-depth"
);
depthHealthMonitor.start();
```

### P2: Add null guard for indexValue
```java
// DhanPayloadNormalizer.normalizeFeedPacket(Index):
String indexValue = index.indexValue();
long indexPaisa = (indexValue == null || indexValue.isBlank()) 
    ? 0L : PriceMath.toPaisa(indexValue);
```

### P3: Extract market-data-specific events for observability
- `MarketTickReceived` counter per broker  
- `BytesReceived` counter  
- `ParseError` counter (currently logged but not counted)

---

## Upstox Refactoring

### P0: Validate parser with real broker fixtures
- Capture binary frames from Upstox V3 sandbox  
- Add `src/test/resources/fixtures/upstox/*.bin` files  
- Add decoder test that asserts exact field values
- Resolve Frame Type 1 ambiguity (TICK vs MARKET_STATUS)

### P0: Replace findKey() with HashMap
```java
// In UpstoxWebSocketMultiplexer:
private final ConcurrentHashMap<Long, MarketSubscriptionRequest> tokenToRequest = new ConcurrentHashMap<>();

// On subscribe:
instrumentResolver.resolveToken(request.symbol(), request.exchangeSegment())
    .ifPresent(token -> tokenToRequest.put(token, request));

// In handleBinaryFrame:
MarketSubscriptionRequest key = tokenToRequest.get(frame.instrumentToken());
```

### P1: Add explicit subscribe/unsubscribe to broker
- Confirm with Upstox docs if broker requires explicit `subscribe` message  
- If yes: add `AuthorizedFeed.subscribe(List<String> instrumentTokens)` method

### P2: Fix sequenceCounter contention
```java
private final java.util.concurrent.ThreadLocalRandom random = new java.util.concurrent.ThreadLocalRandom();
// Use random long for sequenceId, or pre-allocate segments
```

### P2: Add frame validation in parser
- Minimum frame size checks per frame type  
- Detect and skip zero-length fields

---

## ICICI Refactoring

### P0: Fix resubscribe on reconnect (R-01)
```java
// Remove firstConnect AtomicBoolean, use:
socket.on(Socket.EVENT_CONNECT, args -> {
    resubscribeAll();  // Always resubscribe — idempotent
    if (reconnectRegistry != null) {
        wsExecutor.submit(() -> reconnectRegistry.notifyReconnect());
    }
});
```

### P0: Add OHLC streaming or document REST-only
- Either implement `BreezeOhlcWebSocketClient` connecting to `LIVE_OHLC_STREAM_URL`  
- Or document explicitly that ICICI WebSocket provides LTP-only; OHLC via REST polling

### P1: Implement depth streaming
- Add `BreezeDepthWebSocketClient` or extend `handleQuote` to parse depth fields if broker provides them

### P1: Resolve exchange segment from instrument definition
```java
// In handleQuote, replace:
com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
// With:
instrumentResolver.resolveSegment(symbol),
```

### P1: Pre-resolve and cache scriptCodes
```java
private final ConcurrentHashMap<MarketSubscriptionRequest, String> scriptCodeCache = new ConcurrentHashMap<>();

private List<String> scriptCodes(Collection<MarketSubscriptionRequest> instruments) {
    return instruments.stream()
        .map(req -> scriptCodeCache.computeIfAbsent(req, this::resolveScriptCode))
        .toList();
}
```

### P2: Add staleness detection
```java
// Add BreezeWebSocketHealthMonitor or:
socket.on(Socket.EVENT_PING, ...); // Check last message timestamp
```

### P3: Add structured health events
- Emit `StreamHealthChanged` on connect, disconnect, error  
- Log at INFO level (currently only log.info on connect)

---

## Cross-Broker Refactoring

### P0: Per-broker HttpClient with named executor
```java
public static HttpClient dedicatedHttpClient(String name) {
    ThreadFactory threadFactory = r -> {
        Thread t = new Thread(r, name + "-http");
        t.setDaemon(true);
        return t;
    };
    return HttpClient.newHttpBuilder()
        .executor(Executors.newSingleThreadExecutor(threadFactory))
        .build();
}
```
Apply to: `DhanMarketFeedWebSocketClient`, `DhanTwentyDepthWebSocketClient`, `DhanOrderStreamWebSocketClient`, `UpstoxWebSocketMultiplexer`

### P1: Unified WebSocket listener offload
Add a bounded `BlockingQueue<DomainEvent>` between WebSocket callback and listener dispatch:
```java
private final BlockingQueue<DomainEvent> eventQueue = new ArrayBlockingQueue<>(10_000);
private final ExecutorService dispatchExecutor = Executors.newSingleThreadExecutor(...);

// In onText/onBinary:
eventQueue.offer(event);  // drops oldest if full

// Consumer thread:
while (!shutdown) {
    DomainEvent event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
    if (event != null) dispatchToListeners(event);
}
```
Benefits: prevents slow consumer from blocking WebSocket callback; bounded memory

### P1: Common SubscriptionManager interface
Extract common patterns from all three brokers into `BrokerSubscriptionManager`:
- `ConcurrentHashMap<MarketSubscriptionRequest, FeedMode>` state
- `addAll`, `removeAll`, `snapshot`, `size`, `isEmpty`
- Eliminates 80% of duplicated subscription tracking code

### P2: Add tick-level deduplication
```java
private final ConcurrentHashMap<String, Long> lastTickSequence = new ConcurrentHashMap<>();
// Key: symbol:segment
// Value: last sequenceId
// Drop if sequenceId <= lastTickSequence.get(key)
```

### P2: Add broker-specific CircuitBreaker wrapper for REST
Already available in `DhanRetryExecutor`; extract as shared `BrokerCircuitBreaker` used by all three brokers.
