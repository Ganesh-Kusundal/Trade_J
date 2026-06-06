package com.tradej.replay.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("component")
class CandleReplaySessionTest {

    @Test
    void stepAdvancesIndex() {
        CandleReplaySession replay = new CandleReplaySession(
                Optional.empty(),
                Optional.empty(),
                new ObjectMapper()
        );

        List<Candle> candles = List.of(
                new Candle("SBIN", "1m", 1000L, 2000L, 100L, 110L, 90L, 105L, 1000L, true),
                new Candle("SBIN", "1m", 2000L, 3000L, 105L, 115L, 100L, 110L, 1200L, true)
        );
        replay.start(candles);
        replay.step();

        CandleReplaySession.ReplayStatus status = replay.status();
        assertEquals(1, status.currentIndex());
        assertEquals(2, status.totalCandles());
        assertEquals("PAUSED", status.state());
    }
}
