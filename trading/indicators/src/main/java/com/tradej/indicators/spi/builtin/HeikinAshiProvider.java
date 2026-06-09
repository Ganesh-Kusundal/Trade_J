package com.tradej.indicators.spi.builtin;

import com.tradej.core.domain.model.Candle;
import com.tradej.indicators.spi.TransformationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Heikin Ashi candle transformation.
 *
 * <p>Heikin Ashi smooths price action by averaging OHLC values:
 * <ul>
 *   <li>HA Close = (O + H + L + C) / 4</li>
 *   <li>HA Open = (prev HA Open + prev HA Close) / 2</li>
 *   <li>HA High = max(H, HA Open, HA Close)</li>
 *   <li>HA Low = min(L, HA Open, HA Close)</li>
 * </ul>
 */
public final class HeikinAshiProvider implements TransformationProvider {

    @Override
    public String name() { return "heikin-ashi"; }

    @Override
    public String displayName() { return "Heikin Ashi"; }

    @Override
    public int minInputSize() { return 1; }

    @Override
    public List<Candle> transform(List<Candle> input, Map<String, Object> params) {
        if (input == null || input.isEmpty()) return List.of();

        List<Candle> result = new ArrayList<>(input.size());
        long prevHaOpen = 0;
        long prevHaClose = 0;

        for (int i = 0; i < input.size(); i++) {
            Candle c = input.get(i);
            long haClose = (c.openPaisa() + c.highPaisa() + c.lowPaisa() + c.closePaisa()) / 4;
            long haOpen;
            if (i == 0) {
                haOpen = (c.openPaisa() + c.closePaisa()) / 2;
            } else {
                haOpen = (prevHaOpen + prevHaClose) / 2;
            }
            long haHigh = Math.max(c.highPaisa(), Math.max(haOpen, haClose));
            long haLow = Math.min(c.lowPaisa(), Math.min(haOpen, haClose));

            result.add(new Candle(
                    c.symbol(), c.interval(), c.startTimeMs(), c.endTimeMs(),
                    haOpen, haHigh, haLow, haClose, c.volume(), true));

            prevHaOpen = haOpen;
            prevHaClose = haClose;
        }
        return result;
    }
}
