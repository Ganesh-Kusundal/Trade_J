package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineNodeDef;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Extensible base class for pipeline nodes.
 * Centralizes state management, thread-safe performance metrics tracking,
 * and high-precision execution latency measurement.
 */
public abstract class BasePipelineNode implements PipelineNode {

    protected PipelineNodeDef definition;
    protected PipelineContext context;
    protected volatile NodeState state = NodeState.PENDING;

    // High-performance, thread-safe metrics counters
    private final AtomicLong processedCount = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();
    private final AtomicLong lastProcessedTimestampMs = new AtomicLong();
    private final AtomicLong lastExecutionNs = new AtomicLong();
    private final AtomicLong totalExecutionNs = new AtomicLong();

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
        try {
            processEvent(event);
            lastProcessedTimestampMs.set(System.currentTimeMillis());
        } catch (Exception e) {
            errorCount.incrementAndGet();
            onError(event, e);
        } finally {
            long duration = System.nanoTime() - startTime;
            processedCount.incrementAndGet();
            lastExecutionNs.set(duration);
            totalExecutionNs.addAndGet(duration);
        }
    }

    /** Core event processing logic to be implemented by subclass nodes. */
    protected abstract void processEvent(DomainEvent event) throws Exception;

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
        long count = processedCount.get();
        double avg = count == 0 ? 0.0 : (double) totalExecutionNs.get() / count;
        return new NodeMetrics(
                count,
                errorCount.get(),
                lastProcessedTimestampMs.get(),
                lastExecutionNs.get(),
                avg
        );
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
