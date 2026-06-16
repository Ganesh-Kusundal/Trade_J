package com.tradej.app.e2e;

import com.tradej.app.health.AlertChannel;
import com.tradej.app.health.AlertManager;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.event.ReconciliationHaltRequired;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.time.LiveTradingClock;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.position.EventSourcedNetPositionProvider;
import com.tradej.execution.reconcile.OrderReconciler;
import com.tradej.execution.reconcile.ReconciliationAlertLogger;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Chaos test: simulates a WebSocket disconnect during active trading and
 * verifies the full recovery chain end-to-end.
 *
 * <h3>Scenario</h3>
 * <ol>
 *   <li>Order placed and filled at broker (100 shares)</li>
 *   <li>WebSocket disconnects mid-session — 20 fill confirmations lost</li>
 *   <li>OMS records 100 filled; broker reports only 80</li>
 *   <li>Reconciliation detects the 20-share mismatch</li>
 *   <li>ReconciliationHaltRequired event published (auto-halt=true)</li>
 *   <li>PositionRiskHandler engages kill switch on halt</li>
 *   <li>AlertManager fires CRITICAL alert to all channels</li>
 * </ol>
 *
 * <p>This is a deployment-blocking certification test per the stabilization
 * program: if this chain fails, the system cannot be trusted with real capital.
 */
@Tag("chaos")
class WebSocketKillMidTradeChaosTest {

    private Path tempDir;
    private EventSourcedOrderRepository omsRepo;
    private IBrokerConnection brokerConnection;
    private PortfolioProvider portfolioProvider;
    private CapturingEventBus eventBus;
    private RecordingAlertChannel alertChannel;
    private AlertManager alertManager;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("ws-kill-chaos-");
        omsRepo = new EventSourcedOrderRepository(tempDir.resolve("oms"));

        brokerConnection = mock(IBrokerConnection.class);
        portfolioProvider = mock(PortfolioProvider.class);
        when(brokerConnection.portfolio()).thenReturn(portfolioProvider);

        eventBus = new CapturingEventBus();

