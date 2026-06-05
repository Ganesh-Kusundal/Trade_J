package com.tradej.core.testing;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventContractAssertionsTest {

    private static final TestClock CLOCK = TestClock.fixed(Instant.parse("2026-05-27T10:00:00Z"));

    @Test
    void validEventPassesAllAssertions() {
        var event = createValidTick();
        assertDoesNotThrow(() -> EventContractAssertions.assertHasRequiredFields(event));
        assertDoesNotThrow(() -> EventContractAssertions.assertHasEventId(event));
        assertDoesNotThrow(() -> EventContractAssertions.assertHasTimestamp(event));
        assertDoesNotThrow(() -> EventContractAssertions.assertHasCorrelationId(event));
    }

    @Test
    void eventWithEmptyIdFails() {
        var event = createTickWithId("");
        assertThrows(AssertionError.class,
                () -> EventContractAssertions.assertHasRequiredFields(event));
    }

    @Test
    void eventWithZeroTimestampFails() {
        var meta = new EventMetadata("id", 0L, 0L, 0L, "", 1);
        var event = new MarketTickEvent(meta, 0L, "AAPL", ExchangeSegment.NSE_EQ,
                FeedMode.TICKER, 100L, 1L, 1L, 0L, Optional.empty(), 0L, 0L);
        assertThrows(AssertionError.class,
                () -> EventContractAssertions.assertHasTimestamp(event));
    }

    @Test
    void allHaveRequiredFieldsPassesForValidEvents() {
        List<DomainEvent> events = List.of(createValidTick(), createValidTick());
        assertDoesNotThrow(() -> EventContractAssertions.assertAllHaveRequiredFields(events));
    }

    @Test
    void causationChainPassesForSequentialEvents() {
        var events = List.of(createValidTick(), createValidTick(), createValidTick());
        assertDoesNotThrow(() -> EventContractAssertions.assertCausationChain(events));
    }

    @Test
    void timestampOrderPassesForOrderedEvents() {
        var tick1 = createTickAtMs(1000L);
        var tick2 = createTickAtMs(2000L);
        var tick3 = createTickAtMs(3000L);
        assertDoesNotThrow(() ->
                EventContractAssertions.assertTimestampOrder(List.of(tick1, tick2, tick3)));
    }

    @Test
    void timestampOrderFailsForUnorderedEvents() {
        var tick1 = createTickAtMs(3000L);
        var tick2 = createTickAtMs(1000L);
        assertThrows(AssertionError.class,
                () -> EventContractAssertions.assertTimestampOrder(List.of(tick1, tick2)));
    }

    @Test
    void sequenceOrderPassesForOrderedEvents() {
        var tick1 = createTickWithSeq(1L);
        var tick2 = createTickWithSeq(2L);
        var tick3 = createTickWithSeq(3L);
        assertDoesNotThrow(() ->
                EventContractAssertions.assertSequenceOrder(List.of(tick1, tick2, tick3)));
    }

    @Test
    void sequenceOrderFailsForUnorderedEvents() {
        var tick1 = createTickWithSeq(3L);
        var tick2 = createTickWithSeq(1L);
        assertThrows(AssertionError.class,
                () -> EventContractAssertions.assertSequenceOrder(List.of(tick1, tick2)));
    }

    @Test
    void eventMetadataDefaultsUnsetVersionToOne() {
        var meta = new EventMetadata("id", 1000L, 0L, 0L, "", 0);
        assertEquals(1, meta.schemaVersion());
    }

    // -- helpers --

    private static MarketTickEvent createValidTick() {
        return new MarketTickEvent(
                new EventMetadata("test-1", CLOCK.instant().toEpochMilli(),
                        CLOCK.monotonicNanos(), 0L, "corr", 1),
                0L, "AAPL", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                100L, 1L, 1L, CLOCK.instant().toEpochMilli(), Optional.empty(), 0L, 0L
        );
    }

    private static MarketTickEvent createTickWithId(String id) {
        return new MarketTickEvent(
                new EventMetadata(id, CLOCK.instant().toEpochMilli(),
                        CLOCK.monotonicNanos(), 0L, "", 1),
                0L, "AAPL", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                100L, 1L, 1L, CLOCK.instant().toEpochMilli(), Optional.empty(), 0L, 0L
        );
    }

    private static MarketTickEvent createTickAtMs(long ms) {
        return new MarketTickEvent(
                new EventMetadata("test-" + ms, ms, 0L, 0L, "", 1),
                0L, "AAPL", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                100L, 1L, 1L, ms, Optional.empty(), 0L, 0L
        );
    }

    private static MarketTickEvent createTickWithSeq(long seq) {
        return new MarketTickEvent(
                new EventMetadata("test-" + seq, CLOCK.instant().toEpochMilli(),
                        CLOCK.monotonicNanos(), seq, "", 1),
                seq, "AAPL", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                100L, 1L, 1L, CLOCK.instant().toEpochMilli(), Optional.empty(), 0L, 0L
        );
    }
}
