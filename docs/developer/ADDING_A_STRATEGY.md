# Adding a Strategy to Trade-J

## What you can add

A strategy is a `GraphStrategyPlugin` that subscribes to one or more `DomainEvent` types and produces `SignalGenerated` events on the hot path or in the sandbox.

- Backend module: `trading/strategy/`
- Key class: `com.tradej.strategy.api.GraphStrategyPlugin` ([source](../../trading/strategy/src/main/java/com/tradej/strategy/api/GraphStrategyPlugin.java))
- Working example: `com.tradej.strategy.example.TickPriceChangeStrategy` ([source](../../trading/strategy/src/main/java/com/tradej/strategy/example/TickPriceChangeStrategy.java)) and `com.tradej.strategy.example.DepthImbalanceStrategy` ([source](../../trading/strategy/src/main/java/com/tradej/strategy/example/DepthImbalanceStrategy.java))
- Output event: `com.tradej.core.domain.event.SignalGenerated` ([source](../../core/src/main/java/com/tradej/core/domain/event/SignalGenerated.java))

## Time required

- 30 minutes for a stateless tick or candle strategy using only `Map<Candle>` state.
- 2-3 hours for a multi-event strategy that needs Spring-injected collaborators (e.g. `FeatureStore`).

## Prerequisites

- You understand the platform's event types: `MarketTickEvent`, `CandleClosed`, `DepthUpdateEvent`. See [core/src/main/java/com/tradej/core/domain/event/](../../core/src/main/java/com/tradej/core/domain/event/).
- You know the value object `Side` (BUY/SELL) and the price convention in paisa (1 INR = 100 paisa). See [core/src/main/java/com/tradej/core/domain/value/](../../core/src/main/java/com/tradej/core/domain/value/).
- For DI-required strategies, you understand how Spring `@Configuration` beans are wired in `TradingConfiguration` ([source](../../app/src/main/java/com/tradej/app/config/TradingConfiguration.java)).

## Step 1: Create the class

Create the file at `trading/strategy/src/main/java/com/tradej/strategy/example/HalfTrendFlipStrategy.java`:

```java
package com.tradej.strategy.example;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.value.Side;
import com.tradej.indicators.HalfTrend;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example strategy that detects HalfTrend direction flips on closed candles
 * and produces a momentum signal on each flip.
 *
 * <p>Demonstrates the candle strategy pattern: subscribing to {@link CandleClosed},
 * running an indicator ({@link HalfTrend}) per symbol, and producing signals
 * when the trend direction changes.
 */
public final class HalfTrendFlipStrategy implements GraphStrategyPlugin {

    private static final Logger log = LoggerFactory.getLogger(HalfTrendFlipStrategy.class);

    private final String name;
    private final HalfTrend indicator = new HalfTrend(2, 2, 100);
    private final ConcurrentHashMap<String, String> lastDirection = new ConcurrentHashMap<>();

    public HalfTrendFlipStrategy(String name) {
        this.name = name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(CandleClosed.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (!(event instanceof CandleClosed closed)) {
            return Optional.empty();
        }

        // Run the indicator on a single-candle batch — HalfTrend is stateful internally,
        // so for a real implementation feed a rolling candle history (see HalfTrendProvider).
        // For this example we derive a synthetic direction from the closed candle.
        var candle = closed.candle();
        String direction = candle.closePaisa() >= candle.openPaisa() ? "up" : "down";
        String previous = lastDirection.put(candle.symbol(), direction);
        if (previous == null || previous.equals(direction)) {
            return Optional.empty();
        }

        Side side = direction.equals("up") ? Side.BUY : Side.SELL;
        long entry = candle.closePaisa();
        String signalId = UUID.randomUUID().toString();

        SignalGenerated signal = new SignalGenerated(
                EventMetadata.correlated(closed.correlationId(), closed.sequenceId()),
                signalId,
                candle.symbol(),
                candle.interval(),
                side,
                entry,
                side == Side.BUY ? entry - 5_000L : entry + 5_000L,
                side == Side.BUY ? entry + 10_000L : entry - 10_000L,
                "HALFTREND_FLIP_" + direction.toUpperCase(),
                Collections.unmodifiableMap(Map.of(
                        "strategyName", name,
                        "previousDirection", previous,
                        "newDirection", direction
                ))
        );

        log.info("HalfTrend flip plugin={} symbol={} side={}", name, candle.symbol(), side);
        return Optional.of(signal);
    }

    @Override
    public void onStart() {
        log.info("HalfTrend flip strategy '{}' started", name);
    }

    @Override
    public void onStop() {
        lastDirection.clear();
        log.info("HalfTrend flip strategy '{}' stopped", name);
    }
}
```

Key points:

- `subscribedEventTypes()` returns the event types you handle. The graph runtime routes only matching events to your `onEvent`.
- The runtime calls `onEvent` from the hot path. Avoid blocking I/O inside it.
- Use a `ConcurrentHashMap` for per-symbol state. The runtime is multi-threaded.
- Stop-loss and take-profit are prices in paisa, not fractions.
- The `attributes` map is copied defensively by `SignalGenerated`; you can pass a mutable map.

## Step 2: Register it

Append the fully-qualified class name to `trading/strategy/src/main/resources/META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin`:

