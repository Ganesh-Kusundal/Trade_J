package com.tradej.optimizer;

/**
 * PHASE 6 of the Trade-J platform transformation.
 *
 * <p>Provides abstractions for running parameter optimization over any
 * {@link com.tradej.pipeline.platform.PipelineDefinition} pipeline type
 * (scanner, strategy, replay, analytics, execution).
 *
 * <p>Implementations must be provided for:
 * {@code GridSearchEngine}, {@code WalkForwardEngine},
 * {@code MonteCarloEngine}, {@code ParameterSweepEngine}.
 */
class PackageInfo {
    private PackageInfo() {}
}
