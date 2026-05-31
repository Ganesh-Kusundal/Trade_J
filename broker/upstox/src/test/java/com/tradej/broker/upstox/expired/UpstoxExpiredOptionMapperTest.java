package com.tradej.broker.upstox.expired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.model.ExpiredOptionBar;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class UpstoxExpiredOptionMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final UpstoxExpiredOptionMapper mapper = new UpstoxExpiredOptionMapper();

    @Test
    void mapsExpiriesFromResponseArray() throws Exception {
        var root = MAPPER.readTree("""
                {"status":"success","data":["2024-11-27","2024-12-05"]}
                """);
        List<LocalDate> expiries = mapper.mapExpiries(root);
        assertEquals(List.of(LocalDate.of(2024, 11, 27), LocalDate.of(2024, 12, 5)), expiries);
    }

    @Test
    void mapsContractsFromResponse() throws Exception {
        var root = MAPPER.readTree("""
                {
                  "status": "success",
                  "data": [
                    {
                      "instrument_key": "NSE_FO|47983|17-04-2025",
                      "instrument_type": "CE",
                      "strike_price": 22500.0,
                      "underlying_symbol": "NIFTY"
                    },
                    {
                      "instrument_key": "NSE_FO|47984|17-04-2025",
                      "instrument_type": "PE",
                      "strike_price": 22500.0,
                      "underlying_symbol": "NIFTY"
                    }
                  ]
                }
                """);
        LocalDate expiry = LocalDate.of(2025, 4, 17);
        List<ExpiredOptionContractKey> contracts = mapper.mapContracts(root, "NIFTY", ExchangeSegment.IDX_I, expiry);
        assertEquals(2, contracts.size());
        ExpiredOptionContractKey call = contracts.getFirst();
        assertEquals("NIFTY", call.underlying());
        assertEquals(ExchangeSegment.IDX_I, call.segment());
        assertEquals(expiry, call.expiry());
        assertEquals(2_250_000L, call.strikePaisa());
        assertEquals(OptionType.CALL, call.optionType());
        assertEquals("NSE_FO|47983|17-04-2025", call.brokerInstrumentKey());
    }

    @Test
    void mapsCandlesFromUpstoxArrayFormat() throws Exception {
        var root = MAPPER.readTree("""
                {
                  "status": "success",
                  "data": {
                    "candles": [
                      ["2024-11-27T09:15:00+05:30", 120.5, 125.0, 119.0, 123.25, 1500, 42000]
                    ]
                  }
                }
                """);
        List<ExpiredOptionBar> bars = mapper.mapCandles(root);
        assertFalse(bars.isEmpty());
        ExpiredOptionBar bar = bars.getFirst();
        assertEquals(12_050L, bar.openPaisa());
        assertEquals(12_500L, bar.highPaisa());
        assertEquals(11_900L, bar.lowPaisa());
        assertEquals(12_325L, bar.closePaisa());
        assertEquals(1500L, bar.volume());
        assertEquals(42_000L, bar.oi());
    }
}
