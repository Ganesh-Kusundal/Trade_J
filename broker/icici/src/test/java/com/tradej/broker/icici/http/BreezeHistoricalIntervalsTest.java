package com.tradej.broker.icici.http;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class BreezeHistoricalIntervalsTest {

    @Test
    void mapsTradeJIntervalsToBreezeV1ApiValues() {
        assertEquals("minute", BreezeHistoricalIntervals.toApiInterval("1m"));
        assertEquals("minute", BreezeHistoricalIntervals.toApiInterval("1minute"));
        assertEquals("5minute", BreezeHistoricalIntervals.toApiInterval("5m"));
        assertEquals("30minute", BreezeHistoricalIntervals.toApiInterval("30minute"));
        assertEquals("day", BreezeHistoricalIntervals.toApiInterval("1d"));
        assertEquals("day", BreezeHistoricalIntervals.toApiInterval("1day"));
        assertEquals("1second", BreezeHistoricalIntervals.toApiInterval("1s"));
        assertEquals("1second", BreezeHistoricalIntervals.toApiInterval("1second"));
    }

    @Test
    void mapsTradeJIntervalsToBreezeV2ApiValues() {
        assertEquals("1second", BreezeHistoricalIntervals.toV2ApiInterval("1s"));
        assertEquals("1minute", BreezeHistoricalIntervals.toV2ApiInterval("1m"));
        assertEquals("5minute", BreezeHistoricalIntervals.toV2ApiInterval("5m"));
        assertEquals("1day", BreezeHistoricalIntervals.toV2ApiInterval("1d"));
    }
}
