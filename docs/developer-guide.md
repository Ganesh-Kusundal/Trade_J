# Trade-J Developer Guide

How to extend the Trade-J platform with new strategies, scanners, indicators, analytics modules, and dashboards.

---

## Quick Start

```bash
# Generate a new strategy
tradej scaffold strategy --name HalfTrend --type candle

# Generate a new scanner
tradej scaffold scanner --name RelativeStrength

# Generate a new indicator
tradej scaffold indicator --name BollingerBands

# Generate a new analytics module
tradej scaffold analytics --name SectorRotation
```

Each scaffold command generates a compilable Java source file with TODO markers. Edit the generated file, register it via META-INF/services, and it's auto-discovered by the platform.

---

## Building a Strategy

### Interface

```java
// trading/strategy/src/main/java/.../strategy/api/GraphStrategyPlugin.java
public interface GraphStrategyPlugin {
    String name();
    List<Class<? extends DomainEvent>> subscribedEventTypes();
    Optional<SignalGenerated> onEvent(DomainEvent event);
    default void onStart() {}
    default void onStop() {}
}
```

### Complete Example: Tick Price Change Strategy

```java
public final class TickPriceChangeStrategy implements GraphStrategyPlugin {
    private final long thresholdPaisa;
    private final ConcurrentHashMap<String, Long> lastPrice = new ConcurrentHashMap<>();

    public TickPriceChangeStrategy(long thresholdPaisa) {
        this.thresholdPaisa = thresholdPaisa;
    }

    @Override
    public String name() { return "tick-price-change"; }

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(MarketTickEvent.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (!(event instanceof MarketTickEvent tick)) return Optional.empty();

        Long prev = lastPrice.put(tick.symbol(), tick.ltpPaisa());
        if (prev == null) return Optional.empty();

        long change = tick.ltpPaisa() - prev;
        if (Math.abs(change) < thresholdPaisa) return Optional.empty();

        Side side = change > 0 ? Side.BUY : Side.SELL;
        return Optional.of(new SignalGenerated(
            EventMetadata.correlated(tick.correlationId(), tick.sequenceId()),
            UUID.randomUUID().toString(),
            tick.symbol(), "", side,
            tick.ltpPaisa(),
            tick.ltpPaisa() - thresholdPaisa * 2,
            tick.ltpPaisa() + thresholdPaisa * 4,
            change > 0 ? "SPIKE_UP" : "SPIKE_DOWN",
            Map.of()
        ));
    }
}
```

### Registration

Add to `META-INF/services/com.tradej.strategy.api.GraphStrategyPlugin`:
```
com.tradej.custom.strategy.TickPriceChangeStrategy
```

### Lifecycle

1. **onStart()** — called when the strategy is loaded into the runtime
2. **onEvent()** — called for each event matching `subscribedEventTypes()`
3. **onStop()** — called when the strategy is removed

### Available Event Types

| Event | Use Case |
|-------|----------|
| `MarketTickEvent` | Tick-level strategies (scalping, momentum) |
| `CandleClosed` | Bar-level strategies (crossover, breakout) |
| `CandleDeveloping` | Real-time candle strategies |
| `DepthUpdateEvent` | Order book strategies (imbalance, absorption) |

### Signal Output

Your strategy returns `Optional<SignalGenerated>`. The `SignalExecutionBridge` automatically converts signals to orders through the risk check chain.

### Testing

```java
var harness = new StrategyTestHarness();
var strategy = new TickPriceChangeStrategy(100); // 1 rupee threshold

// Fire events
var tick1 = harness.fireTick("RELIANCE", 250000L);
var tick2 = harness.fireTick("RELIANCE", 250200L); // +200 paisa

// Evaluate
var signal = harness.evaluatePlugin(strategy, tick2);
harness.assertSignalProduced("RELIANCE", Side.BUY);
```

---

## Building a Scanner

### Interface

```java
// trading/scanner/src/main/java/.../scanner/criterion/ScanCriterion.java
public interface ScanCriterion {
    String type();
    boolean matches(ScanContext context);
    double score(ScanContext context);
    default String reason(ScanContext context) { return type(); }
}
```

### ScanContext

```java
public record ScanContext(
    ScanAsset asset,          // Instrument + asset class
    Quote quote,              // LTP, volume, OI, bid/ask
    OptionChainSnapshot optionChain,  // Option chain data (nullable)
    List<Candle> intradayCandles     // Intraday candles
) {}
```

### Complete Example: Relative Strength Criterion

