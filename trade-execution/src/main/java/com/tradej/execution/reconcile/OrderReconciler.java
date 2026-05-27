package com.tradej.execution.reconcile;

import org.springframework.stereotype.Service;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.oms.OrderProjection;
import com.tradej.persistence.oms.EventSourcedOrderRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public final class OrderReconciler {
    private final EventSourcedOrderRepository omsRepo;
    private final IBrokerConnection brokerConnection;

    public OrderReconciler(EventSourcedOrderRepository omsRepo, IBrokerConnection brokerConnection) {
        this.omsRepo = omsRepo;
        this.brokerConnection = brokerConnection;
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
                        EventMetadata.root(),
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
     * against what the broker reports. Only considers terminal (final) states.
     */
    public void reconcileAll(Consumer<DomainEvent> downstream) {
        List<String> orderIds = omsRepo.knownOrderIds();
        for (String orderId : orderIds) {
            OrderProjection projection = omsRepo.rebuild(orderId);
            if (projection == null || !projection.status().isFinal()) {
                continue;
            }

            long osmFilledQty = projection.filledQuantity();
            long brokerFilledQty = findBrokerQuantityForSymbol(projection.symbol());

            if (osmFilledQty != brokerFilledQty) {
                downstream.accept(new PositionMismatch(
                        EventMetadata.root(),
                        projection.symbol(),
                        osmFilledQty,
                        brokerFilledQty,
                        "oms-reconcile"
                ));
            }
        }
    }

    private long findBrokerQuantityForSymbol(String symbol) {
        try {
            return brokerConnection.portfolio().getPositions().stream()
                    .filter(p -> p.symbol().equals(symbol))
                    .mapToLong(Position::quantity)
                    .sum();
        } catch (Exception e) {
            return 0L;
        }
    }
}
