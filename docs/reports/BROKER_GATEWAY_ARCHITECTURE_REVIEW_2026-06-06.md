# P0 Market Gateway / Broker Gateway Architecture Review
**Date:** 2026-06-06
**Scope:** Hidden coupling, untestable complexity, and architectural rot in the new `broker-gateway` and `gateway` modules.
**Author persona:** Principal quant engineer — opinionated, file:line specific, brutally honest.

---

## TL;DR (verdict)

The new `broker-gateway` and `gateway` modules were assembled to **look like** a clean abstraction over multi-broker trading, but the layer is **almost entirely pass-through** — it adds no failover, no load balancing, no circuit breaking, no per-broker rate limit, and no broker-agnostic error model. Worse, it **coexists with, and competes with, the older `LoadBalancedBrokerGateway`** in `broker/core/` that already has real failover. You now have **two parallel gateway abstractions** that do different things, and the codebase is migrating to the worse one.

The net effect: any incident that needs "switch brokers when Dhan is down" will land on the new `BrokerRouter`, fail to find a failover path, and the operator will have to manually flip `setActive()` — the exact failure mode `LoadBalancedBrokerGateway` was built to prevent. **Delete the new gateway layer or merge it back into the old one within the next sprint.** I have a recommendation below.

Five P0s and six P1s follow. These are ordered by blast radius.

---

## P0 — Fix or delete in this sprint

### P0-1 — Two parallel gateway abstractions, the new one is a downgrade

**Where:**
- **New:** `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerRouter.java:1-67`
- **Old:** `broker/core/src/main/java/com/tradej/broker/core/routing/LoadBalancedBrokerGateway.java:1-205`

These two classes do **almost the same job** but with different contracts:

| Capability | `LoadBalancedBrokerGateway` (old) | `BrokerRouter` (new) |
|---|---|---|
| Holds multiple connections | yes (`CopyOnWriteArrayList<IBrokerConnection>`) | yes (via `BrokerGateway.handles` map) |
| Failover on order error | yes (`FailoverOrderCommand`, `rotatePrimary()`) | **no** — single active pointer |
| Load balancing for market data | yes (`LoadBalancedMarketDataProvider`) | **no** — single active pointer |
| Per-port capability routing (e.g. options → broker with OptionsCapable) | yes (`options()` iterates all) | **no** |
| Hot swap of broker at runtime | yes (`addConnection`, `removeConnection`) | yes (`setActive`) — but manual |
| Production code paths I can see referenced from `app` | fewer and shrinking | growing — `MarketGateway.create(gateway)` is the new public surface |

The new module's Javadoc (`BrokerRouter.java:5-14`) literally admits it's a stub:

> Currently supports a single active broker. **Future versions will support:**
> - Failover — automatic switching when the active broker becomes unavailable
> - Load balancing — distribute requests across multiple brokers
> - Capability routing — route option chain requests to the broker with best options support

But the **old module already has all three**. The new one is a step backward.

**Required fix — pick one of two paths, do not split the difference:**

**Path A (recommended):** Delete the `broker-gateway/BrokerRouter` class entirely. Have `MarketGateway` take a `LoadBalancedBrokerGateway` directly. Keep `BrokerHandle` and `BrokerGateway` as a thin fluent API over `IBrokerConnection` (they add value: `GatewayResult<T>` with latency, capability checks via `requireCapability`, and a single place to add cross-cutting concerns like timeouts and circuit breakers).

**Path B:** Delete `LoadBalancedBrokerGateway` and the four `Failover*` classes in `broker/core/routing/`. Move the failover logic into `BrokerRouter` (or a new `FailoverBrokerRouter`).

Path A is faster and lower risk. Path B is more architecturally honest. Either is fine. **Doing nothing is the worst option** — the team is currently writing new code against `BrokerRouter` that will have to be re-written the day failover is actually needed.

### P0-2 — `MarketGateway` and `BrokerHandle` are pass-throughs that pretend to be abstractions

**Where:**
- `broker-gateway/src/main/java/com/tradej/brokergateway/MarketGateway.java:1-111`
- `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerHandle.java:1-405`

Every method on `MarketGateway` is a one-liner that calls `router.active().<method>`. Every method on `BrokerHandle` is a one-liner that calls `connection.<port>().<method>`. They add **no behavior** beyond:

