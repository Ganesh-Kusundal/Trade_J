package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.util.ArrayList;
import java.util.List;

public final class DhanPortfolioProvider extends DhanBaseRestAdapter implements PortfolioProvider {

    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;

    public DhanPortfolioProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanRetryExecutor resilienceExecutor,
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
    }

    @Override
    public List<Position> getPositions() {
        return execute(ApiCategory.NON_TRADING, "positions", () -> {
            var response = httpClient.getJson(apiUrlResolver.positionsUrl());
            var data = response.has("data") ? response.path("data") : response;
            if (!data.isArray()) {
                return List.<Position>of();
            }
            List<Position> positions = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                DhanInstrumentDefinition definition = resolvePayload(item.raw());
                positions.add(DhanJsonMapper.toPosition(item, definition.toInstrument()));
            }
            return List.copyOf(positions);
        });
    }

    @Override
    public List<Holding> getHoldings() {
        return execute(ApiCategory.NON_TRADING, "holdings", () -> {
            var response = httpClient.getJson(apiUrlResolver.holdingsUrl());
            var data = response.has("data") ? response.path("data") : response;
            if (!data.isArray()) {
                return List.<Holding>of();
            }
            List<Holding> holdings = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                DhanInstrumentDefinition definition = resolvePayload(item.raw());
                holdings.add(DhanJsonMapper.toHolding(item, definition.toInstrument()));
            }
            return List.copyOf(holdings);
        });
    }

    @Override
    public Balance getBalance() {
        return execute(ApiCategory.NON_TRADING, "fund-limits", () -> {
            var response = httpClient.getJson(apiUrlResolver.fundLimitUrl());
            var data = response.has("data") ? response.path("data") : response;
            return DhanJsonMapper.toBalance(data);
        });
    }
}
