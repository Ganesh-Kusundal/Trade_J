package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.PriceMath;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
            if (settings.isSandbox()) {
                return restOrderClient.placeForeverOrder(request, definition, orderFlag, quantity2, price2Paisa, trigger2Paisa);
            }
            Object raw = invokeByName("placeForever",
                    definition.securityId(),
                    DhanSdkConverters.segment(definition.exchangeSegment()),
                    DhanSdkConverters.transactionType(request.side()),
                    (int) request.quantity(),
                    DhanSdkConverters.orderType(request.orderType()),
                    DhanSdkConverters.productType(request.productType()),
                    PriceMath.fromPaisa(request.pricePaisa()).doubleValue(),
                    PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue(),
                    orderFlag,
                    quantity2 == null ? 0 : quantity2.intValue(),
                    PriceMath.fromPaisa(price2Paisa == null ? 0L : price2Paisa).doubleValue(),
                    PriceMath.fromPaisa(trigger2Paisa == null ? 0L : trigger2Paisa).doubleValue()
            );
            return DhanSdkMapper.toOrder(new DhanSdkResponse<>(raw), definition.toInstrument());
        });
    }

    @Override
    public Order modifyForeverOrder(String orderId, String orderFlag, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        return execute(ApiCategory.ORDER, "forever-order-modify", () -> {
            Object raw = invokeByName("modifyForever",
                    orderId,
                    orderFlag,
                    legName,
                    (int) quantity,
                    PriceMath.fromPaisa(pricePaisa).doubleValue(),
                    PriceMath.fromPaisa(triggerPricePaisa).doubleValue());
            DhanSdkResponse<?> response = new DhanSdkResponse<>(raw);
            DhanInstrumentDefinition definition = resolvePayload(response);
            return DhanSdkMapper.toOrder(response, definition.toInstrument());
        });
    }

    @Override
    public boolean cancelForeverOrder(String orderId) {
        return execute(ApiCategory.ORDER, "forever-order-cancel",
                () -> invokeByName("cancelForever", orderId) != null);
    }

    @Override
    public List<Order> getForeverOrders() {
        return execute(ApiCategory.ORDER, "forever-order-list", () -> {
            Object raw = invokeByName("getForever");
            if (!(raw instanceof List<?> list)) {
                return List.of();
            }
            List<Order> out = new ArrayList<>();
            for (Object item : list) {
                DhanSdkResponse<?> response = new DhanSdkResponse<>(item);
                DhanInstrumentDefinition definition = resolvePayload(response);
                out.add(DhanSdkMapper.toOrder(response, definition.toInstrument()));
            }
            return List.copyOf(out);
        });
    }

    private Object invokeByName(String methodName, Object... args) {
        for (Method method : clientHolder.client().getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            if (method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(clientHolder.client(), args);
            } catch (Exception ignored) {
                // try next overload
            }
        }
        throw new IllegalStateException("Dhan SDK method not available: " + methodName);
    }
}