- Wrapping the return value in `GatewayResult<T>` with latency
- Capability checks via `requireCapability`
- A raw-capture switch (`enableRawCapture`)
- A `PortfolioSummary` aggregation (`portfolioSummary`)

The latency wrapping is real, the capability checks are real, the raw-capture is real. But none of these require a new abstraction layer — they could be:

- A `GatewayResult.wrap(connection.marketData().getQuote(key))` static helper.
- An `assertSupports(connection, BracketOrderProvider.class, "bracket orders")` static helper.
- A `RawCapture` decorator that wraps `IBrokerConnection`.

The new classes are a **refactor smell** that will be very hard to undo: every consumer will have to update imports to migrate back.

**Required fix:** if Path A from P0-1 is taken, replace `MarketGateway` and `BrokerHandle` with a small set of static helpers in the `result` package:

```java
public final class GatewayOps {
    public static <T> GatewayResult<T> wrap(IBrokerConnection conn, Supplier<T> call) { ... }
    public static <T> T requireCapability(IBrokerConnection conn, Class<T> cap, String feature) { ... }
    public static <T> Optional<T> getCapability(IBrokerConnection conn, Class<T> cap) { ... }
}
```

If Path B is taken, keep the classes but **add a behavior** they currently lack: a circuit breaker, a per-broker timeout, a per-symbol rate limiter. Right now they add cost (one extra stack frame, one extra allocation per call) without adding value.

