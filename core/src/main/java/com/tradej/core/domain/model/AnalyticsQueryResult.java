package com.tradej.core.domain.model;

import java.util.List;
import java.util.Map;

public record AnalyticsQueryResult(
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount,
        long elapsedMs
) {
}
