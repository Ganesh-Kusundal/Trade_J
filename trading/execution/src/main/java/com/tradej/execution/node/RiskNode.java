package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.NodeMetrics;
import com.tradej.pipeline.runtime.NodeMetricsTracker;
import com.tradej.pipeline.runtime.NodeState;
import com.tradej.pipeline.runtime.PipelineContext;
import com.tradej.pipeline.runtime.PipelineNode;

/**
 * Pipeline Node wrapper for the PositionRiskHandler.
 * Integrates pre-trade risk checks and signal qualification into the execution graph.
 */
public final class RiskNode implements PipelineNode {

    private final PositionRiskHandler riskHandler;
    private final NodeMetricsTracker metricsTracker = new NodeMetricsTracker();
    private PipelineNodeDef definition;
    private PipelineContext context;
    private volatile NodeState state = NodeState.PENDING;

    public RiskNode(PositionRiskHandler riskHandler) {
        this.riskHandler = riskHandler;
    }

    @Override
    public void init(PipelineNodeDef definition, PipelineContext context) {
        this.definition = definition;
        this.context = context;
        this.state = NodeState.RUNNING;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (state != NodeState.RUNNING) {
            return;
        }
        long startTime = System.nanoTime();
        boolean success = false;
        try {
            riskHandler.onDomainEvent(event, context::publish);
            success = true;
        } catch (Throwable e) {
            // Error handling - metrics will record failure
        } finally {
            long duration = System.nanoTime() - startTime;
            if (success) {
                metricsTracker.recordSuccess(duration);
            } else {
                metricsTracker.recordFailure(duration);
            }
        }
    }

    @Override
    public NodeState getState() {
        return state;
    }

    @Override
    public NodeMetrics getMetrics() {
        return metricsTracker.getMetrics();
    }

    @Override
    public void destroy() {
        this.state = NodeState.HALTED;
    }
}
