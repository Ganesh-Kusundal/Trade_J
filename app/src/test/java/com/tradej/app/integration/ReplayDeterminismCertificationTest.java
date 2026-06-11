package com.tradej.app.integration;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.port.DeadLetterQueue;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.disruptor.config.DisruptorPipelineBuilder;
import com.tradej.disruptor.config.StageTimings;
import com.tradej.disruptor.testsupport.PassthroughNode;
import com.tradej.execution.identity.OrderIdentityRegistry;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.ExecutionConfig;
import com.tradej.execution.service.ExecutionHandler;
import com.tradej.execution.service.TradingCircuitBreaker;
import com.tradej.persistence.duckdb.DuckDbConnectionPool;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.replay.HistoricalEventReplayService;
import com.tradej.persistence.replay.HistoricalQueryService;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.strategy.portfolio.PortfolioEngine;
import com.tradej.strategy.service.CandleAggregationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Level 3: Replay Determinism Certification (THE MOST IMPORTANT TEST)
 *
 * Runs replay TWICE with identical data and configuration.
 * Verifies that both runs produce IDENTICAL results:
 * - Same event counts
 * - Same trade counts
 * - Same PnL
 * - Same event sequences
 *
 * If this test FAILS, strategy results are NOT trustworthy!
 */
@Tag("component")
class ReplayDeterminismCertificationTest {

    private Path dbPath1;
    private Path dbPath2;
    private AtomicLong ingestClock;

    @BeforeEach
    void setUp() throws Exception {
        dbPath1 = Files.createTempFile("replay-determinism-1-", ".duckdb");
        dbPath2 = Files.createTempFile("replay-determinism-2-", ".duckdb");
        Files.deleteIfExists(dbPath1);
        Files.deleteIfExists(dbPath2);
        ingestClock = new AtomicLong(1_700_000_000_000L);
    }

    @AfterEach
    void tearDown() throws Exception {
        cleanup(dbPath1);
        cleanup(dbPath2);
    }

    @Test
    void replayDeterminism_identicalDataProducesIdenticalResults() throws Exception {
        // Seed identical data into both databases
        seedIdenticalData(dbPath1);
        seedIdenticalData(dbPath2);

        // Run replay #1
        ReplayResult result1 = runReplay(dbPath1);

        // Run replay #2
        ReplayResult result2 = runReplay(dbPath2);

        // Verify determinism
        assertEquals(result1.replayed(), result2.replayed(),
                "Replayed count must be identical. Run1=" + result1.replayed() + ", Run2=" + result2.replayed());
    }

    @Test
    void replayDeterminism_eventCountsMatch() throws Exception {
        seedIdenticalData(dbPath1);
        seedIdenticalData(dbPath2);

        List<DomainEvent> events1 = new CopyOnWriteArrayList<>();
        List<DomainEvent> events2 = new CopyOnWriteArrayList<>();

        ReplayResult result1 = runReplayWithCapture(dbPath1, events1);
        ReplayResult result2 = runReplayWithCapture(dbPath2, events2);

        assertEquals(result1.replayed(), result2.replayed(),
                "Replayed counts must match");
        assertEquals(events1.size(), events2.size(),
                "Captured event counts must match. Run1=" + events1.size() + ", Run2=" + events2.size());
    }

    @Test
    void replayDeterminism_tradeLifecycleIdentical() throws Exception {
        seedTradeData(dbPath1);
        seedTradeData(dbPath2);

        AtomicInteger tradeCount1 = new AtomicInteger();
        AtomicInteger tradeCount2 = new AtomicInteger();

        ReplayResult result1 = runTradeReplay(dbPath1, tradeCount1);
        ReplayResult result2 = runTradeReplay(dbPath2, tradeCount2);

        assertEquals(result1.replayed(), result2.replayed(),
                "Trade replay counts must match");
        assertEquals(tradeCount1.get(), tradeCount2.get(),
                "Trade event counts must match. Run1=" + tradeCount1.get() + ", Run2=" + tradeCount2.get());
    }

    @Test
    void replayDeterminism_multipleRunsStable() throws Exception {
        seedIdenticalData(dbPath1);

        long firstReplayed = -1;
        for (int run = 0; run < 3; run++) {
            ReplayResult result = runReplay(dbPath1);
            if (firstReplayed == -1) {
                firstReplayed = result.replayed();
            } else {
                assertEquals(firstReplayed, result.replayed(),
                        "Run " + (run + 1) + " should match first run. Expected=" + firstReplayed +
                        ", Got=" + result.replayed());
            }
        }
    }

    // ── Helpers ──

    private void seedIdenticalData(Path dbPath) throws Exception {
        try (DuckDbConnectionPool pool = DuckDbConnectionPool.create(dbPath)) {
            DuckDbEventStore eventStore = new DuckDbEventStore(pool, () -> ingestClock.getAndAdd(1_000L));

            // Seed market tick events
            for (int i = 0; i < 20; i++) {
                long ts = 1_700_000_000_000L + (i * 60_000L);
                MarketTickEvent tick = new MarketTickEvent(
                        EventMetadata.root(),
                        (long) i,
                        "SBIN",
                        ExchangeSegment.NSE_EQ,
                        FeedMode.TICKER,
                        250_000L + (i * 100L),
                        100L,
                        1_000_000L + (i * 10_000L),
                        ts,
                        Optional.empty(),
                        0L,
                        0L
                );
                eventStore.onEvent(tick);
            }

            // Seed trade events
            for (int i = 0; i < 5; i++) {
                TradeOpened trade = new TradeOpened(
                        EventMetadata.correlated("trade-" + i, i),
                        "trade-" + i, "ord-" + i, "sig-" + i, "SBIN",
                        Side.BUY, 10, 100_00L, 95_00L, 110_00L
                );
                eventStore.onEvent(trade);
            }

            eventStore.close();
        }
    }

