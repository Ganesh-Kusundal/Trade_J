package com.tradej.strategy.node;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.strategy.portfolio.PortfolioEngine;

/**
 * Pipeline Node wrapper for the PortfolioEngine.
 * Integrates portfolio-level capital allocation and net exposure limits into the execution graph.
 */
public final class PortfolioNode extends BasePipelineNode {

    private final PortfolioEngine portfolioEngine;

    public PortfolioNode(PortfolioEngine portfolioEngine) {
        this.portfolioEngine = portfolioEngine;
    }

    @Override
    protected void onInit() {
        // No additional init needed for PortfolioEngine
    }

    @Override
    protected void processEvent(DomainEvent event) {
        portfolioEngine.onDomainEvent(event, context::publish);
    }
}
