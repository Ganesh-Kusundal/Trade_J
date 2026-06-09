package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineNodeDef;

/**
 * Unified execution node abstraction.
 * All strategy, indicator, risk, OMS, partition, or ingress processors must
 * implement this interface to be part of the topological execution engine.
 */
public interface PipelineNode {
    
    /** Initializes the node with its static configuration and graph context. */
    void init(PipelineNodeDef definition, PipelineContext context);

    /** Processes an incoming event. Any side-effect events are published to context. */
    void onEvent(DomainEvent event);

    /** Returns the current runtime state of the node. */
    NodeState getState();

    /** Returns the performance metrics for the node. */
    NodeMetrics getMetrics();

    /** Cleanly releases any resources or connections. */
    void destroy();
}
