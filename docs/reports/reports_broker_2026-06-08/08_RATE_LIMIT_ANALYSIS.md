# Rate Limit Analysis Report
**Generated:** 2026-06-08  
**Source:** Code analysis of rate limiters and broker constants

## Dhan Rate Limits

### REST Rate Limiting — IMPLEMENTED

| Category | Rate | Capacity | Enforcement |
|----------|------|----------|-------------|
| ORDER | 7/s | 10 tokens | `MultiBucketRateLimiter` via `DhanRetryExecutor` |
| DATA | 5/s | 5 tokens | Same |
| QUOTE | 0.5/s | 1 token | Same |
| OPTION_CHAIN | 0.34/s | 1 token | Same |
| NON_TRADING | 15/s | 20 tokens | Same |

### WebSocket Subscribe Rate — NOT PROTECTED
`DhanMarketFeedWebSocketClient.sendText()` has no throttle. A burst of 5,000 instrument subscriptions fires 50 JSON frames in rapid succession. This is not rate-limited.

### Reconnect Rate — PARTIALLY PROTECTED
- `DhanWebSocketMultiplexer.reconnectWithBackoff()` uses exponential backoff (1s base, 30s max)  
- Circuit breaker opens after 3 consecutive failures for 30s  
- No storm cooldown multiplier (single fixed 30s)

### Subscription Rate — NOT PROTECTED
The `subscribe()` method can be called 5,000 times in a loop, generating 50 frames instantly.

---

## Upstox Rate Limits

### REST Rate Limiting — IMPLEMENTED

| Category | Rate | Capacity |
|----------|------|----------|
| DATA | 5/s | 3 tokens |

### WebSocket Rate — NOT PROTECTED
- `UpstoxWebSocketMultiplexer` has no WS emit rate limiter  
- `feedAuthorizer.authorize()` called on each reconnect — could stack if multiple brokers use same token

### Reconnect Rate — PROTECTED
- `ReconnectManager`: max 8 attempts, exponential backoff 1s base / 60s max, jitter ±500ms  
- Storm cooldown: 60s * stormCount after all attempts exhausted  
- `manuallyDisconnected` flag prevents reconnect after intentional disconnect

---

## ICICI Rate Limits

### REST Rate Limiting — IMPLEMENTED

| Category | Rate | Capacity |
|----------|------|----------|
| DATA | 100/60s (~1.67/s) | 100 tokens |
| DAILY | 5000/day | 5000 tokens |
| ORDER | 10/s | 10 tokens |

### WebSocket Rate — NOT PROTECTED
- `quoteSocket.emit("join", ...)` no throttle
- `scriptCodes()` resolution is blocking per instrument — natural throttle but not intentional

### Reconnect Rate — NOT PROTECTED
- Socket.IO client has default reconnect with exponential backoff (library-managed)
- No application-level reconnect manager with configurable limits
- A socket.io reconnect storm (e.g., 10 rapid reconnect attempts) could exhaust broker-side connection limits

---

## Platform Reconnect Storm Protection

| Broker | Storm Protection | Mechanism |
|--------|-----------------|-----------|
| Dhan | PARTIAL | Circuit breaker (3 failures → 30s open). No storm count multiplication. |
| Upstox | PASS | ReconnectManager: exponential backoff + storm cooldown multiplier |
| ICICI | FAIL | Socket.IO defaults only — no application-level backoff config visible |

## Conclusions

1. **WebSocket control channel (subscribe/unsubscribe) is NOT rate-limited for any broker.** Platform risk: burst subscribe can hit broker-side throttling, causing temporary blackout.

2. **Dhan REST rate limiting is solid** but the QUOTE category at 0.5/s with capacity 1 means a single burst quote request blocks subsequent calls for ~2 seconds.

3. **ICICI has no explicit reconnect backoff** at the application level. Socket.IO library behavior needs to be verified against broker limits.

4. **Renewing WS sessions** (OI-specific frames, feed authorization) are not counted against rate limiters — they happen on the WebSocket transport which brokers control independently.