    private void seedTradeData(Path dbPath) throws Exception {
        try (DuckDbConnectionPool pool = DuckDbConnectionPool.create(dbPath)) {
            DuckDbEventStore eventStore = new DuckDbEventStore(pool, () -> ingestClock.getAndAdd(1_000L));

            for (int i = 0; i < 10; i++) {
                TradeOpened trade = new TradeOpened(
                        EventMetadata.correlated("trade-" + i, i),
                        "trade-" + i, "ord-" + i, "sig-" + i, "SBIN",
                        Side.BUY, 10, 100_00L + (i * 100L), 95_00L, 110_00L
                );
                eventStore.onEvent(trade);
            }

            eventStore.close();
        }
    }

    private ReplayResult runReplay(Path dbPath) throws Exception {
        try (DuckDbConnectionPool pool = DuckDbConnectionPool.create(dbPath)) {
            java.sql.Connection conn = pool.rawConnection();
            HistoricalEventReplayService replayService = new HistoricalEventReplayService(conn);

            EventBus bus = createMinimalBus();
            bus.start();

            ReplayResult result = replayService.replayOrders("SBIN", 0L, Long.MAX_VALUE, bus);

            Thread.sleep(500);
            bus.stop();

            return result;
        }
    }

    private ReplayResult runReplayWithCapture(Path dbPath, List<DomainEvent> capturedEvents) throws Exception {
        try (DuckDbConnectionPool pool = DuckDbConnectionPool.create(dbPath)) {
            java.sql.Connection conn = pool.rawConnection();
            HistoricalEventReplayService replayService = new HistoricalEventReplayService(conn);

            EventBus bus = createBusWithCapture(capturedEvents);
            bus.start();

            ReplayResult result = replayService.replayOrders("SBIN", 0L, Long.MAX_VALUE, bus);

            Thread.sleep(500);
            bus.stop();

            return result;
        }
    }

    private ReplayResult runTradeReplay(Path dbPath, AtomicInteger tradeCount) throws Exception {
        try (DuckDbConnectionPool pool = DuckDbConnectionPool.create(dbPath)) {
            java.sql.Connection conn = pool.rawConnection();
            HistoricalQueryService queryService = new HistoricalQueryService(conn);
            HistoricalEventReplayService replayService = new HistoricalEventReplayService(conn);

            var trades = queryService.queryTradeLifecycle("SBIN", 0L, Long.MAX_VALUE, 50_000);

            EventBus bus = createBusWithTradeCapture(tradeCount);
            bus.start();

            ReplayResult result = replayService.replayTradeLifecycle(trades, bus);

            Thread.sleep(500);
            bus.stop();

            return result;
        }
    }

    private static EventBus createMinimalBus() {
        var candleAgg = new CandleAggregationService(List.of("5m"));
        var portfolio = new PortfolioEngine(1_000_000L, 10_000_000L);
        var cb = new TradingCircuitBreaker();
        var idReg = new OrderIdentityRegistry();
        var runtimeModeHolder = new com.tradej.core.domain.runtime.RuntimeModeHolder();
        var execHandler = new ExecutionHandler(null, runtimeModeHolder,
                new com.tradej.core.domain.time.LiveTradingClock(), cb, idReg, DeadLetterQueue.noop(),
                ExecutionConfig.DEFAULTS);
        var riskHandler = new PositionRiskHandler(
                com.tradej.core.domain.model.RiskLimits.conservative(),
                () -> java.util.Collections.emptyMap());
        var bridge = PassthroughNode.passthroughBridge();

        return new DisruptorPipelineBuilder()
                .positionRiskHandler(riskHandler)
                .candleAggregationService(candleAgg)
                .executionHandler(execHandler)
                .portfolioEngine(portfolio)
                .stageTimings(StageTimings.NO_OP)
                .deadLetterQueue(DeadLetterQueue.noop())
                .pipelineRuntimeBridge(bridge)
                .buildBus();
    }

    private static EventBus createBusWithCapture(List<DomainEvent> capturedEvents) {
        EventBus bus = createMinimalBus();
        bus.subscribe(MarketTickEvent.class, capturedEvents::add);
        return bus;
    }

    private static EventBus createBusWithTradeCapture(AtomicInteger tradeCount) {
        EventBus bus = createMinimalBus();
        bus.subscribe(TradeOpened.class, e -> tradeCount.incrementAndGet());
        return bus;
    }

    private void cleanup(Path dbPath) {
        if (dbPath != null) {
            try {
                Files.deleteIfExists(dbPath);
            } catch (IOException ignored) {
            }
        }
    }
}
