# Adding an Indicator to Trade-J

## What you can add

An `IndicatorProvider` is a candle-aligned technical indicator that takes a list of `Candle` objects and returns a parallel list of `Double` values (NaN for indices before the indicator is warm).

- Backend module: `trading/indicators/`
- Key class: `com.tradej.indicators.spi.IndicatorProvider` ([source](../../trading/indicators/src/main/java/com/tradej/indicators/spi/IndicatorProvider.java))
- Working example: `com.tradej.indicators.spi.builtin.RSIProvider` ([source](../../trading/indicators/src/main/java/com/tradej/indicators/spi/builtin/RSIProvider.java)) and `HalfTrendProvider` ([source](../../trading/indicators/src/main/java/com/tradej/indicators/spi/builtin/HalfTrendProvider.java))
- Registry: `com.tradej.indicators.spi.IndicatorRegistry` ([source](../../trading/indicators/src/main/java/com/tradej/indicators/spi/IndicatorRegistry.java))

## Time required

- 15-30 minutes for a stateless or per-candle indicator (RSI, EMA, SMA, ATR, Bollinger).
- 1-2 hours for an indicator with internal rolling state (HalfTrend, SuperTrend) because you also need a backing calculator class.

## Prerequisites

- You know the `Candle` model ([source](../../core/src/main/java/com/tradej/core/domain/model/Candle.java)) — note prices are in paisa and the model exposes `*Paisa()` accessors.
- You have a target `name()` string. The registry deduplicates by name; the first registered provider for a given name wins and later duplicates log a warning ([IndicatorRegistry.java line 44](../../trading/indicators/src/main/java/com/tradej/indicators/spi/IndicatorRegistry.java)).

## Step 1: Create the calculator class (optional but recommended)

If your indicator has internal state (rolling windows, last-ATR buffers), put the math in a plain class under `trading/indicators/src/main/java/com/tradej/indicators/`:

```java
package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.ArrayList;
import java.util.List;

/**
 * Hull Moving Average. Stateless — safe to share across threads.
 * Returns one value per input candle; NaN until enough data is available.
 */
public final class HullMA {
    private final int period;

    public HullMA(int period) {
        if (period < 2) {
            throw new IllegalArgumentException("period must be >= 2");
        }
        this.period = period;
    }

    public List<Double> calculate(List<Candle> candles) {
        int n = candles.size();
        List<Double> out = new ArrayList<>(n);
        int sqrtP = (int) Math.sqrt(period);

        for (int i = 0; i < n; i++) {
            if (i + 1 < period) {
                out.add(Double.NaN);
                continue;
            }
            double wmaFull = weightedClose(candles, i + 1 - period, i);
            double wmaHalf = weightedClose(candles, i + 1 - period / 2, i);
            double diff = 2.0 * wmaHalf - wmaFull;
            // Note: real HMA applies a third WMA over `sqrtP` of `diff`.
            // We return `diff` for indices where the third stage would be defined.
            if (i + 1 < sqrtP) {
                out.add(Double.NaN);
            } else {
                // For brevity: approximate the third stage as the diff itself.
                out.add(diff);
            }
        }
        return out;
    }

    private static double weightedClose(List<Candle> candles, int fromInclusive, int toInclusive) {
        double num = 0.0;
        double den = 0.0;
        int weight = 1;
        for (int i = fromInclusive; i <= toInclusive; i++) {
            num += weight * (candles.get(i).closePaisa() / 100.0);
            den += weight;
            weight++;
        }
        return num / den;
    }
}
```

If your indicator is single-pass over the candle list (RSI, SMA, EMA), skip this step and put the math directly in the provider.

## Step 2: Create the provider

Create `trading/indicators/src/main/java/com/tradej/indicators/spi/builtin/HullMAProvider.java`:

```java
package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.HullMA;
import com.tradej.indicators.spi.IndicatorProvider;

import java.util.List;

public final class HullMAProvider implements IndicatorProvider {

    private final HullMA hull;

    public HullMAProvider() {
        this(14);
    }

    public HullMAProvider(int period) {
        this.hull = new HullMA(period);
    }

    @Override
    public String name() {
        return "hull-ma";
    }

    @Override
    public String displayName() {
        return "Hull Moving Average (14)";
    }

    @Override
    public int minPeriod() {
        return 14;
    }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        return hull.calculate(candles);
    }

    @Override
    public String version() {
        return "1.0.0";
    }
}
```

