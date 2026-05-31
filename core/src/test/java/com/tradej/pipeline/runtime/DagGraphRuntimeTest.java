package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.model.Candle;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineExecutionMode;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class DagGraphRuntimeTest {

    @Test
    void routesEventsAlongEdgesFromIngress() {
        VirtualClock clock = new VirtualClock(VirtualClock.Mode.LIVE);
        CopyOnWriteArrayList<DomainEvent> collected = new CopyOnWriteArrayList<>();

        PipelineGraph graph = new PipelineGraph(
                "dag-test",
                "DAG Test",
                1,
                List.of(
                        new PipelineNodeDef("ingress-1", PipelineNodeTypes.INGRESS, "Ingress", Map.of()),
                        new PipelineNodeDef("sink-1", PipelineNodeTypes.INGRESS, "Sink", Map.of())
                ),
                List.of(new PipelineEdgeDef("e1", "ingress-1", "sink-1")),
                PipelineExecutionMode.DAG
        );

        GraphCompiler compiler = new GraphCompiler(def -> {
            if ("sink-1".equals(def.id())) {
                return new CollectingNode(collected);
            }
            return new IngressNode();
        });

        PipelineContext context = new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
            }

            @Override
            public long getClockTimeMs() {
                return clock.currentTimeMillis();
            }

            @Override
            public <T> Optional<T> getService(Class<T> serviceType) {
                return Optional.empty();
            }
        };

        GraphRuntime runtime = new GraphRuntime(compiler.compile(graph, context, null), graph);
        CandleClosed closed = new CandleClosed(
                EventMetadata.root(),
                new Candle("SBIN", "5m", 1L, 2L, 1L, 2L, 1L, 2L, 10L, true)
        );

        runtime.onEvent(closed);

        assertEquals(1, collected.size());
        assertEquals(closed, collected.getFirst());
    }

    private static final class CollectingNode extends BasePipelineNode {
        private final List<DomainEvent> collected;

        private CollectingNode(List<DomainEvent> collected) {
            this.collected = collected;
        }

        @Override
        protected void onInit() {
        }

        @Override
        protected void processEvent(DomainEvent event) {
            collected.add(event);
        }
    }
}
