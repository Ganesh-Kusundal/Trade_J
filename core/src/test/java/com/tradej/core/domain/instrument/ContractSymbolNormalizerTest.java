package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("unit")
class ContractSymbolNormalizerTest {

    @Test
    void normalizesSpacedBankNiftyOption() {
        assertEquals(
                "BANKNIFTY 30 JUN 30000 CALL",
                ContractSymbolNormalizer.normalize("BANKNIFTY 30 JUN 30000 CALL")
        );
        assertEquals(
                "BANKNIFTY 30 JUN 30000 PUT",
                ContractSymbolNormalizer.normalize("banknifty 30 jun 30000 put")
        );
    }

    @Test
    void normalizesCePeAliasesToCallPut() {
        assertEquals(
                "NIFTY 26 MAY 30750 CALL",
                ContractSymbolNormalizer.normalize("NIFTY 26 MAY 30750 CE")
        );
        assertEquals(
                "NIFTY 26 MAY 30750 PUT",
                ContractSymbolNormalizer.normalize("NIFTY 26 MAY 30750 PE")
        );
    }

    @Test
    void normalizesCompactOptionSymbol() {
        assertEquals(
                "NIFTY 26 MAY 30750 CALL",
                ContractSymbolNormalizer.normalize("NIFTY26MAY30750CE")
        );
    }

    @Test
    void buildsCanonicalFromExpiryAndStrike() {
        assertEquals(
                "BANKNIFTY 30 JUN 30000 CALL",
                ContractSymbolNormalizer.canonicalOption(
                        "BANKNIFTY",
                        LocalDate.of(2026, 6, 30),
                        3_000_000L,
                        OptionType.CALL
                )
        );
        assertEquals(
                "NIFTY 30 JUN FUT",
                ContractSymbolNormalizer.canonicalFuture("NIFTY", LocalDate.of(2026, 6, 30))
        );
    }

    @Test
    void parsesSpacedFuture() {
        ContractSymbolNormalizer.ParsedContract parsed = ContractSymbolNormalizer.parse("NIFTY 30 JUN FUT");
        assertNotNull(parsed);
        assertEquals("NIFTY", parsed.underlying());
        assertEquals(30, parsed.day());
        assertEquals("JUN", parsed.month());
        assertEquals(false, parsed.option());
    }

    @Test
    void equitySymbolPassesThrough() {
        assertEquals("SBIN", ContractSymbolNormalizer.normalize("sbin"));
    }
}
