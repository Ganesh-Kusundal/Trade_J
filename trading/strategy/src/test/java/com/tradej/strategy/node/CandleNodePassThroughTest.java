package com.tradej.strategy.node;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.Candle;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.GraphCompiler;
import com.tradej.pipeline.runtime.GraphRuntime;
import com.tradej.pipeline.runtime.IngressNode;
import com.tradej.pipeline.runtime.PipelineContext;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class CandleNodePassThroughTest {

    @Test
    void passesCandleClosedToDownstreamInDagMode() {
        List<Candle> downstream = new ArrayList<>();
        PipelineGraph graph = new PipelineGraph(
                "candle-pass-through",
                "Test",
                1,
                List.of(
                        new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Ingress", Map.of()),
                        new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle", Map.of()),
                        new PipelineNodeDef("sink-1", PipelineNodeTypes.INGRESS, "Sink", Map.of())
                ),
                List.of(
                        new PipelineEdgeDef("e1", "ingress-1", "candle-1"),
                        new PipelineEdgeDef("e2", "candle-1", "sink-1")
                ),
                PipelineExecutionMode.DAG
        );

        GraphCompiler compiler = new GraphCompiler(def -> switch (def.type()) {
            case PipelineNodeTypes.INGRESS -> {
                if ("sink-1".equals(def.id())) {
                    yield new CollectingSink(downstream);
                }
                yield new IngressNode();
            }
            case PipelineNodeTypes.CANDLE -> new CandleNode(new com.tradej.strategy.service.CandleAggregationService(List.of("5m")));
            default -> throw new IllegalArgumentException(def.type());
        });

        PipelineContext context = new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
            }

            @Override
            public long getClockTimeMs() {
                return System.currentTimeMillis();
            }

            @Override
            public <T> Optional<T> getService(Class<T> serviceType) {
                return Optional.empty();
            }
        };

        GraphRuntime runtime = new GraphRuntime(compiler.compile(graph, context, null), graph);
        Candle candle = new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true);
        runtime.onEvent(new CandleClosed(EventMetadata.root(), candle));

        assertEquals(1, downstream.size());
        assertEquals(candle.symbol(), downstream.getFirst().symbol());
    }

    private static final class CollectingSink extends BasePipelineNode {
        private final List<Candle> downstream;

        private CollectingSink(List<Candle> downstream) {
            this.downstream = downstream;
        }

        @Override
        protected void onInit() {
        }

        @Override
        protected void processEvent(DomainEvent event) {
            if (event instanceof CandleClosed closed) {
                downstream.add(closed.candle());
            }
        }
    }
}
