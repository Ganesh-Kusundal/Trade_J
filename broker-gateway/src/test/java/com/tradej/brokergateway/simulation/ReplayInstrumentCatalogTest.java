package com.tradej.brokergateway.simulation;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class ReplayInstrumentCatalogTest {

    // ── activeInstruments() ───────────────────────────────────────────────

    @Test
    void activeInstrumentsReturnsAllWhenNoExpiryFilteringNeeded() {
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                equity("TCS"),
                equity("SBIN")
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> active = catalog.activeInstruments();
        assertEquals(3, active.size());
    }

    @Test
    void activeInstrumentsIncludesNonExpiredOptions() {
        LocalDate futureExpiry = LocalDate.now().plusMonths(3);
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                option("RELIANCE", futureExpiry, OptionType.CALL, 250_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> active = catalog.activeInstruments();
        assertEquals(2, active.size());
    }

    @Test
    void activeInstrumentsExcludesExpiredOptions() {
        LocalDate pastExpiry = LocalDate.of(2023, 1, 1);
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                option("RELIANCE", pastExpiry, OptionType.CALL, 250_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);
        catalog.advanceTo(LocalDate.of(2023, 6, 1));

        List<Instrument> active = catalog.activeInstruments();
        assertEquals(1, active.size());
        assertEquals("RELIANCE", active.get(0).symbol());
    }

    // ── advanceTo() ───────────────────────────────────────────────────────

    @Test
    void advanceToChangesCurrentDate() {
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(List.of(equity("RELIANCE")));
        LocalDate target = LocalDate.of(2024, 6, 15);

        catalog.advanceTo(target);
        assertEquals(target, catalog.currentDate());
    }

    @Test
    void advanceToMultipleTimesTracksLatestDate() {
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(List.of(equity("RELIANCE")));

        catalog.advanceTo(LocalDate.of(2024, 1, 1));
        catalog.advanceTo(LocalDate.of(2024, 6, 1));
        catalog.advanceTo(LocalDate.of(2024, 12, 31));

        assertEquals(LocalDate.of(2024, 12, 31), catalog.currentDate());
    }

    // ── Expired options filtered after advanceTo past expiry ──────────────

    @Test
    void expiredOptionsFilteredAfterAdvanceToPastExpiry() {
        LocalDate jan24Expiry = LocalDate.of(2024, 1, 25);
        LocalDate feb24Expiry = LocalDate.of(2024, 2, 22);

        List<Instrument> instruments = List.of(
                equity("NIFTY"),
                option("NIFTY", jan24Expiry, OptionType.CALL, 2000_000L),
                option("NIFTY", jan24Expiry, OptionType.PUT, 2000_000L),
                option("NIFTY", feb24Expiry, OptionType.CALL, 2100_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        // Before expiry: all active
        catalog.advanceTo(LocalDate.of(2024, 1, 10));
        assertEquals(4, catalog.activeCount());

        // After Jan expiry but before Feb expiry
        catalog.advanceTo(LocalDate.of(2024, 1, 26));
        assertEquals(2, catalog.activeCount());

        // After Feb expiry
        catalog.advanceTo(LocalDate.of(2024, 2, 23));
        assertEquals(1, catalog.activeCount());
        assertEquals("NIFTY", catalog.activeInstruments().get(0).symbol());
    }

    @Test
    void optionOnExpiryDateIsStillActive() {
        LocalDate expiry = LocalDate.of(2024, 3, 28);
        List<Instrument> instruments = List.of(
                option("NIFTY", expiry, OptionType.CALL, 2200_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        // On the expiry date itself: expiry.isBefore(date) is false, so still active
        catalog.advanceTo(expiry);
        assertEquals(1, catalog.activeCount());
    }

    @Test
    void optionDayAfterExpiryIsFiltered() {
        LocalDate expiry = LocalDate.of(2024, 3, 28);
        List<Instrument> instruments = List.of(
                option("NIFTY", expiry, OptionType.CALL, 2200_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        catalog.advanceTo(expiry.plusDays(1));
        assertEquals(0, catalog.activeCount());
    }

    // ── Non-option instruments (equities) are never filtered ──────────────

    @Test
    void equitiesAreNeverFiltered() {
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                equity("TCS"),
                equity("HDFCBANK")
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        catalog.advanceTo(LocalDate.of(2030, 12, 31));
        assertEquals(3, catalog.activeCount());
    }

    // ── findBySymbol() ────────────────────────────────────────────────────

    @Test
    void findBySymbolReturnsMatchingInstruments() {
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                equity("TCS"),
                equity("RELIANCE") // duplicate symbol, different construction
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> matches = catalog.findBySymbol("RELIANCE", ExchangeSegment.NSE_EQ);
        assertEquals(2, matches.size());
        assertTrue(matches.stream().allMatch(i -> i.symbol().equals("RELIANCE")));
    }

    @Test
    void findBySymbolIsCaseInsensitive() {
        List<Instrument> instruments = List.of(equity("reliance"));
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> matches = catalog.findBySymbol("RELIANCE", ExchangeSegment.NSE_EQ);
        assertEquals(1, matches.size());
    }

    @Test
    void findBySymbolReturnsEmptyForNoMatch() {
        List<Instrument> instruments = List.of(equity("RELIANCE"));
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> matches = catalog.findBySymbol("NONEXISTENT", ExchangeSegment.NSE_EQ);
        assertTrue(matches.isEmpty());
    }

    @Test
    void findBySymbolFiltersBySegment() {
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                fnoInstrument("RELIANCE", LocalDate.of(2030, 12, 25))
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        List<Instrument> eqMatches = catalog.findBySymbol("RELIANCE", ExchangeSegment.NSE_EQ);
        assertEquals(1, eqMatches.size());

        List<Instrument> fnoMatches = catalog.findBySymbol("RELIANCE", ExchangeSegment.NSE_FNO);
        assertEquals(1, fnoMatches.size());
    }

    @Test
    void findBySymbolExcludesExpiredInstruments() {
        LocalDate pastExpiry = LocalDate.of(2023, 1, 25);
        List<Instrument> instruments = List.of(
                equity("NIFTY"),
                option("NIFTY", pastExpiry, OptionType.CALL, 2000_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);
        catalog.advanceTo(LocalDate.of(2024, 1, 1));

        // findBySymbol uses activeInstruments(), which filters expired
        // The option with NSE_EQ segment check: option is NSE_FNO segment
        List<Instrument> eqMatches = catalog.findBySymbol("NIFTY", ExchangeSegment.NSE_EQ);
        assertEquals(1, eqMatches.size());
    }

    // ── activeCount() and totalCount() ────────────────────────────────────

    @Test
    void activeCountEqualsTotalWhenNothingExpired() {
        List<Instrument> instruments = List.of(equity("A"), equity("B"), equity("C"));
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        assertEquals(3, catalog.activeCount());
        assertEquals(3, catalog.totalCount());
    }

    @Test
    void totalCountIncludesExpiredInstruments() {
        LocalDate pastExpiry = LocalDate.of(2023, 6, 1);
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                option("RELIANCE", pastExpiry, OptionType.CALL, 250_000L),
                option("RELIANCE", pastExpiry, OptionType.PUT, 250_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);
        catalog.advanceTo(LocalDate.of(2024, 1, 1));

        assertEquals(1, catalog.activeCount());
        assertEquals(3, catalog.totalCount());
    }

    @Test
    void totalCountDoesNotChangeWithAdvanceTo() {
        List<Instrument> instruments = List.of(
                equity("RELIANCE"),
                option("RELIANCE", LocalDate.of(2024, 1, 25), OptionType.CALL, 250_000L)
        );
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(instruments);

        assertEquals(2, catalog.totalCount());

        catalog.advanceTo(LocalDate.of(2025, 1, 1));
        assertEquals(2, catalog.totalCount());
        assertEquals(1, catalog.activeCount());
    }

    @Test
    void emptyCatalogHasZeroCounts() {
        ReplayInstrumentCatalog catalog = new ReplayInstrumentCatalog(List.of());
        assertEquals(0, catalog.activeCount());
        assertEquals(0, catalog.totalCount());
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static Instrument equity(String symbol) {
        return new Instrument(
                symbol, symbol,
                Exchange.NSE, ExchangeSegment.NSE_EQ,
                "EQ", null, null, null, null, 1L, 5L);
    }

    private static Instrument option(String underlying, LocalDate expiry,
                                     OptionType optionType, long strikePricePaisa) {
        String type = optionType == OptionType.CALL ? "OPTCE" : "OPTPE";
        return new Instrument(
                underlying + " " + strikePricePaisa + " " + type,
                underlying + " " + strikePricePaisa + " " + type,
                Exchange.NSE, ExchangeSegment.NSE_FNO,
                type, underlying, expiry, strikePricePaisa, optionType, 50L, 5L);
    }

    private static Instrument fnoInstrument(String symbol, LocalDate expiry) {
        return new Instrument(
                symbol, symbol,
                Exchange.NSE, ExchangeSegment.NSE_FNO,
                "FUT", symbol, expiry, null, null, 50L, 5L);
    }
}
