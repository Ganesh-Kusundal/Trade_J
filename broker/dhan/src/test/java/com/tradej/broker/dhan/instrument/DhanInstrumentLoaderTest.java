package com.tradej.broker.dhan.instrument;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that the Dhan instrument loader applies {@link com.tradej.core.domain.instrument.IndexSymbols}
 * canonicalization when parsing INDEX rows, so the catalog indexes BOTH the broker-trading
 * alias (e.g. {@code BANKNIFTY}) and the canonical NSE name (e.g. {@code NIFTY BANK}).
 *
 * <p>This is the contract that lets the public broker-gateway API use canonical names
 * end-to-end: a caller using {@code Instruments.bankNifty()} and a caller using the
 * legacy {@code "BANKNIFTY"} alias both resolve to the same Dhan securityId.
 */
@Tag("unit")
class DhanInstrumentLoaderTest {

    @Test
    void bankNiftyIndexRowGetsCanonicalNseName() {
        // Dhan's sem_trading_symbol for NIFTY BANK index is the legacy broker alias "BANKNIFTY".
        // The loader must map it to the canonical NSE name "NIFTY BANK" for canonicalSymbol.
        List<String> lines = List.of(
                "sem_smst_security_id,sem_trading_symbol,sem_custom_symbol,sem_exm_exch_id,sem_segment,sem_instrument_name,sem_expiry_date,sem_strike_price,sem_option_type,sem_lot_units,sem_tick_size",
                "25,BANKNIFTY,,NSE,IDX_I,INDEX,,,0,1,5"
        );
        DhanInstrumentLoader loader = new DhanInstrumentLoader();

        List<DhanInstrumentDefinition> definitions = loader.parse(lines);

        assertEquals(1, definitions.size());
        DhanInstrumentDefinition bankNifty = definitions.getFirst();
        assertEquals("BANKNIFTY", bankNifty.symbol(),
                "Raw broker symbol must be preserved as Dhan's internal identifier");
        assertEquals("NIFTY BANK", bankNifty.canonicalSymbol(),
                "Canonical NSE name must be derived from the broker alias for INDEX rows");
    }

    @Test
    void niftyIndexRowKeepsCanonicalNseName() {
        // Dhan's sem_trading_symbol for the NIFTY 50 index is "NIFTY" (already canonical).
        List<String> lines = List.of(
                "sem_smst_security_id,sem_trading_symbol,sem_custom_symbol,sem_exm_exch_id,sem_segment,sem_instrument_name,sem_expiry_date,sem_strike_price,sem_option_type,sem_lot_units,sem_tick_size",
                "13,NIFTY,,NSE,IDX_I,INDEX,,,0,1,5"
        );
        DhanInstrumentLoader loader = new DhanInstrumentLoader();

        List<DhanInstrumentDefinition> definitions = loader.parse(lines);

        assertEquals(1, definitions.size());
        DhanInstrumentDefinition nifty = definitions.getFirst();
        assertEquals("NIFTY", nifty.symbol());
        assertEquals("NIFTY", nifty.canonicalSymbol(),
                "Already-canonical NSE name must pass through unchanged");
    }

    @Test
    void finNiftyAndMidcpNiftyIndexRowsGetCanonicalNames() {
        // FINNIFTY → NIFTY FIN SERVICE
        // MIDCPNIFTY → NIFTY MID SELECT
        List<String> lines = List.of(
                "sem_smst_security_id,sem_trading_symbol,sem_custom_symbol,sem_exm_exch_id,sem_segment,sem_instrument_name,sem_expiry_date,sem_strike_price,sem_option_type,sem_lot_units,sem_tick_size",
                "27,FINNIFTY,,NSE,IDX_I,INDEX,,,0,1,5",
                "51,MIDCPNIFTY,,NSE,IDX_I,INDEX,,,0,1,5"
        );
        DhanInstrumentLoader loader = new DhanInstrumentLoader();

        List<DhanInstrumentDefinition> definitions = loader.parse(lines);

        assertEquals(2, definitions.size());
        DhanInstrumentDefinition fin = findBySymbol(definitions, "FINNIFTY");
        DhanInstrumentDefinition midcp = findBySymbol(definitions, "MIDCPNIFTY");
        assertEquals("NIFTY FIN SERVICE", fin.canonicalSymbol());
        assertEquals("NIFTY MID SELECT", midcp.canonicalSymbol());
    }

    @Test
    void equityRowIsUnchangedByIndexCanonicalization() {
        // EQUITY rows should NOT be touched by IndexSymbols — they pass through verbatim.
        List<String> lines = List.of(
                "sem_smst_security_id,sem_trading_symbol,sem_custom_symbol,sem_exm_exch_id,sem_segment,sem_instrument_name,sem_expiry_date,sem_strike_price,sem_option_type,sem_lot_units,sem_tick_size",
                "3045,SBIN,,NSE,NSE_EQ,EQUITY,,,0,1,5"
        );
        DhanInstrumentLoader loader = new DhanInstrumentLoader();

        List<DhanInstrumentDefinition> definitions = loader.parse(lines);

        assertEquals(1, definitions.size());
        DhanInstrumentDefinition sbin = definitions.getFirst();
        assertEquals("SBIN", sbin.symbol());
        assertEquals("SBIN", sbin.canonicalSymbol(),
                "Equity rows should not be touched by the index canonicalization");
    }

    private static DhanInstrumentDefinition findBySymbol(List<DhanInstrumentDefinition> defs, String symbol) {
        return defs.stream()
                .filter(d -> symbol.equals(d.symbol()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No definition found for symbol " + symbol));
    }

    @Test
    void catalogIndexesBothBrokerAliasAndCanonicalName() {
        // The catalog indexes by both definition.symbol() and definition.canonicalSymbol().
        // After loader canonicalization, the same Dhan securityId should be reachable via
        // either "BANKNIFTY" (broker alias) or "NIFTY BANK" (canonical NSE name).
        List<String> lines = List.of(
                "sem_smst_security_id,sem_trading_symbol,sem_custom_symbol,sem_exm_exch_id,sem_segment,sem_instrument_name,sem_expiry_date,sem_strike_price,sem_option_type,sem_lot_units,sem_tick_size",
                "25,BANKNIFTY,,NSE,IDX_I,INDEX,,,0,1,5"
        );
        DhanInstrumentLoader loader = new DhanInstrumentLoader();
        List<DhanInstrumentDefinition> definitions = loader.parse(lines);

        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.replaceDefinitions(definitions);

        // Both lookup keys must resolve to the same securityId.
        assertNotNull(catalog.getBySymbol(
                new com.tradej.core.domain.model.InstrumentKey("BANKNIFTY", com.tradej.core.domain.value.ExchangeSegment.IDX_I)),
                "Broker alias BANKNIFTY must resolve to the catalog entry");
        assertNotNull(catalog.getBySymbol(
                new com.tradej.core.domain.model.InstrumentKey("NIFTY BANK", com.tradej.core.domain.value.ExchangeSegment.IDX_I)),
                "Canonical NIFTY BANK must resolve to the same catalog entry");

        // Both must point to the same securityId.
        var viaAlias = catalog.getBySymbol(
                new com.tradej.core.domain.model.InstrumentKey("BANKNIFTY", com.tradej.core.domain.value.ExchangeSegment.IDX_I));
        var viaCanonical = catalog.getBySymbol(
                new com.tradej.core.domain.model.InstrumentKey("NIFTY BANK", com.tradej.core.domain.value.ExchangeSegment.IDX_I));
        assertEquals(viaAlias.canonicalSymbol(), viaCanonical.canonicalSymbol());
    }
}
