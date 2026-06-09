package com.tradej.execution.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.pipeline.runtime.BasePipelineNode;

/**
 * Pipeline Node wrapper for the PositionRiskHandler.
 * Integrates pre-trade risk checks and signal qualification into the execution graph.
 */
public final class RiskNode extends BasePipelineNode {

    private final PositionRiskHandler riskHandler;

    public RiskNode(PositionRiskHandler riskHandler) {
        this.riskHandler = riskHandler;
    }

    @Override
    protected void onInit() {
        // No additional init needed for risk handler
    }

    @Override
    protected void processEvent(DomainEvent event) {
        riskHandler.onDomainEvent(event, context::publish);
    }
}
