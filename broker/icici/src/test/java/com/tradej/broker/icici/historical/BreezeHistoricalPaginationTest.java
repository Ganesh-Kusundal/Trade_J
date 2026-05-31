package com.tradej.broker.icici.historical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class BreezeHistoricalPaginationTest {

    @Test
    void parsesHistoricalCandlesFromArrayPayload() throws Exception {
        ArrayNode array = new ObjectMapper().createArrayNode();
        array.addObject()
                .put("datetime", "2026-05-29 09:15:00")
                .put("open", 1400.0)
                .put("high", 1405.0)
                .put("low", 1399.0)
                .put("close", 1402.0)
                .put("volume", 1000);
        Instrument instrument = new Instrument(
                "RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", "RELIANCE", null, null, OptionType.UNKNOWN, 1L, 1L
        );
        var candles = BreezeHistoricalDataService.parseCandles(array, instrument, "1m");
        assertEquals(1, candles.size());
        assertEquals("RELIANCE", candles.getFirst().symbol());
        assertEquals(140_000L, candles.getFirst().openPaisa());
    }

    @Test
    void rejectsMissingDatetime() {
        ArrayNode array = new ObjectMapper().createArrayNode();
        array.addObject().put("open", 1400.0);
        Instrument instrument = new Instrument(
                "RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", "RELIANCE", null, null, OptionType.UNKNOWN, 1L, 1L
        );
        assertThrows(IllegalArgumentException.class,
                () -> BreezeHistoricalDataService.parseCandles(array, instrument, "1m"));
    }
}
