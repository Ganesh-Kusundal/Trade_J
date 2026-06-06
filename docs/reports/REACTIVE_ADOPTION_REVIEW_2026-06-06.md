# Reactive Adoption Review — Should Trade-J Go Reactive?

> **Date:** 2026-06-06
> **Scope:** evaluate cost / benefit of expanding Project Reactor usage across Trade-J
> **Verdict:** **adopt surgically, not wholesale.** Reactor is already on the classpath and partially wired (cold path). Adopt it for **gateway fan-out, broker REST, and order-placement timeouts**. Do **not** replace the LMAX Disruptor hot path, the `ExecutionHandler` queue, or the broker WebSocket adapters. Three concrete rewrites are worth doing; everything else is theatre.

---

## 1. Current state of reactive in Trade-J

This is not a green-field decision. Reactor is already adopted in three places — and the *shape* of that adoption tells us what the team actually needs.

| Location | What it does | Pattern |
|---|---|---|
| `core/build.gradle:api 'io.projectreactor:reactor-core'` | Transitive everywhere | API dep |
| `core/.../pipeline/reactor/ReactorBridge.java` (104 lines) | Merges per-node `Flux<DomainEvent>` from `ReactivePipelineNode` sources, publishOn `boundedElastic` | Cold-path fan-in |
| `core/.../pipeline/runtime/ReactivePipelineNode.java` (45 lines) | Optional mixin: `outputEvents(): Flux<DomainEvent>` + `Sinks.Many` helper | Node-level opt-in |
| `app/.../pipeline/reactor/ReactorColdPathRegistry.java` (63 lines) | Subscribes to `ReactorBridge.events()`, fans out to `ReactorColdPathConsumer`s | Cold-path fan-out |
| `app/.../pipeline/PipelineConfiguration.java:107` | "Offloads cold-path events to reactive Flux subscribers" | Doc + intent |

That's the entirety of the reactive surface. It is **good code**, well-scoped, and explicitly Spring-free (`SpringFreeArchitectureTest` whitelists `com.tradej.pipeline.reactor`). It does exactly one thing: take events that the hot path has already decided to keep, and give them to I/O-bound subscribers (DuckDB writes, Chronicle append, analytics queries) without re-entering the Disruptor consumer thread.

Everything else — `MarketDataPipeline`, `DisruptorEventBus`, `ExecutionHandler`, `PositionRiskHandler`, every broker WebSocket adapter — is **plain JDK concurrent code** with `Consumer<DomainEvent>` and `CompletableFuture`. So the question isn't "should we go reactive". It's "**where do we extend what we have, and where do we stop**".

---

## 2. The four patterns reactive is good for, and where each fits

There is no such thing as "a reactive architecture". There are four patterns that Reactor (or any reactive-streams library) does measurably better than hand-rolled JDK concurrent code:

| Pattern | What it solves | Where it earns its keep in Trade-J |
|---|---|---|
| **A. Bounded async fan-out with per-subscriber backpressure** | One source → N slow consumers, each with their own buffer; no consumer can stall the others | `GatewayEventBridge` (browser WebSocket fan-out) |
| **B. Race-with-deadline** | "Give me the first of {network call, timeout}" with proper cancellation | `ExecutionHandler.placeOrderWithTimeout` (10s broker call) |
| **C. Periodic polling with cancellation** | Token refresh, market-depth snapshots, expiry calendars | `DefaultTokenLifecycleService`, expired-option refresh, session-risk polling |
| **D. Stream-shaped aggregation** | Group-by-key, windowed reduction, per-key state | Per-symbol indicator pipelines, the institutional scanner |

There is a fifth pattern that **reactive is famous for but is wrong for trading systems**:

| Anti-pattern | Why it's wrong here |
|---|---|
| **E. Reactive on the tick-to-trade hot path** | Disruptor's lock-free single-writer ring is ~50ns p50. Reactor on `Schedulers.parallel()` is ~1–10µs p50 with much worse p99. A quant strategy that needs tick→order in < 1ms cannot pay that. |

Pattern E is the one that every "you should be reactive" consultant will recommend. **Don't do it.** I'll come back to why in §4.

---

## 3. Concrete adoption plan

Three rewrites, all surgical, all bounded, all testable. Plus one explicit "no" list.

### 3.1 (Adopt) Replace `GatewayEventBridge` with a `Flux<DomainEvent>` pipeline

