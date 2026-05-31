package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.core.domain.instrument.RollingOptionSeriesKey;
import com.tradej.core.domain.instrument.StrikeOffset;
import com.tradej.core.domain.value.OptionType;

public final class DhanRollingOptionWireMapper {

    private DhanRollingOptionWireMapper() {
    }

    static void applySeries(ObjectNode payload, RollingOptionSeriesKey series) {
        payload.put("expiryFlag", series.expiry().kind().name());
        payload.put("expiryCode", series.expiry().code());
        payload.put("strike", toDhanStrikeToken(series.strikeOffset()));
        payload.put("drvOptionType", series.optionType().name());
        payload.put("interval", Integer.toString(series.intervalMinutes()));
    }

    static String toDhanStrikeToken(StrikeOffset offset) {
        if (offset.value() == 0) {
            return "ATM";
        }
        if (offset.value() > 0) {
            return "ATM+" + offset.value();
        }
        return "ATM" + offset.value();
    }

    static String responseSideKey(OptionType optionType) {
        return optionType == OptionType.PUT ? "pe" : "ce";
    }
}
