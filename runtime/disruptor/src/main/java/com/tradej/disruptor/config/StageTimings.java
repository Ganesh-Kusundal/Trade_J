package com.tradej.disruptor.config;

/**
 * Aggregates per-stage timing callbacks for the five-stage Disruptor pipeline.
 *
 * <p>Each field corresponds to a pipeline stage in processing order:
 * <ol>
 *   <li>{@code risk} — PositionRiskDisruptorHandler (position/portfolio checks)</li>
 *   <li>{@code candle} — CandleAggregationDisruptorHandler (tick → candle aggregation)</li>
 *   <li>{@code strategy} — StrategyDisruptorHandler (strategy plugin evaluation)</li>
 *   <li>{@code execution} — ExecutionDisruptorHandler (signal → execution enqueue)</li>
 *   <li>{@code dispatch} — AsyncDispatchHandler (subscriber dispatch / persistence)</li>
 * </ol>
 *
 * <p>A singleton {@link #NO_OP} instance is provided for paths where timing is
 * not configured. Micrometer-backed instances are created by the Spring
 * configuration layer which has access to {@code MeterRegistry}.
 *
 * @param risk      stage 1: position risk qualification
 * @param candle    stage 2: candle aggregation
 * @param strategy  stage 3: strategy evaluation
 * @param execution stage 4: execution handler
 * @param dispatch  stage 5: async subscriber dispatch
 */
public record StageTimings(
        StageTiming risk,
        StageTiming candle,
        StageTiming strategy,
        StageTiming execution,
        StageTiming dispatch
) {
    public static final StageTimings NO_OP = new StageTimings(
            StageTiming.noOp(),
            StageTiming.noOp(),
            StageTiming.noOp(),
            StageTiming.noOp(),
            StageTiming.noOp()
    );
}
