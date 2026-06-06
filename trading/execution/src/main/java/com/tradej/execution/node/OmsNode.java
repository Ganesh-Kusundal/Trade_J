package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.BasePipelineNode;

/**
 * Pipeline Node wrapper for the OMS pipeline.
 * <p>
 * This is the single OMS pipeline node. It delegates all order placement and fill
 * reconciliation to the {@link ExecutionHandler}.
 */
public final class OmsNode extends BasePipelineNode {

    private final ExecutionHandler executionHandler;

    public OmsNode(ExecutionHandler executionHandler) {
        this.executionHandler = executionHandler;
    }

    @Override
    protected void onInit() {
    }

    @Override
    protected void processEvent(DomainEvent event) throws Exception {
        executionHandler.onDomainEvent(event, context::publish);
    }
}
