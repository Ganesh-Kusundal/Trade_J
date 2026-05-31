package com.tradej.historical.ingest.universe;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class Nifty500UniverseFetcherTest {

    @Test
    void parsesSymbolAndIndustryColumns() throws Exception {
        String csv = """
                Company Name,Industry,Symbol,Series,ISIN Code
                Tata Consultancy Services Ltd,Information Technology,TCS,EQ,INE467B01029
                State Bank of India,Financial Services,SBIN,EQ,INE062A01020
                """;
        List<Nifty500Constituent> constituents = Nifty500UniverseFetcher.parseCsv(csv, LocalDate.of(2026, 5, 30));
        assertEquals(2, constituents.size());
        assertEquals("TCS", constituents.get(0).symbol());
        assertEquals("Information Technology", constituents.get(0).industry());
        assertEquals("INE467B01029", constituents.get(0).isin());
    }

    @Test
    void dedupesRepeatedSymbols() throws Exception {
        String csv = """
                Company Name,Industry,Symbol,Series,ISIN Code
                First,Tech,TCS,EQ,INE1
                Second,Tech,TCS,EQ,INE2
                """;
        List<Nifty500Constituent> constituents = Nifty500UniverseFetcher.parseCsv(csv, LocalDate.of(2026, 5, 30));
        assertEquals(1, constituents.size());
        assertEquals("TCS", constituents.getFirst().symbol());
    }

    @Test
    void splitCsvLineHandlesQuotedCommas() {
        List<String> fields = Nifty500UniverseFetcher.splitCsvLine("\"Tata, Ltd\",Tech,TCS,EQ,INE1");
        assertEquals(5, fields.size());
        assertTrue(fields.getFirst().contains("Tata, Ltd"));
    }
}
