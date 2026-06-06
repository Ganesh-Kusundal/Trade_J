package com.tradej.brokergateway.query;

import java.util.List;
import java.util.Map;

/**
 * Tabular result set from a SQL query.
 *
 * @param columns          ordered column names
 * @param rows             list of row maps (column name → value)
 * @param executionTimeMs  wall-clock execution time in milliseconds
 * @param sql              the SQL that produced this result
 */
public record QueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        long executionTimeMs,
        String sql
) {
    public QueryResult {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    public int rowCount() {
        return rows.size();
    }

    public int columnCount() {
        return columns.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public Object value(int row, String column) {
        return rows.get(row).get(column);
    }
}
