package com.tradej.strategy.ml;

import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InferenceResult;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.port.MLInferenceEngine;
import com.tradej.core.domain.port.ModelRegistry;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ThresholdMLInferenceEngineTest {

    private ModelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InMemoryModelRegistry("mean-reversion-v1", "trend-following-v1");
        registry.loadModel("mean-reversion-v1");
    }

    // ── RSI oversold (BUY) ──

    @Test
    void rsiOversoldProducesBuySignal() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                20.0,     // rsi < 30 → oversold (confidence (30-20)/30 > 0.3)
                750_00L, 755_00L, 760_00L,
                760_00L, 770_00L,
                755_00L,  // vwap
                0.15,     // volatility
                100_000L, // volume imbalance
                50L);     // spread

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "RSI oversold should produce a BUY signal");
        assertEquals(Side.BUY, result.get().side());
        assertTrue(result.get().confidence() > 0.3,
                "Confidence should be meaningful for deeply oversold RSI");
        assertEquals("RSI_OVERSOLD", result.get().setup());
        assertTrue(result.get().stopLossPaisa() < result.get().entryPricePaisa(),
                "Stop-loss should be below entry for BUY");
        assertTrue(result.get().takeProfitPaisa() > result.get().entryPricePaisa(),
                "Take-profit should be above entry for BUY");
    }

    @Test
    void rsiDeeplyOversoldHasHigherConfidence() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var mildlyOversold = new FeatureVector("SBIN", "5m", 1_000_000L,
                28.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L, 755_00L, 0.15, 100_000L, 50L);
        var deeplyOversold = new FeatureVector("SBIN", "5m", 1_000_000L,
                15.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L, 755_00L, 0.15, 100_000L, 50L);

        double mildConfidence = engine.evaluate(mildlyOversold).get().confidence();
        double deepConfidence = engine.evaluate(deeplyOversold).get().confidence();
        assertTrue(deepConfidence > mildConfidence,
                "Deeply oversold RSI should yield higher confidence than mildly oversold");
    }

    // ── RSI overbought (SELL) ──

    @Test
    void rsiOverboughtProducesSellSignal() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                78.0,     // rsi > 70 → overbought
                800_00L, 795_00L, 780_00L,
                780_00L, 770_00L,
                790_00L,
                0.12,
                -200_000L,
                50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "RSI overbought should produce a SELL signal");
        assertEquals(Side.SELL, result.get().side());
        assertEquals("RSI_OVERBOUGHT", result.get().setup());
        assertTrue(result.get().stopLossPaisa() > result.get().entryPricePaisa(),
                "Stop-loss should be above entry for SELL");
        assertTrue(result.get().takeProfitPaisa() < result.get().entryPricePaisa(),
                "Take-profit should be below entry for SELL");
    }

    // ── EMA crossover ──

    @Test
    void emaBullishCrossProducesBuySignal() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, true, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // RSI neutral (50), EMA5 > EMA21 = bullish cross
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0,
                780_00L,   // ema5
                770_00L,   // ema9
                760_00L,   // ema21
                760_00L, 770_00L,
                770_00L,
                0.10,
                0L,
                50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "EMA bullish cross should produce a BUY signal");
        assertEquals(Side.BUY, result.get().side());
        assertEquals("EMA_BULLISH_CROSS", result.get().setup());
    }

    @Test
    void emaBearishCrossProducesSellSignal() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, true, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // RSI neutral (50), EMA5 < EMA21 = bearish cross
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0,
                750_00L,   // ema5
                760_00L,   // ema9
                780_00L,   // ema21
                780_00L, 770_00L,
                770_00L,
                0.10,
                0L,
                50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "EMA bearish cross should produce a SELL signal");
        assertEquals(Side.SELL, result.get().side());
        assertEquals("EMA_BEARISH_CROSS", result.get().setup());
    }

    @Test
    void emaCrossoverDisabledDoesNotFire() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, false, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0, 780_00L, 770_00L, 760_00L, 760_00L, 770_00L, 770_00L, 0.10, 0L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isEmpty(), "No signal expected with RSI neutral and EMA disabled");
    }

    // ── Volume imbalance ──

    @Test
    void volumeBuyingPressureProducesSignal() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, false, 2.0, 5.0,
                true, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L,
                755_00L,
                0.10,
                1_000_000L, // volume imbalance > 500k threshold
                50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "Volume buying pressure should produce a signal");
        assertEquals(Side.BUY, result.get().side());
        assertEquals("VOLUME_BUYING", result.get().setup());
    }

    @Test
    void volumeSellingPressureProducesSignal() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, false, 2.0, 5.0,
                true, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L,
                755_00L,
                0.10,
                -1_200_000L, // volume imbalance < -500k threshold
                50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent(), "Volume selling pressure should produce a signal");
        assertEquals(Side.SELL, result.get().side());
        assertEquals("VOLUME_SELLING", result.get().setup());
    }

    // ── No signal (neutral zone) ──

    @Test
    void neutralRsiProducesNoSignal() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, false, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L, 755_00L, 0.10, 0L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isEmpty(), "Neutral RSI with no other rules enabled should not produce a signal");
    }

    // ── Model not loaded ──

    @Test
    void modelNotLoadedReturnsEmpty() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("unknown-model");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                25.0, 750_00L, 755_00L, 760_00L, 760_00L, 770_00L, 755_00L, 0.10, 100_000L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isEmpty(), "Engine should return empty when model is not loaded");
    }

    // ── Rule priority: RSI takes precedence over EMA / volume ──

    @Test
    void rsiTakesPrecedenceOverEma() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, true, 2.0, 5.0,
                true, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // RSI deeply oversold (20) AND EMA bullish cross AND volume buying pressure
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                20.0, 780_00L, 770_00L, 760_00L, 760_00L, 770_00L, 755_00L, 0.10, 1_000_000L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent());
        assertEquals("RSI_OVERSOLD", result.get().setup(),
                "RSI oversold should take precedence over EMA/volume rules");
    }

    // ── Edge cases ──

    @Test
    void rsiExactly100ReturnsBuy() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // RSI = 100 → way below oversold? No, RSI=100 > 70 → overbought, SELL
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                100.0, 800_00L, 795_00L, 780_00L, 780_00L, 770_00L, 790_00L, 0.12, -200_000L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent());
        assertEquals(Side.SELL, result.get().side());
    }

    @Test
    void rsiExactly0ReturnsBuy() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                0.0, 700_00L, 705_00L, 710_00L, 710_00L, 720_00L, 705_00L, 0.15, 100_000L, 50L);

        Optional<InferenceResult> result = engine.evaluate(features);
        assertTrue(result.isPresent());
        assertEquals(Side.BUY, result.get().side());
    }

    @Test
    void confidenceIsCappedAtOne() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("mean-reversion-v1");
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // RSI = 0 → extreme oversold → confidence should be high, but capped at 1.0
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                0.0, 700_00L, 705_00L, 710_00L, 710_00L, 720_00L, 705_00L, 0.15, 100_000L, 50L);

        double confidence = engine.evaluate(features).get().confidence();
        assertTrue(confidence <= 1.0, "Confidence must not exceed 1.0");
        assertTrue(confidence > 0.0, "Confidence must be positive for oversold RSI");
    }

    // ── ThresholdConfig defaults ──

    @Test
    void defaultConfigHasSensibleValues() {
        var config = ThresholdMLInferenceEngine.ThresholdConfig.defaults("test-model");
        assertEquals("test-model", config.modelName());
        assertEquals(30.0, config.rsiOversoldThreshold());
        assertEquals(70.0, config.rsiOverboughtThreshold());
        assertTrue(config.emaCrossoverEnabled());
        assertTrue(config.volumeImbalanceEnabled());
        assertTrue(config.defaultStopLossPct() > 0);
        assertTrue(config.defaultTakeProfitPct() > 0);
        assertEquals(10, config.defaultQuantity());
        assertEquals(5.0, config.emaMinSpreadBps(), "Default min spread should be 5 bps");
    }

    // ── Minimum Spread Threshold ──

    @Test
    void emaBullishCrossBelowMinSpreadDoesNotFire() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, true, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // EMA5 and EMA21 very close (1 paisa apart on 76000 ≈ 0.01 bps) — below 5 bps min
        var features = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0,
                760_01L,   // ema5
                760_00L,   // ema9
                760_00L,   // ema21
                760_00L, 770_00L,
                760_00L,
                0.10,
                0L,
                50L);

        assertTrue(engine.evaluate(features).isEmpty(),
                "EMA crossover below min spread should not produce a signal");
    }

    // ── Logarithmic Confidence Scaling ──

    @Test
    void emaConfidenceUsesLogarithmicScaling() {
        var config = new ThresholdMLInferenceEngine.ThresholdConfig(
                "mean-reversion-v1", 30.0, 70.0, true, 2.0, 5.0,
                false, 500_000L, 1.5, 0.02, 0.03, 10);
        var engine = new ThresholdMLInferenceEngine(registry, config);

        // Small bullish cross: ~6 bps spread (EMA5=76046, EMA21=76000)
        var smallSpread = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0,
                760_46L,   // ema5 (~6 bps above ema21)
                760_30L,
                760_00L,  // ema21
                760_00L, 770_00L,
                760_30L,
                0.10,
                0L,
                50L);

        // Large bullish cross: ~11 bps spread (EMA5=76084, EMA21=76000)
        var largeSpread = new FeatureVector("SBIN", "5m", 1_000_000L,
                50.0,
                760_84L,   // ema5 (~11 bps above ema21)
                760_50L,
                760_00L,  // ema21
                760_00L, 770_00L,
                760_50L,
                0.10,
                0L,
                50L);

        double smallConfidence = engine.evaluate(smallSpread).get().confidence();
        double largeConfidence = engine.evaluate(largeSpread).get().confidence();

        assertTrue(largeConfidence > smallConfidence,
                "Larger spread should yield higher confidence");
        // Logarithmic scaling: ~1.8x spread does NOT produce 1.8x confidence
        // (11 bps / 6 bps ≈ 1.8x, but log1p grows sub-linearly)
        assertTrue(largeConfidence / smallConfidence < 1.8,
                "Logarithmic scaling should grow slower-than-linear");
    }
}
