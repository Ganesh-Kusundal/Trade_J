package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Trade;
import com.tradej.indicators.spi.IndicatorProvider;
import com.tradej.indicators.spi.IndicatorRegistry;
import com.tradej.indicators.spi.NonCandleIndicatorProvider;
import com.tradej.indicators.spi.NonCandleIndicatorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class IndicatorEngine {

    private static final Logger log = LoggerFactory.getLogger(IndicatorEngine.class);

    private final HalfTrend halfTrend;
    private final CVD cvd;
    private final BollingerSqueeze bollingerSqueeze;
    private final SwingHighLow swingHighLow;
    private final HighProbabilityOrderBlock orderBlock;
    private final NonCandleIndicatorRegistry nonCandleRegistry;
    private final IndicatorRegistry indicatorRegistry;

    public IndicatorEngine() {
        this(
                new HalfTrend(),
                new CVD(),
                new BollingerSqueeze(),
                new SwingHighLow(),
                new HighProbabilityOrderBlock(),
                IndicatorRegistry.discover(),
                NonCandleIndicatorRegistry.discover()
        );
    }

    public IndicatorEngine(HalfTrend halfTrend, CVD cvd, BollingerSqueeze bollingerSqueeze,
                           SwingHighLow swingHighLow, HighProbabilityOrderBlock orderBlock) {
        this(halfTrend, cvd, bollingerSqueeze, swingHighLow, orderBlock,
                IndicatorRegistry.discover(), NonCandleIndicatorRegistry.discover());
    }

    public IndicatorEngine(HalfTrend halfTrend, CVD cvd, BollingerSqueeze bollingerSqueeze,
                           SwingHighLow swingHighLow, HighProbabilityOrderBlock orderBlock,
                           NonCandleIndicatorRegistry nonCandleRegistry) {
        this(halfTrend, cvd, bollingerSqueeze, swingHighLow, orderBlock,
                IndicatorRegistry.discover(), nonCandleRegistry);
    }

    public IndicatorEngine(HalfTrend halfTrend, CVD cvd, BollingerSqueeze bollingerSqueeze,
                           SwingHighLow swingHighLow, HighProbabilityOrderBlock orderBlock,
                           IndicatorRegistry indicatorRegistry,
                           NonCandleIndicatorRegistry nonCandleRegistry) {
        this.halfTrend = halfTrend;
        this.cvd = cvd;
        this.bollingerSqueeze = bollingerSqueeze;
        this.swingHighLow = swingHighLow;
        this.orderBlock = orderBlock;
        this.indicatorRegistry = indicatorRegistry;
        this.nonCandleRegistry = nonCandleRegistry;
        logRegistryCoverage(indicatorRegistry);
    }

    /**
     * Logs how many of the five typed indicators used by {@link #enrich(List)}
     * are wired through {@link IndicatorRegistry}. When a provider is missing,
     * {@code enrich()} falls back to a local {@code new XxxIndicator()}
     * instance; the log line makes the wiring state visible at startup.
     */
    private static void logRegistryCoverage(IndicatorRegistry registry) {
        String[] names = {"halftrend", "cvd", "bollinger-squeeze", "swing-high-low", "order-block"};
        int present = 0;
        for (String n : names) {
            if (registry.get(n).isPresent()) {
                present++;
            }
        }
        log.info("IndicatorEngine: registry exposes {}/{} of the typed indicators used by enrich(); "
                + "missing providers fall back to local instantiation", present, names.length);
    }

    /**
     * Enriche a candle series with the five typed indicators that the
     * {@link IndicatorRegistry} SPI exposes via
     * {@link com.tradej.indicators.spi.IndicatorProvider#calculateTyped(List, Class)}.
     *
     * <p>Resolution order for each indicator:
     * <ol>
     *   <li>Look up the matching provider in {@link IndicatorRegistry}
     *       (e.g. {@code "halftrend"}, {@code "cvd"}, {@code "bollinger-squeeze"},
     *       {@code "swing-high-low"}, {@code "order-block"}). When present, the
     *       typed record list is obtained via {@code calculateTyped(candles, X.class)},
     *       so the full record (direction, volumeDelta, squeeze, bias, marker
     *       coordinates, …) is preserved end-to-end.</li>
     *   <li>Otherwise, fall back to a local {@code new XxxIndicator()} instance
     *       so the engine still produces a useful result when the registry is
     *       empty (e.g. in unit tests that pass a custom registry).</li>
     * </ol>
     */
    public IndicatorEngine.EnrichedChart enrich(List<Candle> candles) {
        return new IndicatorEngine.EnrichedChart(
                candles,
                resolveTyped(candles, "halftrend", HalfTrend.Point.class, halfTrend::calculate),
                resolveTyped(candles, "cvd", CVD.Point.class, cvd::calculate),
                resolveTyped(candles, "bollinger-squeeze", BollingerSqueeze.Point.class,
                        bollingerSqueeze::calculate),
                resolveTyped(candles, "swing-high-low", SwingHighLow.Marker.class,
                        swingHighLow::calculate),
                resolveTyped(candles, "order-block", HighProbabilityOrderBlock.Zone.class,
                        orderBlock::calculate)
        );
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> resolveTyped(List<Candle> candles,
                                     String providerName,
                                     Class<T> recordType,
                                     java.util.function.Function<List<Candle>, List<T>> fallback) {
        IndicatorProvider provider = indicatorRegistry.get(providerName).orElse(null);
        if (provider != null) {
            try {
                return provider.calculateTyped(candles, recordType);
            } catch (UnsupportedOperationException ignored) {
                // Provider did not implement calculateTyped; fall through to the
                // local instance so callers still receive a populated record.
            }
        }
        return fallback.apply(candles);
    }

    /**
     * Build a {@link VolumeProfile} from a stream of trades.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Look up the "volume-profile" provider in
     *       {@link NonCandleIndicatorRegistry}. If present, delegate to it.</li>
     *   <li>Otherwise, fall back to a local {@code new VolumeProfile(100)} so
     *       the engine still produces a useful result when the registry is
     *       empty (e.g. in unit tests that pass a custom registry).</li>
     * </ol>
     */
    public VolumeProfile volumeProfileFromTrades(List<Trade> trades) {
        NonCandleIndicatorProvider provider = nonCandleRegistry.get("volume-profile").orElse(null);
        if (provider != null) {
            return provider.computeFromTrades(trades);
        }
        VolumeProfile vp = new VolumeProfile(100L);
        for (Trade t : trades) {
            vp.addTrade(t.pricePaisa(), t.quantity());
        }
        return vp;
    }

    /**
     * Build a {@link TickLevelCVD.CvdSnapshot} from a stream of ticks
     * ({@code [pricePaisa, volume]} pairs).
     *
     * <p>Resolution order is the same as
     * {@link #volumeProfileFromTrades(List)}: prefer the registered
     * "tick-level-cvd" provider, fall back to a local
     * {@code new TickLevelCVD()} instance when the registry is empty.
     */
    public TickLevelCVD.CvdSnapshot cvdFromTicks(List<long[]> ticks) {
        NonCandleIndicatorProvider provider = nonCandleRegistry.get("tick-level-cvd").orElse(null);
        if (provider != null) {
            return provider.computeFromTicks(ticks);
        }
        TickLevelCVD cvdLocal = new TickLevelCVD();
        for (long[] tick : ticks) {
            cvdLocal.onTick(tick[0], tick[1]);
        }
        return cvdLocal.snapshot();
    }

    public record EnrichedChart(
            List<Candle> candles,
            List<HalfTrend.Point> halfTrend,
            List<CVD.Point> cvd,
            List<BollingerSqueeze.Point> bollingerSqueeze,
            List<SwingHighLow.Marker> markers,
            List<HighProbabilityOrderBlock.Zone> orderBlockZones
    ) {
    }
}