### P0-3 — `GatewayResult.isSuccess()` can never return false

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/result/GatewayResult.java:36-38`

```java
public boolean isSuccess() {
    return data != null;
}
```

And `BrokerHandle.timed()` at `BrokerHandle.java:385-392`:

```java
private <T> GatewayResult<T> timed(TimedCall<T> call) {
    Instant start = Instant.now();
    T data = call.call();   // <-- any exception escapes
    ...
    return new GatewayResult<>(data, source, metadata);
}
```

If the call throws, the exception propagates **out of `timed()`** and the caller never sees a `GatewayResult`. The `isSuccess()` predicate is therefore always `true` for any `GatewayResult` that exists. There is no way to model a failed market data request, a failed order, a failed cancel — all of which can and will fail at the broker.

The new `result` package looks like an algebraic data type (success/failure/partial) but is actually a non-nullable wrapper. The `failure` case lives in exceptions, not in values.

**Required fix:** make `GatewayResult` a real sum type:

```java
public sealed interface GatewayResult<T> {
    record Success<T>(T data, BrokerSource source, ResultMetadata metadata) implements GatewayResult<T> {}
    record Failure<T>(BrokerSource source, ResultMetadata metadata, BrokerError error) implements GatewayResult<T> {}
}
```

And rewrite `BrokerHandle.timed` to catch exceptions and produce `Failure`. This is a 2-3 day refactor across the `BrokerHandle` API. It is **the** change that justifies the new layer's existence; without it, the layer is cosmetic.

### P0-4 — `GatewayTopicRouter` is a single-thread publisher with no isolation between slow clients

**Where:** `gateway/src/main/java/com/tradej/gateway/router/GatewayTopicRouter.java:75-99, 158-204`

`publish()` enqueues a `SendTask` into a bounded `ArrayBlockingQueue`. A single `gateway-publisher` thread polls, batches, and dispatches to subscribed transports. The dispatch iterates transports **sequentially** in the publisher thread:

```java
private void dispatchToTransports(SendTask task) {
    Set<WebSocketTransport> transports = topicTransports.get(task.topic);
    ...
    for (WebSocketTransport transport : transports) {
        if (!transport.isOpen()) { continue; }
        ...
        try {
            transport.sendBinary(task.frame);   // <-- BLOCKING CALL in publisher thread
            sentEventCount.incrementAndGet();
        } catch (Exception e) { ... }
    }
}
```

And `SpringWebSocketTransport.sendBinary` (`gateway/src/main/java/com/tradej/gateway/transport/SpringWebSocketTransport.java:19-23`):

```java
public void sendBinary(byte[] data) throws Exception {
    synchronized (session) {                       // <-- serializes all topics to one session
        session.sendMessage(new BinaryMessage(data));
    }
}
```

**This means:**
1. One slow or stuck WebSocket client blocks the **entire gateway** — every topic, every other client. The publisher thread is single; it waits.
2. A 60-second socket write timeout on a stuck client = 60 seconds of dropped events across all subscribers.
3. The bounded queue (1024) fills up in milliseconds at high tick rates, and `droppedEventCount` climbs silently.
4. The `synchronized (session)` lock is a needless bottleneck on top: even if you make the publisher multi-threaded, the session lock serializes writes.

**Required fix (Path A — surgical):**
- Replace the `gateway-publisher` thread with a `ExecutorService` of N=2-4 threads.
- Per-transport `MPSC queue` or `MPSC ring buffer` (LMAX Disruptor) feeding a per-transport write thread.
- Remove `synchronized (session)` in `SpringWebSocketTransport` — Spring's `WebSocketSession.sendMessage` is already thread-safe (it uses a `ConcurrentWebSocketSessionDecorator` in Spring 5+). If not, wrap at the call site, not the transport.
- The `GatewayTopicRouter` itself is fine; the per-transport fan-out is the problem.

**Required fix (Path B — Reactive):** see `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` R-1. The topic router becomes a `Sinks.Many<GatewayMessage>` and each transport is a `Flux` consumer with its own backpressure. This is the architecturally honest solution; it costs more.

Either fix, but **stop calling this layer a "router"** until it actually has more than one outgoing path.

### P0-5 — `GatewayEventBridge` allocates a `LinkedHashMap` and a new `ObjectMapper` for every event

**Where:** `gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java:78-87, 237-417`

Two performance footguns in the hottest path of the gateway:

**Footgun A: per-event payload allocations.** Every `marketTickPayload`, `depthPayload`, `candlePayload`, `orderPayload`, `positionPayload`, `signalPayload`, `pnlPayload`, `optionChainPayload`, `greeksPayload`, `maxPainPayload`, `gammaPayload`, `scanPayload` builds a fresh `LinkedHashMap<String, Object>`. At 10,000 ticks/sec and ~5 fields per tick, that's 50,000 map inserts per second. Each map is short-lived → GC pressure → GC pauses → order latency spikes. Use a `Map.of(...)` for ≤10 entries, or a `JsonNode` builder, or — better — a flat `byte[]` codec.

**Footgun B: per-`BrokerHandle` `ObjectMapper`.** `BrokerHandle.java:75, 82`:
```java
private final ObjectMapper objectMapper;
...
public BrokerHandle(BrokerSource source, IBrokerConnection connection) {
    ...
    this.objectMapper = new ObjectMapper();
}
```

Jackson `ObjectMapper` is **expensive to construct** (it builds type factories, serializer caches, deserializer caches, etc. — typically 50-200ms the first time, 5-20ms after warmup). One `BrokerHandle` per broker, but if `BrokerHandle` is allocated per-request (which a future refactor might do), this becomes a per-request cost. Share the `ObjectMapper` from the Spring context (`@Autowired ObjectMapper`).

`GatewayEventBridge` does take an `ObjectMapper` from outside — but `BrokerHandle` does not. Inconsistent.

**Required fix:**
- Either (a) use a code-generated serializer per event type via Jackson's `SimpleModule` and `JsonSerializer<T>` (no Map, no reflection), or (b) move to a binary protocol (Protobuf, SBE) and skip JSON entirely. Option (b) is the right answer for a hot path.
- Pass the `ObjectMapper` into `BrokerHandle` via constructor. Do not allocate it in the constructor.

---

## P1 — Fix in the next two sprints

### P1-1 — `DefaultBrokerGateway.fromRegistry` silently drops profiles with no registered provider

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/DefaultBrokerGateway.java:48-58`

```java
static BrokerGateway fromRegistry(BrokerRegistry registry, BrokerProfile... profiles) {
    Map<BrokerSource, BrokerHandle> handles = new LinkedHashMap<>();
    for (BrokerProfile profile : profiles) {
        BrokerSource source = toSource(profile.brokerType());
        registry.provider(source).ifPresent(provider -> {       // <-- silent drop
            IBrokerConnection conn = provider.connect(profile);
            handles.put(source, new BrokerHandle(source, conn));
        });
    }
    return new DefaultBrokerGateway(handles);
}
```

If the user configures an `UpstoxConfig` but no `UpstoxBrokerProvider` is on the classpath (e.g. the user upgraded and a transitive dep was removed), the profile is silently discarded. The user sees `availableBrokers()` returning one broker and no warning.

