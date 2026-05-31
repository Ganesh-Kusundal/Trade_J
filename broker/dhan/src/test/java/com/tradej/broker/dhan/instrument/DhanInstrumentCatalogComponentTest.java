package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("component")
class DhanInstrumentCatalogComponentTest {
    @Test
    void resolvesCanonicalEquityInstrumentFromCatalog() {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.replaceAll(List.of(equity("SBIN", "3045", 1_000L)));

        Instrument instrument = catalog.resolve(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ));

        assertEquals("SBIN", instrument.canonicalSymbol());
        assertEquals(Exchange.NSE, instrument.exchange());
        assertEquals(ExchangeSegment.NSE_EQ, instrument.exchangeSegment());
        assertEquals(1_000L, instrument.tickSizePaisa());
    }

    @Test
    void resolvesNfoFutureByUnderlyingAlias() {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.replaceAll(List.of(
                future("NIFTY-Jun2026-FUT", "NIFTY", LocalDate.of(2026, 6, 30),
                        Exchange.NFO, ExchangeSegment.NSE_FNO, "62329"),
                future("NIFTY-Jul2026-FUT", "NIFTY", LocalDate.of(2026, 7, 31),
                        Exchange.NFO, ExchangeSegment.NSE_FNO, "61093")
        ));

        Instrument instrument = catalog.resolve(new InstrumentKey("NIFTY", ExchangeSegment.NSE_FNO));

        assertEquals("NIFTY 30 JUN FUT", instrument.canonicalSymbol());
        assertEquals(Exchange.NFO, instrument.exchange());
        assertEquals(ExchangeSegment.NSE_FNO, instrument.exchangeSegment());
    }

    @Test
    void resolvesOptionViaCompactAndCeAliases() {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        LocalDate expiry = LocalDate.of(2026, 5, 26);
        long strikePaisa = 3_075_000L;
        catalog.replaceAll(List.of(
                option("NIFTY-May2026-30750-CE", "NIFTY", expiry, strikePaisa,
                        OptionType.CALL, Exchange.NFO, ExchangeSegment.NSE_FNO, "35038"),
                option("NIFTY-May2026-30750-PE", "NIFTY", expiry, strikePaisa,
                        OptionType.PUT, Exchange.NFO, ExchangeSegment.NSE_FNO, "35039")
        ));

        Instrument compact = catalog.resolve(new InstrumentKey("NIFTYMAY202630750CE", ExchangeSegment.NSE_FNO));
        Instrument spaced = catalog.resolve(new InstrumentKey("NIFTY 26 MAY 30750 CE", ExchangeSegment.NSE_FNO));

        assertEquals("NIFTY 26 MAY 30750 CALL", compact.canonicalSymbol());
        assertEquals("NIFTY 26 MAY 30750 CALL", spaced.canonicalSymbol());
        assertEquals(Exchange.NFO, compact.exchange());
        assertNotNull(compact.optionType());
    }

    @Test
    void resolvesNearestMcxContractExplicitly() {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.replaceAll(List.of(
                future("CRUDEOIL-18Jun2026-FUT", "CRUDEOIL", LocalDate.of(2026, 6, 18),
                        Exchange.MCX, ExchangeSegment.MCX_COMM, "499095"),
                future("CRUDEOIL-20Jul2026-FUT", "CRUDEOIL", LocalDate.of(2026, 7, 20),
                        Exchange.MCX, ExchangeSegment.MCX_COMM, "520702")
        ));

        Instrument instrument = catalog.resolve(new InstrumentKey("CRUDEOIL", ExchangeSegment.MCX_COMM));

        assertEquals("CRUDEOIL 18 JUN FUT", instrument.canonicalSymbol());
        assertEquals(Exchange.MCX, instrument.exchange());
    }

    private static DhanInstrumentDefinition equity(String symbol, String securityId, long tickSizePaisa) {
        return new DhanInstrumentDefinition(
                symbol,
                symbol,
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                securityId,
                "EQUITY",
                symbol,
                null,
                null,
                OptionType.UNKNOWN,
                1L,
                tickSizePaisa,
                null
        );
    }

    private static DhanInstrumentDefinition future(
            String tradingSymbol,
            String underlying,
            LocalDate expiry,
            Exchange exchange,
            ExchangeSegment segment,
            String securityId
    ) {
        String canonical = DhanSymbolNormalizer.canonicalFutureSymbol(underlying, expiry);
        return new DhanInstrumentDefinition(
                tradingSymbol,
                canonical,
                exchange,
                segment,
                securityId,
                "FUTIDX",
                underlying,
                expiry,
                null,
                OptionType.UNKNOWN,
                1L,
                50L,
                null
        );
    }

    private static DhanInstrumentDefinition option(
            String tradingSymbol,
            String underlying,
            LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType,
            Exchange exchange,
            ExchangeSegment segment,
            String securityId
    ) {
        String canonical = DhanSymbolNormalizer.canonicalOptionSymbol(underlying, expiry, strikePricePaisa, optionType);
        return new DhanInstrumentDefinition(
                tradingSymbol,
                canonical,
                exchange,
                segment,
                securityId,
                "OPTIDX",
                underlying,
                expiry,
                strikePricePaisa,
                optionType,
                1L,
                50L,
                null
        );
    }
}
