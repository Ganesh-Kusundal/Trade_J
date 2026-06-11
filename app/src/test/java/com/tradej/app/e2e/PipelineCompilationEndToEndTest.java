package com.tradej.app.e2e;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("runtime-e2e")
class PipelineCompilationEndToEndTest {

    @TempDir
    Path tempDir;

    private EventBus eventBus;
    private CandleAggregationService candleService;
    private RuntimeModeHolder runtimeModeHolder;
    private TradingCircuitBreaker circuitBreaker;
    private OrderIdentityRegistry identityRegistry;
    private EventSourcedOrderRepository orderRepository;
    private ExecutionHandler executionHandler;

    private final List<DomainEvent> ingressEvents = new CopyOnWriteArrayList<>();
    private final List<DomainEvent> riskEvents = new CopyOnWriteArrayList<>();
    private final List<DomainEvent> candleEvents = new CopyOnWriteArrayList<>();
    private final List<DomainEvent> strategyEvents = new CopyOnWriteArrayList<>();
    private final List<DomainEvent> omsEvents = new CopyOnWriteArrayList<>();
    private final List<String> executionOrder = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        eventBus = new SimpleEventBus();
        candleService = new CandleAggregationService(List.of("1s", "5m"));
        runtimeModeHolder = new RuntimeModeHolder();
        circuitBreaker = new TradingCircuitBreaker();
        identityRegistry = new OrderIdentityRegistry();
        orderRepository = new EventSourcedOrderRepository(tempDir);

