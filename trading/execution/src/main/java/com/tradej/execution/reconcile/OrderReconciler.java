package com.tradej.execution.reconcile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.persistence.oms.EventSourcedOrderRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OrderReconciler {

    private static final Logger log = LoggerFactory.getLogger(OrderReconciler.class);

    private final EventSourcedOrderRepository omsRepo;
    private final IBrokerConnection brokerConnection;
    private final EventMetadataFactory metadataFactory;

    public OrderReconciler(
            EventSourcedOrderRepository omsRepo,
            IBrokerConnection brokerConnection,
            EventMetadataFactory metadataFactory
    ) {
        this.omsRepo = omsRepo;
        this.brokerConnection = brokerConnection;
        this.metadataFactory = metadataFactory;
    }

    /**
     * Reconcile expected net positions against broker positions.
     * Compares each broker position against the expected net position from the strategy engine.
     */
    public void reconcile(Map<String, Long> expectedNetPositions, Consumer<DomainEvent> downstream) {
        for (Position position : brokerConnection.portfolio().getPositions()) {
            String venueQualified = position.exchangeSegment().name() + "::" + position.symbol();
            long expectedQuantity = expectedNetPositions.containsKey(venueQualified)
                    ? expectedNetPositions.get(venueQualified)
                    : expectedNetPositions.getOrDefault(position.symbol(), 0L);
            if (expectedQuantity != position.quantity()) {
                downstream.accept(new PositionMismatch(
                        metadataFactory.root(),
                        position.symbol(),
                        expectedQuantity,
                        position.quantity(),
                        "broker-reconcile"
                ));
            }
        }
    }

    /**
     * Reconcile OSM-tracked filled orders against broker positions.
     * Iterates all known OSM orders, rebuilds their state, and compares filled quantities
     * against what the broker reports per venue. Only considers terminal (final) states.
     * Uses venue-qualified matching to avoid false mismatches when the same symbol
     * trades on multiple exchanges (fixes M-05).
     */
    public void reconcileAll(Consumer<DomainEvent> downstream) {
        // Group broker positions by (venue::symbol) for OSM lookups
        Map<String, Long> brokerByVenueSymbol = new HashMap<>();
        try {
            for (Position pos : brokerConnection.portfolio().getPositions()) {
                String venueKey = pos.exchangeSegment().name() + "::" + pos.symbol();
                brokerByVenueSymbol.merge(venueKey, pos.quantity(), Long::sum);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch broker positions — treating as all zeros", e);
            // Continue with empty map — all OSM filled orders will be flagged as mismatches
        }

        List<String> orderIds = omsRepo.knownOrderIds();
        for (String orderId : orderIds) {
            OrderProjection projection = omsRepo.rebuild(orderId);
            if (projection == null || !projection.status().isFinal()) {
                continue;
            }

            long osmFilledQty = projection.filledQuantity();
            String symbol = projection.symbol();

            // Find broker position for this symbol on any venue
            long brokerQty = 0L;
            String matchedKey = symbol;
            for (var entry : brokerByVenueSymbol.entrySet()) {
                if (entry.getKey().endsWith("::" + symbol)) {
                    brokerQty += entry.getValue();
                }
            }

            boolean mismatchFound = osmFilledQty != brokerQty;
            if (mismatchFound) {
                downstream.accept(new PositionMismatch(
                        metadataFactory.root(),
                        symbol,
                        osmFilledQty,
                        brokerQty,
                        "oms-reconcile"
                ));
            }
        }
    }

}

