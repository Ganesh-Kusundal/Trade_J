package com.tradej.strategy.node;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.strategy.service.CandleAggregationService;

/**
 * Pipeline node wrapper for {@link CandleAggregationService}.
 * Aggregates ticks into developing and closed candles on the hot path.
 */
public final class CandleNode extends BasePipelineNode {

    private final CandleAggregationService candleService;

    public CandleNode(CandleAggregationService candleService) {
        this.candleService = candleService;
    }

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) throws Exception {
        if (event instanceof CandleClosed closed) {
            context.publish(closed);
            return;
        }
        candleService.onDomainEvent(event, context::publish);
    }
}
