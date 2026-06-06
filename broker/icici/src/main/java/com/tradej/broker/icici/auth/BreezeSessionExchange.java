package com.tradej.broker.icici.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.tradej.broker.icici.constants.BreezeApiEndpoints.API_BASE;
import static com.tradej.broker.icici.constants.BreezeApiEndpoints.CUSTOMER_DETAILS;

/**
 * Exchanges a daily session input (TOTP code or browser API_Session) for a signed session token.
 */
public final class BreezeSessionExchange {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BreezeSessionExchange() {
        this(HttpClient.newHttpClient(), new ObjectMapper());
    }

    BreezeSessionExchange(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public BreezeSession exchange(String appKey, String sessionInput) {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("ICICI AppKey is blank");
        }
        if (sessionInput == null || sessionInput.isBlank()) {
            throw new IllegalArgumentException("ICICI session input is blank");
        }
        String body = compactJson("""
                {"SessionToken":"%s","AppKey":"%s"}
                """.formatted(escapeJson(sessionInput), escapeJson(appKey)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + CUSTOMER_DETAILS))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .method("GET", HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException(
                        "CustomerDetails HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            int status = root.path("Status").asInt(-1);
            if (status != 200) {
                String error = root.path("Error").asText("unknown error");
                throw new IllegalStateException("CustomerDetails failed: " + error);
            }
            JsonNode success = root.path("Success");
            String sessionToken = success.path("session_token").asText(null);
            if (sessionToken == null || sessionToken.isBlank()) {
                throw new IllegalStateException("CustomerDetails response missing session_token");
            }
            long now = Instant.now().toEpochMilli();
            return BreezeSession.fromEncodedToken(sessionToken, now, nextMidnightEpochMs(now));
        } catch (IOException ex) {
            throw new IllegalStateException("CustomerDetails request failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CustomerDetails request interrupted", ex);
        }
    }

    static long nextMidnightEpochMs(long nowEpochMs) {
        ZonedDateTime now = Instant.ofEpochMilli(nowEpochMs).atZone(INDIA);
        ZonedDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay(INDIA);
        return midnight.toInstant().toEpochMilli();
    }

    private static String compactJson(String json) {
        return json.replace("\n", "").replace("\r", "").replace(" ", "");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
