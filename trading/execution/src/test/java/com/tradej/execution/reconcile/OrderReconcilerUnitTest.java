package com.tradej.execution.reconcile;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.oms.OrderAcknowledged;
import com.tradej.core.domain.oms.OrderFullyFilled;
import com.tradej.core.domain.oms.OrderSubmitted;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.persistence.oms.EventSourcedOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OrderReconcilerUnitTest {

    private Path tempDir;
    private EventSourcedOrderRepository omsRepo;

    @Mock
    private IBrokerConnection brokerConnection;

    @Mock
    private PortfolioProvider portfolioProvider;

    private OrderReconciler reconciler;

    private final List<DomainEvent> emitted = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("reconciler-test-");
        omsRepo = new EventSourcedOrderRepository(tempDir);
        lenient().when(brokerConnection.portfolio()).thenReturn(portfolioProvider);
        reconciler = new OrderReconciler(omsRepo, brokerConnection, new com.tradej.core.domain.event.EventMetadataFactory());
        emitted.clear();
    }

    @AfterEach
    void tearDown() {
        omsRepo.close();
        try (var paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    // ── reconcile() — checks both broker positions AND OSM ──────────────────

    @Test
    void reconcileEmitsMismatchWhenBrokerPositionDiffers() {
        // OSM: order for SBIN filled
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        // Broker: position is 80 instead of 100
        Position brokerPosition = new Position(
                "SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 80, 150_00L, 151_00L, 100_00L
        );
        when(portfolioProvider.getPositions()).thenReturn(List.of(brokerPosition));

        reconciler.reconcile(Map.of("NSE_EQ::SBIN", 100L), emitted::add);

        // Should emit PositionMismatch for the discrepancy
        boolean hasMismatch = emitted.stream().anyMatch(e -> e instanceof PositionMismatch);
        assertTrue(hasMismatch, "Should emit PositionMismatch when broker position differs from expected");
    }

    @Test
    void reconcileDoesNotEmitWhenPositionsMatch() {
        // OSM: order for SBIN filled
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        // Broker: position matches expected
        Position brokerPosition = new Position(
                "SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 100, 150_00L, 151_00L, 100_00L
        );
        when(portfolioProvider.getPositions()).thenReturn(List.of(brokerPosition));

        reconciler.reconcile(Map.of("NSE_EQ::SBIN", 100L), emitted::add);

        // Should not emit mismatch
        boolean hasMismatch = emitted.stream().anyMatch(e -> e instanceof PositionMismatch);
        assertFalse(hasMismatch, "Should not emit PositionMismatch when positions match");
    }

    @Test
    void reconcileEmitsOsmMismatchWhenOsmFilledQtyDiffersFromBroker() {
        // OSM: order for SBIN filled with 100 qty
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        // Broker: position is 50 (OSM says 100)
        Position brokerPosition = new Position(
                "SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 50, 150_00L, 151_00L, 0L
        );
        when(portfolioProvider.getPositions()).thenReturn(List.of(brokerPosition));        reconciler.reconcileAll(emitted::add);

        // Should emit OSM reconciliation mismatch
        boolean hasOsmMismatch = emitted.stream()
                .filter(e -> e instanceof PositionMismatch)
                .anyMatch(e -> ((PositionMismatch) e).engineKey().equals("oms-reconcile"));
        assertTrue(hasOsmMismatch, "Should emit OSM reconciliation mismatch");
    }

    // ── reconcileAll() — OSM-only reconciliation ────────────────────────────

    @Test
    void reconcileAllWithNoOrdersDoesNothing() {
        reconciler.reconcileAll(emitted::add);

        assertTrue(emitted.isEmpty(), "Should not emit any events when no OSM orders exist");
    }

    @Test
    void reconcileAllSkipsNonFinalOrders() {
        // Order submitted but not yet filled
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));

        lenient().when(portfolioProvider.getPositions()).thenReturn(List.of());

        reconciler.reconcileAll(emitted::add);

        assertTrue(emitted.isEmpty(), "Should skip non-final orders");
    }

    @Test
    void reconcileAllDetectsMissingBrokerPosition() {
        // OSM: filled order
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        // Broker: no positions at all
        when(portfolioProvider.getPositions()).thenReturn(List.of());

        reconciler.reconcileAll(emitted::add);

        // Should detect that broker has 0 but OSM expects 100
        boolean hasMismatch = emitted.stream()
                .filter(e -> e instanceof PositionMismatch)
                .anyMatch(e -> {
                    PositionMismatch pm = (PositionMismatch) e;
                    return pm.symbol().equals("SBIN")
                            && pm.paperQuantity() == 100
                            && pm.brokerQuantity() == 0;
                });
        assertTrue(hasMismatch, "Should detect missing broker position for filled order");
    }

    @Test
    void reconcileAllDetectsPartiallyFilledOrderDiscrepancy() {
        // OSM: partially filled
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(new com.tradej.core.domain.oms.OrderPartiallyFilled("ORD-1", 20, 3_200_00L));

        // Partially filled is not final, so should be skipped
        lenient().when(portfolioProvider.getPositions()).thenReturn(List.of());

        reconciler.reconcileAll(emitted::add);

        assertTrue(emitted.isEmpty(), "Should skip non-final (partially filled) orders");
    }

    @Test
    void reconcileAllDetectsMultipleOrderDiscrepancies() {
        // OSM: two filled orders
        omsRepo.append(OrderSubmitted.create("ORD-1", "sig-1", "SBIN", 100));
        omsRepo.append(OrderAcknowledged.event("ORD-1", "EX-001"));
        omsRepo.append(OrderFullyFilled.event("ORD-1", 100, 150_00L));

        omsRepo.append(OrderSubmitted.create("ORD-2", "sig-2", "TCS", 50));
        omsRepo.append(OrderAcknowledged.event("ORD-2", "EX-002"));
        omsRepo.append(OrderFullyFilled.event("ORD-2", 50, 3_200_00L));

        // Broker: only has SBIN
        Position brokerPosition = new Position(
                "SBIN", ExchangeSegment.NSE_EQ, Side.LONG, 100, 150_00L, 151_00L, 0L
        );
        when(portfolioProvider.getPositions()).thenReturn(List.of(brokerPosition));

        reconciler.reconcileAll(emitted::add);

        // Should have one mismatch for TCS (missing from broker)
        long mismatchCount = emitted.stream()
                .filter(e -> e instanceof PositionMismatch)
                .count();
        assertEquals(1, mismatchCount, "Should detect only the missing TCS position");
    }
}
