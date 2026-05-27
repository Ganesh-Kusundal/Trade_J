package com.tradej.execution.reconcile;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Component test that wires a real {@link EventSourcedOrderRepository} backed by
 * Chronicle Queue through the full reconciliation pipeline:
 *
 * <pre>
 *   ReplayViaCrash → EventSourcedOrderRepository → OrderReconciler → ReconciliationScheduler → FakeEventBus
 * </pre>
 *
 * <p>Uses a mock {@link IBrokerConnection} for broker position simulation
 * and a fake {@link EventBus} to capture emitted {@link PositionMismatch} events.
 *
 * <p>All tests provide a {@link NetPositionProvider} that returns expected positions
 * matching the broker's positions, so the expected-vs-broker ({@code reconcile()})
 * pass is silent and only the OSM-vs-broker ({@code reconcileAll()}) pass is verified.
 * The exception is {@link #handlesBrokerConnectionFailureGracefully()} which tests
 * the failure path where both pass catch blocks handle the error independently.
 */
@Tag("component")
class ReconciliationSchedulerComponentTest {

    private Path tempDir;
    private EventSourcedOrderRepository omsRepo;
    private IBrokerConnection brokerConnection;
    private PortfolioProvider portfolioProvider;
    private OrderReconciler reconciler;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("reconciler-component-");
        omsRepo = new EventSourcedOrderRepository(tempDir.resolve("oms"));

        // Mock broker connection
        brokerConnection = mock(IBrokerConnection.class);
        portfolioProvider = mock(PortfolioProvider.class);
        when(brokerConnection.portfolio()).thenReturn(portfolioProvider);

        // Real reconciler (shared across tests)
        reconciler = new OrderReconciler(omsRepo, brokerConnection);
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
    void detectsMismatchBetweenFilledOsmOrderAndBrokerPosition() {
        // Populate OSM: OrderSubmitted → OrderAcknowledged → OrderFullyFilled (100 qty)
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        // Broker reports only 80 qty for SBIN
        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 80, 150_00L, 151_00L, 100_00L)
        ));

        // Expected positions match broker → reconcile() pass is silent
        FakeEventBus eventBus = runScheduler(Map.of("SBIN", 80L));

        // Only the OSM-vs-broker pass should detect a mismatch
        assertFalse(eventBus.published.isEmpty(), "Should publish at least one event");
        PositionMismatch mismatch = (PositionMismatch) eventBus.published.getFirst();
        assertEquals("SBIN", mismatch.symbol());
        assertEquals(100, mismatch.paperQuantity()); // OSM says 100
        assertEquals(80, mismatch.brokerQuantity()); // Broker says 80
        assertEquals("oms-reconcile", mismatch.engineKey());
    }

    @Test
    void doesNotEmitMismatchWhenPositionsMatch() {
        // Populate OSM: filled order for 50 qty
        omsRepo.append(OrderSubmitted.create("ORD-2", "sig-2", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-2", "EX-002"));
        omsRepo.append(OrderFullyFilled.event("ORD-2", 50, 3_200_00L));

        // Broker reports matching position
        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("TCS", ExchangeSegment.NSE_EQ, Side.LONG, 50, 3_200_00L, 3_210_00L, 500_00L)
        ));

        // Expected positions match broker → reconcile() pass is silent
        FakeEventBus eventBus = runScheduler(Map.of("TCS", 50L));

        assertTrue(eventBus.published.isEmpty(), "No mismatches expected when positions match");
    }

    @Test
    void skipsNonFinalOrdersDuringReconciliation() {
        // Order submitted and acknowledged but not yet filled (SUBMITTED state — non-final)
        omsRepo.append(OrderSubmitted.create("ORD-3", "sig-3", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-3", "EX-003"));

        // Broker has a position — but OSM order is non-final, so reconciliation should skip it
        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 80, 150_00L, 151_00L, 100_00L)
        ));

        // Expected positions match broker → reconcile() pass is silent
        FakeEventBus eventBus = runScheduler(Map.of("SBIN", 80L));

        assertTrue(eventBus.published.isEmpty(), "Should skip non-final (SUBMITTED) orders");
    }

    @Test
    void reconcilesMultipleOrdersAcrossDifferentSymbols() {
        // Two filled orders
        omsRepo.append(OrderSubmitted.create("ORD-4", "sig-4", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-4", "EX-004"));
        omsRepo.append(OrderFullyFilled.event("ORD-4", 100, 150_00L));

        omsRepo.append(OrderSubmitted.create("ORD-5", "sig-5", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-5", "EX-005"));
        omsRepo.append(OrderFullyFilled.event("ORD-5", 50, 3_200_00L));

        // Broker has SBIN but not TCS
        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 100, 150_00L, 151_00L, 0L)
        ));

        // Expected positions match broker's SBIN → reconcile() passes for SBIN
        FakeEventBus eventBus = runScheduler(Map.of("SBIN", 100L));

        // Should detect TCS mismatch (0 in broker, 50 in OSM)
        assertEquals(1, eventBus.published.size(), "Should detect exactly one mismatch");
        PositionMismatch mismatch = (PositionMismatch) eventBus.published.getFirst();
        assertEquals("TCS", mismatch.symbol());
        assertEquals(50, mismatch.paperQuantity());
        assertEquals(0, mismatch.brokerQuantity());
    }

    @Test
    void handlesBrokerConnectionFailureGracefully() {
        // Filled order in OSM
        omsRepo.append(OrderSubmitted.create("ORD-6", "sig-6", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-6", "EX-006"));
        omsRepo.append(OrderFullyFilled.event("ORD-6", 100, 150_00L));

        // Broker throws when asked for positions — both passes catch independently
        when(portfolioProvider.getPositions()).thenThrow(new RuntimeException("Connection reset"));

        // Pass 1 (reconcile) catches the exception and logs it; pass 2 (reconcileAll)
        // handles it via findBrokerQuantityForSymbol which returns 0 on failure.
        // An OSM-vs-broker mismatch is emitted: OSM says 100, broker (treated as) 0
        FakeEventBus eventBus = runScheduler(Map.of());

        assertFalse(eventBus.published.isEmpty(), "Should emit mismatch treating broker failure as zero position");
        PositionMismatch mismatch = (PositionMismatch) eventBus.published.getFirst();
        assertEquals("SBIN", mismatch.symbol());
        assertEquals(100, mismatch.paperQuantity());
        assertEquals(0, mismatch.brokerQuantity());
    }

    @Test
    void rebuildsStateCorrectlyAfterChronicleQueueDurability() {
        // Write events to OSM repo
        omsRepo.append(OrderSubmitted.create("ORD-7", "sig-7", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-7", "EX-007"));
        omsRepo.append(OrderFullyFilled.event("ORD-7", 100, 150_00L));

        // Close and reopen to simulate durability across sessions
        omsRepo.close();
        EventSourcedOrderRepository freshRepo = new EventSourcedOrderRepository(tempDir.resolve("oms"));

        OrderReconciler freshReconciler = new OrderReconciler(freshRepo, brokerConnection);

        when(portfolioProvider.getPositions()).thenReturn(List.of(
                new Position("SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 80, 150_00L, 151_00L, 100_00L)
        ));

        // Expected positions match broker → reconcile() pass is silent
        FakeEventBus freshBus = runScheduler(freshReconciler, Map.of("SBIN", 80L));

        assertFalse(freshBus.published.isEmpty(), "Should reconstruct state from Chronicle Queue");
        PositionMismatch mismatch = (PositionMismatch) freshBus.published.getFirst();
        assertEquals("SBIN", mismatch.symbol());
        assertEquals(100, mismatch.paperQuantity());
        assertEquals(80, mismatch.brokerQuantity());

        freshRepo.close();
    }

    /**
     * Creates a scheduler with the given expected positions and runs one tick.
     * Expected positions match the broker's positions so the {@code reconcile()} pass
     * produces no spurious mismatches, leaving only the OSM-vs-broker pass to verify.
     */
    private FakeEventBus runScheduler(Map<String, Long> expectedPositions) {
        return runScheduler(reconciler, expectedPositions);
    }

    private FakeEventBus runScheduler(OrderReconciler targetReconciler, Map<String, Long> expectedPositions) {
        FakeEventBus eventBus = new FakeEventBus();
        NetPositionProvider provider = () -> expectedPositions;
        ReconciliationScheduler scheduler = new ReconciliationScheduler(targetReconciler, eventBus, provider);
        scheduler.reconcilePeriodically();
        return eventBus;
    }

    /**
     * Minimal fake {@link EventBus} that collects published events for
     * verification. Does not support subscription or lifecycle — only
     * captures {@link #publish(DomainEvent)} calls.
     */
    private static final class FakeEventBus implements EventBus {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }

        @Override
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            // Not needed for this test
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            // Not needed for this test
        }

        @Override
        public void start() {
            // Not needed for this test
        }

        @Override
        public void stop() {
            // Not needed for this test
        }
    }
}
