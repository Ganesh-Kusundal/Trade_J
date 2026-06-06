package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.mapper.DhanApiConverters;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.value.PriceMath;

public final class DhanMarginProvider extends DhanBaseRestAdapter implements MarginProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanConnectionSettings settings;

    public DhanMarginProvider(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver resolver,
            DhanRetryExecutor resilienceExecutor,
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanConnectionSettings settings
    ) {
        super(clientHolder, resolver, resilienceExecutor);
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.settings = settings;
    }

    @Override
    public MarginEstimate estimateMargin(MarginEstimateRequest request) {
        DhanInstrumentDefinition definition = resolveDef(request.symbol(), request.exchangeSegment());
        return execute(ApiCategory.ORDER, "margin-calculator", () -> {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("dhanClientId", settings.clientId());
            payload.put("exchangeSegment", DhanSegmentMapper.toWireValue(definition.exchangeSegment()));
            payload.put("transactionType", DhanApiConverters.transactionType(request.side()));
            payload.put("quantity", (int) request.quantity());
            payload.put("productType", restProductType(request.productType()));
            payload.put("securityId", definition.securityId());
            payload.put("price", PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
            payload.put("triggerPrice", PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());

            DhanJsonResponse body = httpClient.postJson(apiUrlResolver.marginCalculatorUrl(), payload);
            DhanJsonResponse data = body.has("data") ? body.path("data") : body;
            return mapRestMargin(data);
        });
    }

    private static MarginEstimate mapRestMargin(DhanJsonResponse data) {
        return new MarginEstimate(
                data.decimalPrice("totalMargin", "total_margin", "getTotalMargin"),
                data.decimalPrice("spanMargin", "span_margin", "getSpanMargin"),
                data.decimalPrice("exposureMargin", "exposure_margin", "getExposureMargin"),
                data.decimalPrice("brokerage", "getBrokerage")
        );
    }

    private static String restProductType(com.tradej.core.domain.value.ProductType productType) {
        return switch (productType) {
            case INTRADAY, INTRADAY_MARGIN -> "INTRADAY";
            case CNC, DELIVERY -> "CNC";
            case MARGIN, MARGIN_FUNDING -> "MARGIN";
            case CARRY_FORWARD -> "CARRY_FORWARD";
        };
    }
}
