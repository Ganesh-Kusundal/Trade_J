package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
            String truncated = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;
            ObjectNode errorNode = mapper.createObjectNode();
            errorNode.put("Status", httpStatus);
            errorNode.put("Error", "Non-JSON response (HTTP " + httpStatus + "): " + truncated);
            return new BreezeJsonResponse(errorNode, httpStatus);
        }
    }
}
