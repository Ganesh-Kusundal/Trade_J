package com.tradej.broker.icici.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.http.BreezeAuthenticatedHttpClient;

public final class BreezeOptionChainRestClient {
    private final BreezeAuthenticatedHttpClient httpClient;

    public BreezeOptionChainRestClient(BreezeAuthenticatedHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getOptionChain(ObjectNode payload) {
        return httpClient.getJson(BreezeApiEndpoints.OPTION_CHAIN, payload).successNode();
    }
}
