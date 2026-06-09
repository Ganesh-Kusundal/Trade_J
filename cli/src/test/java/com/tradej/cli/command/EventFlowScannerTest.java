package com.tradej.cli.command;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EventFlowScannerTest {

    @Test
    void scan_findsProducersForKnownEvents() {
        List<EventFlowScanner.EventFlow> flows = EventFlowScanner.scan(
                List.of("CandleClosed", "SignalGenerated", "OrderAccepted"));

        assertFalse(flows.isEmpty());
        EventFlowScanner.EventFlow candleFlow = flows.stream()
                .filter(f -> f.eventName().equals("CandleClosed")).findFirst().orElse(null);
        assertNotNull(candleFlow, "CandleClosed flow should exist");
    }

    @Test
    void scan_returnsAllRequestedEvents() {
        List<String> requested = List.of("MarketTickEvent", "OrderFilled", "KillSwitchEngaged");
        List<EventFlowScanner.EventFlow> flows = EventFlowScanner.scan(requested);
        assertEquals(3, flows.size());
        assertEquals("MarketTickEvent", flows.get(0).eventName());
        assertEquals("OrderFilled", flows.get(1).eventName());
        assertEquals("KillSwitchEngaged", flows.get(2).eventName());
    }

    @Test
    void scan_handlesUnknownEvents() {
        List<EventFlowScanner.EventFlow> flows = EventFlowScanner.scan(
                List.of("NonExistentEvent12345"));
        assertEquals(1, flows.size());
        assertTrue(flows.get(0).producers().isEmpty());
        assertTrue(flows.get(0).consumers().isEmpty());
    }
}