**File:** `gateway/.../bridge/GatewayEventBridge.java` (455 lines), `GatewayTopicRouter.java`

**Current state.** `GatewayEventBridge` subscribes to `EventBus` (a single `Consumer<DomainEvent>`), maintains its own 200k-entry dedup cache, and synchronously calls `router.dispatch(event, transport)` for every registered WebSocket transport. With 20 browser tabs each subscribed to 9 topics, the broadcast loop serialises across transports. A slow client (mobile network) backpressures the dispatcher thread and stalls everyone else. The hand-rolled `dedupPruner` daemon is itself a smell — every part of the system re-implements the same thing.

**Reactive version.** One `Sinks.Many<DomainEvent>`, fed by the EventBus consumer; the `Flux` is `.groupBy(topic)` then `.publishOn(Schedulers.boundedElastic(), prefetch=64)` per topic. Each `GatewayWebSocketHandler` is a subscriber on the topic `Flux` it cares about. Per-client backpressure is automatic: a slow client fills its prefetch buffer; the upstream `groupBy` applies backpressure to the source; if the source overflows, `Sinks` returns `FAIL_OVERFLOW` and we emit to the existing DLQ.

```java
// Sketch — do not write yet, just to size the change
Sinks.Many<DomainEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
eventBus.subscribe(DomainEvent.class, sink::tryEmitNext);

sink.asFlux()
    .groupBy(this::topicOf)               // MARKET_TICK, CANDLE_CLOSED, ...
    .flatMap(topicFlux -> topicFlux
        .publishOn(Schedulers.boundedElastic())
        .flatMap(event -> Flux.fromIterable(router.transportsFor(topicFlux.key()))
                              .flatMap(t -> writeToWebSocket(t, event)),
                 concurrency = 8))
    .subscribe(...);
```

**Win.**

- 455-line class collapses to ~150 (sink + groupBy + per-transport write).
- The 200k dedup cache and `dedupPruner` daemon **delete** — Reactor's `Sinks` does not deduplicate, so the dedup moves to the upstream `EventBus` (which is the right place — see §3.2). Or accept that gateway events are *idempotent on the client* (the browser already keys by `eventId`) and drop dedup entirely.
- Per-client backpressure is free. Currently `GatewayEventBridge` is the obvious P1 in the architecture review ("GB-01 WebSocket bridge broadcasts all ticks to all clients").

**Cost.** ~1-2 weeks of work. One new test: 100 simulated transports at varying latencies, assert p99 client-write latency < 50ms under sustained 5k events/sec. `StepVerifier` is a good tool for this.

**Risk.** None. The `EventBus.publish` path is unchanged. The only new failure mode is `Sinks.EmitResult.FAIL_OVERFLOW`, which is identical to the existing `DLQ` path.

### 3.2 (Adopt) Replace `ExecutionHandler.placeOrderWithTimeout` with bounded executor + `orTimeout`

**File:** `trading/execution/.../ExecutionHandler.java:539-554`

**Current state.**

```java
private Order placeOrderWithTimeout(OrderRequest request) throws Exception {
    CompletableFuture<Order> placement = CompletableFuture.supplyAsync(
            () -> orderManagementService.placeOrder(request));
    try {
        return placement.get(orderPlacementTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        placement.cancel(true);
        log.warn("Order placement timed out after {}ms", orderPlacementTimeoutMs);
        throw new RuntimeException("Order placement timed out after "
                + orderPlacementTimeoutMs + "ms", e);
    } catch (ExecutionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException re) throw re;
        throw new RuntimeException("Order placement failed", cause);
    }
}
```

**Problems.**

1. `CompletableFuture.supplyAsync(() -> ...)` with no executor uses `ForkJoinPool.commonPool()`. That pool also serves every other `supplyAsync` in the codebase (token refresh, strategy sandbox, depth snapshots). During a burst, placement can wait for an unrelated task. This is the single most common JDK reactive anti-pattern.
2. `placement.cancel(true)` after timeout does not actually cancel the broker HTTP call — the worker thread runs to completion, and the result is discarded but the broker order *is placed*. You now have a position with no local record.
3. Timeout-while-filling creates a window where the worker is mid-broker-call and the timeout fires; cancellation interrupts the JDK thread but the `Socket.write` is non-interruptible.

**Reactive version.** Two clean options; pick one.

