package com.tradej.cli.output;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Rich table printer with Unicode box drawing and ANSI color support.
 *
 * <p>Renders tables with proper box-drawing characters:
 * <pre>
 * ┌──────────┬───────┬───────────┐
 * │ Symbol   │ Qty   │ PnL       │
 * ├──────────┼───────┼───────────┤
 * │ RELIANCE │  100  │ ▲ ₹2,500 │
 * │ TCS      │   50  │ ▼ -₹1,200│
 * └──────────┴───────┴───────────┘
 * </pre>
 *
 * <p>Falls back to ASCII {@code +--+} when ANSI is disabled or terminal
 * does not support Unicode.
 */
public final class RichTable {

    private static final boolean UNICODE = detectUnicodeSupport();

    // Box drawing characters
    private static final String TL = UNICODE ? "┌" : "+";
    private static final String TR = UNICODE ? "┐" : "+";
    private static final String BL = UNICODE ? "└" : "+";
    private static final String BR = UNICODE ? "┘" : "+";
    private static final String H  = UNICODE ? "─" : "-";
    private static final String V  = UNICODE ? "│" : "|";
    private static final String TM = UNICODE ? "┬" : "+";
    private static final String BM = UNICODE ? "┴" : "+";
    private static final String LM = UNICODE ? "├" : "+";
    private static final String RM = UNICODE ? "┤" : "+";
    private static final String CM = UNICODE ? "┼" : "+";

    private final String[] headers;
    private final List<String[]> rows = new ArrayList<>();
    private final List<Function<String[], String[]>> rowFormatters = new ArrayList<>();
    private String title;

    private RichTable(String[] headers) {
        this.headers = headers;
    }

    public static RichTable of(String... headers) {
        return new RichTable(headers);
    }

    public RichTable title(String title) {
        this.title = title;
        return this;
    }

    public RichTable addRow(String... cells) {
        rows.add(cells);
        return this;
    }

    public RichTable addRows(List<String[]> rows) {
        this.rows.addAll(rows);
        return this;
    }

    /**
     * Add a row formatter that can apply ANSI colors to individual cells.
     * The formatter receives the raw cell values and returns formatted (colored) values.
     * Note: formatted values must have the same visible width as raw values for alignment.
     */
    public RichTable rowFormatter(Function<String[], String[]> formatter) {
        rowFormatters.add(formatter);
        return this;
    }

    public void print() {
        if (rows.isEmpty()) {
            System.out.println(Ansi.dim("(no rows)"));
            return;
        }

        int columns = headers.length;
        int[] widths = new int[columns];

        // Calculate column widths from headers and data
        for (int i = 0; i < columns; i++) {
            widths[i] = visibleWidth(headers[i]);
        }
        for (String[] row : rows) {
            for (int i = 0; i < columns; i++) {
                String cell = i < row.length ? nullToEmpty(row[i]) : "";
                widths[i] = Math.max(widths[i], visibleWidth(cell));
            }
        }

        StringBuilder sb = new StringBuilder();

        // Title
        if (title != null) {
            int totalWidth = totalWidth(widths);
            sb.append(Ansi.bold("  " + title)).append("\n");
        }

        // Top border
        sb.append(topBorder(widths)).append("\n");

        // Header row
        sb.append(dataRow(headers, widths, true)).append("\n");

        // Header separator
        sb.append(midBorder(widths)).append("\n");

        // Data rows
        for (String[] row : rows) {
            String[] padded = padRow(row, columns);
            String[] formatted = applyFormatters(padded);
            sb.append(dataRow(formatted, widths, false)).append("\n");
        }

        // Bottom border
        sb.append(botBorder(widths));

        System.out.println(sb);
    }

    // ── Border rendering ───────────────────────────────────────────

    private String topBorder(int[] widths) {
        return borderLine(TL, TM, TR, widths);
    }

    private String midBorder(int[] widths) {
        return borderLine(LM, CM, RM, widths);
    }

    private String botBorder(int[] widths) {
        return borderLine(BL, BM, BR, widths);
    }

    private String borderLine(String left, String mid, String right, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) sb.append(mid);
            sb.append(H.repeat(widths[i] + 2)); // +2 for padding spaces
        }
        sb.append(right);
        return sb.toString();
    }

    private String dataRow(String[] cells, int[] widths, boolean isHeader) {
        StringBuilder sb = new StringBuilder();
        sb.append(V);
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.length ? nullToEmpty(cells[i]) : "";
            int padding = widths[i] - visibleWidth(cell);
            sb.append(" ");
            if (isHeader) {
                sb.append(Ansi.bold(cell));
                sb.append(" ".repeat(Math.max(0, padding)));
            } else {
                sb.append(cell);
                sb.append(" ".repeat(Math.max(0, padding)));
            }
            sb.append(" ").append(V);
        }
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────

    private String[] padRow(String[] row, int columns) {
        String[] padded = new String[columns];
        for (int i = 0; i < columns; i++) {
            padded[i] = i < row.length ? nullToEmpty(row[i]) : "";
        }
        return padded;
    }

    private String[] applyFormatters(String[] row) {
        String[] result = row;
        for (Function<String[], String[]> formatter : rowFormatters) {
            result = formatter.apply(result);
        }
        return result;
    }

    /**
     * Calculate visible width (strips ANSI escape codes).
     */
    private static int visibleWidth(String text) {
        if (text == null) return 0;
        // Strip ANSI escape codes for width calculation
        return text.replaceAll("\033\\[[;\\d]*m", "").length();
    }

    private static int totalWidth(int[] widths) {
        int total = 1; // left border
        for (int w : widths) {
            total += w + 3; // cell + 2 spaces + border
        }
        return total;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean detectUnicodeSupport() {
        if (System.console() == null) return false;
        String term = System.getenv("TERM");
        if ("dumb".equals(term)) return false;
        // Most modern terminals support Unicode
        String lang = System.getenv("LANG");
        if (lang != null && lang.toLowerCase().contains("utf")) return true;
        // Default to true on non-Windows
        return !System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    // ── Static convenience ─────────────────────────────────────────

    /**
     * Quick print a simple table.
     */
    public static void print(String[] headers, List<String[]> rows) {
        RichTable table = RichTable.of(headers).addRows(rows);
        table.print();
    }

    /**
     * Build rows from a list of objects using a mapper.
     */
    public static <T> List<String[]> fromItems(List<T> items, Function<T, String[]> mapper) {
        List<String[]> rows = new ArrayList<>();
        for (T item : items) {
            rows.add(mapper.apply(item));
        }
        return rows;
    }
}
