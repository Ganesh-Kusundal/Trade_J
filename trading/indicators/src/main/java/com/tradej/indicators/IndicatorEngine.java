package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;

import java.util.List;

public final class IndicatorEngine {

    private final HalfTrend halfTrend;
    private final CVD cvd;
    private final BollingerSqueeze bollingerSqueeze;
    private final SwingHighLow swingHighLow;
    private final HighProbabilityOrderBlock orderBlock;

    public IndicatorEngine() {
        this.halfTrend = new HalfTrend();
        this.cvd = new CVD();
        this.bollingerSqueeze = new BollingerSqueeze();
        this.swingHighLow = new SwingHighLow();
        this.orderBlock = new HighProbabilityOrderBlock();
    }

    public IndicatorEngine(HalfTrend halfTrend, CVD cvd, BollingerSqueeze bollingerSqueeze,
                           SwingHighLow swingHighLow, HighProbabilityOrderBlock orderBlock) {
        this.halfTrend = halfTrend;
        this.cvd = cvd;
        this.bollingerSqueeze = bollingerSqueeze;
        this.swingHighLow = swingHighLow;
        this.orderBlock = orderBlock;
    }

    public EnrichedChart enrich(List<Candle> candles) {
        return new EnrichedChart(
                candles,
                halfTrend.calculate(candles),
                cvd.calculate(candles),
                bollingerSqueeze.calculate(candles),
                swingHighLow.calculate(candles),
                orderBlock.calculate(candles)
        );
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
