package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.value.PriceMath;
import java.lang.reflect.Method;

public final class DhanMarginProvider extends DhanBaseRestAdapter implements MarginProvider {
    private final DhanClientHolder clientHolder;

    public DhanMarginProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanResilienceExecutor resilienceExecutor
    ) {
        super(clientHolder, resolver, resilienceExecutor);
        this.clientHolder = clientHolder;
    }

    @Override
    public MarginEstimate estimateMargin(MarginEstimateRequest request) {
        DhanInstrumentDefinition definition = resolveDef(request.symbol(), request.exchangeSegment());
        return execute(ApiCategory.ORDER, "margin-calculator", () -> {
            Object raw = invokeMarginCalculator(
                    definition.securityId(),
                    DhanSdkConverters.segment(definition.exchangeSegment()),
                    DhanSdkConverters.transactionType(request.side()),
                    (int) request.quantity(),
                    DhanSdkConverters.productType(request.productType()),
                    PriceMath.fromPaisa(request.pricePaisa()).doubleValue(),
                    PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue()
            );
            DhanSdkResponse<?> data = new DhanSdkResponse<>(raw);
            return new MarginEstimate(
                    PriceMath.toPaisa(data.decimal("getTotalMargin")),
                    PriceMath.toPaisa(data.optionalDecimal("getSpanMargin").orElse(java.math.BigDecimal.ZERO)),
                    PriceMath.toPaisa(data.optionalDecimal("getExposureMargin").orElse(java.math.BigDecimal.ZERO)),
                    PriceMath.toPaisa(data.optionalDecimal("getBrokerage").orElse(java.math.BigDecimal.ZERO))
            );
        });
    }

    private Object invokeMarginCalculator(Object... args) {
        for (Method method : clientHolder.client().getClass().getMethods()) {
            if (!"marginCalculator".equals(method.getName())) {
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
        throw new IllegalStateException("Dhan SDK method not available: marginCalculator");
    }
}