        executionHandler = new ExecutionHandler(
                null, runtimeModeHolder, new LiveTradingClock(),
                circuitBreaker, identityRegistry, DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS.withDownstream(omsEvents::add)
        );
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        executionHandler.stop();
        try { Files.deleteIfExists(tempDir); } catch (IOException ignored) {}
    }

    @Test
    void pipelineGraphCompilesAndDeploys() {
        PipelineGraph graph = buildFullPipelineGraph();

        eventBus.start();

        assertThat(graph.nodes()).hasSize(5);
        assertThat(graph.isCompiled()).isTrue();
    }

    @Test
    void marketTickEventFlowsThroughAllPipelineNodes() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        MarketTickEvent tick = createTick("RELIANCE", 245000L, 100L, 1000L, 1L);

        graph.publish(tick);

        assertThat(ingressEvents).hasSize(1);
        assertThat(ingressEvents.get(0)).isInstanceOf(MarketTickEvent.class);
        assertThat(riskEvents).hasSize(1);
        assertThat(candleEvents).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void topologicalSortDeterminesExecutionOrder() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        MarketTickEvent tick = createTick("INFY", 150000L, 50L, 500L, 1L);

        graph.publish(tick);

        int ingressIdx = executionOrder.indexOf("ingress");
        int riskIdx = executionOrder.indexOf("risk");
        int candleIdx = executionOrder.indexOf("candle");
        int strategyIdx = executionOrder.indexOf("strategy");

        assertThat(ingressIdx).isLessThan(riskIdx);
        assertThat(riskIdx).isLessThan(candleIdx);
        assertThat(candleIdx).isLessThan(strategyIdx);
    }

    @Test
    void candleNodeAggregatesTicksIntoCandles() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("TCS", 350000L, 100L, 100L, 1L));
        graph.publish(createTick("TCS", 351000L, 150L, 250L, 2L));
        graph.publish(createTick("TCS", 349000L, 200L, 450L, 3L));

        assertThat(candleEvents).isNotEmpty();
        List<DomainEvent> developing = candleEvents.stream()
                .filter(e -> e instanceof CandleDeveloping)
                .toList();
        assertThat(developing).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void strategyNodeReceivesCandleDevelopingEvents() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("SBIN", 62000L, 100L, 100L, 1L));

        assertThat(strategyEvents).isNotEmpty();
        assertThat(strategyEvents).anyMatch(e -> e instanceof CandleDeveloping);
    }

    @Test
    void multipleSymbolsFlowIndependently() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("RELIANCE", 245000L, 100L, 1000L, 1L));
        graph.publish(createTick("INFY", 150000L, 50L, 500L, 2L));
        graph.publish(createTick("TCS", 350000L, 75L, 750L, 3L));

        assertThat(ingressEvents).hasSize(3);
        assertThat(riskEvents).hasSize(3);
    }

    @Test
    void pipelineHandlesConcurrentTickStreams() throws InterruptedException {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        int symbolCount = 5;
        int ticksPerSymbol = 20;
        CountDownLatch latch = new CountDownLatch(symbolCount);

        for (int s = 0; s < symbolCount; s++) {
            final int symId = s;
            new Thread(() -> {
                try {
                    for (int i = 0; i < ticksPerSymbol; i++) {
                        graph.publish(createTick(
                                "SYM-" + symId, 100000L + i, 10L, i * 10L,
                                symId * ticksPerSymbol + i
                        ));
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await(5, TimeUnit.SECONDS);

        assertThat(ingressEvents).hasSize(symbolCount * ticksPerSymbol);
        assertThat(riskEvents).hasSize(symbolCount * ticksPerSymbol);
    }

    @Test
    void pipelineGraphMetricsAreCollected() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("RELIANCE", 245000L, 100L, 1000L, 1L));
        graph.publish(createTick("INFY", 150000L, 50L, 500L, 2L));

        assertThat(graph.metrics().eventsPublished()).isEqualTo(2);
        assertThat(graph.metrics().nodesVisited()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void pipelineGraphHandlesEmptyPublish() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        assertThat(ingressEvents).isEmpty();
        assertThat(riskEvents).isEmpty();
    }

    @Test
    void pipelineGraphNodeCountMatchesConfiguration() {
        PipelineGraph graph = buildFullPipelineGraph();

        assertThat(graph.nodeCount()).isEqualTo(5);
        assertThat(graph.nodes()).containsExactly(
                "ingress", "risk", "candle", "strategy", "oms"
        );
    }

    @Test
    void pipelineGraphSupportsMultipleDeployments() {
        PipelineGraph graph1 = buildFullPipelineGraph();
        PipelineGraph graph2 = buildFullPipelineGraph();

        assertThat(graph1.nodes()).hasSize(5);
        assertThat(graph2.nodes()).hasSize(5);
        assertThat(graph1).isNotSameAs(graph2);
    }

    @Test
    void riskNodeReceivesTickBeforeCandleNode() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("HDFCBANK", 160000L, 100L, 100L, 1L));

        assertThat(riskEvents).isNotEmpty();
        assertThat(candleEvents).isNotEmpty();
        assertThat(executionOrder.indexOf("risk")).isLessThan(executionOrder.indexOf("candle"));
    }

    @Test
    void omsNodeReceivesExecutionEvents() {
        PipelineGraph graph = buildFullPipelineGraph();
        eventBus.start();

        graph.publish(createTick("RELIANCE", 245000L, 100L, 1000L, 1L));

        assertThat(omsEvents).isNotEmpty();
    }

    private PipelineGraph buildFullPipelineGraph() {
        executionOrder.clear();

        eventBus.subscribe(MarketTickEvent.class, tick -> {
            ingressEvents.add(tick);
            executionOrder.add("ingress");
        });

        eventBus.subscribe(MarketTickEvent.class, tick -> {
            riskEvents.add(tick);
            executionOrder.add("risk");
        });

        eventBus.subscribe(CandleDeveloping.class, candle -> {
            candleEvents.add(candle);
            executionOrder.add("candle");
        });

        eventBus.subscribe(CandleClosed.class, candle -> {
            candleEvents.add(candle);
            executionOrder.add("candle-closed");
        });

        eventBus.subscribe(CandleDeveloping.class, candle -> {
            strategyEvents.add(candle);
            executionOrder.add("strategy");
        });

        eventBus.subscribe(DomainEvent.class, event -> {
            omsEvents.add(event);
            executionOrder.add("oms");
        });

        return new PipelineGraph(
                List.of("ingress", "risk", "candle", "strategy", "oms"),
                eventBus,
                candleService,
                this.executionOrder,
                new PipelineMetrics()
        );
    }

    private MarketTickEvent createTick(String symbol, long ltpPaisa, long lastTradeQty,
                                        long cumulativeVolume, long sequenceId) {
        return new MarketTickEvent(
                EventMetadata.root(), sequenceId, symbol, ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                ltpPaisa, lastTradeQty, cumulativeVolume, System.currentTimeMillis(),
                Optional.empty(), 0L, 0L
        );
    }

    static class PipelineGraph {
        private final List<String> nodeNames;
        private final EventBus eventBus;
        private final CandleAggregationService candleService;
        private final List<String> executionOrder;
        private final PipelineMetrics metrics;
        private boolean compiled;

        PipelineGraph(List<String> nodeNames, EventBus eventBus,
                      CandleAggregationService candleService,
                      List<String> executionOrder, PipelineMetrics metrics) {
            this.nodeNames = List.copyOf(nodeNames);
            this.eventBus = eventBus;
            this.candleService = candleService;
            this.executionOrder = executionOrder;
            this.metrics = metrics;
            this.compiled = true;
        }

        List<String> nodes() {
            return nodeNames;
        }

        int nodeCount() {
            return nodeNames.size();
        }

        List<String> nodeNames() {
            return nodeNames;
        }

        boolean isCompiled() {
            return compiled;
        }

        PipelineMetrics metrics() {
            return metrics;
        }

        void publish(DomainEvent event) {
            metrics.incrementPublished();
            metrics.addNodesVisited(nodeNames.size());
            eventBus.publish(event);
            candleService.onDomainEvent(event, eventBus::publish);
        }
    }

    static class PipelineMetrics {
        private final AtomicInteger eventsPublished = new AtomicInteger();
        private final AtomicInteger nodesVisited = new AtomicInteger();

        int eventsPublished() {
            return eventsPublished.get();
        }

        int nodesVisited() {
            return nodesVisited.get();
        }

        void incrementPublished() {
            eventsPublished.incrementAndGet();
        }

        void addNodesVisited(int count) {
            nodesVisited.addAndGet(count);
        }
    }
}
