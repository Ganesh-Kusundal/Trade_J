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
