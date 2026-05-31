package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class DhanRollingOptionMapperTest {

    private final DhanRollingOptionMapper mapper = new DhanRollingOptionMapper();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsAllFieldsFromNestedCePayload() throws Exception {
        var payload = json.readTree("""
                {
                  "data": {
                    "ce": {
                      "open": [100.5, 101.0],
                      "high": [102.0, 103.0],
                      "low": [99.5, 100.0],
                      "close": [101.5, 102.5],
                      "volume": [1000, 1200],
                      "iv": [18.5, 19.0],
                      "oi": [50000, 51000],
                      "spot": [22000.0, 22010.0],
                      "strike": [22000.0, 22000.0],
                      "timestamp": [1704067200, 1704067500]
                    },
                    "pe": null
                  }
                }
                """);
        var seriesKey = new RollingOptionSeriesKey(
                "NIFTY",
                ExchangeSegment.IDX_I,
                new RollingExpiryRoll(RollingExpiryKind.MONTH, 1),
                StrikeOffset.atm(),
                OptionType.CALL,
                5
        );
        var request = new RollingOptionHistoryRequest(
                seriesKey,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31)
        );

        var bars = mapper.toBars(new DhanJsonResponse(payload), request);
        assertEquals(2, bars.size());
        assertEquals(1_704_067_200_000L, bars.get(0).timestampMs());
        assertEquals(10_050L, bars.get(0).openPaisa());
        assertEquals(10_150L, bars.get(0).closePaisa());
        assertEquals(1000L, bars.get(0).volume());
        assertEquals(18.5, bars.get(0).iv());
        assertEquals(50_000L, bars.get(0).oi());
        assertEquals(2_200_000L, bars.get(0).spotPaisa());
        assertFalse(bars.get(1).timestampMs() <= bars.get(0).timestampMs());
    }
}
