package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import java.util.List;

public final class DhanPortfolioProvider extends DhanBaseRestAdapter implements PortfolioProvider {

    public DhanPortfolioProvider(DhanClientHolder clientHolder, DhanInstrumentResolver instrumentResolver, DhanResilienceExecutor resilienceExecutor) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
    }

    @Override
    public List<Position> getPositions() {
        return execute(ApiCategory.NON_TRADING, "positions",
                () -> clientHolder.client().getPositions().stream().map(position -> {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(position);
                    DhanInstrumentDefinition definition = resolvePayload(response);
                    return DhanSdkMapper.toPosition(response, definition.toInstrument());
                }).toList());
    }

    @Override
    public List<Holding> getHoldings() {
        return execute(ApiCategory.NON_TRADING, "holdings",
                () -> clientHolder.client().getHoldings().stream().map(holding -> {
                    DhanSdkResponse<?> response = new DhanSdkResponse<>(holding);
                    DhanInstrumentDefinition definition = resolvePayload(response);
                    return DhanSdkMapper.toHolding(response, definition.toInstrument());
                }).toList());
    }

    @Override
    public Balance getBalance() {
        return execute(ApiCategory.NON_TRADING, "fund-limits",
                () -> DhanSdkMapper.toBalance(new DhanSdkResponse<>(clientHolder.client().getFundLimits())));
    }
}
