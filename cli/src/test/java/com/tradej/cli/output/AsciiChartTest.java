package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AsciiChartTest {

    @Test
    void barChart_validData_renders() {
        String result = AsciiChart.barChart(
                new String[]{"RELIANCE", "TCS", "INFY"},
                new double[]{2500, 3800, 1500},
                30
        );
        assertFalse(result.isEmpty());
        assertTrue(result.contains("RELIANCE"));
        assertTrue(result.contains("TCS"));
    }

    @Test
    void barChart_negativeValues_renders() {
        String result = AsciiChart.barChart(
                new String[]{"Profit", "Loss"},
                new double[]{5000, -2000},
                20
        );
        assertFalse(result.isEmpty());
    }

    @Test
    void barChart_emptyData_returnsEmpty() {
        assertEquals("", AsciiChart.barChart(new String[]{}, new double[]{}, 20));
    }

    @Test
    void barChart_nullData_returnsEmpty() {
        assertEquals("", AsciiChart.barChart(null, null, 20));
    }

    @Test
    void lineChart_validData_renders() {
        String result = AsciiChart.lineChart(
                new double[]{100, 105, 102, 108, 112, 110, 115},
                "Price", 6
        );
        assertFalse(result.isEmpty());
        assertTrue(result.contains("Price"));
    }

    @Test
    void lineChart_twoValues_works() {
        String result = AsciiChart.lineChart(new double[]{10, 20}, "Test", 4);
        assertFalse(result.isEmpty());
    }

    @Test
    void lineChart_singleValue_returnsEmpty() {
        assertEquals("", AsciiChart.lineChart(new double[]{42}, "Test", 4));
    }

    @Test
    void lineChart_nullData_returnsEmpty() {
        assertEquals("", AsciiChart.lineChart(null, "Test", 4));
    }

    @Test
    void miniChart_validData_renders() {
        String result = AsciiChart.miniChart(new double[]{10, 12, 11, 15, 14});
        assertFalse(result.isEmpty());
    }

    @Test
    void miniChart_singleValue_returnsEmpty() {
        assertEquals("", AsciiChart.miniChart(new double[]{42}));
    }
}