```java
public final class RelativeStrengthCriterion implements ScanCriterion {
    private final double minRatio;

    public RelativeStrengthCriterion(double minRatio) {
        this.minRatio = minRatio;
    }

    @Override
    public String type() { return "relative-strength"; }

    @Override
    public boolean matches(ScanContext context) {
        if (!context.hasValidQuote() || context.intradayCandles().isEmpty()) return false;

        double stockReturn = calculateReturn(context.intradayCandles());
        // Compare to index return (would be passed via context or config)
        double indexReturn = 0.5; // Example: index up 0.5%
        double ratio = stockReturn / Math.max(indexReturn, 0.01);

        return ratio >= minRatio;
    }

    @Override
    public double score(ScanContext context) {
        return calculateReturn(context.intradayCandles());
    }

    private double calculateReturn(List<Candle> candles) {
        if (candles.size() < 2) return 0;
        double first = candles.getFirst().openPaisa();
        double last = candles.getLast().closePaisa();
        return ((last - first) / first) * 100;
    }
}
```

### Available Built-in Criteria

| Criterion | Type | Description |
|-----------|------|-------------|
| `VolumeSpikeCriterion` | `volume-spike` | Volume >= avg * multiplier |
| `PctChangeFromOpenCriterion` | `pct-change-from-open` | % change from day open |
| `PctChangeFromPrevCloseCriterion` | `pct-change-from-prev-close` | % change from previous close |
| `PcrRangeCriterion` | `pcr-range` | Put-call ratio within range |
| `MaxOiStrikeCriterion` | `max-oi-strike` | Strike with maximum open interest |
| `CriterionGroup` | `group-and` | AND composition of multiple criteria |

### Testing

```java
var harness = new ScannerTestHarness();
var criterion = new VolumeSpikeCriterion(2.0, 1000);

// Build context
var ctx = harness.highVolumeContext("RELIANCE"); // 10M volume, low avg candles
harness.assertMatches(criterion, ctx);

// Test negative case
var lowVolCtx = harness.context("RELIANCE", 250000L, 500); // 500 volume
harness.assertNotMatches(criterion, lowVolCtx);
```

---

## Building an Indicator

### Interface

```java
// trading/indicators/src/main/java/.../indicators/spi/IndicatorProvider.java
public interface IndicatorProvider {
    String name();
    String displayName();
    List<Double> calculate(List<Candle> candles);
}
```

### Example: Simple Moving Average

```java
public final class SMAProvider implements IndicatorProvider {
    private final int period;

    public SMAProvider(int period) {
        this.period = period;
    }

    @Override
    public String name() { return "sma-" + period; }

    @Override
    public String displayName() { return "SMA(" + period + ")"; }

    @Override
    public List<Double> calculate(List<Candle> candles) {
        if (candles.size() < period) return List.of();
        List<Double> result = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).closePaisa() / 100.0;
            }
            result.add(sum / period);
        }
        return result;
    }
}
```

### Registration

Add to `META-INF/services/com.tradej.indicators.spi.IndicatorProvider`:
```
com.tradej.custom.indicator.SMAProvider
```

### Built-in Indicators

| Name | Description |
|------|-------------|
| `ema` | Exponential Moving Average |
| `sma` | Simple Moving Average |
| `rsi` | Relative Strength Index |
| `atr` | Average True Range |
| `vwap` | Volume Weighted Average Price |
| `obv` | On Balance Volume |

---

## Building an Analytics Module

### Interface

```java
// data/analytics/src/main/java/.../analytics/spi/AnalyticsProvider.java
public interface AnalyticsProvider {
    String name();
    String displayName();
    List<String> capabilities();
    default Map<String, String> metadata() { return Map.of(); }
    default boolean isEnabled() { return true; }
}
```

Analytics modules register capabilities that map to DuckDB SQL queries executed by the `DuckDbAnalyticsEngine`.

### Registration

Add to `META-INF/services/com.tradej.analytics.spi.AnalyticsProvider`.

---

## Building a Dashboard

### Available Components (all props-driven)

| Component | Key Props | Category |
|-----------|-----------|----------|
| `CandlestickChart` | `bars: OHLCVBar[]` | Market |
| `OrderBook` | `bids: L2Level[], asks: L2Level[]` | Market |
| `TradesList` | `trades: TradeTick[]` | Market |
| `MarketOverview` | `indices: IndexData[]` | Market |
| `WatchlistPanel` | `items: WatchlistItem[]` | Market |
| `PortfolioPanel` | `orders: OrderEntry[], tab, onTabChange` | Trading |
| `ScannerResults` | `hits, loading, error, onRunScan` | Analysis |
| `OptionChain` | `strikes, spotPrice, loading, error` | Analysis |
| `StrategyVisualization` | `signals: SignalView[]` | Analysis |
| `ReplayControls` | `isReplaying, progress, onReplayStart/Stop` | Tools |
| `NewsFeed` | `items: NewsItem[]` | Tools |
| `PriceAlerts` | `alerts, onAdd, onRemove` | Tools |
| `RiskCalculator` | `capital, onCalculate` | Tools |
| `SettingsPanel` | `broker, onBrokerChange` | Tools |
| `CertificationDashboard` | Self-contained | Tools |

### Available Hooks

