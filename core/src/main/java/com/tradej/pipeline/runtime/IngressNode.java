package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;

/**
 * Pass-through ingress node for DAG-mode graphs.
 * External events enter here and propagate to downstream edges via {@code context.publish()}.
 */
public final class IngressNode extends BasePipelineNode {

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) {
        context.publish(event);
    }
}
