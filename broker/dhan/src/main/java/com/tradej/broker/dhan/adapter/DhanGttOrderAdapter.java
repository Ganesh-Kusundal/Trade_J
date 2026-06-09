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

public final class DhanGttOrderAdapter implements GttOrderProvider {
    private final DhanAdapterContext context;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanGttOrderAdapter(
            DhanAdapterContext context,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient
    ) {
        this.context = context;
        this.settings = settings;
        this.restOrderClient = restOrderClient;
    }

    @Override
    public Order placeForeverOrder(OrderRequest request, String orderFlag, Long quantity2, Long price2Paisa, Long trigger2Paisa) {
        return context.execute(ApiCategory.ORDER, "forever-order-place", () -> {
            DhanInstrumentDefinition definition = context.resolveDef(request.symbol(), request.exchangeSegment());
            return restOrderClient.placeForeverOrder(request, definition, orderFlag, quantity2, price2Paisa, trigger2Paisa);
        });
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        return context.execute(ApiCategory.ORDER, "forever-order-modify", () ->
                restOrderClient.modifyForeverOrder(orderId, orderFlag, legName, quantity, pricePaisa, triggerPricePaisa));
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        return context.execute(ApiCategory.ORDER, "forever-order-cancel", () -> restOrderClient.cancelForeverOrder(orderId));
    }

    @Override
    public List<Order> getForeverOrders() {
        return context.execute(ApiCategory.ORDER, "forever-order-list", restOrderClient::getForeverOrders);
    }
}
