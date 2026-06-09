package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.NodeMetrics;
import com.tradej.pipeline.runtime.NodeMetricsTracker;
import com.tradej.pipeline.runtime.NodeState;
import com.tradej.pipeline.runtime.PipelineContext;
import com.tradej.pipeline.runtime.PipelineNode;

/**
 * Pipeline Node wrapper for the OMS pipeline.
 * <p>
 * This is the single OMS pipeline node. It delegates all order placement and fill
 * reconciliation to the {@link ExecutionHandler}.
 *
 * <p>The ExecutionHandler must be constructed with its downstream consumer
 * configured via {@link com.tradej.execution.service.ExecutionConfig} so that
 * emitted events flow back into the pipeline automatically.
 */
public final class OmsNode implements PipelineNode {

    private final ExecutionHandler executionHandler;
    private final NodeMetricsTracker metricsTracker = new NodeMetricsTracker();
    private PipelineNodeDef definition;
    private PipelineContext context;
    private volatile NodeState state = NodeState.PENDING;

    public OmsNode(ExecutionHandler executionHandler) {
        this.executionHandler = executionHandler;
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
            executionHandler.onDomainEvent(event);
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
