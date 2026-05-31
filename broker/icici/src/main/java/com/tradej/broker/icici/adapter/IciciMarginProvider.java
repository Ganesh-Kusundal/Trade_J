package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;

public final class IciciMarginProvider implements MarginProvider {
    private final BreezeAuthenticatedHttpClient httpClient;
    private final BreezeInstrumentResolver instrumentResolver;
    private final BreezeDomainMapper mapper;

    public IciciMarginProvider(
            BreezeAuthenticatedHttpClient httpClient,
            BreezeInstrumentResolver instrumentResolver,
            BreezeDomainMapper mapper
    ) {
        this.httpClient = httpClient;
        this.instrumentResolver = instrumentResolver;
        this.mapper = mapper;
    }

    @Override
    public MarginEstimate estimateMargin(MarginEstimateRequest request) {
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        ObjectNode payload = mapper.toPlaceOrderPayload(
                new com.tradej.core.domain.model.OrderRequest(
                        request.symbol(),
                        request.exchangeSegment(),
                        request.side(),
                        request.quantity(),
                        request.orderType(),
                        request.pricePaisa(),
                        request.triggerPricePaisa(),
                        request.productType(),
                        com.tradej.core.domain.value.Validity.DAY,
                        null
                ),
                definition
        );
        var response = httpClient.getJson(BreezeApiEndpoints.MARGIN_CALCULATOR, payload);
        long margin = Math.round(response.successNode().path("order_margin").asDouble(0.0) * 100.0);
        return new MarginEstimate(margin, margin, 0L, 0L);
    }
}
