package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineNodeDef;

/**
 * Extensible base class for pipeline nodes.
 * Centralizes state management, thread-safe performance metrics tracking,
 * and high-precision execution latency measurement.
 */
public abstract class BasePipelineNode implements PipelineNode {

    protected PipelineNodeDef definition;
    protected PipelineContext context;
    protected volatile NodeState state = NodeState.PENDING;

    // Encapsulated thread-safe metrics tracker
    protected final NodeMetricsTracker metricsTracker = new NodeMetricsTracker();

    @Override
    public final void init(PipelineNodeDef definition, PipelineContext context) {
        this.definition = definition;
        this.context = context;
        this.state = NodeState.RUNNING;
        onInit();
    }

    /** Hook method executed when the node is initialized. */
    protected abstract void onInit();

    @Override
    public final void onEvent(DomainEvent event) {
        if (state != NodeState.RUNNING) {
            return;
        }
        long startTime = System.nanoTime();
        boolean success = false;
        try {
            processEvent(event);
            success = true;
        } catch (Throwable e) {
            onError(event, e);
        } finally {
            long duration = System.nanoTime() - startTime;
            if (success) {
                metricsTracker.recordSuccess(duration);
            } else {
                metricsTracker.recordFailure(duration);
            }
        }
    }

    /** Core event processing logic to be implemented by subclass nodes. */
    protected abstract void processEvent(DomainEvent event);

    /** Hook method to handle exceptions thrown during event processing. */
    protected void onError(DomainEvent event, Throwable t) {
        // Default: no-op, can be overridden by subclasses
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
    public final void destroy() {
        this.state = NodeState.HALTED;
        onDestroy();
    }

    /** Hook method executed when the node is destroyed/stopped. */
    protected void onDestroy() {
        // Default: no-op, can be overridden by subclasses
    }
}
