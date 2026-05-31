package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.strategy.node.CandleNode;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies graph-runtime candle aggregation parity across two identical sequential passes.
 */
@Tag("component")
class GraphPipelineReplayParityTest {

    @Test
    void sequentialGraphPipelineProducesIdenticalCandleHashes() {
        VirtualClock clock = new VirtualClock(VirtualClock.Mode.REPLAY);

        List<Candle> firstPass = new ArrayList<>();
        List<Candle> secondPass = new ArrayList<>();

        GraphRuntime firstRuntime = buildRuntime(clock, firstPass);
        GraphRuntime secondRuntime = buildRuntime(clock, secondPass);

        long baseMs = 1_700_000_000_000L;
        for (int i = 0; i < 120; i++) {
            TickReceived tick = new TickReceived(
                    EventMetadata.root(),
                    "SBIN",
                    "1s",
                    100_000L + i,
                    10L,
                    1_000L + i,
                    baseMs + (i * 1_000L),
                    null
            );
            clock.advanceVirtualTimeMs(tick.exchangeTimestampMs());
            firstRuntime.processSequential(tick);
        }

        for (int i = 0; i < 120; i++) {
            TickReceived tick = new TickReceived(
                    EventMetadata.root(),
                    "SBIN",
                    "1s",
                    100_000L + i,
                    10L,
                    1_000L + i,
                    baseMs + (i * 1_000L),
                    null
            );
            clock.advanceVirtualTimeMs(tick.exchangeTimestampMs());
            secondRuntime.processSequential(tick);
        }

        assertEquals(firstPass.size(), secondPass.size());
        assertEquals(hashCloses(firstPass), hashCloses(secondPass));
    }

    private static GraphRuntime buildRuntime(VirtualClock clock, List<Candle> closes) {
        CandleAggregationService candleService = new CandleAggregationService(List.of("1s"));
        PipelineGraph graph = new PipelineGraph(
                "parity-test",
                "parity",
                1,
                List.of(new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle", Map.of())),
                List.of()
        );

        GraphCompiler compiler = new GraphCompiler(def -> new CandleNode(candleService));
        PipelineContext context = new PipelineContext() {
            @Override
            public void publish(DomainEvent event) {
                if (event instanceof CandleClosed closed) {
                    closes.add(closed.candle());
                }
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

        ExecutionPlan plan = compiler.compile(graph, context, context::publish);
        return new GraphRuntime(plan, graph);
    }

    private static long hashCloses(List<Candle> candles) {
        AtomicLong hash = new AtomicLong(17L);
        for (Candle candle : candles) {
            hash.updateAndGet(h -> h * 31L + candle.startTimeMs());
            hash.updateAndGet(h -> h * 31L + candle.closePaisa());
            hash.updateAndGet(h -> h * 31L + candle.volume());
        }
        return hash.get();
    }
}
