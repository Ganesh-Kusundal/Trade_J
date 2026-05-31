package com.tradej.cli.output;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class TablePrinterTest {

    @Test
    void printsHeadersAndRows() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            TablePrinter.print(
                    new String[]{"Symbol", "Qty"},
                    List.of(new String[]{"NIFTY", "1"}, new String[]{"RELIANCE", "10"})
            );
        } finally {
            System.setOut(original);
        }
        String output = buffer.toString();
        assertTrue(output.contains("Symbol"));
        assertTrue(output.contains("NIFTY"));
        assertTrue(output.contains("RELIANCE"));
    }

    @Test
    void emptyRowsPrintsPlaceholder() {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer));
        try {
            TablePrinter.print(new String[]{"A"}, List.of());
        } finally {
            System.setOut(original);
        }
        assertTrue(buffer.toString().contains("(no rows)"));
    }
}
