package com.tradej.cli.output;

import java.util.List;

/**
 * ASCII/Unicode sparkline renderer for inline trend visualization.
 *
 * <p>Converts a series of numeric values into a compact sparkline using
 * Unicode block characters: ▁▂▃▄▅▆▇█
 *
 * <p>Example:
 * <pre>
 *   double[] prices = {100, 102, 101, 105, 108, 106, 110, 112};
 *   Sparkline.render(prices)  →  "▁▂▂▄▆▅▇█"
 * </pre>
 */
public final class Sparkline {

    private static final char[] BLOCKS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    private static final String ASCII_BLOCKS = " _.:-!*$#@";

    private Sparkline() {}

    /**
     * Render a sparkline from an array of values.
     * Returns empty string if fewer than 2 values.
     */
    public static String render(double... values) {
        if (values == null || values.length < 2) return "";
        return render(Ansi.isEnabled() ? BLOCKS : ASCII_BLOCKS.toCharArray(), values);
    }

    /**
     * Render a sparkline from a list of values.
     */
    public static String render(List<? extends Number> values) {
        if (values == null || values.size() < 2) return "";
        double[] arr = values.stream().mapToDouble(Number::doubleValue).toArray();
        return render(arr);
    }

    /**
     * Render a sparkline from long values (e.g. paisa prices).
     */
    public static String renderLongs(long... values) {
        if (values == null || values.length < 2) return "";
        double[] arr = new double[values.length];
        for (int i = 0; i < values.length; i++) arr[i] = values[i];
        return render(arr);
    }

    /**
     * Render with color: green for uptrend, red for downtrend.
     */
    public static String renderColored(double... values) {
        if (values == null || values.length < 2) return "";
        String sparkline = render(values);
        double trend = values[values.length - 1] - values[0];
        if (trend > 0) return Ansi.green(sparkline);
        if (trend < 0) return Ansi.red(sparkline);
        return Ansi.dim(sparkline);
    }

    /**
     * Render with color from long values.
     */
    public static String renderColoredLongs(long... values) {
        if (values == null || values.length < 2) return "";
        double[] arr = new double[values.length];
        for (int i = 0; i < values.length; i++) arr[i] = values[i];
        return renderColored(arr);
    }

    private static String render(char[] blocks, double[] values) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        double range = max - min;
        if (range == 0) range = 1;

        StringBuilder sb = new StringBuilder(values.length);
        for (double v : values) {
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                sb.append(' ');
                continue;
            }
            int index = (int) ((v - min) / range * (blocks.length - 1));
            index = Math.max(0, Math.min(blocks.length - 1, index));
            sb.append(blocks[index]);
        }
        return sb.toString();
    }
}
