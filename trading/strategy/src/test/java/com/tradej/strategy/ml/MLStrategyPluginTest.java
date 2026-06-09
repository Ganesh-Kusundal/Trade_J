package com.tradej.strategy.ml;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InferenceResult;
import com.tradej.core.domain.port.MLInferenceEngine;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class MLStrategyPluginTest {

    private InMemoryFeatureStore featureStore;
    private MLInferenceEngine dummyEngine;
    private CandleClosed sampleCandle;

    @BeforeEach
    void setUp() {
        featureStore = new InMemoryFeatureStore();
        dummyEngine = features -> {
            if (features.rsi() < 30) {
                return Optional.of(new InferenceResult(Side.BUY, 10, 750_00L, 740_00L, 770_00L, 0.85, "RSI_OVERSOLD"));
            }
            if (features.rsi() > 70) {
                return Optional.of(new InferenceResult(Side.SELL, 15, 750_00L, 760_00L, 730_00L, 0.80, "RSI_OVERBOUGHT"));
            }
            return Optional.empty();
        };

        sampleCandle = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1_000_000L, 1_000_300L,
                        750_00L, 755_00L, 748_00L, 752_00L, 10_000L, true)
        );
    }

    @Test
    void producesSignalWhenEngineReturnsResult() {
        var plugin = new MLStrategyPlugin("ml-mean-rev", featureStore, dummyEngine, "5m", 14);

        // Seed features that trigger BUY (RSI < 30)
        featureStore.features = Optional.of(new FeatureVector(
                "SBIN", "5m", 1_000_300L,
                25.0, 752_00L, 755_00L, 760_00L, 760_00L, 770_00L,
                754_00L, 0.12, 100_000L, 50L
        ));

        Optional<SignalGenerated> signal = plugin.onEvent(sampleCandle);
        assertTrue(signal.isPresent(), "ML engine with BUY result should produce a SignalGenerated");
        assertEquals(Side.BUY, signal.get().side());
        assertEquals("SBIN", signal.get().symbol());
        assertEquals("5m", signal.get().interval());
        assertEquals(750_00L, signal.get().entryPricePaisa());
        assertEquals("RSI_OVERSOLD", signal.get().setup());
        assertEquals("ml-mean-rev", signal.get().attributes().get("mlModel"));
    }

    @Test
    void returnsEmptyWhenEngineReturnsEmpty() {
        var plugin = new MLStrategyPlugin("ml-mean-rev", featureStore, dummyEngine, "5m", 14);

        // RSI neutral → engine returns empty
        featureStore.features = Optional.of(new FeatureVector(
                "SBIN", "5m", 1_000_300L,
                50.0, 752_00L, 755_00L, 760_00L, 760_00L, 770_00L,
                754_00L, 0.10, 0L, 50L
        ));

        Optional<SignalGenerated> signal = plugin.onEvent(sampleCandle);
        assertTrue(signal.isEmpty(), "Neutral RSI should produce no signal");
    }

    @Test
    void returnsEmptyWhenFeatureStoreHasNoData() {
        var plugin = new MLStrategyPlugin("ml-mean-rev", featureStore, dummyEngine, "5m", 14);

        // Feature store returns empty
        featureStore.features = Optional.empty();

        Optional<SignalGenerated> signal = plugin.onEvent(sampleCandle);
        assertTrue(signal.isEmpty(), "No features should produce no signal");
    }

    @Test
    void propagatesSellSignal() {
        var plugin = new MLStrategyPlugin("ml-trend", featureStore, dummyEngine, "5m", 14);

        featureStore.features = Optional.of(new FeatureVector(
                "SBIN", "5m", 1_000_300L,
                80.0, 760_00L, 755_00L, 750_00L, 750_00L, 740_00L,
                755_00L, 0.15, -500_000L, 50L
        ));

        Optional<SignalGenerated> signal = plugin.onEvent(sampleCandle);
        assertTrue(signal.isPresent());
        assertEquals(Side.SELL, signal.get().side());
        assertEquals("RSI_OVERBOUGHT", signal.get().setup());
        assertEquals(0.80, (double) signal.get().attributes().get("confidence"), 0.001);
    }

    @Test
    void signalAttributesIncludeConfidenceAndSetup() {
        var plugin = new MLStrategyPlugin("ml-test", featureStore, dummyEngine, "5m", 14);

        featureStore.features = Optional.of(new FeatureVector(
                "SBIN", "5m", 1_000_300L,
                20.0, 752_00L, 755_00L, 760_00L, 760_00L, 770_00L,
                754_00L, 0.12, 100_000L, 50L
        ));

        Optional<SignalGenerated> signal = plugin.onEvent(sampleCandle);
        assertTrue(signal.isPresent());
        assertEquals(0.85, (double) signal.get().attributes().get("confidence"), 0.001);
        assertEquals("RSI_OVERSOLD", signal.get().attributes().get("setup"));
        assertEquals("ml-test", signal.get().attributes().get("mlModel"));
        assertEquals(10, (int) signal.get().attributes().get("quantity"));
    }

    @Test
    void pluginNameIsReturned() {
        var plugin = new MLStrategyPlugin("my-ml-strategy", featureStore, dummyEngine, "5m", 14);
        assertEquals("my-ml-strategy", plugin.name());
    }

    // ── In-memory feature store for testing ──

    private static final class InMemoryFeatureStore implements com.tradej.core.domain.port.FeatureStore {
        Optional<FeatureVector> features = Optional.empty();

        @Override
        public void feed(com.tradej.core.domain.event.DomainEvent event) {
            // no-op for testing
        }

        @Override
        public Optional<FeatureVector> getFeatures(String symbol, String interval, int lookback) {
            return features;
        }
    }
}
