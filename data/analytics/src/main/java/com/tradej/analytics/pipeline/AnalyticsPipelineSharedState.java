package com.tradej.analytics.pipeline;

import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.port.RollingOptionHistoricalRepository;

import java.util.Map;

/**
 * Shared-state keys for {@code HistoricalDataNode} when executed inside DAG pipelines.
 */
public final class AnalyticsPipelineSharedState {

    public static final String HISTORICAL_BAR_REPOSITORY = "historicalBarRepository";
    public static final String ROLLING_OPTION_REPOSITORY = "rollingOptionHistoricalRepository";

    private AnalyticsPipelineSharedState() {
    }

    public static Map<String, Object> create(
            HistoricalBarRepository historicalBarRepository,
            RollingOptionHistoricalRepository rollingOptionHistoricalRepository
    ) {
        return Map.of(
                HISTORICAL_BAR_REPOSITORY, historicalBarRepository,
                ROLLING_OPTION_REPOSITORY, rollingOptionHistoricalRepository
        );
    }
}
