package com.tradej.cli.output;

/**
 * Terminal progress bar with configurable width and style.
 *
 * <p>Renders an inline progress bar:
 * <pre>
 *   Downloading equity data... [████████████░░░░░░░░] 60% (300/500)
 * </pre>
 *
 * <p>Usage:
 * <pre>
 *   ProgressBar bar = new ProgressBar("Downloading", 500);
 *   for (int i = 0; i &lt; 500; i++) {
 *       downloadItem(i);
 *       bar.update(i + 1);
 *   }
 *   bar.complete();
 * </pre>
 */
public final class ProgressBar {

    private static final int DEFAULT_WIDTH = 30;
    private static final boolean UNICODE = Ansi.isEnabled();

    private final String label;
    private final int total;
    private final int width;
    private int current;
    private long startTimeMs;

    public ProgressBar(String label, int total) {
        this(label, total, DEFAULT_WIDTH);
    }

    public ProgressBar(String label, int total, int width) {
        this.label = label;
        this.total = Math.max(1, total);
        this.width = width;
        this.current = 0;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Update progress to the given value and render the bar.
     */
    public void update(int value) {
        this.current = Math.min(value, total);
        render();
    }

    /**
     * Increment progress by 1 and render.
     */
    public void increment() {
        update(current + 1);
    }

    /**
     * Mark as complete and print final line.
     */
    public void complete() {
        this.current = total;
        render();
        System.out.println();
    }

    private void render() {
        double pct = (double) current / total;
        int filled = (int) (pct * width);
        int empty = width - filled;

        String filledBar;
        String emptyBar;
        if (UNICODE) {
            filledBar = Ansi.green("█".repeat(filled));
            emptyBar = Ansi.dim("░".repeat(empty));
        } else {
            filledBar = "#".repeat(filled);
            emptyBar = "-".repeat(empty);
        }

        long elapsed = System.currentTimeMillis() - startTimeMs;
        String eta = current > 0 && current < total
                ? " ETA: " + formatEta(elapsed, current, total)
                : "";

        String line = String.format("\r%s [%s%s] %d%% (%d/%d)%s   ",
                label, filledBar, emptyBar,
                (int) (pct * 100), current, total, eta);

        System.out.print(line);
        System.out.flush();
    }

    private String formatEta(long elapsedMs, int done, int total) {
        if (done == 0) return "—";
        long remaining = total - done;
        long msPerItem = elapsedMs / done;
        long etaMs = remaining * msPerItem;
        if (etaMs < 1000) return "<1s";
        long seconds = etaMs / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m" + seconds + "s";
        return (minutes / 60) + "h" + (minutes % 60) + "m";
    }
}
