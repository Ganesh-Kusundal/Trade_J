package com.tradej.broker.dhan.options;

import com.tradej.core.domain.instrument.RollingExpiryKind;
import com.tradej.core.domain.instrument.RollingExpiryRoll;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class DhanRollingOptionWireMapperTest {

    @Test
    void encodesStrikeOffsetsAsAtmTokens() {
        assertEquals("ATM", DhanRollingOptionWireMapper.toDhanStrikeToken(StrikeOffset.atm()));
        assertEquals("ATM+3", DhanRollingOptionWireMapper.toDhanStrikeToken(new StrikeOffset(3)));
        assertEquals("ATM-2", DhanRollingOptionWireMapper.toDhanStrikeToken(new StrikeOffset(-2)));
    }

    @Test
    void mapsOptionTypeToResponseSide() {
        assertEquals("ce", DhanRollingOptionWireMapper.responseSideKey(OptionType.CALL));
        assertEquals("pe", DhanRollingOptionWireMapper.responseSideKey(OptionType.PUT));
    }

    @Test
    void roundTripsCanonicalExpiryRollToWireFields() {
        var roll = new RollingExpiryRoll(RollingExpiryKind.MONTH, 2);
        var key = new com.tradej.core.domain.instrument.RollingOptionSeriesKey(
                "NIFTY",
                com.tradej.core.domain.value.ExchangeSegment.IDX_I,
                roll,
                StrikeOffset.atm(),
                OptionType.PUT,
                5
        );
        var payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        DhanRollingOptionWireMapper.applySeries(payload, key);
        assertEquals("MONTH", payload.get("expiryFlag").asText());
        assertEquals(2, payload.get("expiryCode").asInt());
        assertEquals("ATM", payload.get("strike").asText());
        assertEquals("PUT", payload.get("drvOptionType").asText());
        assertEquals("5", payload.get("interval").asText());
    }
}
