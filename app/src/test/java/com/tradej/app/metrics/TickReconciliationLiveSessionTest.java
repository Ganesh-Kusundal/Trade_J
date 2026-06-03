package com.tradej.app.metrics;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.execution.reconcile.TickReconciler;
import com.tradej.hotpath.MarketDataPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("component")
class TickReconciliationLiveSessionTest {

    private TickReconciler reconciler;
    private MarketDataPipeline pipeline;
    private List<MarketTickEvent> processedTicks;
    private Path reportPath;

    @BeforeEach
    void setUp() throws IOException {
        reconciler = new TickReconciler();
        processedTicks = new ArrayList<>();
        pipeline = new MarketDataPipeline(event -> {
            if (event instanceof MarketTickEvent tick) {
                processedTicks.add(tick);
            }
        });
        reportPath = Paths.get("logs/reconciliation/tick-reconciliation.log");
        Files.deleteIfExists(reportPath);
        Files.createDirectories(reportPath.getParent());
    }

    @Test
    void executeTickReconciliationLiveSession() throws IOException {
        System.out.println("Starting simulated Live Session Tick Reconciliation...");

        String[] symbols = {"NIFTY", "BANKNIFTY", "RELIANCE", "SBIN", "MCX GOLD"};
        List<TickReconciler.BrokerTick> brokerTicks = new ArrayList<>();

        // 1. Generate broker ticks for all target symbols
        long baseTime = System.currentTimeMillis();
        int ticksPerSymbol = 1000;
        long globalSeq = 1;

        for (String symbol : symbols) {
            ExchangeSegment segment = symbol.contains("GOLD") ? ExchangeSegment.MCX_COMM : ExchangeSegment.NSE_EQ;
            for (int i = 0; i < ticksPerSymbol; i++) {
                TickReconciler.BrokerTick brokerTick = new TickReconciler.BrokerTick(
                        globalSeq++,
                        symbol,
                        1000_00L + (i * 5),
                        50000L + i,
                        baseTime + (i * 10)
                );
                brokerTicks.add(brokerTick);

                // Simulate processing through the pipeline
                MarketTickEvent systemTick = new MarketTickEvent(
                        EventMetadata.root(),
                        brokerTick.sequenceId(),
                        brokerTick.symbol(),
                        segment,
                        FeedMode.TICKER,
                        brokerTick.ltpPaisa(),
                        10L,
                        brokerTick.cumulativeVolume(),
                        brokerTick.exchangeTimestampEpochMs(),
                        Optional.empty(),
                        0L,
                        0L
                );
                pipeline.onMarketTickEvent(systemTick);
            }
        }

        // 2. Run reconciliation for each symbol
        List<String> reportLines = new ArrayList<>();
        reportLines.add("=== TICK RECONCILIATION REPORT ===");
        reportLines.add("Timestamp: " + new java.util.Date());
        reportLines.add("");

        boolean allReconciled = true;

        for (String symbol : symbols) {
            TickReconciler.ReconciliationReport report = reconciler.reconcile(symbol, brokerTicks, processedTicks);

            String status = report.isReconciled() ? "SUCCESS" : "FAILED";
            String line = String.format("Symbol: %-10s | Status: %-7s | Broker Ticks: %5d | System Ticks: %5d | Missing: %d | Duplicates: %d | Out-of-Order: %d",
                    symbol, status, report.totalBrokerTicks(), report.totalSystemTicks(),
                    report.missingTicks().size(), report.duplicateTicks().size(), report.outOfOrderTicks().size());

            System.out.println(line);
            reportLines.add(line);

            if (!report.isReconciled()) {
                allReconciled = false;
            }

            // Assertions for each symbol
            assertTrue(report.isReconciled(), "Symbol " + symbol + " should be 100% reconciled");
            assertEquals(ticksPerSymbol, report.totalBrokerTicks());
            assertEquals(ticksPerSymbol, report.totalSystemTicks());
            assertTrue(report.missingTicks().isEmpty());
            assertTrue(report.duplicateTicks().isEmpty());
            assertTrue(report.outOfOrderTicks().isEmpty());
        }

        reportLines.add("");
        reportLines.add("Overall Status: " + (allReconciled ? "100% RECONCILED" : "RECONCILIATION FAILURE"));
        Files.write(reportPath, reportLines);

        assertTrue(allReconciled, "All symbols must be 100% reconciled");
        assertTrue(Files.exists(reportPath), "Reconciliation report log file should exist");
        System.out.println("Live Session Tick Reconciliation completed successfully. Report written to: " + reportPath);
    }

    @Test
    void detectsAnomaliesDuringLiveSession() {
        // Verify that the reconciler successfully detects anomalies (missing, duplicate, out-of-order)
        List<TickReconciler.BrokerTick> brokerTicks = List.of(
                new TickReconciler.BrokerTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                new TickReconciler.BrokerTick(2L, "SBIN", 751_00L, 1010L, 1001000L),
                new TickReconciler.BrokerTick(3L, "SBIN", 752_00L, 1020L, 1002000L)
        );

        // System has:
        // - Tick 1 (Normal)
        // - Tick 1 (Duplicate)
        // - Tick 3 (Normal)
        // - Tick 2 (Out-of-order)
        List<MarketTickEvent> systemTicks = List.of(
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                createSystemTick(1L, "SBIN", 750_00L, 1000L, 1000000L),
                createSystemTick(3L, "SBIN", 752_00L, 1020L, 1002000L),
                createSystemTick(2L, "SBIN", 751_00L, 1010L, 1001000L)
        );

        TickReconciler.ReconciliationReport report = reconciler.reconcile("SBIN", brokerTicks, systemTicks);

        assertFalse(report.isReconciled());
        assertEquals(3, report.totalBrokerTicks());
        assertEquals(4, report.totalSystemTicks());
        assertTrue(report.missingTicks().isEmpty(), "No missing ticks because all broker ticks are present in system");
        assertEquals(1, report.duplicateTicks().size(), "Should detect 1 duplicate tick");
        assertEquals(1, report.outOfOrderTicks().size(), "Should detect 1 out-of-order tick (Tick 2 arrived after Tick 3)");
        assertEquals(2L, report.outOfOrderTicks().get(0).sequenceId());
    }

    private MarketTickEvent createSystemTick(long seqId, String symbol, long ltp, long volume, long exchangeTime) {
        return new MarketTickEvent(
                EventMetadata.root(),
                seqId,
                symbol,
                ExchangeSegment.NSE_EQ,
                FeedMode.TICKER,
                ltp,
                10L,
                volume,
                exchangeTime,
                Optional.empty(),
                0L,
                0L
        );
    }
}
