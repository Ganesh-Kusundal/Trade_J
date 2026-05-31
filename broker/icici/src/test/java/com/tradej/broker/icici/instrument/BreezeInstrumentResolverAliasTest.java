package com.tradej.broker.icici.instrument;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("unit")
class BreezeInstrumentResolverAliasTest {

    private BreezeInstrumentResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        Path zip = Path.of("/tmp/SecurityMaster.zip");
        assumeTrue(Files.exists(zip), "Download SecurityMaster.zip to /tmp for resolver alias test");
        resolver = new BreezeInstrumentResolver(new BreezeInstrumentLoader());
        resolver.loadCatalog(zip);
    }

    @Test
    void resolvesRelianceAndRelindToSameCanonicalSymbol() {
        var reliance = resolver.resolveNormalized("RELIANCE", ExchangeSegment.NSE_EQ);
        var relind = resolver.resolveNormalized("RELIND", ExchangeSegment.NSE_EQ);
        assertEquals("RELIANCE", reliance.canonicalSymbol());
        assertEquals("RELIANCE", relind.canonicalSymbol());
        assertEquals(reliance.key(), relind.key());
    }

    @Test
    void resolveBySecurityIdReturnsCanonicalSymbol() {
        var instrument = resolver.resolveBySecurityId("2885");
        assertNotNull(instrument);
        assertEquals("RELIANCE", instrument.canonicalSymbol());
        assertEquals(new InstrumentKey("RELIANCE", ExchangeSegment.NSE_EQ), instrument.key());
    }
}
