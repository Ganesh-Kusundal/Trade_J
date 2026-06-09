# Subscription Management Report
**Generated:** 2026-06-08  
**Source:** Code analysis of broker WebSocket multiplexers

## Dhan Subscription Management

### Architecture
- `DhanWebSocketSubscriptionManager` holds canonical `ConcurrentHashMap<MarketSubscriptionRequest, FeedMode>`  
- `DhanMarketFeedWebSocketClient` holds its own `ConcurrentHashMap<SubscriptionKey, FeedMode>` + `pendingSubscriptions`  
- Depth subscriptions tracked separately in `DhanTwentyDepthWebSocketClient.subscriptions (ConcurrentHashMap)`

### Deduplication
**PASS** — `DhanMarketFeedWebSocketClient.subscribe()` filters:
```java
List<SubscriptionKey> newKeys = instruments.stream()
    .filter(key -> !subscriptions.containsKey(key))
    .toList();
```
Duplicate keys are not re-sent.

### Stale Subscription Prevention
**PARTIAL** — On `disconnect()`, both `subscriptions.clear()` and `pendingSubscriptions.clear()` are called. Resubscription relies on `subscriptionManager` which is preserved across disconnect/reconnect in the multiplexer. Correct path.

### Memory Leak Prevention
**PASS** — `subscriptions.clear()` on disconnect. Listeners use `CopyOnWriteArrayList` (no leak on removal).

### Bulk Subscribe
**PASS** — Splits into 100-instrument batches via `sendBatched()`

### Bulk Unsubscribe
**PASS** — Groups by mode before unsubscribing

### Resubscribe on Connect
**PASS** — `pendingSubscriptions` flushed on `notifyConnected()` via `scheduleResubscribe()`

---

## Upstox Subscription Management

### Architecture
- Single `ConcurrentHashMap<MarketSubscriptionRequest, FeedMode>` in multiplexer  
- No separate state from WebSocket client

### Deduplication
**PARTIAL** — Map semantics prevent duplicate keys, but `subscribe()` does not check `subscriptions.containsKey()` before putting (Map replaces existing value silently). This is safe for dedup but the lack of explicit check means no early exit.

### Stale Subscription Prevention
**PASS** — `unsubscribe()` removes from map. `disconnect()` does not clear subscriptions — they survive disconnect and are used by `resubscribeAll()`.  
**GAP:** `disconnect()` calls `reconnectManager.reset()` but does NOT clear subscriptions, which is correct for reconnect but means a manual disconnect + reconnect cycle re-subscribes all — could be unexpected.

### Memory Leak Prevention
**PARTIAL** — No explicit clear on disconnect. Relying on `resubscribeAll()` semantics. If the application lives for months without disconnect, the map grows with all unique subscription keys ever added (unsubscribe removes, so bounded).

### Bulk Subscribe
**PARTIAL** — No batching in broker message; all local. Acceptable since no broker command is sent for market data subscriptions.

### Bulk Unsubscribe
**PARTIAL**

### Resubscribe on Connect
**PASS** — `resubscribeAll()` iterates `subscriptions.entrySet()` and calls `subscribe()` per entry. Restores full state after reconnect.

---

## ICICI Subscription Management

### Architecture
- `ConcurrentHashMap<MarketSubscriptionRequest, FeedMode>`  
- Socket.IO `"join"`/`"leave"` emits

### Deduplication
**PARTIAL** — Same as Upstox — map dedup only. No `containsKey` guard.

### Stale Subscription Prevention
**PASS** — `disconnect()` clears `subscriptions`

### Memory Leak Prevention
**PASS**

### Bulk Subscribe
**PARTIAL** — Single `emitJoin()` call with all instruments in one JSONArray. No batching.

### Bulk Unsubscribe  
**PARTIAL** — Single `emit("leave", JSONArray)` call

### Resubscribe on Connect
**FAIL** — First connect works via `firstConnect.compareAndSet(true, false)` → `resubscribeAll()`. Reconnect (Socket.IO reconnect after first) calls `reconnectRegistry.notifyReconnect()` but no listener in codebase is wired to `BreezeWebSocketMultiplexer` to re-emit join events.

---

## Memory Leak Analysis

| Component | Dhan | Upstox | ICICI |
|-----------|------|--------|-------|
| Subscription map cleared on disconnect | Yes | No | Yes |
| Pending subscriptions bounded | Yes (cleared on disconnect) | No pending queue | N/A |
| Listener list grows unbounded | No (CopyOnWriteArrayList) | No (CopyOnWriteArrayList) | No (CopyOnWriteArrayList) |
| WebSocket reference held after close | No (set to null) | No (set to null) | Potentially — `quoteSocket`/`orderSocket` set to null on disconnect, but Socket.IO internal state may persist |
| reconnectScheduler shutdown | No shutdown hook for `dhan-reconnect` thread | `healthExecutor.shutdown()` on disconnect | `wsExecutor.shutdown()` on disconnect |

**Dhan gap:** `reconnectScheduler` is a `newSingleThreadScheduledExecutor` created in constructor but never shutdown in `disconnect()`. Thread leak on repeated connect/disconnect.

## Duplicate Prevention

| Broker | Market Tick Dedup | Order Dedup |
|--------|------------------|-------------|
| Dhan | No market tick dedup (relies on broker) | Yes — `latestOrderStatuses` map |
| Upstox | No market tick dedup | Yes — `latestOrderStatuses` map |
| ICICI | No market tick dedup | Yes — `latestOrderStatuses` map |

No broker implements market tick deduplication at the WebSocket client level. Duplicate ticks from broker propagate to event bus.