**Required fix:** collect dropped profiles and either:
- Throw `IllegalStateException("Profiles configured but no providers registered: " + dropped)`, or
- Log a WARN with the list and continue (the operationally friendly option).

### P1-2 — `LoadBalancedBrokerGateway` uses three different failover strategies in one class

**Where:** `broker/core/src/main/java/com/tradej/broker/core/routing/LoadBalancedBrokerGateway.java:77-200`

- `marketData()` → `LoadBalancedMarketDataProvider` (round-robin or first-healthy, depends on impl)
- `orders()` → `FailoverOrderCommand` (tries next on failure)
- `websocket()` → `FailoverWebSocketMultiplexer` (reconnect-aware)
- `options()` → walks all connections, picks first with capability
- `futures()`, `portfolio()`, `margin()`, `sessionRisk()`, `alerts()`, `news()`, `instruments()`, `orderQuery()`, `sliceOrders()`, `bracketOrders()`, `gttOrders()` → straight to `primary()`

So 12 of 16 ports are **single-broker** (no failover). If Dhan is down, market data can still come from Upstox, orders can still go via failover, but your balance, your positions, your margin — all from Dhan. The user has no signal that the system is degraded.

**Required fix:** either
- Make all 16 ports use the same failover primitive, or
- Document explicitly that the single-broker ports are intentional and that a broker outage means the system is degraded.

I'd take the second option. The first is a 3-week refactor.

### P1-3 — `ServiceLoaderBrokerRegistry` does not work correctly in fat-jar / module-path deployments

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/spi/ServiceLoaderBrokerRegistry.java:24-31`

```java
public ServiceLoaderBrokerRegistry() {
    this.delegate = new DefaultBrokerRegistry();
    ServiceLoader.load(BrokerProvider.class).forEach(provider -> { ... });
}
```

`ServiceLoader.load(Class)` uses the **caller's classloader** by default. In a Spring Boot fat jar (the production deployment), the classloader chain is:
- `LaunchedURLClassLoader` (the Spring Boot loader)
- `AppClassLoader`
- `PlatformClassLoader`

`ServiceLoader.load(BrokerProvider.class)` from `ServiceLoaderBrokerRegistry` (which is on the app classloader) will only see providers in `META-INF/services` files that are **on the same classloader**. Some `META-INF/services` files in nested jars are not enumerated.

**Required fix:** explicitly thread the classloader:

```java
public ServiceLoaderBrokerRegistry() {
    this(Thread.currentThread().getContextClassLoader());
}

public ServiceLoaderBrokerRegistry(ClassLoader cl) {
    this.delegate = new DefaultBrokerRegistry();
    ServiceLoader.load(BrokerProvider.class, cl).forEach(...);
}
```

This must be tested in a fat-jar build, not in IDE / classpath builds.

### P1-4 — `SpringWebSocketTransport.sendBinary` synchronizes on the session — wrong lock

**Where:** `gateway/src/main/java/com/tradej/gateway/transport/SpringWebSocketTransport.java:19-23`

```java
public void sendBinary(byte[] data) throws Exception {
    synchronized (session) {
        session.sendMessage(new BinaryMessage(data));
    }
}
```

`synchronized (session)` locks on the `WebSocketSession` instance. Spring's own `WebSocketSession` is **not** designed to be an external lock — it has its own internal lock for outbound writes (`StompSubProtocolHandler` etc. acquire internal locks; interleaving external and internal locks risks deadlock).

Spring Boot's `WebSocketMessagingTemplate` (or its underlying `WebSocketStompClient`) uses a per-session `Lock` via `ConcurrentWebSocketSessionDecorator`. The decorator is opt-in; if you don't apply it, the session is **not thread-safe** for concurrent writes.

**Required fix:** remove the `synchronized (session)`. Apply `ConcurrentWebSocketSessionDecorator` at session creation time (`WebSocketHandlerDecoratorFactory`), or move concurrency control up to the `GatewayTopicRouter` (P0-4 fix).

### P1-5 — `GatewayEventBridge.register` does not unregister on close → memory leak

**Where:** `gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java:95-113, 173-184`

`register(EventBus eventBus)` subscribes the bridge to 17 event types:

```java
public void register(EventBus eventBus) {
    eventBus.subscribe(MarketTickEvent.class, this::onDomainEvent);
    eventBus.subscribe(DepthUpdateEvent.class, this::onDomainEvent);
    ...  // 17 subscriptions
}
```

`close()` (`:174-184`) only stops the dedup pruner:

```java
public void close() {
    dedupPruner.shutdown();
    ...
}
```

The bridge never calls `eventBus.unsubscribe(...)`. After `close()`, the bus still has a method reference to the bridge. If the bus is long-lived and the bridge is short-lived (per-session, per-test), this is a memory leak. In a test that creates and closes bridges 1000 times, the bus will hold 17,000 dead method references.

**Required fix:** have `close()` call `eventBus.unsubscribe` for all 17 types. This requires the `EventBus` API to have an `unsubscribe` method — if it doesn't, add it.

### P1-6 — `BrokerRouter.setActive` does not check the source is in the gateway — `active()` can throw

**Where:** `broker-gateway/src/main/java/com/tradej/brokergateway/BrokerRouter.java:47-52, 33-35`

```java
public void setActive(BrokerSource source) {
    if (!gateway.availableBrokers().contains(source)) { throw ... }
    this.activeSource = source;
}

