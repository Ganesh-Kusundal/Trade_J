package com.tradej.broker.upstox.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Parses validated Upstox JSON responses.
 */
public final class UpstoxJsonHttpClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxHttpClient httpClient;

    public UpstoxJsonHttpClient(UpstoxHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public JsonNode getJson(String path) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.get(path)));
    }

    public JsonNode postJson(String path, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.post(path, jsonBody)));
    }

    public JsonNode putJson(String path, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.put(path, jsonBody)));
    }

    public JsonNode deleteJson(String path) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.delete(path)));
    }

    /** Sends a DELETE with a JSON body (used by GTT cancel which sends {"gtt_order_id": "..."}). */
    public JsonNode deleteJson(String path, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.deleteJsonBody(path, jsonBody)));
    }

    /** GET to an arbitrary full URL (bypasses configured baseUrl for cross-version calls). */
    public JsonNode getUrlJson(String fullUrl) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.getUrl(fullUrl)));
    }

    /** POST to an arbitrary full URL (bypasses configured baseUrl for cross-version calls). */
    public JsonNode postUrlJson(String fullUrl, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.postUrl(fullUrl, jsonBody)));
    }

    /** PUT to an arbitrary full URL (bypasses configured baseUrl for cross-version calls). */
    public JsonNode putUrlJson(String fullUrl, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.putUrl(fullUrl, jsonBody)));
    }

    /** DELETE to an arbitrary full URL (bypasses configured baseUrl for cross-version calls). */
    public JsonNode deleteUrlJson(String fullUrl) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.deleteUrl(fullUrl)));
    }

    /** DELETE with JSON body to an arbitrary full URL (bypasses configured baseUrl for cross-version calls). */
    public JsonNode deleteUrlJson(String fullUrl, String jsonBody) {
        return parse(UpstoxResponseGuard.requireSuccessBody(httpClient.deleteUrlJsonBody(fullUrl, jsonBody)));
    }

    /**
     * Exposes the underlying HTTP client base URL for callers that need
     * to compute API version-specific URLs (e.g. v3 GTT endpoints).
     */
    public String baseUrl() {
        return httpClient.baseUrl();
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            return MAPPER.readTree(body);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to parse Upstox JSON response", ex);
        }
    }
}