        alertChannel = new RecordingAlertChannel();
        // Short cooldown so all alerts in test are captured
        alertManager = new AlertManager(List.of(alertChannel), Duration.ofMillis(1));
    }

    @AfterEach
    void tearDown() throws IOException {
        omsRepo.close();
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        }
    }

    @Test
    void webSocketKillMidTrade_reconciliationDetectsMismatch_haltFires_alertPaged() {
        // ── Step 1: Simulate a filled order in OMS (100 shares) ──────────
        omsRepo.append(OrderSubmitted.create("ORD-WS-1", "sig-ws-1", "RELIANCE", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-WS-1", "BROKER-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-WS-1", 100, 2500_00L));

        // ── Step 2: WebSocket disconnect — broker lost 20 fill confirmations ──
        // Broker reports only 80 shares for RELIANCE
        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("RELIANCE", ExchangeSegment.NSE_EQ, Side.LONG, 80, 2500_00L, 2510_00L, 2000_00L)
        ));

        // ── Step 3: Wire the full reconciliation chain ────────────────────
        ReconciliationAlertLogger alertLogger = new ReconciliationAlertLogger(eventBus, true, 0L);
        eventBus.subscribe(PositionMismatch.class, alertLogger);

        EventSourcedNetPositionProvider netPositionProvider = new EventSourcedNetPositionProvider();
        PositionRiskHandler riskHandler = new PositionRiskHandler(
                new RiskLimits(5000_00L, 3, 10_000_00L, 10, 10),
                netPositionProvider
        );
        eventBus.subscribe(ReconciliationHaltRequired.class, event -> {
            riskHandler.handleReconciliationHalt(event);
            alertManager.critical("reconciliation", "Position mismatch halt: " + event.symbol()
                    + " expected=" + event.expectedQuantity() + " broker=" + event.brokerQuantity());
        });

        // ── Step 4: Run reconciliation ───────────────────────────────────
        OrderReconciler reconciler = new OrderReconciler(
                omsRepo, brokerConnection,
                new EventMetadataFactory(new LiveTradingClock())
        );
        reconciler.reconcileAll(eventBus::publish);

        // ── Step 5: Verify the chain ─────────────────────────────────────

        // 5a: PositionMismatch was published
        List<PositionMismatch> mismatches = eventBus.eventsOfType(PositionMismatch.class);
        assertFalse(mismatches.isEmpty(),
                "CHAOS FAIL: No PositionMismatch — reconciliation missed the lost fills");
        PositionMismatch mismatch = mismatches.getFirst();
        assertEquals("RELIANCE", mismatch.symbol());
        assertEquals(100, mismatch.paperQuantity(), "OMS should show 100 filled");
        assertEquals(80, mismatch.brokerQuantity(), "Broker should show 80 (20 lost)");
        assertEquals("oms-reconcile", mismatch.engineKey());

        // 5b: ReconciliationHaltRequired was published
        List<ReconciliationHaltRequired> halts = eventBus.eventsOfType(ReconciliationHaltRequired.class);
        assertFalse(halts.isEmpty(),
                "CHAOS FAIL: No ReconciliationHaltRequired — auto-halt should fire on mismatch");
        ReconciliationHaltRequired halt = halts.getFirst();
        assertEquals("RELIANCE", halt.symbol());
        assertEquals(100, halt.expectedQuantity());
        assertEquals(80, halt.brokerQuantity());

        // 5c: PositionRiskHandler engaged the kill switch
        assertTrue(riskHandler.isReconciliationHaltActive(),
                "CHAOS FAIL: Kill switch should be active after reconciliation halt");
        assertTrue(riskHandler.isKillSwitchActive(),
                "CHAOS FAIL: Kill switch should be engaged on reconciliation halt");

        // 5d: AlertManager fired a CRITICAL alert
        assertFalse(alertChannel.alerts.isEmpty(),
                "CHAOS FAIL: AlertManager did not fire — operators would not know about mismatch");
        var alert = alertChannel.alerts.getFirst();
        assertEquals("CRITICAL", alert.severity(), "Alert should be CRITICAL severity");
        assertEquals("reconciliation", alert.component(), "Alert component should be 'reconciliation'");
        assertTrue(alert.message().contains("RELIANCE"), "Alert should mention the symbol");
    }

    @Test
    void webSocketKill_multipleOrdersAcrossSymbols_allMismatchesDetected() {
        omsRepo.append(OrderSubmitted.create("ORD-M1", "sig-m1", "RELIANCE", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-M1", "BROKER-M1"));
        omsRepo.append(OrderFullyFilled.event("ORD-M1", 100, 2500_00L));

        omsRepo.append(OrderSubmitted.create("ORD-M2", "sig-m2", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-M2", "BROKER-M2"));
        omsRepo.append(OrderFullyFilled.event("ORD-M2", 50, 3200_00L));

        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("RELIANCE", ExchangeSegment.NSE_EQ, Side.LONG, 80, 2500_00L, 2510_00L, 2000_00L),
                new Position("TCS", ExchangeSegment.NSE_EQ, Side.LONG, 30, 3200_00L, 3210_00L, 1600_00L)
        ));

        ReconciliationAlertLogger alertLogger = new ReconciliationAlertLogger(eventBus, true, 0L);
        eventBus.subscribe(PositionMismatch.class, alertLogger);

        OrderReconciler reconciler = new OrderReconciler(
                omsRepo, brokerConnection,
                new EventMetadataFactory(new LiveTradingClock())
        );
        reconciler.reconcileAll(eventBus::publish);

        assertEquals(2, eventBus.eventsOfType(PositionMismatch.class).size(),
                "Should detect 2 mismatches — one per symbol");
        assertEquals(2, eventBus.eventsOfType(ReconciliationHaltRequired.class).size(),
                "Should fire halt for both symbols");
    }

    @Test
    void brokerConnectionFailure_duringReconciliation_handledGracefully() {
        omsRepo.append(OrderSubmitted.create("ORD-F1", "sig-f1", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-F1", "BROKER-F1"));
        omsRepo.append(OrderFullyFilled.event("ORD-F1", 50, 3200_00L));

        when(portfolioProvider.getPositions()).thenThrow(new RuntimeException("Broker unreachable"));

        ReconciliationAlertLogger alertLogger = new ReconciliationAlertLogger(eventBus, true, 0L);
        eventBus.subscribe(PositionMismatch.class, alertLogger);

        OrderReconciler reconciler = new OrderReconciler(
                omsRepo, brokerConnection,
                new EventMetadataFactory(new LiveTradingClock())
        );
        assertDoesNotThrow(() -> reconciler.reconcileAll(eventBus::publish),
                "reconcileAll must not throw on broker connection failure");

        // Broker unreachable → treated as zero position → mismatch
        assertFalse(eventBus.eventsOfType(PositionMismatch.class).isEmpty(),
                "Should detect mismatch when broker unreachable (treats as zero)");
    }

    @Test
    void noMismatch_scenarioDoesNotFireAlerts() {
        omsRepo.append(OrderSubmitted.create("ORD-OK", "sig-ok", "RELIANCE", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-OK", "BROKER-OK"));
        omsRepo.append(OrderFullyFilled.event("ORD-OK", 100, 2500_00L));

        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("RELIANCE", ExchangeSegment.NSE_EQ, Side.LONG, 100, 2500_00L, 2510_00L, 2500_00L)
        ));

        ReconciliationAlertLogger alertLogger = new ReconciliationAlertLogger(eventBus, true, 0L);
        eventBus.subscribe(PositionMismatch.class, alertLogger);

        OrderReconciler reconciler = new OrderReconciler(
                omsRepo, brokerConnection,
                new EventMetadataFactory(new LiveTradingClock())
        );
        reconciler.reconcileAll(eventBus::publish);

        assertTrue(eventBus.eventsOfType(PositionMismatch.class).isEmpty(),
                "Should not detect mismatches when positions match exactly");
        assertTrue(eventBus.eventsOfType(ReconciliationHaltRequired.class).isEmpty(),
                "Should not fire halt when positions match");
        assertTrue(alertChannel.alerts.isEmpty(),
                "Should not fire alerts when no mismatch exists");
    }

    // ── Test infrastructure ──────────────────────────────────────────

    /**
     * Event bus that captures all published events and dispatches to
     * exact-type subscribers (synchronous, single-threaded like SimpleEventBus).
     */
    private static final class CapturingEventBus implements EventBus {
        private final List<DomainEvent> allEvents = new ArrayList<>();
        private final Map<Class<?>, List<DomainEventHandler<?>>> subscribers = new HashMap<>();

        @Override
        public void publish(DomainEvent event) {
            allEvents.add(event);
            List<DomainEventHandler<?>> handlers = subscribers.get(event.getClass());
            if (handlers != null) {
                for (DomainEventHandler<?> handler : handlers) {
                    @SuppressWarnings("unchecked")
                    DomainEventHandler<DomainEvent> h = (DomainEventHandler<DomainEvent>) handler;
                    h.onEvent(event);
                }
            }
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            subscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            List<DomainEventHandler<?>> handlers = subscribers.get(eventType);
            if (handlers != null) {
                handlers.remove(handler);
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @SuppressWarnings("unchecked")
        <T extends DomainEvent> List<T> eventsOfType(Class<T> type) {
            List<T> result = new ArrayList<>();
            for (DomainEvent e : allEvents) {
                if (type.isInstance(e)) {
                    result.add((T) e);
                }
            }
            return result;
        }
    }

    /**
     * Records alert calls for verification.
     */
    private static final class RecordingAlertChannel implements AlertChannel {
        final List<AlertRecord> alerts = new ArrayList<>();

        @Override
        public void send(String severity, String component, String message) {
            alerts.add(new AlertRecord(severity, component, message));
        }

        record AlertRecord(String severity, String component, String message) {}
    }
}
