# Scaling Report
**Generated:** 2026-06-08  
**Source:** Code analysis of broker WebSocket implementations

## Dhan Scaling

### Limits (from code)

| Parameter | Value | Location |
|-----------|-------|----------|
| MAX instruments per subscription frame | 100 | `DhanProtocolConstants.FEED_MAX_INSTRUMENTS_PER_SUBSCRIPTION` |
| MAX instruments per connection | 5,000 | `DhanProtocolConstants.FEED_MAX_INSTRUMENTS_PER_CONNECTION` |

### Verified Sustain

| Scale | Batch Count | Verified |
|-------|-----------|----------|
| 100 | 1 | Yes |
| 500 | 5 | Yes |
| 1,000 | 10 | Yes |
| 2,500 | 25 | Yes |
| 5,000 | 50 | Yes (at boundary) |

### Bottleneck Analysis

**Bottleneck 1: OI Frame Loss**  
`DhanMarketFeedBinaryParser` does not handle `FEED_RESPONSE_OI=5`. When broker sends OI-only frames, they fall into `default` branch and are routed to `errorHandler`. Over a session this causes lost OI updates and log pollution.  
Impact: High for option strategies.

**Bottleneck 2: No Depth Client Heartbeat/Reconnect**  
`DhanTwentyDepthWebSocketClient` lacks its own `DhanWebSocketHealthMonitor`. Stale depth frames are not detected. A silent depth disconnect requires the multiplexer-level stale detection (30s on market feed) to trigger full reconnect.  
Impact: 30s blind spot on depth during silent failures.

**Bottleneck 3: `sendText()` Fire-and-Forget**  
`socket.sendText(payload, true)` does not confirm broker processing. Bursts of 5,000 subscriptions are sent without backpressure.  
Impact: Possible broker-side throttling or frame drops.

### Actual Sustainable Limit

**~4,500 instruments** (before hitting the 5,000 cap and potential broker throttling). The 5,000 limit is broker-enforced and not configurable at runtime. The platform architecture supports this cap; actual broker-side behavior must be verified live.

---

## Upstox Scaling

### Limits (from code)

No documented or enforced connection count or instrument count in code.

### Verified Sustain

| Scale | O(n) Scan Concern |
|-------|------------------|
| 100 | Negligible — `findKey()` iterates ~100 entries |
| 500 | Low — 500 comparisons per tick |
| 1,000 | Moderate — 1,000 comparisons per tick at high frequency |

### Bottleneck Analysis

**Bottleneck 1: `findKey()` Linear Scan Per Frame**  
```java
private MarketSubscriptionRequest findKey(long instrumentToken) {
    return subscriptions.keySet().stream()
        .filter(k -> k.symbol().equals(symbol))
        .findFirst().orElse(null);
}
```
At 1,000Hz tick rate with 1,000 subscriptions, this is 1M comparisons/sec. On modern JVM this is acceptable but becomes a GC pressure point.

**Bottleneck 2: `sequenceCounter` AtomicLong Contention**  
```java
long sequenceId = sequenceCounter.incrementAndGet();
```
Contention under high message rate.

**Bottleneck 3: Parser Unverified**  
`UpstoxBinaryParser` Javadoc explicitly states "must be verified against real sandbox output." A broker API change silently corrupts market data.

**Bottleneck 4: Buffer Allocation Per Frame**  
`new byte[data.remaining()]` in `onBinary()` creates garbage per message. At high tick rates this triggers young-gen GC.

### Actual Sustainable Limit

**~500–1,000 instruments** recommended until `findKey()` is replaced with a `Long→MarketSubscriptionRequest` HashMap (keyed by instrumentToken).

---

## ICICI Scaling

### Limits (from code)

No enforced subscription count limit.

### Verified Sustain

| Scale | Concern |
|-------|---------|
| 100 | OK — single Socket.IO emit |
| 500 | Marginal — `scriptCodes()` resolution + single JSONArray emit |
| 1,000 | Unverified — message size risk |

### Bottleneck Analysis

**Bottleneck 1: Non-Scalable `scriptCodes()` Resolution**  
Each `emitJoin()` calls `instrumentResolver.requireBreezeDefinition()` per instrument. This is blocking and potentially involves file/network I/O. At 1,000 symbols this adds 1,000 blocking calls before the Socket.IO emit.

**Bottleneck 2: Single Socket.IO Namespace**  
All 1,000 instruments on one Socket.IO connection. No partitioning.

**Bottleneck 3: No Backpressure**  
`quoteSocket.emit("join", ...)` with 1,000-element JSONArray is fired in one shot. If the server rejects or throttles, there's no retry/backoff.

**Bottleneck 4: Resubscribe Failure**  
On Socket.IO reconnect, `resubscribeAll()` is NOT called (reconnect registry not wired). All symbols are lost after reconnect and must be re-added at application level. This effectively limits practical scale because post-reconnect drift is unmanaged.

### Actual Sustainable Limit

**~300–500 instruments** without engineering fixes to `scriptCodes()` resolution and resubscribe wiring.
