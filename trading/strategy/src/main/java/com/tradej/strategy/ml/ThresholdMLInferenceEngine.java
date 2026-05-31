package com.tradej.strategy.ml;

import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InferenceResult;
import com.tradej.core.domain.port.MLInferenceEngine;
import com.tradej.core.domain.port.ModelRegistry;
import com.tradej.core.domain.value.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * A configurable, rules-based implementation of {@link MLInferenceEngine} that
 * simulates ML inference using feature thresholds.
 *
 * <p>This implementation demonstrates the full ML inference adapter pattern
 * without requiring ONNX Runtime or an actual trained model. It uses classic
 * technical indicators (RSI, EMA crossovers, volume imbalance) encoded as
 * threshold rules that produce {@link InferenceResult}s with a simulated
 * confidence score.
 *
 * <p>Configuration is provided via a {@link ThresholdConfig} record, making
 * the rules externally tuneable without code changes.
 */
public final class ThresholdMLInferenceEngine implements MLInferenceEngine {

    private static final Logger log = LoggerFactory.getLogger(ThresholdMLInferenceEngine.class);

    private final ModelRegistry registry;
    private final ThresholdConfig config;

    /**
     * Creates the engine with the given model registry and threshold configuration.
     *
     * @param registry model registry (used to verify model readiness)
     * @param config   threshold-based trading rules
     */
    public ThresholdMLInferenceEngine(ModelRegistry registry, ThresholdConfig config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Optional<InferenceResult> evaluate(FeatureVector features) {
        if (!registry.isLoaded(config.modelName())) {
            log.warn("Model '{}' not loaded; skipping inference for symbol={}",
                    config.modelName(), features.symbol());
            return Optional.empty();
        }

        // ── Rule evaluation ──

        // 1. RSI overbought / oversold
        if (features.rsi() <= config.rsiOversoldThreshold()) {
            double confidence = computeConfidence(
                    config.rsiOversoldThreshold() - features.rsi(),
                    0, config.rsiOversoldThreshold());
            long entry = features.vwapPaisa() > 0 ? features.vwapPaisa() : features.ema9Paisa();
            log.debug("RSI oversold signal symbol={} rsi={} confidence={}",
                    features.symbol(), features.rsi(), confidence);
            return Optional.of(new InferenceResult(
                    Side.BUY,
                    config.defaultQuantity(),
                    entry,
                    Math.round(entry * (1.0 - config.defaultStopLossPct())),
                    Math.round(entry * (1.0 + config.defaultTakeProfitPct())),
                    clamp(confidence),
                    "RSI_OVERSOLD"
            ));
        }

        if (features.rsi() >= config.rsiOverboughtThreshold()) {
            double confidence = computeConfidence(
                    features.rsi() - config.rsiOverboughtThreshold(),
                    0, 100 - config.rsiOverboughtThreshold());
            long entry = features.vwapPaisa() > 0 ? features.vwapPaisa() : features.ema9Paisa();
            log.debug("RSI overbought signal symbol={} rsi={} confidence={}",
                    features.symbol(), features.rsi(), confidence);
            return Optional.of(new InferenceResult(
                    Side.SELL,
                    config.defaultQuantity(),
                    entry,
                    Math.round(entry * (1.0 + config.defaultStopLossPct())),
                    Math.round(entry * (1.0 - config.defaultTakeProfitPct())),
                    clamp(confidence),
                    "RSI_OVERBOUGHT"
            ));
        }        // 2. EMA crossover (bullish: EMA5 > EMA21)
        if (config.emaCrossoverEnabled() && features.ema5Paisa() > 0 && features.ema21Paisa() > 0) {
            long emaDiff = Math.abs(features.ema5Paisa() - features.ema21Paisa());
            double spreadBps = (double) emaDiff / features.ema21Paisa() * 10_000.0;

            // Require minimum spread to avoid noisy signals from near-equal EMAs
            if (spreadBps >= config.emaMinSpreadBps()) {
                double ratio = spreadBps / config.emaMinSpreadBps();
                double confidence = clamp(
                        Math.log1p(ratio) / Math.log1p(10) * config.emaCrossoverMultiplier()
                );

                if (features.ema5Paisa() > features.ema21Paisa()) {
                    log.debug("EMA bullish crossover signal symbol={} spreadBps={} confidence={}",
                            features.symbol(), spreadBps, confidence);
                    return Optional.of(new InferenceResult(
                            Side.BUY,
                            config.defaultQuantity(),
                            features.ema9Paisa(),
                            Math.round(features.ema21Paisa() * (1.0 - config.defaultStopLossPct())),
                            Math.round(features.ema5Paisa() * (1.0 + config.defaultTakeProfitPct())),
                            confidence,
                            "EMA_BULLISH_CROSS"
                    ));
                }

                if (features.ema5Paisa() < features.ema21Paisa()) {
                    log.debug("EMA bearish crossover signal symbol={} spreadBps={} confidence={}",
                            features.symbol(), spreadBps, confidence);
                    return Optional.of(new InferenceResult(
                            Side.SELL,
                            config.defaultQuantity(),
                            features.ema9Paisa(),
                            Math.round(features.ema21Paisa() * (1.0 + config.defaultStopLossPct())),
                            Math.round(features.ema5Paisa() * (1.0 - config.defaultTakeProfitPct())),
                            confidence,
                            "EMA_BEARISH_CROSS"
                    ));
                }
            }
        }

        // 3. Volume imbalance (extreme buying/selling pressure)
        if (config.volumeImbalanceEnabled() && features.volumeImbalance() != 0) {
            if (features.volumeImbalance() > config.volumeImbalanceThreshold()) {
                double ratio = (double) features.volumeImbalance() / config.volumeImbalanceThreshold();
                double confidence = clamp(Math.log1p(ratio) / Math.log1p(10) * config.volumeImbalanceMultiplier());
                log.debug("Volume buying pressure signal symbol={} imbalance={} confidence={}",
                        features.symbol(), features.volumeImbalance(), confidence);
                return Optional.of(new InferenceResult(
                        Side.BUY,
                        config.defaultQuantity(),
                        features.vwapPaisa(),
                        Math.round(features.vwapPaisa() * (1.0 - config.defaultStopLossPct())),
                        Math.round(features.vwapPaisa() * (1.0 + config.defaultTakeProfitPct())),
                        clamp(confidence),
                        "VOLUME_BUYING"
                ));
            }
            if (features.volumeImbalance() < -config.volumeImbalanceThreshold()) {
                double ratio = (double) -features.volumeImbalance() / config.volumeImbalanceThreshold();
                double confidence = clamp(Math.log1p(ratio) / Math.log1p(10) * config.volumeImbalanceMultiplier());
                log.debug("Volume selling pressure signal symbol={} imbalance={} confidence={}",
                        features.symbol(), features.volumeImbalance(), confidence);
                return Optional.of(new InferenceResult(
                        Side.SELL,
                        config.defaultQuantity(),
                        features.vwapPaisa(),
                        Math.round(features.vwapPaisa() * (1.0 + config.defaultStopLossPct())),
                        Math.round(features.vwapPaisa() * (1.0 - config.defaultTakeProfitPct())),
                        clamp(confidence),
                        "VOLUME_SELLING"
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Configuration for {@link ThresholdMLInferenceEngine} threshold rules.
     *
     * @param modelName                  name of the model to load via {@link ModelRegistry}
     * @param rsiOversoldThreshold       RSI level below which a BUY signal is generated (default 30)
     * @param rsiOverboughtThreshold     RSI level above which a SELL signal is generated (default 70)
     * @param emaCrossoverEnabled        whether to evaluate EMA5/EMA21 crossovers
     * @param emaCrossoverMultiplier     confidence multiplier for log-scaled EMA spread ratio
     * @param emaMinSpreadBps            minimum spread (basis points) between EMA5 and EMA21 to trigger a crossover signal
     * @param volumeImbalanceEnabled     whether to evaluate volume imbalance
     * @param volumeImbalanceThreshold   minimum absolute imbalance to trigger a signal
     * @param volumeImbalanceMultiplier  confidence multiplier for volume imbalance ratio
     * @param defaultStopLossPct         fraction of entry price for stop-loss (e.g. 0.02 = 2%)
     * @param defaultTakeProfitPct        fraction of entry price for take-profit (e.g. 0.03 = 3%)
     * @param defaultQuantity             default trade quantity when model does not specify (e.g. 10 lots)
     */
    public record ThresholdConfig(
            String modelName,
            double rsiOversoldThreshold,
            double rsiOverboughtThreshold,
            boolean emaCrossoverEnabled,
            double emaCrossoverMultiplier,
            double emaMinSpreadBps,
            boolean volumeImbalanceEnabled,
            long volumeImbalanceThreshold,
            double volumeImbalanceMultiplier,
            double defaultStopLossPct,
            double defaultTakeProfitPct,
            int defaultQuantity
    ) {
        /** Default configuration suitable for most index stocks. */
        public static ThresholdConfig defaults(String modelName) {
            return new ThresholdConfig(
                    modelName,
                    30.0, 70.0,
                    true, 2.0, 5.0,
                    true, 500_000L, 1.5,
                    0.02, 0.03, 10
            );
        }
    }

    // ── Private helpers ──

    /**
     * Simple linear confidence: how far {@code value} is from {@code min}
     * within the range {@code [min, max]}, capped at 1.0.
     */
    private static double computeConfidence(double value, double min, double max) {
        if (max <= min) {
            return 0.5;
        }
        return Math.min(1.0, value / (max - min));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
