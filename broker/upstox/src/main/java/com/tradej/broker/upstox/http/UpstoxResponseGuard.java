package com.tradej.broker.upstox.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpResponse;

/**
 * Validates Upstox HTTP responses before JSON parsing.
 */
public final class UpstoxResponseGuard {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UpstoxResponseGuard() {
    }

    public static String requireSuccessBody(HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        if (status < 200 || status >= 300) {
            throw new UpstoxApiException(status, extractErrorCode(body),
                    "Upstox HTTP " + status + ": " + summarizeBody(body));
        }
        if (body.isBlank()) {
            return body;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            if (root.has("status") && "error".equalsIgnoreCase(root.get("status").asText())) {
                String code = extractErrorCode(body);
                String message = extractErrorMessage(root);
                throw new UpstoxApiException(status, code, "Upstox API error: " + message);
            }
        } catch (UpstoxApiException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Non-JSON success bodies are allowed.
        }
        return body;
    }

    private static String extractErrorCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(body);
            return extractErrorCode(root);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String extractErrorCode(JsonNode root) {
        if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
            JsonNode first = root.get("errors").get(0);
            if (first.has("errorCode")) {
                return first.get("errorCode").asText();
            }
            if (first.has("error_code")) {
                return first.get("error_code").asText();
            }
        }
        return null;
    }

    private static String extractErrorMessage(JsonNode root) {
        if (root.has("errors") && root.get("errors").isArray() && !root.get("errors").isEmpty()) {
            JsonNode first = root.get("errors").get(0);
            if (first.has("message")) {
                return first.get("message").asText();
            }
        }
        return root.toString();
    }

    private static String summarizeBody(String body) {
        if (body == null || body.isBlank()) {
            return "(empty body)";
        }
        return body.length() > 300 ? body.substring(0, 300) + "..." : body;
    }
}
