package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.RollingOptionHistoryRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DhanRollingOptionClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanResilienceExecutor resilienceExecutor;

    public DhanRollingOptionClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanResilienceExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    public Map<String, List<Double>> fetch(DhanInstrumentDefinition underlying, RollingOptionHistoryRequest request) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("securityId", underlying.securityId());
        payload.put("exchangeSegment", underlying.exchangeSegment().name());
        payload.put("instrument", "OPT");
        payload.put("interval", Integer.toString(request.intervalMinutes()));
        payload.put("expiryFlag", request.expiryFlag());
        payload.put("expiryCode", request.expiryCode());
        payload.put("strike", request.strike());
        payload.put("optionType", request.optionType());
        if (request.fromDate() != null) payload.put("fromDate", request.fromDate().toString());
        if (request.toDate() != null) payload.put("toDate", request.toDate().toString());
        return resilienceExecutor.execute(ApiCategory.DATA, "rolling-option-history", () -> {
            var response = httpClient.postJson(apiUrlResolver.rollingOptionUrl(), payload);
            var data = response.has("data") ? response.path("data") : response;
            return Map.of(
                    "open", toDoubleList(data.path("open")),
                    "high", toDoubleList(data.path("high")),
                    "low", toDoubleList(data.path("low")),
                    "close", toDoubleList(data.path("close"))
            );
        });
    }

    private List<Double> toDoubleList(com.tradej.broker.dhan.mapper.DhanJsonResponse value) {
        List<Double> out = new ArrayList<>();
        for (var element : value.asList()) {
            out.add(element.asDouble());
        }
        return List.copyOf(out);
    }
}
