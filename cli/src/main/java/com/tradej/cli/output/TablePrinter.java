package com.tradej.cli.output;

import java.util.ArrayList;
import java.util.List;

public final class TablePrinter {
    private TablePrinter() {
    }

    public static void print(String[] headers, List<String[]> rows) {
        if (rows.isEmpty()) {
            System.out.println("(no rows)");
            return;
        }
        int columns = headers.length;
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < columns; i++) {
                String cell = i < row.length ? nullToEmpty(row[i]) : "";
                widths[i] = Math.max(widths[i], cell.length());
            }
        }
        printLine(widths);
        printRow(headers, widths);
        printLine(widths);
        for (String[] row : rows) {
            printRow(padRow(row, columns), widths);
        }
        printLine(widths);
    }

    private static String[] padRow(String[] row, int columns) {
        String[] padded = new String[columns];
        for (int i = 0; i < columns; i++) {
            padded[i] = i < row.length ? nullToEmpty(row[i]) : "";
        }
        return padded;
    }

    private static void printLine(int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append("-+-");
            }
            sb.append("-".repeat(widths[i]));
        }
        System.out.println(sb);
    }

    private static void printRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            String cell = i < cells.length ? nullToEmpty(cells[i]) : "";
            sb.append(cell);
            sb.append(" ".repeat(Math.max(0, widths[i] - cell.length())));
        }
        System.out.println(sb);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static List<String[]> fromKeyValue(Iterable<?> items, java.util.function.Function<Object, String[]> mapper) {
        List<String[]> rows = new ArrayList<>();
        for (Object item : items) {
            rows.add(mapper.apply(item));
        }
        return rows;
    }
}
