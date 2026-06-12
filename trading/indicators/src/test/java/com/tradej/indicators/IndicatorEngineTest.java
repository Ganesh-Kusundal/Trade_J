package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.indicators.spi.IndicatorRegistry;
import com.tradej.indicators.spi.NonCandleIndicatorRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class IndicatorEngineTest {

    @Test
    void enrichesCandlesWithIndicators() {
        List<Candle> candles = List.of(
                candle(0, 10000, 10100, 9900, 10050, 1000),
                candle(60_000, 10050, 10200, 10000, 10150, 1200),
                candle(120_000, 10150, 10300, 10100, 10250, 900)
        );

        IndicatorEngine.EnrichedChart chart = new IndicatorEngine().enrich(candles);

        assertEquals(3, chart.halfTrend().size());
        assertEquals(3, chart.cvd().size());
        assertEquals(3, chart.bollingerSqueeze().size());
    }

    @Test
    void volumeProfileFromTradesUsesRegistry() {
        List<Trade> trades = List.of(
                trade(250000, 500),
                trade(250100, 300),
                trade(250000, 200));
        VolumeProfile vp = new IndicatorEngine().volumeProfileFromTrades(trades);
        assertNotNull(vp);
        assertEquals(1000L, vp.totalVolume());
        assertEquals(700L, vp.volumeAt(250000));
    }

    @Test
    void cvdFromTicksUsesRegistry() {
        List<long[]> ticks = List.of(
                new long[]{250000, 100},
                new long[]{250100, 50},
                new long[]{249900, 200});
        TickLevelCVD.CvdSnapshot snap = new IndicatorEngine().cvdFromTicks(ticks);
        assertNotNull(snap);
        assertEquals(150L, snap.buyVolume());
        assertEquals(200L, snap.sellVolume());
    }

    @Test
    void enrichesCandlesWithRegistryBackedEngine() {
        List<Candle> candles = twentyCandles();

        IndicatorEngine engine = new IndicatorEngine(
                new HalfTrend(),
                new CVD(),
                new BollingerSqueeze(),
                new SwingHighLow(),
                new HighProbabilityOrderBlock(),
                IndicatorRegistry.discover(),
                NonCandleIndicatorRegistry.discover()
        );

        IndicatorEngine.EnrichedChart chart = engine.enrich(candles);

        assertNotNull(chart);
        assertEquals(candles.size(), chart.candles().size());
        assertEquals(20, chart.halfTrend().size());
        assertEquals(20, chart.cvd().size());
        assertEquals(20, chart.bollingerSqueeze().size());
    }

    private static List<Candle> twentyCandles() {
        List<Candle> out = new ArrayList<>(20);
        long open = 10000;
        for (int i = 0; i < 20; i++) {
            long startMs = i * 60_000L;
            long close = open + (i % 2 == 0 ? 50 : -30);
            out.add(new Candle("SBIN", "1m", startMs, startMs + 59_999,
                    open, open + 100, open - 50, close, 1000 + i, true));
            open = close;
        }
        return out;
    }

    private static Candle candle(long startMs, long open, long high, long low, long close, long volume) {
        return new Candle("SBIN", "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }

    private static Trade trade(long pricePaisa, long quantity) {
        return new Trade("t-" + pricePaisa + "-" + quantity,
                "o-1", "SBIN", ExchangeSegment.NSE_EQ,
                Side.BUY, quantity, pricePaisa, 0L);
    }
}
