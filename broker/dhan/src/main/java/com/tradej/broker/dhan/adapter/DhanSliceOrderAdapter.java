package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.List;

public final class DhanSliceOrderAdapter extends DhanBaseRestAdapter implements SliceOrderCommand {
    private final OrderCommand orderCommand;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanSliceOrderAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanRetryExecutor resilienceExecutor,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient,
            OrderCommand orderCommand
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.settings = settings;
        this.restOrderClient = restOrderClient;
        this.orderCommand = orderCommand;
    }

    @Override
    public List<Order> placeSliceOrder(SliceOrderRequest request) {
        return execute(ApiCategory.ORDER, "place-slice-order", () -> {
            DhanInstrumentDefinition definition = resolveDef(request.symbol(), request.exchangeSegment());
            List<Order> result = restOrderClient.placeSliceOrder(request, definition);
            if (result != null && !result.isEmpty()) {
                return result;
            }
            OrderRequest fallback = new OrderRequest(
                    request.symbol(),
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
            return List.of(orderCommand.placeOrder(fallback));
        });
    }
}
