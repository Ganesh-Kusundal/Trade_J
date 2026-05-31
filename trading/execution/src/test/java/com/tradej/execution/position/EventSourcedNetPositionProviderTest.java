package com.tradej.execution.position;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventSourcedNetPositionProviderTest {

    private EventSourcedNetPositionProvider positionProvider;

    @BeforeEach
    void setUp() {
        positionProvider = new EventSourcedNetPositionProvider();
    }

    @Test
    void testSingleTradeLong() {
        TradeOpened openEvent = new TradeOpened(
                EventMetadata.root(),
                "T1",
                "ORD-1",
                "SIG-1",
                "SBIN",
                Side.BUY,
                100L,
                50000L,
                0L,
                0L
        );
        positionProvider.onDomainEvent(openEvent);

        assertEquals(100L, positionProvider.getNetPosition("SBIN"));
        assertEquals(100L, positionProvider.getNetPositions().get("SBIN"));

        TradeClosed closeEvent = new TradeClosed(
                EventMetadata.root(),
                "T1",
                "SBIN",
                51000L,
                100000L,
                "Target reached"
        );
        positionProvider.onDomainEvent(closeEvent);

        assertEquals(0L, positionProvider.getNetPosition("SBIN"));
        assertTrue(positionProvider.getNetPositions().isEmpty());
    }

    @Test
    void testSingleTradeShort() {
        TradeOpened openEvent = new TradeOpened(
                EventMetadata.root(),
                "T2",
                "ORD-2",
                "SIG-2",
                "SBIN",
                Side.SELL,
                50L,
                50000L,
                0L,
                0L
        );
        positionProvider.onDomainEvent(openEvent);

        assertEquals(-50L, positionProvider.getNetPosition("SBIN"));
        assertEquals(-50L, positionProvider.getNetPositions().get("SBIN"));

        TradeClosed closeEvent = new TradeClosed(
                EventMetadata.root(),
                "T2",
                "SBIN",
                49000L,
                50000L,
                "Target reached"
        );
        positionProvider.onDomainEvent(closeEvent);

        assertEquals(0L, positionProvider.getNetPosition("SBIN"));
        assertTrue(positionProvider.getNetPositions().isEmpty());
    }

    @Test
    void testMultiTradeSameSymbol() {
        // Trade 1: Long 100 SBIN
        TradeOpened openEvent1 = new TradeOpened(
                EventMetadata.root(),
                "T1",
                "ORD-1",
                "SIG-1",
                "SBIN",
                Side.BUY,
                100L,
                50000L,
                0L,
                0L
        );
        positionProvider.onDomainEvent(openEvent1);

        // Trade 2: Long 50 SBIN
        TradeOpened openEvent2 = new TradeOpened(
                EventMetadata.root(),
                "T2",
                "ORD-2",
                "SIG-2",
                "SBIN",
                Side.BUY,
                50L,
                50000L,
                0L,
                0L
        );
        positionProvider.onDomainEvent(openEvent2);

        // Verify accumulated position = 150
        assertEquals(150L, positionProvider.getNetPosition("SBIN"));

        // Close Trade 1 (should leave Trade 2 position of 50 active!)
        TradeClosed closeEvent1 = new TradeClosed(
                EventMetadata.root(),
                "T1",
                "SBIN",
                51000L,
                100000L,
                "Exit part 1"
        );
        positionProvider.onDomainEvent(closeEvent1);

        // Verify remaining position is exactly 50!
        assertEquals(50L, positionProvider.getNetPosition("SBIN"));
        assertEquals(50L, positionProvider.getNetPositions().get("SBIN"));

        // Close Trade 2
        TradeClosed closeEvent2 = new TradeClosed(
                EventMetadata.root(),
                "T2",
                "SBIN",
                52000L,
                100000L,
                "Exit part 2"
        );
        positionProvider.onDomainEvent(closeEvent2);

        // Verify position is fully closed
        assertEquals(0L, positionProvider.getNetPosition("SBIN"));
        assertTrue(positionProvider.getNetPositions().isEmpty());
    }

    @Test
    void testSnapshotRestore() {
        TradeOpened openEvent = new TradeOpened(
                EventMetadata.root(),
                "T1",
                "ORD-1",
                "SIG-1",
                "SBIN",
                Side.BUY,
                100L,
                50000L,
                0L,
                0L
        );
        positionProvider.onDomainEvent(openEvent);

        EventSourcedNetPositionProvider.StateSnapshot snapshot = positionProvider.snapshot();

        TradeClosed closeEvent = new TradeClosed(
                EventMetadata.root(),
                "T1",
                "SBIN",
                51000L,
                100000L,
                "Exit"
        );
        positionProvider.onDomainEvent(closeEvent);
        assertEquals(0L, positionProvider.getNetPosition("SBIN"));

        positionProvider.restore(snapshot);
        assertEquals(100L, positionProvider.getNetPosition("SBIN"));
    }
}
