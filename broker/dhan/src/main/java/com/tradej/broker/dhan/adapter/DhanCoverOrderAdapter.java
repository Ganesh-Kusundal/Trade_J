package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;

public final class DhanCoverOrderAdapter extends DhanBaseRestAdapter implements CoverOrderProvider {

    public DhanCoverOrderAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanRetryExecutor executor
    ) {
        super(clientHolder, resolver, executor);
    }

    @Override
    public Order placeCoverOrder(OrderRequest request, long slPaisa) {
        return execute(ApiCategory.ORDER, "cover-order-place", () -> {
            throw new UnsupportedOperationException(
                    "Dhan cover order placement requires Dhan API v2 super-order endpoint");
        });
    }

    @Override
    public Order exitCoverOrder(String orderId) {
        return execute(ApiCategory.ORDER, "cover-order-exit", () -> {
            throw new UnsupportedOperationException(
                    "Dhan cover order exit requires Dhan API v2 super-order endpoint");
        });
    }
}
