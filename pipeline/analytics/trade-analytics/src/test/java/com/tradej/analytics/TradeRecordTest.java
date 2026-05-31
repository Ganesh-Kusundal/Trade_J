package com.tradej.analytics;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TradeRecordTest {

    @Test
    void tradeRecordConstruction() {
        Instant entry = Instant.parse("2024-01-01T09:30:00Z");
        Instant exit = Instant.parse("2024-01-01T15:00:00Z");

        TradeRecord trade = new TradeRecord(
                "trade-1",
                "NIFTY",
                entry,
                exit,
                BigDecimal.valueOf(24500),
                BigDecimal.valueOf(24750),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(12500),
                BigDecimal.valueOf(1.02),
                "LONG"
        );

        assertEquals("trade-1", trade.tradeId());
        assertEquals("NIFTY", trade.symbol());
        assertEquals(entry, trade.entryTime());
        assertEquals(exit, trade.exitTime());
        assertEquals(BigDecimal.valueOf(24500), trade.entryPrice());
        assertEquals(BigDecimal.valueOf(24750), trade.exitPrice());
        assertEquals(BigDecimal.valueOf(50), trade.quantity());
        assertEquals(BigDecimal.valueOf(12500), trade.pnl());
        assertEquals(BigDecimal.valueOf(1.02), trade.returnPct());
        assertEquals("LONG", trade.direction());
    }

    @Test
    void shortTradeRecord() {
        Instant entry = Instant.parse("2024-01-01T09:30:00Z");
        Instant exit = Instant.parse("2024-01-01T10:30:00Z");

        TradeRecord trade = new TradeRecord(
                "short-1",
                "BANKNIFTY",
                entry,
                exit,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(49500),
                BigDecimal.valueOf(30),
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1.0),
                "SHORT"
        );

        assertEquals("SHORT", trade.direction());
        assertTrue(trade.pnl().compareTo(BigDecimal.ZERO) > 0);
    }
}