**Option A — `Mono.firstWithSignal`** (uses the existing `DhanAuthenticatedHttpClient` if you have it as `Mono<Order>`).

```java
return Mono.firstWithSignal(
        brokerCall(request),
        Mono.delay(Duration.ofMillis(orderPlacementTimeoutMs))
            .then(Mono.error(new OrderPlacementTimeoutException(...)))
    ).block();
```

This is mechanically the same code, but the timeout error is a real signal, the broker call can be a `Mono` that *actually* cancels the underlying HTTP on `dispose()`, and the failure modes are typed, not string-typed.

**Option B — keep `CompletableFuture` but with a *bounded* executor and a real cancellable future.**

```java
this.placementExecutor = Executors.newFixedThreadPool(8, ...);
return CompletableFuture.supplyAsync(() -> placeOrder(request), placementExecutor)
        .orTimeout(orderPlacementTimeoutMs, TimeUnit.MILLISECONDS);
```

`orTimeout` is a JDK 9+ method. It schedules a `ScheduledFuture` that fires `completeExceptionally`; if the future *does* complete first, the timer is cancelled. This is the smallest-diff fix and arguably better than Option A because it doesn't require rewriting the broker call as `Mono`.

**Recommendation: Option B** for now. It is the smallest, most reviewable change. The codebase already uses `CompletableFuture` in 40+ places; introducing `Mono` here breaks the convention without enough benefit. **Reactor's value is in fan-out (3.1) and stream-shaped aggregation (3.3), not in replacing a `Future` you already have.**

**Win.** The placement executor is bounded. The timeout does not leak worker threads. The `ForkJoinPool.commonPool()` anti-pattern is gone.

**Cost.** ~2 hours. One new test: an `orderManagementService` that sleeps 30s; assert the caller receives a timeout at 10s and that the underlying thread is *interrupted* (not just the future completed exceptionally). Currently the test cannot be written correctly because cancellation does not propagate.

**Risk.** Zero. Behaviour matches the existing path under happy conditions.

### 3.3 (Adopt) Use Reactor for broker REST polling and token refresh

**Files:** `broker/core/.../auth/DefaultTokenLifecycleService.java:59`, `DhanTokenManager`, `data/historical-ingest/.../DhanHistoricalDataClient` (long-poll patterns), `service/broker/BrokerExpiredOptionQueryService`.

**Current state.** All "poll every N seconds for state" logic is either `ScheduledExecutorService.scheduleAtFixedRate` or absent (the broker session runs synchronously). Token refresh is a `CompletableFuture.supplyAsync(this::acquireToken)` — same `commonPool()` anti-pattern.

**Reactive version.**

```java
// Token refresh, replacing the daemon + polling pair
Flux.interval(Duration.ofMinutes(15))
    .flatMap(t -> Mono.fromCallable(this::refreshToken).subscribeOn(Schedulers.boundedElastic()))
    .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofMinutes(5)))
    .subscribe(token -> tokenStateStore.put(token));
```

**Win.**

- Retry, backoff, jitter — all in one line. Currently scattered.
- Cancellation is correct: a context shutdown disposes the `Disposable`, the scheduled tick stops, no orphan daemon.
- `Schedulers.boundedElastic()` gives you a thread pool sized to the workload with a queue. It is the *correct* scheduler for blocking I/O. (`Schedulers.parallel()` is for CPU-bound work.)

