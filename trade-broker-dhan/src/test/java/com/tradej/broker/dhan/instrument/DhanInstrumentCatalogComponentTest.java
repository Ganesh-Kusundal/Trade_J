package com.tradej.broker.dhan.instrument;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("component")
class DhanInstrumentCatalogComponentTest {
    @Test
    void resolvesCanonicalEquityInstrumentFromCatalog() throws Exception {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.load(writeCatalogSlice(line -> line.contains("NSE,E,3045,EQUITY,0,SBIN,")));

        Instrument instrument = catalog.resolve(new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ));

        assertEquals("SBIN", instrument.canonicalSymbol());
        assertEquals(Exchange.NSE, instrument.exchange());
        assertEquals(ExchangeSegment.NSE_EQ, instrument.exchangeSegment());
        assertEquals(1_000L, instrument.tickSizePaisa());
    }

    @Test
    void resolvesNfoFutureByUnderlyingAlias() throws Exception {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.load(writeCatalogSlice(line -> line.contains("NSE,D,62329,FUTIDX,0,NIFTY-Jun2026-FUT,")
                || line.contains("NSE,D,61093,FUTIDX,0,NIFTY-Jul2026-FUT,")));

        Instrument instrument = catalog.resolve(new InstrumentKey("NIFTY", ExchangeSegment.NSE_FNO));

        assertEquals("NIFTY 30 JUN FUT", instrument.canonicalSymbol());
        assertEquals(Exchange.NFO, instrument.exchange());
        assertEquals(ExchangeSegment.NSE_FNO, instrument.exchangeSegment());
    }

    @Test
    void resolvesOptionViaCompactAndCeAliases() throws Exception {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.load(writeCatalogSlice(line -> line.contains("NSE,D,35038,OPTIDX,0,NIFTY-May2026-30750-CE,")
                || line.contains("NSE,D,35039,OPTIDX,0,NIFTY-May2026-30750-PE,")));

        Instrument compact = catalog.resolve(new InstrumentKey("NIFTYMAY202630750CE", ExchangeSegment.NSE_FNO));
        Instrument spaced = catalog.resolve(new InstrumentKey("NIFTY 26 MAY 30750 CE", ExchangeSegment.NSE_FNO));

        assertEquals("NIFTY 26 MAY 30750 CALL", compact.canonicalSymbol());
        assertEquals("NIFTY 26 MAY 30750 CALL", spaced.canonicalSymbol());
        assertEquals(Exchange.NFO, compact.exchange());
        assertNotNull(compact.optionType());
    }

    @Test
    void resolvesNearestMcxContractExplicitly() throws Exception {
        DhanInstrumentCatalog catalog = new DhanInstrumentCatalog();
        catalog.load(writeCatalogSlice(line -> line.contains("MCX,M,499095,FUTCOM,0,CRUDEOIL-18Jun2026-FUT,")
                || line.contains("MCX,M,520702,FUTCOM,0,CRUDEOIL-20Jul2026-FUT,")));

        Instrument instrument = catalog.resolve(new InstrumentKey("CRUDEOIL", ExchangeSegment.MCX_COMM));

        assertEquals("CRUDEOIL 18 JUN FUT", instrument.canonicalSymbol());
        assertEquals(Exchange.MCX, instrument.exchange());
    }

    private Path writeCatalogSlice(Predicate<String> selector) throws Exception {
        Path source = masterCatalog();
        Path file = Files.createTempFile("dhan-catalog", ".csv");
        try (Stream<String> lines = Files.lines(source)) {
            List<String> selected = lines.filter(line -> line.startsWith("SEM_") || selector.test(line)).toList();
            Files.write(file, selected);
        }
        return file;
    }

    private Path masterCatalog() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("brokers/data/instruments/instruments_2026-05-26.csv");
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate brokers/data/instruments/instruments_2026-05-26.csv");
    }
}
