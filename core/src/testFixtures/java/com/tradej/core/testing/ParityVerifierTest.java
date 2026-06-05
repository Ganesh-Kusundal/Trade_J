package com.tradej.core.testing;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.DomainEventVisitor;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParityVerifierTest {

    private static final TestClock CLOCK = TestClock.fixed(1_000_000L);

    @Test
    void identicalEventsAreMatched() {
        var tick1 = createTick("AAPL", 100L, 1L);
        var tick2 = createTick("AAPL", 100L, 2L); // different metadata
        var result = ParityVerifier.compare(List.of(tick1), List.of(tick2));
        assertTrue(result.isMatch());
        assertEquals(1, result.comparedEvents());
    }

    @Test
    void differentEventTypesAreMismatched() {
        var tick = createTick("AAPL", 100L, 1L);
        var candle = createTick("AAPL", 200L, 1L);
        // Force different types by using different class
        var result = ParityVerifier.compare(
                List.of(tick),
                List.of(new DifferentEvent(EventMetadata.root()))
        );
        assertFalse(result.isMatch());
        assertTrue(result.message().contains("Type mismatch"));
    }

    @Test
    void differentSizesAreMismatched() {
        var tick = createTick("AAPL", 100L, 1L);
        var result = ParityVerifier.compare(List.of(tick), List.of(tick, tick));
        assertFalse(result.isMatch());
        assertTrue(result.message().contains("Size mismatch"));
    }

    @Test
    void emptyListsMatch() {
        var result = ParityVerifier.compare(List.of(), List.of());
        assertTrue(result.isMatch());
        assertEquals(0, result.comparedEvents());
    }

    @Test
    void differentFieldValuesAreMismatched() {
        var tick1 = createTick("AAPL", 100L, 1L);
        var tick2 = createTick("AAPL", 200L, 1L); // different ltp
        var result = ParityVerifier.compare(List.of(tick1), List.of(tick2));
        assertFalse(result.isMatch());
        assertTrue(result.message().contains("Content mismatch"));
    }

    @Test
    void differentSymbolsAreMismatched() {
        var tick1 = createTick("AAPL", 100L, 1L);
        var tick2 = createTick("GOOG", 100L, 2L);
        var result = ParityVerifier.compare(List.of(tick1), List.of(tick2));
        assertFalse(result.isMatch());
    }

    @Test
    void assertMatchPassesOnMatch() {
        var tick1 = createTick("AAPL", 100L, 1L);
        var tick2 = createTick("AAPL", 100L, 2L);
        var result = ParityVerifier.compare(List.of(tick1), List.of(tick2));
        result.assertMatch(); // should not throw
    }

    @Test
    void assertMatchFailsOnMismatch() {
        var tick1 = createTick("AAPL", 100L, 1L);
        var tick2 = createTick("AAPL", 200L, 2L);
        var result = ParityVerifier.compare(List.of(tick1), List.of(tick2));
        assertThrows(AssertionError.class, result::assertMatch);
    }

    @Test
    void multipleEventsAllMatch() {
        var live = List.of(
                createTick("AAPL", 100L, 1L),
                createTick("AAPL", 101L, 2L),
                createTick("AAPL", 102L, 3L)
        );
        var replay = List.of(
                createTick("AAPL", 100L, 10L),
                createTick("AAPL", 101L, 20L),
                createTick("AAPL", 102L, 30L)
        );
        var result = ParityVerifier.compare(live, replay);
        assertTrue(result.isMatch());
        assertEquals(3, result.comparedEvents());
    }

    @Test
    void eventsStructurallyEqualIgnoresMetadata() {
        var meta1 = new EventMetadata("id-1", 1000L, 100L, 0L, "corr-1", 1);
        var meta2 = new EventMetadata("id-2", 2000L, 200L, 0L, "corr-1", 1);
        var tick1 = new MarketTickEvent(meta1, 0L, "AAPL", ExchangeSegment.NSE_EQ,
                FeedMode.TICKER, 100L, 1L, 1L, 1000L, Optional.empty(), 0L, 0L);
        var tick2 = new MarketTickEvent(meta2, 0L, "AAPL", ExchangeSegment.NSE_EQ,
                FeedMode.TICKER, 100L, 1L, 1L, 2000L, Optional.empty(), 0L, 0L);
        assertTrue(ParityVerifier.eventsStructurallyEqual(tick1, tick2));
    }

    // -- helpers --

    private static MarketTickEvent createTick(String symbol, long ltp, long eventId) {
        return new MarketTickEvent(
                new EventMetadata("evt-" + eventId, CLOCK.instant().toEpochMilli(),
                        CLOCK.monotonicNanos(), 0L, "", 1),
                0L, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                ltp, 1L, 1L, CLOCK.instant().toEpochMilli(), Optional.empty(), 0L, 0L
        );
    }

    private record DifferentEvent(EventMetadata metadata) implements DomainEvent {
        @Override
        public void accept(DomainEventVisitor visitor) {
            // No-op for test event
        }
    }
}
