package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.List;

public final class DhanGttOrderAdapter extends DhanBaseRestAdapter implements GttOrderProvider {
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanGttOrderAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanRetryExecutor resilienceExecutor,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient
    ) {
        super(clientHolder, resolver, resilienceExecutor);
        this.settings = settings;
        this.restOrderClient = restOrderClient;
    }

    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        return execute(ApiCategory.ORDER, "forever-order-place", () -> {
            DhanInstrumentDefinition definition = resolveDef(request.symbol(), request.exchangeSegment());
            return restOrderClient.placeForeverOrder(request, definition, orderFlag, quantity2, price2Paisa, trigger2Paisa);
        });
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        return execute(ApiCategory.ORDER, "forever-order-modify", () ->
                restOrderClient.modifyForeverOrder(orderId, orderFlag, legName, quantity, pricePaisa, triggerPricePaisa));
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        return execute(ApiCategory.ORDER, "forever-order-cancel", () -> restOrderClient.cancelForeverOrder(orderId));
    }

    @Override
    public List<Order> getForeverOrders() {
        return execute(ApiCategory.ORDER, "forever-order-list", restOrderClient::getForeverOrders);
    }
}
