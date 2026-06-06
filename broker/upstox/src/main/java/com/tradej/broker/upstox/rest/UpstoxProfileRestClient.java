package com.tradej.broker.upstox.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.upstox.http.UpstoxJsonHttpClient;

import static com.tradej.broker.upstox.constants.UpstoxEndpoints.USER_PROFILE_PATH;

/**
 * REST client for Upstox user profile endpoint.
 */
public final class UpstoxProfileRestClient {

    private final UpstoxJsonHttpClient httpClient;

    public UpstoxProfileRestClient(UpstoxJsonHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Fetches the authenticated user's profile.
     * GET /v2/user/profile
     */
    public JsonNode getProfile() {
        return httpClient.getJson(USER_PROFILE_PATH);
    }
}
