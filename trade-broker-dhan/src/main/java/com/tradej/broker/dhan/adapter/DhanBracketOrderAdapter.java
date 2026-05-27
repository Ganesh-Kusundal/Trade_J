package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
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
import com.tradej.core.domain.value.PriceMath;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class DhanBracketOrderAdapter extends DhanBaseRestAdapter implements BracketOrderProvider {
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanBracketOrderAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanResilienceExecutor resilienceExecutor,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient
    ) {
        super(clientHolder, resolver, resilienceExecutor);
        this.settings = settings;
        this.restOrderClient = restOrderClient;
    }

    @Override
    public Order placeSuperOrder(OrderRequest request, long targetPricePaisa, long stopLossPricePaisa, long trailingJumpPaisa) {
        return execute(ApiCategory.ORDER, "super-order-place", () -> {
            DhanInstrumentDefinition definition = resolveDef(request.symbol(), request.exchangeSegment());
            if (settings.isSandbox()) {
                return restOrderClient.placeSuperOrder(request, definition, targetPricePaisa, stopLossPricePaisa, trailingJumpPaisa);
            }
            Object raw = invokeByName("placeSuperOrder",
                    definition.securityId(),
                    DhanSdkConverters.segment(definition.exchangeSegment()),
                    DhanSdkConverters.transactionType(request.side()),
                    (int) request.quantity(),
                    DhanSdkConverters.orderType(request.orderType()),
                    DhanSdkConverters.productType(request.productType()),
                    PriceMath.fromPaisa(request.pricePaisa()).doubleValue(),
                    PriceMath.fromPaisa(targetPricePaisa).doubleValue(),
                    PriceMath.fromPaisa(stopLossPricePaisa).doubleValue(),
                    PriceMath.fromPaisa(trailingJumpPaisa).doubleValue()
            );
            return DhanSdkMapper.toOrder(new DhanSdkResponse<>(raw), definition.toInstrument());
        });
    }

    @Override
    public Order modifySuperOrder(String orderId, String legName, long quantity, long pricePaisa, long triggerPricePaisa) {
        return execute(ApiCategory.ORDER, "super-order-modify", () -> {
            Object raw = invokeByName("modifySuperOrder",
                    orderId,
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
    public boolean cancelSuperOrder(String orderId, String legName) {
        return execute(ApiCategory.ORDER, "super-order-cancel",
                () -> invokeByName("cancelSuperOrder", orderId, legName) != null);
    }

    @Override
    public List<Order> getSuperOrders() {
        return execute(ApiCategory.ORDER, "super-order-list", () -> {
            Object raw = invokeByName("getSuperOrderList");
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
