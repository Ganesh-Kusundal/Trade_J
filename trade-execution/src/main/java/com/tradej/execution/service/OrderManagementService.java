package com.tradej.execution.service;

import org.springframework.stereotype.Service;
import com.tradej.broker.api.IBrokerConnection;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

@Service
public final class OrderManagementService {
    private final IBrokerConnection brokerConnection;

    public OrderManagementService(IBrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public Order placeOrder(OrderRequest request) {
        return brokerConnection.orders().placeOrder(request);
    }

    public void activateKillSwitch() {
        brokerConnection.orders().setKillSwitch(true);
    }
}
