# Stability Report
**Generated:** 2026-06-08  
**Source:** Static code analysis; no runtime profiling performed

## Dhan Stability

### Thread Model
- `reconnectScheduler`: single-thread daemon executor — `newSingleThreadScheduledExecutor`  
- `healthMonitor.executor`: single-thread daemon executor  
- `DhanWebSocketMultiplexer` main thread for `connect()`/`disconnect()` under `transportLock`

### Memory Leak Vectors

| Risk | Assessment | Evidence |
|------|-----------|----------|
| reconnectScheduler not shutdown | HIGH | Created in constructor, never shutdown on `disconnect()`. Scheduling new tasks on shutdown executor works, but thread not reclaimed. |
| Listener list growth | LOW | `CopyOnWriteArrayList` — explicit `close()` clears listeners in `DhanMarketFeedWebSocketClient` and `DhanOrderStreamWebSocketClient` but NOT in `DhanWebSocketMultiplexer` itself. |
| Depth client thread leak | LOW | `DhanTwentyDepthWebSocketClient` has no dedicated thread — runs on JDK WebSocket callback threads |

### GC Pressure
- `DhanMarketFeedWebSocketClient.onBinary()`: `new byte[data.remaining()]` — allocation per binary frame  
- `DhanPayloadNormalizer`: creates new `MarketTickEvent` record per frame — allocation per tick  
- `DhanTwentyDepthWebSocketClient`: `ByteBuffer.allocate(16_384)` per client, grows incrementally  

### Thread Count Leak
- Dhan creates 2 named daemon threads total (reconnect + health). No leak on repeated connect if scheduler is not shutdown.

### Connection Count
- 3 WebSocket connections max: market feed, order stream, depth  
- `disconnect()` nulls out references to allow GC

### Subscription Count
- Bounded by `FEED_MAX_INSTRUMENTS_PER_CONNECTION = 5,000`

### Message Drop Risk
- No internal queue in WebSocket client — direct callback dispatch  
- If listener is slow, JDK WebSocket `request(1)` flow control pauses the broker  
- Risk: slow listener causes broker backpressure → missed frames

---

## Upstox Stability

### Thread Model
- `healthExecutor`: single-thread scheduled executor  
- `reconnectInProgress` AtomicBoolean prevents parallel reconnect  
- `manuallyDisconnected` AtomicBoolean prevents reconnect storms after intentional disconnect

### Memory Leak Vectors

| Risk | Assessment | Evidence |
|------|-----------|----------|
| `orderMessageBuffer` StringBuilder | MEDIUM | Grows per message fragment. Cleared on `last=true`. Malformed fragmented text could grow unbounded if `last` never arrives. |
| `latestOrderStatuses` map | LOW | Bounded by unique orders per session |

### GC Pressure
- `new byte[data.remaining()]` per binary frame  
- `ParsedFeedFrame` record allocation per tick  
- `ByteBuffer.allocate(8192)` created in `MarketFeedHandler` inner class — one per WebSocket instance

### Thread Count Leak
- Up to 2 named threads per multiplexer instance (health + potential reconnect)

### Connection Count
- 2 WebSocket connections: market data + order/portfolio  
- `orderWs` reconnect is fire-and-forget via `CompletableFuture.runAsync()` — uses ForkJoinPool.commonPool()

### Subscription Count
- No enforced limit — map grows until `unsubscribe()` removes entries

### Message Drop Risk
- `handleBinaryFrame()` catches `UpstoxParserException` and skips — malformed frames dropped silently  
- `supervisor.onMessage()` resets staleness timer — a series of malformed frames could suppress stale detection

---

## ICICI Stability

### Thread Model
- `wsExecutor`: single-thread executor for reconnect callbacks  
- Socket.IO client has its own internal thread pool (engine.io)

### Memory Leak Vectors

| Risk | Assessment | Evidence |
|------|-----------|----------|
| `subscriptions` cleared on disconnect | LOW | `disconnect()` calls `subscriptions.clear()` |
| `firstConnect` AtomicBoolean | NONE | Per-socket, reclaimed on socket close |
| `latestOrderStatuses` | LOW | Bounded |

### GC Pressure
- JSON parsing per `stock`/`if` event — `new JSONObject(...)` and `JSONObject.opt*` methods  
- `scriptCodes()` creates `ArrayList<String>` per emit

### Thread Count Leak
- `wsExecutor` shutdown in `disconnect()` — 1 thread  
- Socket.IO engine.io threads — depend on library version

### Connection Count
- 2 Socket.IO connections: `quoteSocket` + `orderSocket`

### Subscription Count
- No enforced limit — bounded by broker

### Message Drop Risk
- `handleQuote()` catches exceptions and logs at DEBUG — malformed quote silently dropped  
- No sequence number or dedup for market ticks

---

## Cross-Broker Stability Summary

| Issue | Dhan | Upstox | ICICI |
|-------|------|--------|-------|
| Memory leak on repeated connect | HIGH (scheduler not shutdown) | LOW | LOW |
| Thread leak on repeated connect | LOW | LOW | LOW |
| GC pressure under load | MEDIUM | MEDIUM | LOW |
| Slow consumer risk | HIGH (backpressure from listener) | MEDIUM | MEDIUM |
| Parse error handling | Drops unknown frame types as error | Skips malformed binary | Silently catches JSON parse errors |
| Connection degradation over time | No observed mechanism | No observed mechanism | No observed mechanism |

**Overall Stability Rating:**  
- **Dhan:** PRODUCTION with 1 critical fix (scheduler shutdown) and 2 high-priority fixes (OI parser, depth client heartbeat)  
- **Upstox:** PRODUCTION with parser verification requirement  
- **ICICI:** NOT PRODUCTION — resubscribe failure, no OHLC stream, no depth stream
