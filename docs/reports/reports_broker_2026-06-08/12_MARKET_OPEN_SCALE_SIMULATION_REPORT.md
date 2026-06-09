# Market Open Scale Simulation Report
**Generated:** 2026-06-08  
**Source:** Static analysis against expected market-open behavior

## Expected Market Open Conditions

- Peak message rate: 50–200 messages/second per heavily-traded symbol  
- Burst pattern: sharp increase at 9:15 IST  
- Simultaneous symbols: 1,000–5,000 active instruments across Dhan  
- Latency sensitivity: <100ms end-to-end for strategy signals

## Dhan Scale Simulation

### 100 Symbols
**PASS** — 1 batch of 100 instruments. Zero queue concern.

### 500 Symbols
**PASS** — 5 batches of 100. Binary parser handles ~500 frames/sec with headroom.

### 1,000 Symbols
**PARTIAL** — 10 batches. `groupedByMode()` iterates entire map on reconnect (O(n)). Parser allocates ~100 bytes per frame. No backpressure — slow consumer will block `publishMarket()`.

### Market Open Backpressure
- **Risk:** `publishMarket()` iterates `CopyOnWriteArrayList<MarketDataListener>` synchronously. If a listener blocks (e.g., slow DB write), all market feed processing stalls.  
- **No queue** between WebSocket and listeners — direct dispatch.  

### Expected Behavior
- At 5,000 symbols with 50 msg/s average: ~250,000 events/sec  
- `ByteBuffer` allocation per event: 250K × ~100 bytes ≈ 25MB/sec young-gen allocation  
- GC pause risk: 25MB/sec sustained may trigger young-gen GC every 200-500ms depending on heap size

---

## Upstox Scale Simulation

### 100 Symbols
**PASS** — `findKey()` iterates 100 entries per tick. Negligible.

### 500 Symbols
**PARTIAL** — 500 comparisons per tick. At 50 ticks/sec × 500 = 25,000 comparisons/sec. Acceptable.

### 1,000 Symbols
**PARTIAL** — 1M comparisons/sec at 1,000 ticks/sec for 1,000 symbols. On modern JVM with -XX:+UseStringDedup this is acceptable but contributes to GC pressure.

### Market Open Backpressure
- Same direct-dispatch pattern as Dhan
- `handleBinaryFrame()` catches `UpstoxParserException` silently — a parser mismatch during high-traffic would silently drop ticks
- `OrderFeedHandler.onText()` uses `StringBuilder` which grows — long fragmented messages without `last=true` could be a problem

### Expected Behavior
- Parser unverified risk: a single unexpected frame during market open could cascade into mass tick drops
- `sequenceCounter` contention: multi-core benefit negated by AtomicLong CAS retries

---

## ICICI Scale Simulation

### 100 Symbols
**PASS**

### 500 Symbols
**PARTIAL** — `scriptCodes()` blocking resolution on critical path. At 9:15 IST, 500 blocking calls could add seconds of latency before subscription emit.

### 1,000 Symbols
**FAIL** — Single Socket.IO emit of 1,000-element JSONArray. Unverified server behavior. No batching or retry.

### Market Open Backpressure
- Socket.IO library buffers messages — if broker can't keep up, internal queue grows
- No timeout on `quoteSocket.emit("join", ...)` — hangs indefinitely if broker unresponsive

---

## Backpressure and Queue Growth

| Broker | Queue Mechanism | Risk |
|--------|----------------|------|
| Dhan | None — direct listener dispatch | HIGH — slow consumer stalls entire feed |
| Upstox | None — direct listener dispatch | HIGH — same |
| ICICI | Socket.IO internal queue | MEDIUM — library-managed but opaque |

## Consumer Starvation

- All three brokers iterate listener lists on the same thread as WebSocket callback  
- A single slow `MarketDataListener.onEvent()` blocks all subsequent tick processing  
- **No timeout, no offload, no thread isolation** in any broker implementation

## Event Loss Risk

| Broker | Risk Level | Cause |
|--------|-----------|-------|
| Dhan | MEDIUM | OI frame type not handled |
| Upstox | HIGH | Parser mismatch drops ticks silently; no recovery |
| ICICI | HIGH | Resubscribe failure on reconnect loses entire subscription |

## Verdict: FAIL for Production Market Open

No broker implementation has backpressure handling or consumer isolation. Market-open scale (5,000+ symbols, 250K+ events/sec) will cause:
1. GC pauses from high allocation rate
2. Consumer starvation from slow listeners
3. Silent event drops from parser gaps

Required fixes before scaling to 1,000+ symbols:
- Dhan: shutdown hook for reconnect scheduler; OI parser fix
- Upstox: real-broker parser fixture validation; instrumentToken HashMap
- ICICI: resubscribe fix; depth/OHLC WebSocket implementation
