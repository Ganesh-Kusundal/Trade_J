package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class DhanSliceOrderAdapter extends DhanBaseRestAdapter implements SliceOrderCommand {
    private final OrderCommand orderCommand;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanSliceOrderAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanResilienceExecutor resilienceExecutor,
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
            if (settings.isSandbox()) {
                return restOrderClient.placeSliceOrder(request, definition);
            }
            try {
                Method candidate = null;
                for (Method method : clientHolder.client().getClass().getMethods()) {
                    if ("placeSliceOrder".equals(method.getName())) {
                        candidate = method;
                        break;
                    }
                }
                if (candidate != null) {
                    Object response = invokeSlice(candidate, definition, request);
                    if (response instanceof List<?> list && !list.isEmpty()) {
                        List<Order> mapped = new ArrayList<>();
                        for (Object raw : list) {
                            DhanSdkResponse<?> sdk = new DhanSdkResponse<>(raw);
                            mapped.add(DhanSdkMapper.toOrder(sdk, definition.toInstrument()));
                        }
                        return List.copyOf(mapped);
                    }
                    if (response != null) {
                        DhanSdkResponse<?> sdk = new DhanSdkResponse<>(response);
                        return List.of(DhanSdkMapper.toOrder(sdk, definition.toInstrument()));
                    }
                }
            } catch (Exception ignored) {
                // SDK signature drift fallback: place a normal order.
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

    private Object invokeSlice(Method method, DhanInstrumentDefinition definition, SliceOrderRequest request) throws Exception {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 8) {
            return method.invoke(
                    clientHolder.client(),
                    definition.securityId(),
                    DhanSdkConverters.segment(definition.exchangeSegment()),
                    DhanSdkConverters.transactionType(request.side()),
                    (int) request.quantity(),
                    DhanSdkConverters.productType(request.productType()),
                    DhanSdkConverters.orderType(request.orderType()),
                    com.tradej.core.domain.value.PriceMath.fromPaisa(request.pricePaisa()).doubleValue(),
                    com.tradej.core.domain.value.PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue()
            );
        }
        if (types.length == 7) {
            return method.invoke(
                    clientHolder.client(),
                    definition.securityId(),
                    DhanSdkConverters.segment(definition.exchangeSegment()),
                    DhanSdkConverters.transactionType(request.side()),
                    (int) request.quantity(),
                    DhanSdkConverters.productType(request.productType()),
                    DhanSdkConverters.orderType(request.orderType()),
                    com.tradej.core.domain.value.PriceMath.fromPaisa(request.pricePaisa()).doubleValue()
            );
        }
        throw new IllegalStateException("Unsupported placeSliceOrder signature");
    }
}
