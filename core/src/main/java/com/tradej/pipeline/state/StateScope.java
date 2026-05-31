package com.tradej.pipeline.state;

/**
 * Defines the ownership scope of a pipeline node's state.
 * <p>
 * Used by the graph compiler to decide:
 * <ul>
 *   <li>Whether to wrap a node in {@link com.tradej.pipeline.runtime.PartitionedNode}</li>
 *   <li>Which {@link StateStore} implementation to inject</li>
 *   <li>How to snapshot/restore state across replay sessions</li>
 * </ul>
 */
public enum StateScope {

    /**
     * One state instance per symbol partition.
     * Example: candles, indicators, positions.
     * In a 4-shard system, 4 independent state instances exist.
     */
    PER_SYMBOL,

    /**
     * One state instance per graph deployment.
     * Example: scanner aggregate results, portfolio allocation.
     */
    PER_GRAPH,

    /**
     * One state instance for the entire runtime.
     * Example: global risk limits, circuit breaker state, kill switch.
     */
    GLOBAL
}