| Hook | Returns | Description |
|------|---------|-------------|
| `useMarketData()` | `tick, depth, trades, candles` | Market data from MarketDataBus |
| `useOrders()` | `orders, tab, setTab` | Order polling |
| `useScanner()` | `hits, loading, error, runScan` | Scanner execution |
| `useOptionChain()` | `strikes, spotPrice, loading` | Option chain data |
| `useWatchlist()` | `items, addItem, removeItem` | Watchlist management |
| `useMarketIndices()` | `IndexData[]` | Index values |

### Dashboard Configuration Model

```typescript
// src/domain/dashboard.ts
interface DashboardConfig {
  id: string;
  name: string;
  widgets: WidgetConfig[];
}

interface WidgetConfig {
  id: string;
  type: WidgetType;
  props: Record<string, unknown>;
  position: { x: number; y: number; w: number; h: number };
}
```

Use `DashboardRenderer` to render any config:
```tsx
<DashboardRenderer config={myDashboardConfig} />
```

---

## Testing

### Test Infrastructure

| Class | Module | Purpose |
|-------|--------|---------|
| `CollectingEventBus` | core/testFixtures | Captures published events for assertions |
| `EventFactories` | core/testFixtures | Factory methods for all event types |
| `StrategyTestHarness` | strategy/testFixtures | Strategy plugin testing |
| `ScannerTestHarness` | scanner/testFixtures | Scanner criterion testing |
| `EventContractAssertions` | core/testFixtures | Event schema validation |

### Strategy Test Pattern

```java
@Test
void shouldGenerateBuySignalOnPriceSpike() {
    var harness = new StrategyTestHarness();
    var strategy = new MyStrategy();

    harness.fireTick("RELIANCE", 250000L);
    harness.fireTick("RELIANCE", 252000L); // +2000 paisa

    var signal = harness.evaluatePlugin(strategy, lastTick);
    assertTrue(signal.isPresent());
    assertEquals(Side.BUY, signal.get().side());
}
```

### Scanner Test Pattern

```java
@Test
void volumeSpikeMatchesHighVolume() {
    var harness = new ScannerTestHarness();
    var criterion = new VolumeSpikeCriterion(2.0, 1000);

    var ctx = harness.highVolumeContext("RELIANCE");
    harness.assertMatches(criterion, ctx);
    harness.assertScoreAtLeast(criterion, ctx, 1000);
}
```

### Contract Tests

Located in `app/src/test/java/com/tradej/app/contract/`:
- `OpenApiContractTest` — every REST controller has OpenAPI spec entry
- `WebSocketContractTest` — every GatewayTopic has serializer
- `EventSerializationContractTest` — events round-trip through JSON
- `EventSchemaCompatibilityTest` — schema versions managed correctly
- `BackwardCompatibilityTest` — no breaking changes to enums/interfaces

---

## Plugin Registration

All plugins use Java `ServiceLoader` for automatic discovery.

### How It Works

1. Implement the SPI interface (e.g., `GraphStrategyPlugin`)
2. Create `META-INF/services/<fully-qualified-interface-name>` containing your class name
3. Ensure your JAR is on the classpath
4. Platform discovers it automatically at startup

### Verify Registration

```bash
tradej plugins          # Shows all discovered SPI plugins
tradej doctor           # System health check
tradej events --catalog # Event catalog
```

---

## Deployment

### Docker

```bash
# Build and run
docker compose up --build -d

# With broker credentials
DHAN_CLIENT_ID=xxx DHAN_ACCESS_TOKEN=yyy docker compose up --build -d

# Check health
curl http://localhost:8080/actuator/health
```

### Platform Discovery

```bash
# Single endpoint returns all capabilities
curl http://localhost:8080/api/v1/discovery

# Individual registries
curl http://localhost:8080/api/v1/brokers
curl http://localhost:8080/api/v1/events
curl http://localhost:8080/api/v1/features
```

---

## Source File Reference

| What | Path |
|------|------|
| Strategy SPI | `trading/strategy/src/main/java/.../strategy/api/GraphStrategyPlugin.java` |
| Scanner SPI | `trading/scanner/src/main/java/.../scanner/criterion/ScanCriterion.java` |
| Indicator SPI | `trading/indicators/src/main/java/.../indicators/spi/IndicatorProvider.java` |
| Analytics SPI | `data/analytics/src/main/java/.../analytics/spi/AnalyticsProvider.java` |
| Event types | `core/src/main/java/.../core/domain/event/` |
| Event bus | `core/src/main/java/.../core/domain/port/EventBus.java` |
| Gateway topics | `gateway/src/main/java/.../gateway/protocol/GatewayTopic.java` |
| OpenAPI spec | `docs/openapi.yaml` |
| AsyncAPI spec | `docs/asyncapi.yaml` |
| Frontend components | `trade_j_frontend/src/components/` |
| Frontend hooks | `trade_j_frontend/src/hooks/` |
| Frontend generated types | `trade_j_frontend/src/generated/` |
| Architecture tests | `architecture-test/src/test/java/.../architecture/` |
| Contract tests | `app/src/test/java/.../app/contract/` |
