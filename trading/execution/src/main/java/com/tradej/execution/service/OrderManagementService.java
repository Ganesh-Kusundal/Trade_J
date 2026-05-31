package com.tradej.execution.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.runtime.RuntimeModeHolder;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.simulation.MatchingEngine;
import com.tradej.simulation.SimulatedOrderService;

import java.util.Optional;
import java.util.UUID;

@Service
public final class OrderManagementService {
    private final IBrokerConnection brokerConnection;
    private final RuntimeModeHolder runtimeModeHolder;
    private final SimulatedOrderService simulatedOrderService;
    private volatile MatchingEngine.MatchResult lastSimulatedMatch;

    public OrderManagementService(
            IBrokerConnection brokerConnection,
            RuntimeModeHolder runtimeModeHolder
    ) {
        this(brokerConnection, runtimeModeHolder, null);
    }

    @Autowired
    public OrderManagementService(
            IBrokerConnection brokerConnection,
            RuntimeModeHolder runtimeModeHolder,
            @Autowired(required = false) SimulatedOrderService simulatedOrderService
    ) {
        this.brokerConnection = brokerConnection;
        this.runtimeModeHolder = runtimeModeHolder;
        this.simulatedOrderService = simulatedOrderService;
    }

    public Order placeOrder(OrderRequest request) {
        String canonicalSymbol = ContractSymbolNormalizer.normalize(request.symbol());
        OrderRequest normalizedRequest = new OrderRequest(
                canonicalSymbol,
                request.exchangeSegment(),
                request.side(),
                request.quantity(),
                request.orderType(),
                request.pricePaisa(),
                request.triggerPricePaisa(),
                request.productType(),
                request.validity(),
                request.correlationId()
        );
        if (runtimeModeHolder.mode().usesSimulatedExecution()) {
            if (simulatedOrderService == null) {
                return legacySimulatedOpenOrder(normalizedRequest);
            }
            MatchingEngine.MatchResult match = simulatedOrderService.placeOrder(normalizedRequest);
            lastSimulatedMatch = match;
            return match.order();
        }
        lastSimulatedMatch = null;
        return brokerConnection.orders().placeOrder(normalizedRequest);
    }

    public Optional<MatchingEngine.MatchResult> lastSimulatedMatch() {
        return Optional.ofNullable(lastSimulatedMatch);
    }

    public void activateKillSwitch() {
        brokerConnection.orders().setKillSwitch(true);
    }

    private static Order legacySimulatedOpenOrder(OrderRequest request) {
        return new Order(
                "SIM-" + UUID.randomUUID(),
                request.correlationId(),
                request.symbol(),
                request.exchangeSegment(),
                request.side(),
                request.productType(),
                request.orderType(),
                OrderStatus.OPEN,
                request.quantity(),
                0L,
                request.pricePaisa(),
                request.triggerPricePaisa(),
                System.currentTimeMillis(),
                null
        );
    }
}
