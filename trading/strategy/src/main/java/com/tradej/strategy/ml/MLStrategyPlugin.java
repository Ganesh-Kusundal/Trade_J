package com.tradej.strategy.ml;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.FeatureVector;
import com.tradej.core.domain.model.InferenceResult;
import com.tradej.core.domain.port.FeatureStore;
import com.tradej.core.domain.port.MLInferenceEngine;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.api.StrategyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A {@link StrategyPlugin} and {@link GraphStrategyPlugin} adapter that bridges
 * the ML inference pipeline into the strategy execution sandbox.
 *
 * <p>On each {@link CandleClosed} event, this plugin:
 * <ol>
 *   <li>Retrieves a {@link FeatureVector} from the {@link FeatureStore}</li>
 *   <li>Runs it through the {@link MLInferenceEngine} to produce an {@link InferenceResult}</li>
 *   <li>Converts the result into a {@link SignalGenerated} for the downstream pipeline</li>
 * </ol>
 *
 * <p>Implements both {@link StrategyPlugin} (legacy candle-only) and
 * {@link GraphStrategyPlugin} (unified event dispatch) so it works in
 * both the old sandbox and the new graph sandbox.
 *
 * <p>This allows ML-driven strategies to participate in the same signal pipeline
 * as traditional rule-based strategies, with portfolio-level risk checks applied
 * uniformly by the {@code PortfolioEngine} and {@code PositionRiskHandler}.
 */
public final class MLStrategyPlugin implements StrategyPlugin, GraphStrategyPlugin {

    private static final Logger log = LoggerFactory.getLogger(MLStrategyPlugin.class);

    private final String name;
    private final FeatureStore featureStore;
    private final MLInferenceEngine inferenceEngine;
    private final String interval;
    private final int lookback;

    /**
     * @param name             unique plugin name
     * @param featureStore     feature store for retrieving feature vectors
     * @param inferenceEngine  ML inference engine to evaluate features
     * @param interval         candle interval to query (e.g. "5m", "1d")
     * @param lookback         number of historical candles for feature computation
     */
    public MLStrategyPlugin(
            String name,
            FeatureStore featureStore,
            MLInferenceEngine inferenceEngine,
            String interval,
            int lookback
    ) {
        this.name = name;
        this.featureStore = featureStore;
        this.inferenceEngine = inferenceEngine;
        this.interval = interval;
        this.lookback = lookback;
    }

    @Override
    public String name() {
        return name;
    }

    // ── Legacy StrategyPlugin (candle-only) ──

    @Override
    public Optional<SignalGenerated> onCandleClosed(CandleClosed event) {
        return evaluateCandle(event);
    }

    // ── GraphStrategyPlugin (unified dispatch) ──

    @Override
    public List<Class<? extends DomainEvent>> subscribedEventTypes() {
        return List.of(CandleClosed.class);
    }

    @Override
    public Optional<SignalGenerated> onEvent(DomainEvent event) {
        if (event instanceof CandleClosed candleClosed) {
            return evaluateCandle(candleClosed);
        }
        return Optional.empty();
    }

    /**
     * Core ML evaluation logic shared by both {@link #onCandleClosed}
     * and {@link #onEvent}.
     */
    private Optional<SignalGenerated> evaluateCandle(CandleClosed event) {
        // Only respond to candles at our configured interval to avoid running
        // ML inference on every tick-level candle close (e.g., 1s candles).
        if (!interval.equals(event.candle().interval())) {
            return Optional.empty();
        }

        String symbol = event.candle().symbol();

        // 1. Retrieve features for this symbol at the configured interval
        Optional<FeatureVector> featuresOpt = featureStore.getFeatures(symbol, interval, lookback);
        if (featuresOpt.isEmpty()) {
            log.trace("Insufficient data for ML inference symbol={} interval={}", symbol, interval);
            return Optional.empty();
        }
        FeatureVector features = featuresOpt.get();

        // 2. Run inference
        Optional<InferenceResult> resultOpt = inferenceEngine.evaluate(features);
        if (resultOpt.isEmpty()) {
            log.trace("No ML signal for symbol={} interval={}", symbol, interval);
            return Optional.empty();
        }
        InferenceResult result = resultOpt.get();

        // 3. Convert InferenceResult → SignalGenerated
        String signalId = UUID.randomUUID().toString();
        Map<String, Object> attrs = Map.of(
                "confidence", result.confidence(),
                "setup", result.setup(),
                "mlModel", name,
                "quantity", result.quantity()
        );
        SignalGenerated signal = new SignalGenerated(
                EventMetadata.correlated(event.correlationId(), event.sequenceId()),
                signalId,
                features.symbol(),
                features.interval(),
                result.side(),
                result.entryPricePaisa(),
                result.stopLossPaisa(),
                result.takeProfitPaisa(),
                result.setup(),
                Collections.unmodifiableMap(attrs)
        );

        log.info("ML signal generated plugin={} symbol={} side={} qty={} confidence={} setup={}",
                name, symbol, result.side(), attrs.get("quantity"), result.confidence(), result.setup());

        return Optional.of(signal);
    }
}
