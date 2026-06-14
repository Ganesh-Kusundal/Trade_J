package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.strategy.node.CandleNode;
import com.tradej.strategy.service.CandleAggregationService;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the CANDLE node type.
 */
public final class CandleNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.CANDLE;
    }

    @Override
    public String displayName() {
        return "Candle Aggregation";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "transformation",
                "Aggregates ticks into OHLCV candles",
                List.of(
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Incoming tick (deprecated)")
                ),
                List.of(
                        new NodeTypeDescriptor.EventType(CandleDeveloping.class, "Developing candle"),
                        new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")
                ),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        CandleAggregationService service = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_CANDLE_AGGREGATION_SERVICE, CandleAggregationService.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "transformation",
                "Aggregates ticks into OHLCV candles",
                List.of(
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Incoming tick (deprecated)")
                ),
                List.of(
                        new NodeTypeDescriptor.EventType(CandleDeveloping.class, "Developing candle"),
                        new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")
                ),
                Map.of(),
                def -> new CandleNode(service)));
    }
}
