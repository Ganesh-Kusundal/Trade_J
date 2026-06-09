# Multi-Broker Isolation Report
**Generated:** 2026-06-08  
**Source:** Code analysis of broker multiplexer architecture and shared infrastructure

## Architecture Isolation Assessment

### Thread Isolation

| Component | Dhan Threads | Upstox Threads | ICICI Threads |
|-----------|-------------|----------------|---------------|
| WebSocket callbacks | JDK WebSocket thread pool | JDK WebSocket thread pool | Socket.IO engine.io pool |
| Reconnect | `dhan-reconnect` daemon | `upstox-feed-health` daemon + ForkJoinPool for order WS retry | `breeze-ws-reconnect` daemon |
| Health monitor | `dhan-feed-health` daemon | `upstox-feed-health` daemon | None |
| Listeners | WebSocket callback thread | WebSocket callback thread | Socket.IO callback thread |

**Isolation: MODERATE** — Each broker has dedicated threads. However, JDK WebSocket uses a shared internal thread pool per `HttpClient` instance. If all three brokers are instantiated in the same JVM with default `HttpClient.newHttpClient()`, they share the same virtual thread pool (Java 21+) or common fork-join pool (older JVMs).

### Memory Isolation
- Each broker's `WebSocketMultiplexer`, `SubscriptionManager`, and client classes are independent Java objects  
- **Shared risk:** `ReconnectListenerRegistry` is a shared singleton (if injected via Spring/DI). A broker reconnect can trigger resubscription on OTHER brokers through shared registry listeners.  
- `EventMetadataFactory` and `LiveTradingClock` may be shared singletons — no isolation concern if read-only.

### Subscription Isolation
**PASS** — Each broker maintains its own `subscriptions` ConcurrentHashMap. Keys include `ExchangeSegment` + `symbol`. No cross-contamination.

### Feed Isolation
**PASS** — Each broker has separate WebSocket URL and connection. `MarketTickEvent` includes broker-identifying metadata.

### Failure Isolation

| Failure Mode | Dhan | Upstox | ICICI | Cross-Impact |
|-------------|------|--------|-------|--------------|
| Network disconnect | Isolated reconnect | Isolated reconnect | Socket.IO auto-reconnect | None — each broker's reconnect is independent |
| Parser crash | Dhan: errorHandler logs, continues | Upstox: catch + skip | ICICI: catch + debug log | None — no shared parser state |
| Token expiry | Isolated token rotation | Isolated refresh | Isolated session | None — tokens are broker-scoped |
| Listener slow | Blocks Dhan feed only | Blocks Upstox feed only | Blocks ICICI feed only | CRITICAL — JDK HttpClient thread pool shared |
| OOM in one broker | JVM-wide OOM | JVM-wide OOM | JVM-wide OOM | JVM-wide — no process isolation |

## Critical Cross-Broker Risk

### JDK HttpClient Thread Pool Contention
```java
// DhanMarketFeedWebSocketClient:
private final HttpClient httpClient = HttpClient.newHttpClient();

// UpstoxWebSocketMultiplexer:
this.httpClient = HttpClient.newHttpClient();
```
Both use `HttpClient.newHttpClient()` which returns a shared cached instance. JDK 21 `HttpClient` uses virtual threads by default; a single slow WebSocket response blocks a virtual thread but doesn't block carrier threads. In Java 17 or earlier, the default executor is the common ForkJoinPool, which is shared across brokers.

### Recommendation
Each broker should use its own `HttpClient` instance with a dedicated executor:
```java
HttpClient.newHttpBuilder()
    .executor(Executors.newSingleThreadExecutor(r -> new Thread(r, "dhan-http")))
    .build();
```

### Multi-Broker Simultaneous Operation

| Combination | Isolation | Risk |
|-------------|-----------|------|
| Dhan + Upstox | Moderate | Shared HttpClient thread pool (if pre-Java 21) |
| Dhan + ICICI | Moderate | Socket.IO uses its own threads; Dhan uses JDK threads |
| Upstox + ICICI | Good | Separate threading models |
| All three | Moderate | Combined thread pool contention risk |

## Verdict: PARTIAL

- Subscription and feed isolation are correct  
- Thread isolation is MODERATE — JDK HttpClient sharing is the main risk  
- No process-level isolation — a JVM crash from one broker takes all  
- Recommended fix: use per-broker `HttpClient` instances with named executors
