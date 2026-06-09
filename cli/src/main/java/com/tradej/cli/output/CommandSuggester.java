package com.tradej.cli.output;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Suggests similar commands using Levenshtein edit distance.
 * Used when a user types an unknown command in the REPL.
 *
 * <p>Example:
 * <pre>
 *   tradej> qoute RELIANCE
 *   Unknown command: qoute
 *   Did you mean: quote, quotes, depth?
 * </pre>
 */
public final class CommandSuggester {

    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_DISTANCE = 3;

    private CommandSuggester() {}

    /**
     * Find the closest matching commands to the given input.
     *
     * @param input    the mistyped command
     * @param commands the available commands
     * @return list of suggested commands, sorted by similarity (best first)
     */
    public static List<String> suggest(String input, Collection<String> commands) {
        if (input == null || input.isBlank()) return List.of();

        String lower = input.toLowerCase();
        List<ScoredCommand> scored = new ArrayList<>();

        for (String cmd : commands) {
            String lowerCmd = cmd.toLowerCase();
            int distance = levenshteinDistance(lower, lowerCmd);
            if (distance <= MAX_DISTANCE || lowerCmd.startsWith(lower) || lowerCmd.contains(lower)) {
                scored.add(new ScoredCommand(cmd, distance));
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingInt(ScoredCommand::distance))
                .limit(MAX_SUGGESTIONS)
                .map(ScoredCommand::command)
                .toList();
    }

    /**
     * Format a "did you mean?" message.
     */
    public static String formatSuggestion(String input, Collection<String> commands) {
        List<String> suggestions = suggest(input, commands);
        if (suggestions.isEmpty()) {
            return "Unknown command: " + input;
        }
        return "Unknown command: " + Ansi.red(input)
                + "\nDid you mean: " + suggestions.stream()
                .map(Ansi::bold)
                .reduce((a, b) -> a + ", " + b)
                .orElse("") + "?";
    }

    /**
     * Levenshtein edit distance between two strings.
     */
    static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private record ScoredCommand(String command, int distance) {}
}
