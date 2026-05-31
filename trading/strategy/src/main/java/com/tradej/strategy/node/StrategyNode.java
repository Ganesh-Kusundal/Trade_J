package com.tradej.strategy.node;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.strategy.service.GraphStrategySandbox;
import com.tradej.strategy.service.StrategyEngine;

/**
 * Pipeline node wrapper that evaluates both candle-only {@link StrategyEngine} plugins
 * and {@link GraphStrategySandbox} plugins (tick, depth, multi-event).
 * <p>
 * Candle-only plugins receive {@link CandleClosed} events via the legacy engine.
 * Graph strategy plugins receive any event type they subscribe to.
 */
public final class StrategyNode extends BasePipelineNode {

    private final StrategyEngine strategyEngine;
    private final GraphStrategySandbox graphSandbox;

    public StrategyNode(StrategyEngine strategyEngine) {
        this(strategyEngine, null);
    }

    public StrategyNode(StrategyEngine strategyEngine, GraphStrategySandbox graphSandbox) {
        this.strategyEngine = strategyEngine;
        this.graphSandbox = graphSandbox;
    }

    @Override
    protected void onInit() {
        // no-op
    }

    @Override
    protected void processEvent(DomainEvent event) throws Exception {
        // LEGACY PATH: candle-only plugins via StrategyEngine
        if (event instanceof CandleClosed && strategyEngine != null) {
            strategyEngine.onDomainEvent(event, context::publish);
        }

        // GRAPH PATH: tick/depth/multi-event plugins via GraphStrategySandbox.
        // GraphStrategyPlugins that subscribe to CandleClosed are processed here,
        // NOT via the legacy engine, to prevent double evaluation.
        if (graphSandbox != null) {
            graphSandbox.onDomainEvent(event, context::publish);
        }
    }
}
