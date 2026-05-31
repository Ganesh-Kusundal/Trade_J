package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record BreezeJsonResponse(JsonNode root, int httpStatus) {
    public boolean isSuccess() {
        return httpStatus == 200 && root.path("Status").asInt(-1) == 200;
    }

    public JsonNode successNode() {
        return root.path("Success");
    }

    public String errorMessage() {
        return root.path("Error").asText("unknown ICICI Breeze error");
    }

    public static BreezeJsonResponse parse(ObjectMapper mapper, int httpStatus, String body) {
        try {
            return new BreezeJsonResponse(mapper.readTree(body), httpStatus);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid ICICI JSON response: " + body, ex);
        }
    }
}
