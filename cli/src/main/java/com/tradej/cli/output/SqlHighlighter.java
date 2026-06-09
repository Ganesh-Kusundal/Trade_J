package com.tradej.cli.output;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL syntax highlighter for terminal output.
 *
 * <p>Applies ANSI colors to SQL keywords, strings, numbers, and comments.
 *
 * <p>Example:
 * <pre>
 *   String sql = "SELECT symbol, open_interest FROM option_chain WHERE oi > 1000 ORDER BY oi DESC";
 *   System.out.println(SqlHighlighter.highlight(sql));
 * </pre>
 */
public final class SqlHighlighter {

    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "IS", "NULL",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
            "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "CROSS",
            "GROUP", "BY", "ORDER", "ASC", "DESC", "HAVING",
            "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
            "AS", "CASE", "WHEN", "THEN", "ELSE", "END",
            "COUNT", "SUM", "AVG", "MIN", "MAX",
            "LIKE", "BETWEEN", "EXISTS", "ANY",
            "WITH", "RECURSIVE", "OVER", "PARTITION", "ROW", "ROWS",
            "CAST", "COALESCE", "IF", "IIF", "NULLIF"
    );

    private static final Set<String> TYPES = Set.of(
            "INT", "INTEGER", "BIGINT", "SMALLINT", "TINYINT",
            "VARCHAR", "CHAR", "TEXT", "BLOB", "BOOLEAN", "BOOL",
            "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC", "REAL",
            "DATE", "TIME", "TIMESTAMP", "DATETIME", "INTERVAL"
    );

    private static final Pattern STRING_PATTERN = Pattern.compile("'[^']*'");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("--[^\n]*");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");

    private SqlHighlighter() {}

    /**
     * Highlight a SQL string with ANSI colors.
     */
    public static String highlight(String sql) {
        if (sql == null || sql.isEmpty() || !Ansi.isEnabled()) return sql;

        StringBuilder result = new StringBuilder(sql.length() * 2);
        int pos = 0;

        while (pos < sql.length()) {
            // Try comment
            Matcher commentMatcher = COMMENT_PATTERN.matcher(sql).region(pos, sql.length());
            if (commentMatcher.lookingAt()) {
                result.append(Ansi.dim(commentMatcher.group()));
                pos = commentMatcher.end();
                continue;
            }

            // Try string literal
            Matcher stringMatcher = STRING_PATTERN.matcher(sql).region(pos, sql.length());
            if (stringMatcher.lookingAt()) {
                result.append(Ansi.green(stringMatcher.group()));
                pos = stringMatcher.end();
                continue;
            }

            // Try number
            Matcher numberMatcher = NUMBER_PATTERN.matcher(sql).region(pos, sql.length());
            if (numberMatcher.lookingAt()) {
                result.append(Ansi.cyan(numberMatcher.group()));
                pos = numberMatcher.end();
                continue;
            }

            // Try keyword/identifier
            Matcher wordMatcher = WORD_PATTERN.matcher(sql).region(pos, sql.length());
            if (wordMatcher.lookingAt()) {
                String word = wordMatcher.group();
                String upper = word.toUpperCase();
                if (KEYWORDS.contains(upper)) {
                    result.append(Ansi.bold(Ansi.blue(word)));
                } else if (TYPES.contains(upper)) {
                    result.append(Ansi.magenta(word));
                } else {
                    result.append(word);
                }
                pos = wordMatcher.end();
                continue;
            }

            // Other character
            result.append(sql.charAt(pos));
            pos++;
        }

        return result.toString();
    }
}
