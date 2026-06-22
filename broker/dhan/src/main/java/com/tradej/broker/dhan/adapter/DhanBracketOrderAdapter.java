package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

import java.util.ArrayList;
import java.util.List;

public final class DhanBracketOrderAdapter implements BracketOrderProvider {
    private final DhanAdapterContext context;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanBracketOrderAdapter(
            DhanAdapterContext context,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient
    ) {
        this.context = context;
        this.settings = settings;
        this.restOrderClient = restOrderClient;
    }

    @Override
    public Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        return context.execute(ApiCategory.ORDER, "super-order-place", () -> {
            DhanInstrumentDefinition definition = context.resolveDef(request.symbol(), request.exchangeSegment());
            return restOrderClient.placeSuperOrder(request, definition, targetPricePaisa, stopLossPricePaisa, trailingJumpPaisa);
        });
    }

    @Override
    public Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        return context.execute(ApiCategory.ORDER, "modify-super-order", () -> {
            Object raw = restOrderClient.modifySuperOrderViaApi(orderId, quantity, pricePaisa, triggerPricePaisa, settings);
            DhanJsonResponse response = DhanJsonMapper.wrap(raw);
            DhanJsonResponse data = response.has("data") ? response.path("data") : response;
            DhanInstrumentDefinition definition = context.resolvePayload(data.raw());
            return DhanJsonMapper.toOrder(data, definition.toInstrument());
        });
    }

    @Override
    public boolean cancelSuperOrder(String orderId, String legName) {
        return context.execute(ApiCategory.ORDER, "cancel-super-order",
                () -> restOrderClient.cancelSuperOrderViaApi(orderId, legName, settings));
    }

    @Override
    public List<Order> getSuperOrders() {
        return context.execute(ApiCategory.ORDER, "get-super-orders", () -> {
            List<Object> rawOrders = restOrderClient.fetchSuperOrdersViaApi(settings);
            List<Order> orders = new ArrayList<>();
            for (Object raw : rawOrders) {
                DhanJsonResponse data = DhanJsonMapper.wrap(raw);
                DhanInstrumentDefinition definition = context.resolvePayload(data.raw());
                orders.add(DhanJsonMapper.toOrder(data, definition.toInstrument()));
            }
            return List.copyOf(orders);
        });
    }
}
