package com.tradej.cli.output;

/**
 * ASCII chart renderer for terminal-based data visualization.
 *
 * <p>Renders simple line/bar charts using Unicode block characters.
 *
 * <p>Example:
 * <pre>
 *   double[] prices = {100, 102, 105, 103, 108, 112, 110};
 *   AsciiChart.line(prices, "Price", 10)
 * </pre>
 *
 * <p>Output:
 * <pre>
 *   Price
 *   112 │        ▄█
 *   108 │    ▅▆▇█
 *   104 │  ▃▄
 *   100 │▁▂
 *       └────────────
 * </pre>
 */
public final class AsciiChart {

    private static final int DEFAULT_HEIGHT = 8;
    private static final int DEFAULT_WIDTH = 40;

    private AsciiChart() {}

    /**
     * Render a horizontal bar chart from labeled values.
     */
    public static String barChart(String[] labels, double[] values, int maxWidth) {
        if (labels == null || values == null || labels.length == 0) return "";

        double max = Double.MIN_VALUE;
        for (double v : values) max = Math.max(max, Math.abs(v));
        if (max == 0) max = 1;

        int labelWidth = 0;
        for (String label : labels) labelWidth = Math.max(labelWidth, label.length());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < labels.length; i++) {
            String label = String.format("%" + labelWidth + "s", labels[i]);
            int barLen = (int) (Math.abs(values[i]) / max * maxWidth);
            String bar;
            if (Ansi.isEnabled()) {
                bar = values[i] >= 0
                        ? Ansi.green("█".repeat(barLen))
                        : Ansi.red("█".repeat(barLen));
            } else {
                bar = "#".repeat(barLen);
            }
            sb.append(label).append(" │ ").append(bar)
                    .append(" ").append(formatValue(values[i])).append("\n");
        }
        return sb.toString();
    }

    /**
     * Render a simple vertical line chart.
     */
    public static String lineChart(double[] values, String title, int height) {
        if (values == null || values.length < 2) return "";

        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double v : values) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double range = max - min;
        if (range == 0) range = 1;

        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(Ansi.bold("  " + title)).append("\n");
        }

        for (int row = height - 1; row >= 0; row--) {
            double threshold = min + (range * row / (height - 1));
            String label = String.format("%6.0f", threshold);
            sb.append(Ansi.dim(label)).append(" │");
            for (double v : values) {
                int pos = (int) ((v - min) / range * (height - 1));
                if (pos == row) {
                    sb.append(Ansi.green("●"));
                } else if (pos > row && row == 0) {
                    sb.append(Ansi.dim("│"));
                } else {
                    sb.append(" ");
                }
            }
            sb.append("\n");
        }
        sb.append(Ansi.dim("       └")).append("─".repeat(values.length)).append("\n");
        return sb.toString();
    }

    /**
     * Render a mini chart suitable for embedding in a table cell.
     * Returns sparkline + min/max labels.
     */
    public static String miniChart(double[] values) {
        if (values == null || values.length < 2) return "";
        String spark = Sparkline.renderColored(values);
        double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
        for (double v : values) { min = Math.min(min, v); max = Math.max(max, v); }
        return spark + " " + Ansi.dim(String.format("%.0f-%.0f", min, max));
    }

    private static String formatValue(double value) {
        if (Math.abs(value) >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (Math.abs(value) >= 1_000) return String.format("%.1fK", value / 1_000);
        return String.format("%.0f", value);
    }
}
