package com.tradej.strategy.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.strategy.service.GraphStrategySandbox;

/**
 * Pipeline node wrapper that evaluates {@link GraphStrategySandbox} plugins
 * (tick, depth, multi-event, candle).
 * <p>
 * Graph strategy plugins receive any event type they subscribe to.
 */
public final class StrategyNode extends BasePipelineNode {

    private final GraphStrategySandbox graphSandbox;

    public StrategyNode(GraphStrategySandbox graphSandbox) {
        this.graphSandbox = graphSandbox;
    }

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) {
        if (graphSandbox != null) {
            graphSandbox.onDomainEvent(event, context::publish);
        }
    }
}
