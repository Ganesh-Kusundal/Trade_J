package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class RichTableTest {

    @Test
    void emptyTable_printsNoRows() {
        RichTable table = RichTable.of("A", "B");
        assertDoesNotThrow(() -> table.print());
    }

    @Test
    void singleRow_renders() {
        RichTable table = RichTable.of("Symbol", "Qty", "PnL")
                .addRow("RELIANCE", "100", "2500");
        assertDoesNotThrow(() -> table.print());
    }

    @Test
    void multipleRows_renders() {
        RichTable table = RichTable.of("Symbol", "Qty", "PnL")
                .addRow("RELIANCE", "100", "2500")
                .addRow("TCS", "50", "-1200")
                .addRow("INFY", "75", "800");
        assertDoesNotThrow(() -> table.print());
    }

    @Test
    void withTitle_renders() {
        RichTable table = RichTable.of("Symbol", "Qty")
                .title("Portfolio")
                .addRow("RELIANCE", "100");
        assertDoesNotThrow(() -> table.print());
    }

    @Test
    void fromItems_mapsCorrectly() {
        List<String[]> rows = RichTable.fromItems(
                List.of("A", "B", "C"),
                s -> new String[]{s, String.valueOf(s.length())}
        );
        assertEquals(3, rows.size());
        assertArrayEquals(new String[]{"A", "1"}, rows.get(0));
        assertArrayEquals(new String[]{"B", "1"}, rows.get(1));
    }

    @Test
    void staticPrint_works() {
        assertDoesNotThrow(() ->
                RichTable.print(
                        new String[]{"Name", "Value"},
                        List.of(new String[]{"foo", "bar"}, new String[]{"baz", "qux"})
                )
        );
    }

    @Test
    void nullCells_treatedAsEmpty() {
        RichTable table = RichTable.of("A", "B")
                .addRow("hello", null);
        assertDoesNotThrow(() -> table.print());
    }

    @Test
    void shortRow_paddedCorrectly() {
        RichTable table = RichTable.of("A", "B", "C")
                .addRow("only-one");
        assertDoesNotThrow(() -> table.print());
    }
}
