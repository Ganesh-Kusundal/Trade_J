package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class GraphRuntimeTest {

    @Test
    void processSequentialInvokesNodesInTopologicalOrder() {
        AtomicInteger order = new AtomicInteger();

        PipelineGraph graph = new PipelineGraph(
                "test",
                "test",
                1,
                List.of(
                        new PipelineNodeDef("a", "A", "A", Map.of()),
                        new PipelineNodeDef("b", "B", "B", Map.of()),
                        new PipelineNodeDef("c", "C", "C", Map.of())
                ),
                List.of(
                        new PipelineEdgeDef("e1", "a", "b"),
                        new PipelineEdgeDef("e2", "b", "c")
                )
        );

        GraphCompiler compiler = new GraphCompiler(def -> new BasePipelineNode() {
            @Override
            protected void onInit() {
            }

            @Override
            protected void processEvent(DomainEvent event) {
                order.updateAndGet(current -> current * 10 + switch (definition.id()) {
                    case "a" -> 1;
                    case "b" -> 2;
                    case "c" -> 3;
                    default -> 0;
                });
            }
        });

        PipelineContext context = new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
            }

            @Override
            public long getClockTimeMs() {
                return 0L;
            }

            @Override
            public <T> java.util.Optional<T> getService(Class<T> serviceType) {
                return java.util.Optional.empty();
            }
        };

        GraphRuntime runtime = new GraphRuntime(compiler.compile(graph, context), graph);
        runtime.processSequential(new TickReceived(
                EventMetadata.root(),
                "NIFTY",
                "1m",
                100L,
                1L,
                1L,
                System.currentTimeMillis(),
                null
        ));

        assertEquals(123, order.get());
    }
}