public BrokerHandle active() {
    return gateway.broker(activeSource);   // <-- can throw if source was removed concurrently
}
```

Between `setActive` and `active()`, another thread could call `DefaultBrokerGateway`'s constructor with a new map (impossible — `handles` is `Map.copyOf` and the gateway is immutable). But if the architecture ever changes to allow hot-add/remove of brokers, this becomes a latent race.

**Required fix:** make `BrokerRouter` resilient. Either:
- Cache the `BrokerHandle` reference at `setActive` time (stale-but-safe), or
- Make `active()` return `Optional<BrokerHandle>` and propagate up.

---

## P2 — Fix in the next quarter

### P2-1 — `market-utils` is an empty, non-existent module

I checked: `market-utils` does not exist as a directory and is not in `settings.gradle` or any `build.gradle`. It is referenced in the original architecture docs as the home for canonical symbol resolution, exchange segment enum, and instrument ID helpers. Those concerns are scattered across `core/domain/value/` and `broker-gateway/certification/`. Consolidate or remove from the docs.

### P2-2 — `BrokerGateway.dhan(config)` is a Dhan-only factory on a multi-broker interface

`broker-gateway/src/main/java/com/tradej/brokergateway/BrokerGateway.java:74-76`:

```java
static BrokerGateway dhan(BrokerProfile.DhanConfig config) {
    return DefaultBrokerGateway.dhan(config);
}
```

This is a backdoor — it lets a caller construct a Dhan-only gateway without going through the registry. The whole point of the SPI is to be broker-agnostic. Delete this method; callers should use `fromRegistry(registry, profile)`.

### P2-3 — `BrokerHandle.portfolioSummary` does three separate broker calls under one timer

`broker-gateway/src/main/java/com/tradej/brokergateway/BrokerHandle.java:280-287`:

```java
public GatewayResult<PortfolioSummary> portfolioSummary() {
    return timed(() -> {
        Balance bal = connection.portfolio().getBalance();
        List<Position> pos = connection.portfolio().getPositions();
        List<Holding> hold = connection.portfolio().getHoldings();
        return new PortfolioSummary(bal, pos, hold);
    });
}
```

This is three serial REST calls. At 200-500ms per call, this is 600-1500ms latency. Either:
- Make it `CompletableFuture.supplyAsync` for the three calls and join, or
- Use a per-broker composite endpoint (most brokers have a `portfolio/composite`).

### P2-4 — `GatewayEventBridge` dedup is a second copy of the same P0 defect from the disruptor bus

`gateway/src/main/java/com/tradej/gateway/bridge/GatewayEventBridge.java:210-224` is the **same** `eventId`-only dedup as `DisruptorEventBus.java:368-388`. The fix in one place should be the fix in the other. Extract a `DedupCache` interface in `core` and use it in both places.

### P2-5 — `BrokerHandle.requireCapability` throws `UnsupportedOperationException` — should be a `GatewayResult.Failure`

Once P0-3 is done, `requireCapability` should produce a `GatewayResult.Failure` with `BrokerError.UNSUPPORTED_CAPABILITY`, not throw. Throwing breaks the value-typed result model.

### P2-6 — `DefaultBrokerGateway` constructor uses `Map.copyOf` — not a copy-on-write, just a copy

`broker-gateway/src/main/java/com/tradej/brokergateway/DefaultBrokerGateway.java:22-26`:
```java
private final Map<BrokerSource, BrokerHandle> handles;
DefaultBrokerGateway(Map<BrokerSource, BrokerHandle> handles) {
    this.handles = Map.copyOf(handles);
}
```

`Map.copyOf` creates an **immutable** map. Iteration order is **not** guaranteed (it's hash-based). `first()` and `availableBrokers()` will return brokers in arbitrary order. If the user passes a `LinkedHashMap` with `[dhan, upstox, icici]`, `first()` might return `upstox`.

**Required fix:** use `Collections.unmodifiableMap(new LinkedHashMap<>(handles))` to preserve insertion order.

### P2-7 — `LoadBalancedBrokerGateway.removeConnection` does not reset `primaryIndex`

`broker/core/src/main/java/com/tradej/broker/core/routing/LoadBalancedBrokerGateway.java:61-63, 73-75`:

```java
public void removeConnection(IBrokerConnection connection) {
    connections.remove(connection);
}

