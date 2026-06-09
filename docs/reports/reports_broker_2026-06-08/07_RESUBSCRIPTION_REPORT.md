# Resubscription Report
**Generated:** 2026-06-08  
**Source:** Code analysis of broker WebSocket reconnect logic

## Dhan Resubscription

### Mechanism
- `DhanWebSocketMultiplexer.onConnected()` calls `scheduleResubscribe()`  
- `scheduleResubscribe()` → `resubscribeAll()` via `reconnectScheduler` at zero delay  
- `resubscribeAll()` groups subscriptions by FeedMode and re-subscribes per mode

### Internet Disconnect → Resubscribe
**PASS** — `onClose()` fires → multiplexer `scheduleReconnect()` via `DhanWebSocketHealthMonitor` on stale detection. After reconnect, `onConnected()` → `scheduleResubscribe()` restores all.

### Broker Disconnect / Socket Disconnect
**PASS** — Same path via `onDisconnected()` → health monitor → reconnect

### Session Expiry (Token)
**PASS** — `DhanClientHolder.addRotationListener()` triggers `rebindAfterTokenRotation()` which:
1. Closes both clients
2. Rebuilds clients with new token
3. Reconnects market feed + order stream
4. Health monitor triggers `resubscribeAll()` after `onConnected`

### Application Restart
**PARTIAL** — On fresh `connect()`, subscriptions in `subscriptionManager` are replayed:
- Pending subscriptions flushed on `notifyConnected()`
- `connectDepthClientIfNeeded()` re-establishes depth

### Authentication Refresh
**PASS** — Token rotation path handles this without application restart

### State Restoration
**PASS** — `subscriptionManager` survives in multiplexer across reconnects

### Duplicate Prevention
**PASS** — `DhanMarketFeedWebSocketClient.subscribe()` dedups via `subscriptions.containsKey(key)` check

### Subscription Drift
**PARTIAL** — `DhanMarketFeedWebSocketClient.subscribe()` sends broker command only for `newKeys` (not already in map). If broker silently drops a subscription, `subscriptions` map retains the key indefinitely. No periodic reconciliation.

---

## Upstox Resubscription

### Mechanism
- `UpstoxWebSocketMultiplexer.resubscribeAll()` iterates `subscriptions.entrySet()`  
- `scheduleReconnect()` → `reconnectWithBackoff()` → `connectInternal()` + `resubscribeAll()` + `reconnectRegistry.notifyReconnect()`

### Internet Disconnect → Resubscribe
**PASS** — `onClose()` or `checkHealth()` (stale) → `scheduleReconnect()` → backoff → reconnect → resubscribeAll

### Broker Disconnect / Socket Disconnect
**PASS** — Same path

### Session Expiry
**PARTIAL** — `UpstoxTokenManager` auto-refreshes via `DefaultTokenLifecycleService`. Refresh happens on `feedAuthorizer.authorize()` call. The new WebSocket authorization (`/feed/market-data-feed/authorize`) obtains a fresh WS URI automatically.

### Application Restart
**N/A** — State lost; requires fresh subscription from application layer

### Authentication Refresh
**PASS** — Transparent to WebSocket; token refresh triggers new authorize call on next reconnect

### State Restoration
**PARTIAL** — `subscriptions` map survives in multiplexer. `resubscribeAll()` replays entries. However, no broker-side subscribe command is sent — relies on implicit push model. If the broker requires explicit subscribe per ticker after reconnect, the current code would restore empty subscriptions from broker's perspective.

### Duplicate Prevention
**PASS** — Map semantics

### Subscription Drift
**PARTIAL** — No reconciliation with broker state. If broker silently drops a subscription mid-session (e.g., due to internal throttling), the `subscriptions` map incorrectly retains it.

---

## ICICI Resubscription

### Mechanism
- First connect: `socket.on(EVENT_CONNECT)` with `firstConnect.compareAndSet(true, false)` → `resubscribeAll()`  
- Subsequent connects: `reconnectRegistry.notifyReconnect()` submitted to `wsExecutor`

### Internet Disconnect → Resubscribe
**PARTIAL** — Socket.IO auto-reconnects. On reconnect (not first), `reconnectRegistry.notifyReconnect()` is called. **No `emitJoin()` is called.** This means:
- After any Socket.IO reconnect (which happens on network hiccup), all subscriptions are **silently lost** from broker's perspective
- Broker stops sending updates, but local `subscriptions` map still shows them as active
- Application must manually re-subscribe

### Broker Disconnect
**PARTIAL** — Same as above

### Session Expiry
**PARTIAL** — No automatic session refresh in WebSocket path. `BreezeTokenProvider.ensureValid()` is called in `connect()` only. If session expires mid-session, Socket.IO connection degrades silently.

### Application Restart
**N/A** — Full reconnect required

### State Restoration
**PARTIAL** — Local state preserved in `subscriptions`, but not re-emitted to broker

### Duplicate Prevention
**PASS** — Map semantics

### Subscription Drift
**FAIL** — After any reconnect, subscriptions in local map do not match broker state. Every reconnect requires manual application-level `subscribe()` call.

---

## Summary

| Scenario | Dhan | Upstox | ICICI |
|----------|------|--------|-------|
| Internet disconnect recovery | PASS | PASS | FAIL |
| Broker-initiated disconnect | PASS | PASS | FAIL |
| Token rotation | PASS | PARTIAL | FAIL |
| App restart | PARTIAL | N/A | FAIL |
| No duplicate sub on resubscribe | PASS | PASS | PASS |
| No subscription drift | PARTIAL | PARTIAL | FAIL |

**Critical Finding:** ICICI resubscription is BROKEN for any reconnect scenario. After the first connection, any Socket.IO reconnect causes total subscription loss.
