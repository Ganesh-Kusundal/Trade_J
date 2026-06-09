package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AnsiTest {

    @Test
    void formatPaisa_formatsCorrectly() {
        assertEquals("₹2,500.00", Ansi.formatPaisa(250000L));
        assertEquals("₹0.00", Ansi.formatPaisa(0L));
        assertEquals("₹1,234.56", Ansi.formatPaisa(123456L));
        assertEquals("₹100.50", Ansi.formatPaisa(10050L));
    }

    @Test
    void formatNumber_addsCommas() {
        assertEquals("1,000", Ansi.formatNumber(1000));
        assertEquals("1,000,000", Ansi.formatNumber(1000000));
        assertEquals("0", Ansi.formatNumber(0));
    }

    @Test
    void formatPct_formatsPercentage() {
        assertEquals("12.34%", Ansi.formatPct(12.34));
        assertEquals("-5.67%", Ansi.formatPct(-5.67));
        assertEquals("0.00%", Ansi.formatPct(0.0));
    }

    @Test
    void pnl_positive_showsGreenArrow() {
        String result = Ansi.pnl(250000L);
        assertTrue(result.contains("▲"), "Positive PnL should show up arrow");
        assertTrue(result.contains("₹"), "Should contain rupee symbol");
    }

    @Test
    void pnl_negative_showsRedArrow() {
        String result = Ansi.pnl(-120000L);
        assertTrue(result.contains("▼"), "Negative PnL should show down arrow");
    }

    @Test
    void pnl_zero_showsDash() {
        String result = Ansi.pnl(0L);
        assertTrue(result.contains("—"), "Zero PnL should show dash");
    }

    @Test
    void signed_positive_showsPlus() {
        String result = Ansi.signed(12.34);
        assertTrue(result.contains("+"), "Positive should show + sign");
    }

    @Test
    void signed_negative_showsMinus() {
        String result = Ansi.signed(-5.67);
        assertTrue(result.contains("-"), "Negative should show - sign");
    }

    @Test
    void check_true_returnsGreenCheck() {
        String result = Ansi.check(true);
        assertTrue(result.contains("✓"), "True should show check mark");
    }

    @Test
    void check_false_returnsRedCross() {
        String result = Ansi.check(false);
        assertTrue(result.contains("✗"), "False should show cross mark");
    }

    @Test
    void status_true_showsLabel() {
        String result = Ansi.status(true, "Connected", "Disconnected");
        assertTrue(result.contains("Connected"));
    }

    @Test
    void status_false_showsFalseLabel() {
        String result = Ansi.status(false, "Connected", "Disconnected");
        assertTrue(result.contains("Disconnected"));
    }

    @Test
    void latency_fastIsGreen() {
        String result = Ansi.latency(15);
        // When colors enabled, should contain green code; when disabled, just "15ms"
        assertTrue(result.contains("15ms"));
    }

    @Test
    void latency_slowIsRed() {
        String result = Ansi.latency(500);
        assertTrue(result.contains("500ms"));
    }
}
