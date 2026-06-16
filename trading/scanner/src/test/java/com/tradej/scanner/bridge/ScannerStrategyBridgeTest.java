package com.tradej.scanner.bridge;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.value.Side;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
@DisplayName("ScannerStrategyBridge: ScanResultsPublished → SignalGenerated")
class ScannerStrategyBridgeTest {

    private SimpleEventBus eventBus;
    private CopyOnWriteArrayList<SignalGenerated> capturedSignals;
    private DomainEventHandler<SignalGenerated> signalCapture;

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
        eventBus.start();
        capturedSignals = new CopyOnWriteArrayList<>();
        signalCapture = capturedSignals::add;
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario A: Single hit → single SignalGenerated emitted
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A: Single qualifying hit → SignalGenerated emitted with correct fields")
    void singleHit_emitsSignalWithCorrectFields() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        ScanResultsPublished scan = scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 85.0, List.of("volume-spike", "rsi-overbought"))
        ));

        eventBus.publish(scan);

        assertEquals(1, capturedSignals.size(), "One qualifying hit must produce one signal");

        SignalGenerated signal = capturedSignals.get(0);
        assertEquals("RELIANCE", signal.symbol());
        assertEquals(Side.BUY, signal.side());
        assertEquals("scanner", signal.interval());
        assertEquals("scanner-hit", signal.setup());
        assertEquals(0L, signal.entryPricePaisa(), "Entry price is 0 (LTP injected at execution)");
        assertEquals(0L, signal.stopLossPaisa());
        assertEquals(0L, signal.takeProfitPaisa());

        // Attributes carry scanner context
        assertEquals(25L, signal.attributes().get("quantity"));
        assertEquals("NSE_EQ", signal.attributes().get("exchangeSegment"));
        assertEquals("volume-spike,rsi-overbought", signal.attributes().get("criterionTypes"));
        assertEquals(85.0, signal.attributes().get("scannerScore"));
        assertEquals("scanner-signal", signal.attributes().get("setup"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario B: Multiple hits → multiple signals
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("B: Multiple hits → one SignalGenerated per hit")
    void multipleHits_emitsOneSignalPerHit() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        ScanResultsPublished scan = scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 90.0, List.of("volume")),
                hit("TCS", "NSE_EQ", "TCS", 75.0, List.of("breakout")),
                hit("INFY", "NSE_EQ", "INFY", 60.0, List.of("momentum"))
        ));

        eventBus.publish(scan);

        assertEquals(3, capturedSignals.size());

        List<String> symbols = capturedSignals.stream().map(SignalGenerated::symbol).toList();
        assertTrue(symbols.contains("RELIANCE"));
        assertTrue(symbols.contains("TCS"));
        assertTrue(symbols.contains("INFY"));

        // Verify each signal has unique signalId
        long distinctIds = capturedSignals.stream().map(SignalGenerated::signalId).distinct().count();
        assertEquals(3, distinctIds, "Each signal must have a unique signalId");

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario C: Score below threshold → suppressed
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("C: Hit with score below default threshold → suppressed (no SignalGenerated)")
    void lowScoreHit_suppressed() {
        // Default minScore = 0.1
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        ScanResultsPublished scan = scanResult(List.of(
                hit("PENNY", "NSE_EQ", "PENNY", 0.05, List.of("low-score"))
        ));

        eventBus.publish(scan);

        assertTrue(capturedSignals.isEmpty(),
                "Hit with score 0.05 below default threshold 0.1 must be suppressed");

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario D: Custom minScore threshold
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("D: Custom minScore=50 — hit with score 45 suppressed, score 75 passes")
    void customMinScore_passesOnlyAboveThreshold() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus, 50.0, 10L);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        ScanResultsPublished scan = scanResult(List.of(
                hit("LOW", "NSE_EQ", "LOW", 45.0, List.of("weak")),
                hit("HIGH", "NSE_EQ", "HIGH", 75.0, List.of("strong"))
        ));

        eventBus.publish(scan);

        assertEquals(1, capturedSignals.size());
        assertEquals("HIGH", capturedSignals.get(0).symbol());
        assertEquals(75.0, capturedSignals.get(0).attributes().get("scannerScore"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario E: Custom quantity in attributes
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("E: Custom defaultQuantity=100 → signal attribute quantity is 100")
    void customQuantity_preservedInAttributes() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus, 0.1, 100L);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        eventBus.publish(scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 80.0, List.of("test"))
        )));

        assertEquals(1, capturedSignals.size());
        assertEquals(100L, capturedSignals.get(0).attributes().get("quantity"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario F: Empty hits list → no signals
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("F: Empty hits list → zero SignalGenerated events")
    void emptyHits_producesZeroSignals() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        eventBus.publish(scanResult(List.of()));

        assertTrue(capturedSignals.isEmpty(), "Empty hits must produce zero signals");

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario G: stop() prevents further signals
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("G: After stop() → scan hits are ignored (no signals emitted)")
    void stopped_ignoresScanResults() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        // Publish while running — should emit
        eventBus.publish(scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 90.0, List.of("pre-stop"))
        )));
        assertEquals(1, capturedSignals.size());

        bridge.stop();

        // Publish after stop — should NOT emit
        eventBus.publish(scanResult(List.of(
                hit("TCS", "NSE_EQ", "TCS", 85.0, List.of("post-stop"))
        )));

        assertEquals(1, capturedSignals.size(),
                "After stop(), no more signals should be emitted");
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario H: start() is idempotent
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("H: start() is idempotent — double start produces one signal per hit")
    void doubleStart_stillProducesOneSignalPerHit() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();
        bridge.start(); // Second call should be no-op

        eventBus.publish(scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 80.0, List.of("test"))
        )));

        assertEquals(1, capturedSignals.size(),
                "Double start() must not double-subscribe (exactly 1 signal expected)");

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario I: Unknown exchange segment → defaults to NSE_EQ
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("I: Unknown exchange segment → defaults to NSE_EQ in attributes")
    void unknownSegment_defaultsToNseEq() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        eventBus.publish(scanResult(List.of(
                hit("GOLD", "INVALID_SEG", "GOLD", 70.0, List.of("commodity"))
        )));

        assertEquals(1, capturedSignals.size());
        assertEquals("NSE_EQ", capturedSignals.get(0).attributes().get("exchangeSegment"),
                "Unknown segment must default to NSE_EQ");

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario J: Null reasons → defaults to "scanner"
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("J: Null/empty reasons → criterionTypes defaults to 'scanner'")
    void nullReasons_defaultsToScanner() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        eventBus.publish(scanResult(List.of(
                hit("RELIANCE", "NSE_EQ", "RELIANCE", 80.0, null)
        )));

        assertEquals(1, capturedSignals.size());
        assertEquals("scanner", capturedSignals.get(0).attributes().get("criterionTypes"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario K: isRunning() reflects lifecycle
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("K: isRunning() reflects start/stop lifecycle")
    void isRunning_reflectsLifecycle() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        assertFalse(bridge.isRunning());
        bridge.start();
        assertTrue(bridge.isRunning());
        bridge.stop();
        assertFalse(bridge.isRunning());
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario L: Hit with empty reasons list → defaults to "scanner"
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("L: Empty reasons list → criterionTypes defaults to 'scanner'")
    void emptyReasons_defaultsToScanner() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        eventBus.publish(scanResult(List.of(
                hit("TCS", "NSE_EQ", "TCS", 75.0, List.of())
        )));

        assertEquals(1, capturedSignals.size());
        assertEquals("scanner", capturedSignals.get(0).attributes().get("criterionTypes"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Scenario M: profileId and runId preserved in attributes
    // ════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("M: profileId and runId preserved in SignalGenerated attributes")
    void profileAndRunId_preservedInAttributes() {
        ScannerStrategyBridge bridge = new ScannerStrategyBridge(eventBus);

        eventBus.subscribe(SignalGenerated.class, signalCapture);
        bridge.start();

        ScanResultsPublished scan = new ScanResultsPublished(
                EventMetadata.root(),
                "nifty-volume-scanner",
                "run-2026-001",
                1,
                System.currentTimeMillis() - 1000,
                System.currentTimeMillis(),
                List.of(hit("RELIANCE", "NSE_EQ", "RELIANCE", 92.0, List.of("volume")))
        );

        eventBus.publish(scan);

        assertEquals(1, capturedSignals.size());
        assertEquals("nifty-volume-scanner", capturedSignals.get(0).attributes().get("profileId"));
        assertEquals("run-2026-001", capturedSignals.get(0).attributes().get("scannerRunId"));

        bridge.stop();
    }

    // ════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════

    private static ScanResultsPublished scanResult(List<ScanResultsPublished.ScanHitSummary> hits) {
        return new ScanResultsPublished(
                EventMetadata.root(),
                "test-profile",
                "test-run",
                hits.size(),
                System.currentTimeMillis() - 1000,
                System.currentTimeMillis(),
                hits
        );
    }

    private static ScanResultsPublished.ScanHitSummary hit(
            String symbol, String exchangeSegment, String underlying,
            double score, List<String> reasons) {
        return new ScanResultsPublished.ScanHitSummary(
                symbol, exchangeSegment, underlying, score,
                reasons != null ? reasons : List.of()
        );
    }
}
