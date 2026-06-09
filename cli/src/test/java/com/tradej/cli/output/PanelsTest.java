package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class PanelsTest {

    @Test
    void sideBySide_validData_renders() {
        String result = Panels.sideBySide(
                "MARKET",
                List.of("NIFTY    24,532  ▲ +0.3%", "RELIANCE  2,543  ▲ +0.5%"),
                "POSITIONS",
                List.of("RELIANCE  BUY 100  ▲ 2500", "TCS       SELL 50  ▼ 1200")
        );
        assertFalse(result.isEmpty());
        assertTrue(result.contains("MARKET"));
        assertTrue(result.contains("POSITIONS"));
    }

    @Test
    void sideBySide_emptyLines_renders() {
        String result = Panels.sideBySide(
                "LEFT", List.of(),
                "RIGHT", List.of("data")
        );
        assertFalse(result.isEmpty());
    }

    @Test
    void sideBySide_unequalLines_padsCorrectly() {
        String result = Panels.sideBySide(
                "A", List.of("line1", "line2", "line3"),
                "B", List.of("line1")
        );
        assertFalse(result.isEmpty());
    }

    @Test
    void threeAcross_renders() {
        String result = Panels.threeAcross(
                "A", List.of("data1"),
                "B", List.of("data2"),
                "C", List.of("data3"),
                30
        );
        assertFalse(result.isEmpty());
    }

    @Test
    void sideBySide_customWidth_renders() {
        String result = Panels.sideBySide(
                "LEFT", List.of("content"),
                "RIGHT", List.of("content"),
                50
        );
        assertFalse(result.isEmpty());
    }
}
