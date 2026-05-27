package com.tradej.broker.api.port;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test that ALL InstrumentResolver implementations must pass.
 *
 * <p>This is a TDD RED test — it references methods that do not yet exist
 * on the {@link InstrumentResolver} interface ({@code resolveBySecurityId},
 * {@code isLoaded}, {@code catalogSize}). The test will NOT compile until
 * those methods are added (Phase A.1 GREEN).
 *
 * <p>Subclass this test for each concrete implementation:
 * <ul>
 *   <li>{@code InMemoryInstrumentResolverContractTest} in {@code trade-broker-dhan}</li>
 *   <li>{@code DhanInstrumentCatalogContractTest} in {@code trade-broker-dhan}</li>
 * </ul>
 */
@Tag("unit")
public abstract class InstrumentResolverContractTest {

    /** Subclass must provide a freshly-constructed implementation. */
    protected abstract InstrumentResolver createResolver();

    private InstrumentResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = createResolver();
    }

    // ── Security ID resolution ──────────────────────────────────────────

    @Test
    void unknownSecurityIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveBySecurityId("does-not-exist"));
    }

    @Test
    void resolvesBySecurityIdAfterLoading() {
        populateResolver(resolver);
        Instrument result = resolver.resolveBySecurityId("3045");
        assertNotNull(result, "resolveBySecurityId must return a non-null Instrument");
        assertEquals("SBIN", result.canonicalSymbol(),
                "Security ID 3045 should resolve to SBIN");
    }

    // ── Catalog state ───────────────────────────────────────────────────

    @Test
    void notLoadedBeforePopulation() {
        assertFalse(resolver.isLoaded(),
                "isLoaded() must be false before the catalog is populated");
    }

    @Test
    void loadedAfterPopulation() {
        populateResolver(resolver);
        assertTrue(resolver.isLoaded(),
                "isLoaded() must be true after the catalog is populated");
    }

    @Test
    void emptyBeforePopulation() {
        assertEquals(0, resolver.catalogSize(),
                "catalogSize() must be 0 before the catalog is populated");
    }

    @Test
    void reportsCatalogSizeAfterLoading() {
        populateResolver(resolver);
        assertTrue(resolver.catalogSize() > 0,
                "catalogSize() must report > 0 after population");
    }

    // ── Existing interface contract (should already pass) ────────────────

    @Test
    void allInstrumentsReturnsPopulatedList() {
        populateResolver(resolver);
        List<Instrument> all = resolver.allInstruments();
        assertNotNull(all);
        assertFalse(all.isEmpty(), "allInstruments() must return populated list after loading");
    }

    @Test
    void resolveReturnsInstrumentForKey() {
        populateResolver(resolver);
        Instrument instrument = resolver.resolve(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ));
        assertNotNull(instrument);
        assertEquals("SBIN", instrument.canonicalSymbol());
    }

    @Test
    void getBySymbolReturnsNullForUnknownSymbol() {
        assertNull(resolver.getBySymbol(new InstrumentKey("UNKNOWN", ExchangeSegment.NSE_EQ)),
                "getBySymbol must return null for unknown symbols");
    }

    // ── Helper for subclasses ───────────────────────────────────────────

    /**
     * Populate the resolver with test data containing at least one instrument
     * with security ID {@code "3045"} that resolves to {@code "SBIN"} on
     * {@link ExchangeSegment#NSE_EQ}.
     */
    protected abstract void populateResolver(InstrumentResolver resolver);
}
