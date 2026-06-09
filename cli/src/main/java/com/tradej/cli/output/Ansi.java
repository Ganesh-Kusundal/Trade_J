package com.tradej.cli.output;

/**
 * ANSI escape code utilities for terminal colorized output.
 *
 * <p>Automatically detects whether the terminal supports colors
 * (disabled when output is piped or redirected).
 */
public final class Ansi {

    private static final boolean ENABLED = detectColorSupport();

    private Ansi() {}

    public static boolean isEnabled() {
        return ENABLED;
    }

    // ── Foreground colors ──────────────────────────────────────────

    public static String red(String text) {
        return wrap("\033[31m", text);
    }

    public static String green(String text) {
        return wrap("\033[32m", text);
    }

    public static String yellow(String text) {
        return wrap("\033[33m", text);
    }

    public static String blue(String text) {
        return wrap("\033[34m", text);
    }

    public static String magenta(String text) {
        return wrap("\033[35m", text);
    }

    public static String cyan(String text) {
        return wrap("\033[36m", text);
    }

    public static String white(String text) {
        return wrap("\033[37m", text);
    }

    public static String gray(String text) {
        return wrap("\033[90m", text);
    }

    // ── Styles ─────────────────────────────────────────────────────

    public static String bold(String text) {
        return wrap("\033[1m", text);
    }

    public static String dim(String text) {
        return wrap("\033[2m", text);
    }

    public static String italic(String text) {
        return wrap("\033[3m", text);
    }

    public static String underline(String text) {
        return wrap("\033[4m", text);
    }

    public static String strikethrough(String text) {
        return wrap("\033[9m", text);
    }

    // ── Semantic colors ────────────────────────────────────────────

    /** Green for profit/positive, red for loss/negative, dim for zero. */
    public static String pnl(long valuePaisa) {
        if (valuePaisa > 0) {
            return green("▲ " + formatPaisa(valuePaisa));
        } else if (valuePaisa < 0) {
            return red("▼ " + formatPaisa(Math.abs(valuePaisa)));
        }
        return dim("— " + formatPaisa(0));
    }

    /** Green for positive, red for negative. */
    public static String signed(double value) {
        if (value > 0) {
            return green("+" + String.format("%.2f", value));
        } else if (value < 0) {
            return red(String.format("%.2f", value));
        }
        return dim("0.00");
    }

    /** Green for up/connected/true, red for down/disconnected/false. */
    public static String status(boolean ok, String trueLabel, String falseLabel) {
        return ok ? green("✓ " + trueLabel) : red("✗ " + falseLabel);
    }

    /** Green check or red cross. */
    public static String check(boolean ok) {
        return ok ? green("✓") : red("✗");
    }

    // ── Formatting ─────────────────────────────────────────────────

    /** Format paisa as ₹X,XXX.XX */
    public static String formatPaisa(long paisa) {
        long rupees = paisa / 100;
        long paise = Math.abs(paisa % 100);
        return "₹" + String.format("%,d.%02d", rupees, paise);
    }

    /** Format number with commas. */
    public static String formatNumber(long value) {
        return String.format("%,d", value);
    }

    /** Format percentage. */
    public static String formatPct(double value) {
        return String.format("%.2f%%", value);
    }

    /** Format latency in ms with color (green < 50ms, yellow < 200ms, red >= 200ms). */
    public static String latency(long ms) {
        String text = ms + "ms";
        if (ms < 50) return green(text);
        if (ms < 200) return yellow(text);
        return red(text);
    }

    // ── Internal ───────────────────────────────────────────────────

    private static String wrap(String code, String text) {
        if (!ENABLED) return text;
        return code + text + "\033[0m";
    }

    private static boolean detectColorSupport() {
        // Disabled when piped or redirected
        if (System.console() == null) return false;
        // NO_COLOR convention (https://no-color.org/)
        if (System.getenv("NO_COLOR") != null) return false;
        // TERM=dumb means no color
        String term = System.getenv("TERM");
        if ("dumb".equals(term)) return false;
        return true;
    }
}