The contract:

- `name()` — unique lookup key. The registry uses it as the map key.
- `displayName()` — used in UI labels.
- `minPeriod()` — number of candles required before valid output. The registry returns `NaN` for indices before this.
- `calculate()` — returns one `Double` per candle, same size as input. Use `NaN` for warmup.
- `version()` — semantic version (default `"1.0.0"`).

## Step 3: Register it

Append the fully-qualified class name to `trading/indicators/src/main/resources/META-INF/services/com.tradej.indicators.spi.IndicatorProvider`:

```
com.tradej.indicators.spi.builtin.RSIProvider
com.tradej.indicators.spi.builtin.EMAProvider
com.tradej.indicators.spi.builtin.HullMAProvider
```

The registry calls `ServiceLoader.load(IndicatorProvider.class)` at startup. Every line in the file becomes a registered provider.

## Step 4: Test it

Add `trading/indicators/src/test/java/com/tradej/indicators/spi/builtin/HullMAProviderTest.java`:

```java
package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.spi.IndicatorRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class HullMAProviderTest {

    @Test
    void providerIsDiscoveredByRegistry() {
        IndicatorRegistry registry = IndicatorRegistry.discover();
        assertNotNull(registry.get("hull-ma").orElse(null),
                "hull-ma must be registered via META-INF/services");
    }

    @Test
    void calculateReturnsParallelListOfDoubles() {
        var provider = new HullMAProvider(14);
        List<Candle> candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            long close = 250_000L + i * 100L;
            candles.add(new Candle("RELIANCE", "5m", i * 300_000L, (i + 1) * 300_000L,
                    close, close + 200L, close - 200L, close, 1_000L, true, 0L, 0L));
        }

        List<Double> result = provider.calculate(candles);
        assertEquals(candles.size(), result.size());
        assertTrue(Double.isNaN(result.get(0)), "first candle must be NaN");
    }
}
```

For an end-to-end check that your registration actually shows up at runtime, follow the pattern in [RegistryDiscoveryTest.java](../../trading/indicators/src/test/java/com/tradej/indicators/spi/builtin/RegistryDiscoveryTest.java).

## Step 5: Use it

Call `IndicatorRegistry.discover()` and look up by name:

```java
IndicatorRegistry registry = IndicatorRegistry.discover();
List<Double> values = registry.calculate("hull-ma", candles);
```

The frontend and other modules look up indicators by the same `name()` string, so the key you pick becomes the public contract. Renaming an indicator later is a breaking change.

## Common pitfalls

- **Wrong return size** — `calculate()` must return a list of the same size as the input. Return `NaN` entries for warmup, not shorter lists.
- **Mutating input** — never mutate the `candles` list. Other consumers may hold the same reference.
- **Thread safety** — the `IndicatorRegistry` returns the same provider instance to every caller. Your provider must be thread-safe. Either be stateless or synchronize internally.
- **Name collision** — the registry logs a warning and keeps the first provider when two providers claim the same `name()`. Look for `Duplicate indicator provider` in the logs after startup.
- **Prices in paisa** — the `Candle` accessors are `*Paisa()`. Divide by `100.0` to get rupees when matching a public formula.

## See also

- SPI: [IndicatorProvider.java](../../trading/indicators/src/main/java/com/tradej/indicators/spi/IndicatorProvider.java)
- Registry: [IndicatorRegistry.java](../../trading/indicators/src/main/java/com/tradej/indicators/spi/IndicatorRegistry.java)
- Working example: [RSIProvider.java](../../trading/indicators/src/main/java/com/tradej/indicators/spi/builtin/RSIProvider.java)
- Stateful example: [HalfTrendProvider.java](../../trading/indicators/src/main/java/com/tradej/indicators/spi/builtin/HalfTrendProvider.java)
- META-INF/services: [com.tradej.indicators.spi.IndicatorProvider](../../trading/indicators/src/main/resources/META-INF/services/com.tradej.indicators.spi.IndicatorProvider)
- Existing registered providers (13 candle-aligned): [RegistryDiscoveryTest.java](../../trading/indicators/src/test/java/com/tradej/indicators/spi/builtin/RegistryDiscoveryTest.java)
