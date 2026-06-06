package com.tradej.broker.dhan.options;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.instrument.DhanSegmentMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;

import java.time.LocalDate;
import java.util.List;

/**
 * REST client for Dhan option-chain endpoints, routed through {@link DhanRetryExecutor}
 * and the OPTION_CHAIN rate bucket.
 */
public final class DhanOptionChainClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;
    private final DhanRetryExecutor resilienceExecutor;

    public DhanOptionChainClient(
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver,
            DhanRetryExecutor resilienceExecutor
    ) {
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
        this.resilienceExecutor = resilienceExecutor;
    }

    public List<LocalDate> fetchExpiries(DhanInstrumentDefinition underlying) {
        ObjectNode payload = underlyingPayload(underlying);
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.OPTION_CHAIN,
                "option-chain-expirylist",
                () -> httpClient.postJson(apiUrlResolver.optionChainExpiryListUrl(), payload)
        );
        return DhanOptionChainResponseMapper.parseExpiries(response);
    }

    public DhanOptionChainResponseMapper.OptionChainData fetchChain(DhanInstrumentDefinition underlying, LocalDate expiry) {
        ObjectNode payload = underlyingPayload(underlying);
        payload.put("Expiry", expiry.toString());
        DhanJsonResponse response = resilienceExecutor.execute(
                ApiCategory.OPTION_CHAIN,
                "option-chain",
                () -> httpClient.postJson(apiUrlResolver.optionChainUrl(), payload)
        );
        return DhanOptionChainResponseMapper.parseChain(
                response,
                underlying.canonicalSymbol(),
                expiry
        );
    }

    private ObjectNode underlyingPayload(DhanInstrumentDefinition underlying) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("UnderlyingScrip", Integer.parseInt(underlying.securityId()));
        request.put("UnderlyingSeg", DhanSegmentMapper.toWireValue(underlying.exchangeSegment()));
        return request;
    }
}