private IBrokerConnection primary() {
    return connections.get(Math.floorMod(primaryIndex.get(), connections.size()));
}
```

If you start with 3 connections, rotate 5 times (primaryIndex = 5), then remove the connection at index 1, primaryIndex is still 5, and 5 % 2 = 1 → correct. If you remove the connection at index 0, the math stays correct because of `floorMod`. So this is **fine**, but only by accident. A future change that uses `primaryIndex` for anything other than `floorMod` will break. **Add a comment** explaining the invariant, or reset on remove.

### P2-8 — No contract test for the `BrokerProvider` SPI

If a third party writes a `BrokerProvider`, they have to read the source to know what `connect`, `descriptor`, `isEnabled`, `source`, and `displayName` should do. The interface has no Javadoc on the contract (e.g. "connect must be idempotent" or "descriptor must reflect live state"). Add a `BrokerProviderContractTest` abstract class that any provider can extend to prove compliance.

### P2-9 — `BrokerGateway.broker(String)` and `BrokerSource.parse` are case-insensitive but the enum is not

`BrokerSource.parse(name)` does `valueOf(name.trim().toUpperCase())` (BrokerSource.java:12-17). The enum constants are already uppercase. The `toUpperCase()` is dead code unless someone passes a lowercase string. If they do, the parse succeeds. If they pass `"dHan"`, the parse succeeds. If they pass `"DHAN "` (trailing space), the `trim()` saves them. If they pass `" dhan"` (leading space), the `trim()` saves them. Good. But the inconsistency with the enum's case-sensitivity is confusing. Document or remove `toUpperCase`.

---

## Architectural recommendation

**The `broker-gateway` layer is a half-built facade. You have three choices:**

1. **Adopt** the new layer as the single gateway surface. **Add the missing behaviors**: real failover (port from `LoadBalancedBrokerGateway`), real `GatewayResult.Failure` (P0-3), real backpressure isolation (P0-4), real per-broker timeouts and circuit breakers. **Cost: 4-6 weeks. Risk: medium.**
2. **Delete** the new layer. Keep `LoadBalancedBrokerGateway` as the gateway. Re-export the `GatewayResult`, `requireCapability`, and `RawCapture` as static helpers. **Cost: 1 week. Risk: low.**
3. **Do nothing** and let the layer rot. **Cost: zero now. Risk: high — the day you need failover, you'll find out it doesn't exist.**

I'd take **option 2** today, plan **option 1** for the next quarter. The codebase is small enough that the cleanup pays for itself in a single incident.

---

## Closing thought

The new module was clearly written with intent: a clean broker-agnostic surface, an SPI for extensibility, a result type that carries latency. The intent is good. The implementation is missing the load-bearing parts: failover, real error model, real backpressure. The layer is **a name without a body**. The next time someone says "we have failover because we have BrokerRouter", push back. We don't. We have a pointer.

See also:
- `docs/reports/ARCHITECTURE_REVIEW_2026-06-06.md` — P0 defects in the runtime
- `docs/reports/REACTIVE_ADOPTION_REVIEW_2026-06-06.md` — R-1 (GatewayEventBridge) is the right target for the next refactor
- `docs/reports/TEST_COVERAGE_CHAOS_REVIEW_2026-06-06.md` — the new module has 5 trivial unit tests and zero chaos tests
