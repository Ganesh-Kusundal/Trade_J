package com.tradej.app.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tradej.core.domain.event.*;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test: domain events must round-trip through JSON
 * serialization/deserialization without losing key fields.
 */
@Tag("contract")
class EventSerializationContractTest {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    static Stream<DomainEvent> eventSamples() {
        EventMetadata meta = new EventMetadata("test-id", System.currentTimeMillis(),
                System.nanoTime(), 1L, "corr-123", 1);

        long now = System.currentTimeMillis();
        Candle candle = new Candle("RELIANCE", "5m", now, now + 300_000,
                250000, 255000, 248000, 253000, 10000, true);

        return Stream.of(
                new MarketTickEvent(meta, 1L, "RELIANCE", ExchangeSegment.NSE_EQ,
                        FeedMode.TICKER, 250000L, 100L, 50000L, now,
                        Optional.empty(), 5000L, 5000L),

                new DepthUpdateEvent(meta, "RELIANCE", ExchangeSegment.NSE_EQ,
                        List.of(), List.of(), 5, now),

                new CandleClosed(meta, candle),

                new CandleDeveloping(meta, candle),

                new SignalGenerated(meta, "sig-1", "RELIANCE", "5m", Side.BUY,
                        250000L, 245000L, 260000L, "breakout", Map.of("confidence", 0.85)),

                new PnlUpdatedEvent(meta, 50000L, -10000L, 200000L),

                new PositionUpdateEvent(meta, "RELIANCE", ExchangeSegment.NSE_EQ,
                        100L, 250000L, 253000L, 30000L, 0L),

                new TradeExecutionEvent(meta, "order-1", "trade-1", "RELIANCE",
                        ExchangeSegment.NSE_EQ, Side.BUY, 100L, 250000L, now),

                new ReplayTimeChangedEvent(meta, now, 1_000_000_000L),

                new EventBusBackpressure(meta, 900, 1024, 0.88),

                new ScanResultsPublished(meta, "momentum", "run-1", 5, now - 1000, now, List.of())
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventSamples")
    @DisplayName("Domain events must round-trip through JSON without data loss")
    void roundTripSerialization(DomainEvent event) throws Exception {
        String json = mapper.writeValueAsString(event);
        assertNotNull(json);
        assertFalse(json.isBlank());

        DomainEvent deserialized = mapper.readValue(json, event.getClass());
        assertNotNull(deserialized);

        assertEquals(event.eventId(), deserialized.eventId(),
                "eventId must survive round-trip for " + event.getClass().getSimpleName());
        assertEquals(event.correlationId(), deserialized.correlationId(),
                "correlationId must survive round-trip for " + event.getClass().getSimpleName());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventSamples")
    @DisplayName("Domain events must have non-null metadata")
    void metadataIsPresent(DomainEvent event) {
        assertNotNull(event.metadata(), event.getClass().getSimpleName() + " must have metadata");
        assertNotNull(event.metadata().eventId(), "eventId must not be null");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventSamples")
    @DisplayName("Domain events must implement accept(DomainEventVisitor)")
    void visitorPatternImplemented(DomainEvent event) {
        assertDoesNotThrow(() -> event.accept(new DomainEventVisitor() {}),
                event.getClass().getSimpleName() + " must implement accept(visitor)");
    }
}