**Cost.** ~1 day. Three or four `Flux.interval` conversions, each with a `StepVerifier`-based test using `VirtualTimeScheduler` (Reactor's virtual time).

**Risk.** Low. These are non-critical-path code paths; failure modes are already well-handled (catch + log + retry).

### 3.4 (Maybe) Per-symbol stream aggregation in the scanner and feature engine

**Files:** `trading/scanner/.../ScanEngine.java`, `trading/strategy/.../CandleAggregationService`, `trading/institutional-scanner/.../FeaturePipeline`.

**Where Reactor helps.** Grouping market data by `InstrumentKey` and applying per-symbol windowed reductions is genuinely cleaner in reactive:

```java
marketDataFlux
    .groupBy(MarketTickEvent::symbol)               // hot groupBy, not blocking
    .flatMap(symbolFlux -> symbolFlux
        .window(Duration.ofMinutes(1))               // 1-minute candle
        .flatMap(window -> window.reduce(/* build Candle */)))
    .subscribe(candle -> candleAggregation.onCandleClosed(candle));
```

**Where it doesn't help.** Anything that already does the same job synchronously inside a single Disruptor consumer. The current `CandleAggregationService` is already a stateful per-symbol reducer inside the ring; rewriting it as a `Flux` that *consumes* Disruptor events and produces Candle events is a lateral move, not an upgrade.

**Recommendation: leave the per-symbol candle/feature aggregation in the ring. The pattern is single-writer per shard, which Reactor cannot replicate without losing the latency advantage.** If the team wants to experiment, do it in a *scanner* pipeline that ingests `CandleClosed` for ranking — that's an I/O-bound, fan-out-friendly workload where Reactor shines.

---

## 4. Where reactive would HURT (the explicit "no" list)

### 4.1 Don't replace LMAX Disruptor with `Sinks.Many + Schedulers.parallel`

This is the most common mistake. The pitch is: "Disruptor is a manual reactive stream, so let's standardise on Reactor." No. The two primitives have different invariants.

| Property | LMAX Disruptor (current) | Reactor + `Schedulers.parallel` |
|---|---|---|
| Median latency (single stage, no I/O) | ~50 ns | ~1–5 µs |
| p99 latency | ~200 ns | ~50–200 µs (with `parallel`, can be ms under contention) |
| Memory model | Single-writer, no false sharing, cache-line aligned slots | `WorkQueue` per worker, false-sharing possible |
| Allocation per event | One `MutableDomainEventEnvelope` (pre-allocated) | 1–2 `FluxNext`/`OnNext` objects |
| Throughput cap on a modern Xeon | ~100M events/sec/shard | ~5–20M events/sec on `parallel()` |
| Determinism (for replay) | Strict — ring slot is the event order | Strict per subscriber, but merge/race/zip reorders |
| Cancellation on shutdown | `disruptor.shutdown(timeout)` | `disposable.dispose()` (releases resources but does not guarantee in-flight events are processed) |
| Backpressure | Ring-buffer full → publisher blocks (or DLQ) | Per-subscriber demand |
| Multi-producer | Yes (`ProducerType.MULTI`) | Implicit (every `subscribe` is a producer) |

For tick-to-trade the **determinism** and **p99** matter more than backpressure. Disruptor wins both. Reactor wins for fan-out, which is what you use the **gateway** for.

**Concrete rule for the team.** If a class currently consumes `DomainEvent` from the Disruptor ring and runs synchronously (no I/O, no waiting), do not rewrite it as a `Flux.transform` or `Mono.fromCallable`. The ring consumer is *the* hot path; keep it imperative.

### 4.2 Don't replace `ArrayBlockingQueue<ExecutionCommand>` in `ExecutionHandler`

The execution handler is the one place where you might think "reactive queue = `Flux<ExecutionCommand>.flatMap`". Don't.

- The queue is **single-producer (the ring), single-consumer (the worker thread)**. `Flux.flatMap(concurrency=N)` is a *fan-out* primitive. Using it for sequential work trades a 50ns queue for a 1µs scheduler dispatch.
- The order of execution matters: `OrderAccepted` for order A must precede `OrderFilled` for order A in the audit log, otherwise reconciliation diverges. `Flux.merge` doesn't preserve ordering across branches; you need `.concat()` per partition. At that point you've reimplemented a sharded ring buffer.
- The current per-order timeout + retry (`scheduleFillRetry`) is *intentionally* stateful on the order ID. Reactor would force you to use `Sinks` per partition or `groupBy(orderId) + windowTimeout` — both of which add latency and memory.

**If the single-threaded `ExecutionHandler` is your bottleneck** (it is — see P1 in the previous review), the right fix is to *partition by `symbol`* into N independent worker threads each with its own `ArrayBlockingQueue`. This is 50 lines of code. Don't reach for Reactor to do it.

### 4.3 Don't rewrite broker WebSocket adapters as `Flux<MarketTickEvent>`

**Files:** `broker/dhan/.../DhanMarketFeedWebSocketClient.java`, `DhanOrderStreamWebSocketClient.java`, `DhanTwentyDepthWebSocketClient.java`, `broker/upstox/.../UpstoxWebSocketMultiplexer.java`, `broker/icici/.../BreezeWebSocketMultiplexer.java`.

The JDK `WebSocket.Listener` API is already callback-based and gives you `onText` / `onBinary` / `onPing`. The current code maps these callbacks into `DhanBinaryParser` and emits `MarketTickEvent` to the bus. It's simple, it works, and it has zero thread context switches (the callback runs on the WebSocket I/O thread, and `publishEvent` on the Disruptor ring is non-blocking).

A reactive wrapper would look like:

```java
// What you'd be tempted to write
webSocket.textMessageFlux()
    .map(this::parse)
    .subscribe(eventBus::publish);
```

This **adds** a `Sinks.Many` hop with a bounded queue, plus a `Schedulers.parallel()` dispatch. You gain per-adapter backpressure (which you already have at the ring). You lose: low-latency path, deterministic event ordering inside a single binary frame, and a clear stack trace when parsing fails.

**Exception:** the gateway-bridge fan-out (3.1) is the only place I'd add a `Flux`, and it's the *consumer* side, not the producer.

### 4.4 Don't use `Mono.zip` / `Flux.combineLatest` for OMS state derivation

A common refactor temptation: "we have three events (`OrderAccepted`, `OrderPartiallyFilled`, `OrderFullyFilled`) arriving at different times; let's `Flux.combineLatest` them into an `OrderProjection`". The current implementation rebuilds the projection synchronously inside `OrderStateMachine.on(event)`, which is **correct, deterministic, and event-sourced**. A `combineLatest` is a snapshot operator — it would lose event ordering on replay. Keep the state machine.

---

## 5. Migration order and ownership

Three concrete PRs, one quarter:

| PR | Scope | Effort | Risk | Owner |
|---|---|---|---|---|
| **R-1** | `GatewayEventBridge` → `Flux<DomainEvent>` + per-topic groupBy; delete dedup cache; per-client backpressure | 1-2 weeks | Low | gateway module owner |
| **R-2** | `ExecutionHandler` + `DefaultTokenLifecycleService` switch to bounded executor; `orTimeout`; kill `ForkJoinPool.commonPool()` usage everywhere | 2-3 days | Zero | execution module owner |
| **R-3** | `Flux.interval`-based token refresh, session-risk polling, expired-options refresh | 1 day | Low | broker-core owner |

**Then stop.** Anything beyond R-3 is opt-in per node, on a case-by-case basis, and only when the workload is genuinely I/O-bound or fan-out-shaped.

---

## 6. Tests to add alongside R-1 and R-2

- **R-1 chaos test:** spin up 50 virtual WebSocket transports at random latencies 0–500ms; feed 10k `MarketTickEvent` into the gateway; assert no transport drops > 5% of messages and the slowest p99 write latency is < 100ms. Use `StepVerifier` with `VirtualTimeScheduler` so the test runs in 200ms of wall time, not 50 seconds.
- **R-1 backpressure test:** force one transport's `write` to block for 30s; assert the source `Sinks` returns `FAIL_OVERFLOW` within 1s and the DLQ receives the dropped events. **This is the test that would have caught P0-1 in the architecture review.**
- **R-2 placement timeout test:** a `FakeBroker` that sleeps 30s; `placeOrder` returns at 10s with a typed `OrderPlacementTimeoutException`; the worker thread is interruptible; on shutdown, no orphaned worker remains.
- **R-2 `commonPool()` audit:** a `Gradle` task (`reactorThreadAudit`) that greps for `CompletableFuture.supplyAsync` without an explicit executor, and `scheduleAtFixedRate` without a custom `ThreadFactory`, and fails the build. This is a 30-line test and a permanent guard against the worst JDK reactive anti-pattern.

---

## 7. Bottom line

The question "should we adopt reactive" is the wrong framing. You already have. The right question is **"where do we extend it, and where do we stop"**.

- **Adopt** for gateway fan-out, broker timeouts, and broker polling.
- **Keep** LMAX Disruptor for tick → risk → strategy → order.
- **Keep** `ArrayBlockingQueue` in the execution handler.
- **Keep** the JDK `WebSocket.Listener`-based broker adapters.
- **Keep** the imperative state machine in `OrderManagementService`.

Three PRs (R-1, R-2, R-3). One quarter. After that, the next time someone suggests "let's make the hot path reactive", point them at §4.1 of this review.

The biggest risk to Trade-J is not that it's not reactive enough. It's that the P0 defects in the architecture review (silent OMS drops, dedup misdesign, kill-switch race, OMS-first cancel) get papered over with a reactive rewrite. Don't. Fix the defects first; then adopt R-1/R-2/R-3 in parallel.