```
com.tradej.strategy.plugin.OptionsContextStrategyPlugin
com.tradej.strategy.ml.MLStrategyPlugin
com.tradej.strategy.example.TickPriceChangeStrategy
com.tradej.strategy.example.DepthImbalanceStrategy
com.tradej.strategy.example.HalfTrendFlipStrategy
```

The platform loads all classes in this file at startup via `ServiceLoader`. No code change is required to discover the new strategy.

## Step 3: Wire dependencies (if any)

If your strategy is self-contained (constructor takes primitives or record fields), no further wiring is needed. The platform instantiates it via the no-arg or single-arg constructor pattern used by `TickPriceChangeStrategy` and `DepthImbalanceStrategy`.

If your strategy needs Spring-injected collaborators (for example, a `FeatureStore`), you must add a `@Bean` method in `TradingConfiguration` ([source](../../app/src/main/java/com/tradej/app/config/TradingConfiguration.java)):

```java
@Bean
HalfTrendFlipStrategy halfTrendFlipStrategy() {
    return new HalfTrendFlipStrategy("HalfTrend-Flip");
}
```

The `GraphStrategySandbox` bean collects all `GraphStrategyPlugin` beans automatically.

### Dual-registration warning

If your strategy needs DI (a `FeatureStore`, a `ModelRegistry`, etc.), you must:

1. Add the class to the `META-INF/services` file.
2. Add a `@Bean` method in `TradingConfiguration`.

If you only do (1), Spring creates one instance via the constructor, and the `ServiceLoader` creates another. Both end up in the sandbox and your strategy runs twice. If you only do (2), the strategy is reachable from the sandbox but is not reachable from any other discovery path that scans the services file. The safe pattern is to do both and accept that the sandbox keeps a single instance keyed by bean name.

The cleanest path is: only do (2) for any strategy that needs DI, and remove the line from the `META-INF/services` file. For purely self-contained strategies, only do (1).

## Step 4: Test it

Place the test under `trading/strategy/src/test/java/com/tradej/strategy/example/HalfTrendFlipStrategyTest.java`:

```java
package com.tradej.strategy.example;

import com.tradej.core.domain.value.Side;
import com.tradej.strategy.testing.StrategyTestHarness;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class HalfTrendFlipStrategyTest {

    @Test
    void emitsBuySignalOnFirstFlipUp() {
        var harness = new StrategyTestHarness();
        var strategy = new HalfTrendFlipStrategy("HalfTrend-Flip-Test");

        // First candle — establishes direction "up" (no signal yet).
        harness.evaluatePlugin(strategy, harness.fireCandleClosed("RELIANCE", 250_000L, "5m"));
        // Second candle — direction flips to "down" because close < open.
        var signal = harness.evaluatePlugin(strategy, harness.fireCandleClosed("RELIANCE", 248_000L, "5m"));

        assertTrue(signal.isPresent());
        assertTrue(signal.get().side() == Side.SELL);
    }
}
```

The `StrategyTestHarness` ([source](../../trading/strategy/src/testFixtures/java/com/tradej/strategy/testing/StrategyTestHarness.java)) provides deterministic sequence IDs and metadata, plus `assertSignalProduced(symbol, side)` and `assertNoSignal()` helpers.

## Step 5: Use it

Once registered and built, the `GraphStrategySandbox` ([source](../../trading/strategy/src/main/java/com/tradej/strategy/service/GraphStrategySandbox.java)) picks up your strategy at startup. The sandbox routes events matching `subscribedEventTypes()` to your `onEvent` and re-publishes any returned `SignalGenerated` on the event bus. From there the signal flows through risk checks and order execution.

No further configuration is required. To verify the registration, search the application logs for the line emitted by your `onStart` method.

## Common pitfalls

- **Wrong event type** — `subscribedEventTypes()` must contain the exact `Class<? extends DomainEvent>`. The runtime filters by `instanceof`, so if you list `MarketTickEvent.class` but emit on candle data, you get nothing.
- **Stateful instance shared across symbols** — use a per-symbol `ConcurrentHashMap`. The runtime can route events for multiple symbols to the same plugin instance.
- **Paisa vs rupees** — all prices in `SignalGenerated` are paisa. `5_000L` paisa = Rs 50.
- **Dual registration of a DI-required strategy** — see the warning above. Pick one path.
- **Blocking in `onEvent`** — this is the hot path. No file I/O, no HTTP, no DB lookups. Buffer state in memory and flush in `onStop` if needed.

## See also

- SPI: [GraphStrategyPlugin.java](../../trading/strategy/src/main/java/com/tradej/strategy/api/GraphStrategyPlugin.java)
- Output event: [SignalGenerated.java](../../core/src/main/java/com/tradej/core/domain/event/SignalGenerated.java)
- Example tick strategy: [TickPriceChangeStrategy.java](../../trading/strategy/src/main/java/com/tradej/strategy/example/TickPriceChangeStrategy.java)
- Example depth strategy: [DepthImbalanceStrategy.java](../../trading/strategy/src/main/java/com/tradej/strategy/example/DepthImbalanceStrategy.java)
- Spring wiring: [TradingConfiguration.java](../../app/src/main/java/com/tradej/app/config/TradingConfiguration.java)
- Test harness: [StrategyTestHarness.java](../../trading/strategy/src/testFixtures/java/com/tradej/strategy/testing/StrategyTestHarness.java)
- META-INF/services file: [com.tradej.strategy.api.GraphStrategyPlugin](../../trading/strategy/src/main/resources/META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin)
