package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.List;

public final class DhanConditionalAlertProvider extends DhanBaseRestAdapter implements ConditionalAlertProvider {
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final ObjectMapper mapper = new ObjectMapper();

    public DhanConditionalAlertProvider(
            DhanInstrumentResolver resolver,
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanRetryExecutor resilienceExecutor
    ) {
        super(resolver, resilienceExecutor);
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
    }

    @Override
    public String placeAlert(ConditionalAlertRequest request) {
        var definition = resolveDef(request.symbol(), request.exchangeSegment());
        ObjectNode payload = mapper.createObjectNode();
        payload.put("securityId", definition.securityId());
        payload.put("exchangeSegment", definition.exchangeSegment().name());
        payload.put("transactionType", DhanSdkConverters.transactionType(request.side()).name());
        payload.put("orderType", DhanSdkConverters.orderType(request.orderType()).name());
        payload.put("productType", DhanSdkConverters.productType(request.productType()).name());
        payload.put("quantity", (int) request.quantity());
        payload.put("price", PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
        payload.put("triggerPrice", PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
        payload.put("validity", request.validity().name());
        payload.put("comparisonType", request.comparisonType());
        if (request.operator() != null) payload.put("operator", request.operator());
        if (request.timeFrame() != null) payload.put("timeFrame", request.timeFrame());
        if (request.comparingValue() != null) payload.put("comparingValue", request.comparingValue());
        if (request.indicatorName() != null) payload.put("indicatorName", request.indicatorName());
        if (request.comparingIndicatorName() != null) payload.put("comparingIndicatorName", request.comparingIndicatorName());
        if (request.frequency() != null) payload.put("frequency", request.frequency());
        if (request.expiryDate() != null) payload.put("expDate", request.expiryDate());
        if (request.userNote() != null) payload.put("userNote", request.userNote());
        return execute(ApiCategory.ORDER, "alerts-place", () -> {
            var response = httpClient.postJson(apiUrlResolver.alertOrdersUrl(), payload);
            var data = response.has("data") ? response.path("data") : response;
            String id = data.string("alertId", "id");
            return id.isBlank() ? data.string("message") : id;
        });
    }

    @Override
    public ConditionalAlert getAlert(String alertId) {
        return execute(ApiCategory.ORDER, "alerts-get", () -> {
            var response = httpClient.getJson(apiUrlResolver.alertOrdersUrl() + "/" + alertId);
            var data = response.has("data") ? response.path("data") : response;
            return new ConditionalAlert(
                    data.string("alertId", "id"),
                    data.string("alertStatus", "status"),
                    data.string("message", "remarks")
            );
        });
    }

    @Override
    public List<ConditionalAlert> listAlerts() {
        return execute(ApiCategory.ORDER, "alerts-list", () -> {
            var response = httpClient.getJson(apiUrlResolver.alertOrdersUrl());
            var data = response.has("data") ? response.path("data") : response;
            List<ConditionalAlert> out = new ArrayList<>();
            for (var item : data.asList()) {
                out.add(new ConditionalAlert(
                        item.string("alertId", "id"),
                        item.string("alertStatus", "status"),
                        item.string("message", "remarks")
                ));
            }
            return List.copyOf(out);
        });
    }

    @Override
    public boolean deleteAlert(String alertId) {
        return execute(ApiCategory.ORDER, "alerts-delete", () -> {
            httpClient.deleteJson(apiUrlResolver.alertOrdersUrl() + "/" + alertId);
            return true;
        });
    }
}
