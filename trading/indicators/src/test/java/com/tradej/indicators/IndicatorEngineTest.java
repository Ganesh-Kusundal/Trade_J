package com.tradej.indicators;

import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static Candle candle(long startMs, long open, long high, long low, long close, long volume) {
        return new Candle("SBIN", "1m", startMs, startMs + 59_999, open, high, low, close, volume, true);
    }
}
