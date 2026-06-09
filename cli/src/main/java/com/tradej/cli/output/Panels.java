package com.tradej.cli.output;

import java.util.ArrayList;
import java.util.List;

/**
 * Side-by-side panel renderer for terminal output.
 *
 * <p>Renders two or more content blocks side-by-side using Unicode box drawing.
 *
 * <p>Example:
 * <pre>
 *   ┌─ MARKET ──────────────────┐  ┌─ POSITIONS ──────────────┐
 *   │ NIFTY    24,532  ▲ +0.3% │  │ RELIANCE  BUY 100  ▲ 2500│
 *   │ RELIANCE  2,543  ▲ +0.5% │  │ TCS       SELL 50  ▼ 1200│
 *   └───────────────────────────┘  └───────────────────────────┘
 * </pre>
 */
public final class Panels {

    private static final int DEFAULT_PANEL_WIDTH = 40;

    private Panels() {}

    /**
     * Render two panels side-by-side.
     */
    public static String sideBySide(String title1, List<String> lines1,
                                     String title2, List<String> lines2) {
        return sideBySide(title1, lines1, title2, lines2, DEFAULT_PANEL_WIDTH);
    }

    /**
     * Render two panels side-by-side with custom width.
     */
    public static String sideBySide(String title1, List<String> lines1,
                                     String title2, List<String> lines2,
                                     int panelWidth) {
        int innerWidth = panelWidth - 4; // Account for "│ " and " │"

        // Normalize lines to same height
        int maxLines = Math.max(lines1.size(), lines2.size());
        List<String> padded1 = padLines(lines1, maxLines, innerWidth);
        List<String> padded2 = padLines(lines2, maxLines, innerWidth);

        StringBuilder sb = new StringBuilder();

        // Top borders
        sb.append(topBorder(title1, panelWidth)).append("  ")
                .append(topBorder(title2, panelWidth)).append("\n");

        // Content lines
        for (int i = 0; i < maxLines; i++) {
            String line1 = i < padded1.size() ? padded1.get(i) : " ".repeat(innerWidth);
            String line2 = i < padded2.size() ? padded2.get(i) : " ".repeat(innerWidth);
            sb.append(Ansi.dim("│ ")).append(truncateVisible(line1, innerWidth))
                    .append(Ansi.dim(" │")).append("  ")
                    .append(Ansi.dim("│ ")).append(truncateVisible(line2, innerWidth))
                    .append(Ansi.dim(" │")).append("\n");
        }

        // Bottom borders
        sb.append(botBorder(panelWidth)).append("  ")
                .append(botBorder(panelWidth)).append("\n");

        return sb.toString();
    }

    /**
     * Render three panels side-by-side.
     */
    public static String threeAcross(String title1, List<String> lines1,
                                      String title2, List<String> lines2,
                                      String title3, List<String> lines3,
                                      int panelWidth) {
        int innerWidth = panelWidth - 4;
        int maxLines = Math.max(Math.max(lines1.size(), lines2.size()), lines3.size());
        List<String> p1 = padLines(lines1, maxLines, innerWidth);
        List<String> p2 = padLines(lines2, maxLines, innerWidth);
        List<String> p3 = padLines(lines3, maxLines, innerWidth);

        StringBuilder sb = new StringBuilder();
        sb.append(topBorder(title1, panelWidth)).append("  ")
                .append(topBorder(title2, panelWidth)).append("  ")
                .append(topBorder(title3, panelWidth)).append("\n");

        for (int i = 0; i < maxLines; i++) {
            String l1 = i < p1.size() ? p1.get(i) : " ".repeat(innerWidth);
            String l2 = i < p2.size() ? p2.get(i) : " ".repeat(innerWidth);
            String l3 = i < p3.size() ? p3.get(i) : " ".repeat(innerWidth);
            sb.append(Ansi.dim("│ ")).append(truncateVisible(l1, innerWidth)).append(Ansi.dim(" │")).append("  ")
                    .append(Ansi.dim("│ ")).append(truncateVisible(l2, innerWidth)).append(Ansi.dim(" │")).append("  ")
                    .append(Ansi.dim("│ ")).append(truncateVisible(l3, innerWidth)).append(Ansi.dim(" │")).append("\n");
        }

        sb.append(botBorder(panelWidth)).append("  ")
                .append(botBorder(panelWidth)).append("  ")
                .append(botBorder(panelWidth)).append("\n");

        return sb.toString();
    }

    private static String topBorder(String title, int width) {
        int titleLen = title != null ? title.length() + 2 : 0;
        int lineLen = Math.max(0, width - titleLen - 2);
        if (title != null && !title.isBlank()) {
            return Ansi.dim("┌─ ") + Ansi.bold(title) + Ansi.dim(" " + "─".repeat(lineLen) + "┐");
        }
        return Ansi.dim("┌" + "─".repeat(width - 2) + "┐");
    }

    private static String botBorder(int width) {
        return Ansi.dim("└" + "─".repeat(width - 2) + "┘");
    }

    private static List<String> padLines(List<String> lines, int targetSize, int width) {
        List<String> result = new ArrayList<>(targetSize);
        for (int i = 0; i < targetSize; i++) {
            if (i < lines.size()) {
                String line = lines.get(i);
                int visLen = visibleWidth(line);
                if (visLen < width) {
                    line = line + " ".repeat(width - visLen);
                }
                result.add(line);
            } else {
                result.add(" ".repeat(width));
            }
        }
        return result;
    }

    private static String truncateVisible(String text, int maxWidth) {
        int visLen = visibleWidth(text);
        if (visLen <= maxWidth) return text;
        // Strip ANSI and truncate
        String stripped = text.replaceAll("\033\\[[;\\d]*m", "");
        if (stripped.length() > maxWidth) {
            return stripped.substring(0, maxWidth);
        }
        return text;
    }

    private static int visibleWidth(String text) {
        if (text == null) return 0;
        return text.replaceAll("\033\\[[;\\d]*m", "").length();
    }
}
