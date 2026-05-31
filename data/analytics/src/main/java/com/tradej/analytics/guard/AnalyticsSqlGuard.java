package com.tradej.analytics.guard;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AnalyticsSqlGuard {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "\\b(attach|copy|create|delete|drop|insert|update|pragma|export|import|load|install|set|call|execute|prepare)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private AnalyticsSqlGuard() {
    }

    public static void validateReadOnly(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        String trimmed = sql.strip();
        if (!trimmed.regionMatches(true, 0, "select", 0, 6)
                && !trimmed.regionMatches(true, 0, "with", 0, 4)
                && !trimmed.regionMatches(true, 0, "describe", 0, 8)
                && !trimmed.regionMatches(true, 0, "show", 0, 4)) {
            throw new IllegalArgumentException("Only read-only SELECT/WITH/DESCRIBE/SHOW queries are allowed");
        }
        if (FORBIDDEN.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Query contains forbidden statement keyword");
        }
        if (trimmed.contains(";")) {
            throw new IllegalArgumentException("Multi-statement SQL is not allowed");
        }
    }

    public static String applyRowLimit(String sql, int rowLimit) {
        validateReadOnly(sql);
        String normalized = sql.strip();
        if (normalized.toLowerCase(Locale.ROOT).contains(" limit ")) {
            return normalized;
        }
        return normalized + " limit " + Math.max(1, rowLimit);
    }
}
