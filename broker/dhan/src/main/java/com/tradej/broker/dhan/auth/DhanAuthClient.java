package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.exceptions.DhanExceptionUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

public class DhanAuthClient {
    private static final String GENERATE_TOKEN_ENDPOINT = "https://auth.dhan.co/app/generateAccessToken";
    private static final String RENEW_TOKEN_ENDPOINT = "https://api.dhan.co/v2/RenewToken";
    private static final String PROFILE_ENDPOINT = "https://api.dhan.co/v2/profile";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter PROFILE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter EXPIRY_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
            .optionalStart()
            .appendFraction(java.time.temporal.ChronoField.MILLI_OF_SECOND, 1, 3, true)
            .optionalEnd()
            .toFormatter();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DhanAuthClient() {
        this(newHttpClient(), new ObjectMapper());
    }

    private static HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public DhanAuthClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public DhanTokenState generateViaTotp(String clientId, String pin, String totp) {
        String url = GENERATE_TOKEN_ENDPOINT
                + "?dhanClientId=" + encode(clientId)
                + "&pin=" + encode(pin)
                + "&totp=" + encode(totp);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .build();
        JsonNode body = send(request, "generate Dhan access token");
        return mapTokenState(body, "TOTP_GENERATED");
    }

    public DhanTokenState renewToken(String clientId, String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RENEW_TOKEN_ENDPOINT))
                .header("Accept", "application/json")
                .header("access-token", accessToken)
                .header("dhanClientId", clientId)
                .POST(HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(15))
                .build();
        JsonNode body = send(request, "renew Dhan access token");
        return mapTokenState(body, "WEB_RENEWABLE");
    }

    public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMs) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_ENDPOINT))
                .header("Accept", "application/json")
                .header("access-token", accessToken)
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();
        JsonNode body = send(request, "fetch Dhan profile");
        String tokenValidity = body.path("tokenValidity").asText("");
        if (tokenValidity.isBlank()) {
            throw new IllegalStateException("Dhan profile response did not include tokenValidity");
        }
        long expiryEpochMs = LocalDateTime.parse(tokenValidity, PROFILE_FORMAT).atZone(INDIA).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        boolean valid = expiryEpochMs > now;
        return new DhanTokenInfo(valid, expiryEpochMs, expiryEpochMs <= now + refreshBufferMs);
    }

    protected JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            DhanExceptionUtil.verifyHttpSuccess(response.statusCode(), response.body(), action);
            JsonNode body = objectMapper.readTree(response.body());
            verifyBusinessSuccess(body, action);
            return body;
        } catch (IOException ex) {
            DhanExceptionUtil.rethrowIoError(action, ex);
        } catch (InterruptedException ex) {
            DhanExceptionUtil.rethrowInterruption(action, ex);
        }
        throw new IllegalStateException("Unreachable");
    }

    protected DhanTokenState mapTokenState(JsonNode body, String source) {
        JsonNode root = unwrapDataNode(body);
        String accessToken = textField(root, "accessToken", "access_token");
        String expiryTime = textField(root, "expiryTime", "expiry_time");
        if (accessToken.isBlank() || expiryTime.isBlank()) {
            throw new IllegalStateException("Dhan auth response did not contain accessToken and expiryTime");
        }
        long issuedAtEpochMs = System.currentTimeMillis();
        long expiryEpochMs = LocalDateTime.parse(expiryTime, EXPIRY_FORMAT).atZone(INDIA).toInstant().toEpochMilli();
        return new DhanTokenState(accessToken, expiryEpochMs, issuedAtEpochMs, source);
    }

    private static JsonNode unwrapDataNode(JsonNode body) {
        JsonNode data = body.path("data");
        if (data.isObject()) {
            return data;
        }
        return body;
    }

    private static String textField(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = node.path(fieldName).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    protected static void verifyBusinessSuccess(JsonNode body, String action) {
        String status = body.path("status").asText("");
        if ("error".equalsIgnoreCase(status)) {
            String message = body.path("message").asText("unknown error");
            String normalized = message.toLowerCase();
            boolean rateLimited = normalized.contains("2 minutes")
                    || normalized.contains("once every")
                    || normalized.contains("too many attempts")
                    || normalized.contains("try again after");
            throw new DhanAuthRejectedException("Dhan " + action + " rejected: " + message, rateLimited);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
