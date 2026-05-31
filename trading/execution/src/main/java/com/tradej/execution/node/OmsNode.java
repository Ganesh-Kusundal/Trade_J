package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.pipeline.runtime.BasePipelineNode;

/**
 * Pipeline Node wrapper for the OMS pipeline.
 * <p>
 * This node delegates to the {@link ExecutionHandler} for backward compatibility.
 * For graph-mode execution, prefer wiring the decomposed nodes directly:
 * <ol>
 *   <li>{@link SignalGateNode} — pre-trade gating (kill switch, circuit breaker)</li>
 *   <li>{@link OrderPlacementNode} — broker order placement with OMS event sourcing</li>
 *   <li>{@link FillReconciliationNode} — fill reconciliation and trade lifecycle</li>
 * </ol>
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
