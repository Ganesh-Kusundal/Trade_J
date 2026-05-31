package com.tradej.broker.dhan.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class DhanTotpGeneratorUnitTest {
    @Test
    void generatesExpectedCodeForKnownRfcVectorAtSixDigits() {
        DhanTotpGenerator generator = new DhanTotpGenerator();

        String code = generator.codeAt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Instant.ofEpochSecond(59));

        assertEquals("287082", code);
    }
}
