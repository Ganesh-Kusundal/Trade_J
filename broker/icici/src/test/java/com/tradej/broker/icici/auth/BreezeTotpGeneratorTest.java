package com.tradej.broker.icici.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class BreezeTotpGeneratorTest {
    private final BreezeTotpGenerator generator = new BreezeTotpGenerator();

    @Test
    void generatesSixDigitCode() {
        String code = generator.currentCode("NRUVAR3SGV3DSWCTJVCUUSKQOE");
        assertEquals(6, code.length());
        assertTrue(code.chars().allMatch(Character::isDigit));
    }

    @Test
    void codeIsStableWithinSameTimeStep() {
        Instant instant = Instant.parse("2026-05-31T10:00:00Z");
        String first = generator.codeAt("NRUVAR3SGV3DSWCTJVCUUSKQOE", instant);
        String second = generator.codeAt("NRUVAR3SGV3DSWCTJVCUUSKQOE", instant);
        assertEquals(first, second);
    }
}
