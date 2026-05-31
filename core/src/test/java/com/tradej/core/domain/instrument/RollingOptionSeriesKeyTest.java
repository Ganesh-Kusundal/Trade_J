package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("unit")
class RollingOptionSeriesKeyTest {

    @Test
    void normalizesUnderlyingAndFingerprintsCanonicalFields() {
        RollingOptionSeriesKey key = new RollingOptionSeriesKey(
                "nifty",
                ExchangeSegment.IDX_I,
                new RollingExpiryRoll(RollingExpiryKind.WEEK, 1),
                StrikeOffset.atm(),
                OptionType.CALL,
                5
        );
        assertEquals("NIFTY", key.underlying());

        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2025, 1, 31);
        String fingerprint = key.fingerprint(from, to);
        assertEquals(64, fingerprint.length());

        RollingOptionSeriesKey same = new RollingOptionSeriesKey(
                "NIFTY",
                ExchangeSegment.IDX_I,
                new RollingExpiryRoll(RollingExpiryKind.WEEK, 1),
                StrikeOffset.atm(),
                OptionType.CALL,
                5
        );
        assertEquals(fingerprint, same.fingerprint(from, to));

        RollingOptionSeriesKey differentOffset = new RollingOptionSeriesKey(
                "NIFTY",
                ExchangeSegment.IDX_I,
                new RollingExpiryRoll(RollingExpiryKind.WEEK, 1),
                new StrikeOffset(1),
                OptionType.CALL,
                5
        );
        assertNotEquals(fingerprint, differentOffset.fingerprint(from, to));
    }
}
