package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.PnlExitPolicy;
import com.tradej.core.domain.model.PnlExitResult;
import com.tradej.core.domain.value.PriceMath;

public final class DhanSessionRiskProvider implements SessionRiskProvider {
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanResilienceExecutor resilienceExecutor;
    private final ObjectMapper mapper = new ObjectMapper();

    public DhanSessionRiskProvider(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanResilienceExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Override
    public PnlExitResult enablePnlExit(PnlExitPolicy policy) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("profit", PriceMath.fromPaisa(policy.profitThresholdPaisa()).doubleValue());
        payload.put("loss", PriceMath.fromPaisa(policy.lossThresholdPaisa()).doubleValue());
        payload.put("killSwitch", policy.enableKillSwitch());
        return resilienceExecutor.execute(ApiCategory.NON_TRADING, "pnl-exit", () -> {
            var response = httpClient.postJson(apiUrlResolver.pnlExitUrl(), payload);
            var data = response.has("data") ? response.path("data") : response;
            return new PnlExitResult(
                    true,
                    data.string("status", "pnlExitStatus"),
                    data.string("message", "remarks")
            );
        });
    }
}
