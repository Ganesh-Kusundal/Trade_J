package com.tradej.disruptor;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.node.RiskNode;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineEdgeDef;
import com.tradej.pipeline.graph.PipelineGraph;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.runtime.*;
import com.tradej.strategy.node.CandleNode;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import com.tradej.strategy.service.StrategyEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies compiled graph execution through {@link DisruptorEventBus} matches direct
 * {@link GraphRuntime#processSequential(DomainEvent)} semantics for candle aggregation.
 */
@Tag("component")
class DisruptorGraphReplayParityTest {

    @Test
    void disruptorGraphPipelineMatchesDirectSequentialRuntime() throws Exception {
        VirtualClock clock = new VirtualClock(VirtualClock.Mode.REPLAY);
        clock.enterReplayMode();

        List<Candle> directCloses = new ArrayList<>();
        List<Candle> disruptorCloses = new CopyOnWriteArrayList<>();

        GraphRuntime directRuntime = buildRuntime(clock, directCloses, graphWithRiskAndCandle());
        EventBus disruptorBus = buildDisruptorBus(clock, graphWithRiskAndCandle());

        disruptorBus.subscribe(CandleClosed.class, closed -> disruptorCloses.add(closed.candle()));
        disruptorBus.start();

        long baseMs = 1_700_000_000_000L;
        for (int i = 0; i < 120; i++) {
            TickReceived tick = tick("SBIN", baseMs + (i * 1_000L), 100_000L + i, i);
            clock.advanceVirtualTimeMs(tick.exchangeTimestampMs());
            directRuntime.processSequential(tick);
            disruptorBus.publish(tick);
        }

        Thread.sleep(400);
        disruptorBus.stop();
        clock.enterLiveMode();

        assertEquals(directCloses.size(), disruptorCloses.size());
        assertEquals(hashCloses(directCloses), hashCloses(disruptorCloses));
    }

    private static PipelineGraph graphWithRiskAndCandle() {
        return new PipelineGraph(
                "parity-disruptor",
                "Parity Test Graph",
                1,
                List.of(
                        new PipelineNodeDef("risk-1", PipelineNodeTypes.RISK, "Risk", java.util.Map.of()),
                        new PipelineNodeDef("candle-1", PipelineNodeTypes.CANDLE, "Candle", java.util.Map.of())
                ),
                List.of(new PipelineEdgeDef("e1", "risk-1", "candle-1"))
        );
    }

    private static GraphRuntime buildRuntime(
            VirtualClock clock,
            List<Candle> closes,
            PipelineGraph graph
    ) {
        CandleAggregationService candleService = new CandleAggregationService(List.of("1s"));
        PositionRiskHandler riskHandler = riskHandler();

        Function<PipelineNodeDef, PipelineNode> factory = def -> switch (def.type()) {
            case PipelineNodeTypes.RISK -> new RiskNode(riskHandler);
            case PipelineNodeTypes.CANDLE -> new CandleNode(candleService);
            default -> throw new IllegalArgumentException("Unexpected node type: " + def.type());
        };

        GraphCompiler compiler = new GraphCompiler(factory);
        PipelineContext context = pipelineContext(clock, closes);
        ExecutionPlan plan = compiler.compile(graph, context, context::publish);
        return new GraphRuntime(plan, graph);
    }

    private static EventBus buildDisruptorBus(
            VirtualClock clock,
            PipelineGraph graph
    ) {
        CandleAggregationService candleService = new CandleAggregationService(List.of("1s"));
        PositionRiskHandler riskHandler = riskHandler();
        PortfolioEngine portfolioEngine = new PortfolioEngine(1_000_000L, 10_000_000L);
        StrategyEngine strategyEngine = new StrategyEngine(List.of(), new com.tradej.core.domain.event.EventMetadataFactory(new com.tradej.core.domain.time.LiveTradingClock()));
        ExecutionHandler executionHandler = executionHandler();

        TestPipelineRuntimeBridge bridge = new TestPipelineRuntimeBridge(
                clock,
                graph,
                def -> switch (def.type()) {
                    case PipelineNodeTypes.RISK -> new RiskNode(riskHandler);
                    case PipelineNodeTypes.CANDLE -> new CandleNode(candleService);
                    default -> throw new IllegalArgumentException("Unexpected node type: " + def.type());
                }
        );

        return new DisruptorEventBus(
                riskHandler,
                candleService,
                strategyEngine,
                executionHandler,
                portfolioEngine,
                StageTimings.NO_OP,
                null,
                DeadLetterQueue.noop(),
                bridge
        );
    }

    private static PipelineContext pipelineContext(VirtualClock clock, List<Candle> closes) {
        return new PipelineContext() {
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
                if (serviceType.isInstance(clock)) {
                    return Optional.of(serviceType.cast(clock));
                }
                return Optional.empty();
            }
        };
    }

    private static PositionRiskHandler riskHandler() {
        PortfolioEngine portfolioEngine = new PortfolioEngine(1_000_000L, 10_000_000L);
        InstrumentResolver noopResolver = new InstrumentResolver() {
            @Override
            public Instrument resolve(InstrumentKey key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instrument getBySymbol(InstrumentKey key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<Instrument> allInstruments() {
                return List.of();
            }

            @Override
            public Instrument resolveBySecurityId(String id) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instrument requireDefinition(InstrumentKey key) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instrument resolvePayload(Object payload) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isLoaded() {
                return true;
            }

            @Override
            public int catalogSize() {
                return 0;
            }
        };
        return new PositionRiskHandler(RiskLimits.conservative(), () -> java.util.Collections.emptyMap());
    }

    private static ExecutionHandler executionHandler() {
        return new ExecutionHandler(
                null,
                new RuntimeModeHolder(),
                new TradingCircuitBreaker(),
                new OrderIdentityRegistry(),
                DeadLetterQueue.noop()
        );
    }

    private static TickReceived tick(String symbol, long exchangeMs, long ltpPaisa, int sequence) {
        return new TickReceived(
                EventMetadata.correlated("parity", sequence),
                symbol,
                "1s",
                ltpPaisa,
                10L,
                1_000L + sequence,
                exchangeMs,
                null
        );
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

    private static final class TestPipelineRuntimeBridge implements PipelineRuntimeBridge {
        private final VirtualClock clock;
        private final PipelineGraph graph;
        private final Function<PipelineNodeDef, PipelineNode> nodeFactory;
        private final AtomicReference<GraphRuntime> runtimeRef = new AtomicReference<>();
        private volatile PipelineGraph activeGraph;
        private volatile Consumer<DomainEvent> hotPathPublisher;

        private TestPipelineRuntimeBridge(
                VirtualClock clock,
                PipelineGraph graph,
                Function<PipelineNodeDef, PipelineNode> nodeFactory
        ) {
            this.clock = clock;
            this.graph = graph;
            this.nodeFactory = nodeFactory;
            this.activeGraph = graph;
        }

        @Override
        public void compileHotPath(Consumer<DomainEvent> hotPathPublisher) {
            this.hotPathPublisher = hotPathPublisher;
            reload(graph, hotPathPublisher);
        }

        @Override
        public void reload(PipelineGraph graph, Consumer<DomainEvent> hotPathPublisher) {
            GraphCompiler compiler = new GraphCompiler(nodeFactory);
            PipelineContext context = new PipelineContext() {
                @Override
                public void publish(DomainEvent event) {
                    // hot-path publisher injected by compiler
                }

                @Override
                public long getClockTimeMs() {
                    return clock.currentTimeMillis();
                }

                @Override
                public <T> Optional<T> getService(Class<T> serviceType) {
                    if (serviceType.isInstance(clock)) {
                        return Optional.of(serviceType.cast(clock));
                    }
                    return Optional.empty();
                }
            };
            ExecutionPlan plan = compiler.compile(graph, context, hotPathPublisher);
            runtimeRef.set(new GraphRuntime(plan, graph));
            activeGraph = graph;
        }

        @Override
        public AtomicReference<GraphRuntime> runtimeRef() {
            return runtimeRef;
        }

        @Override
        public PipelineGraph activeGraph() {
            return activeGraph;
        }
    }
}
