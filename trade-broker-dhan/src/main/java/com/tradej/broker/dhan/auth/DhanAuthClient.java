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
        this(HttpClient.newHttpClient(), new ObjectMapper());
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
                .build();
        JsonNode body = send(request, "renew Dhan access token");
        return mapTokenState(body, "WEB_RENEWABLE");
    }

    public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMs) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(PROFILE_ENDPOINT))
                .header("Accept", "application/json")
                .header("access-token", accessToken)
                .GET()
                .build();
        JsonNode body = send(request, "fetch Dhan profile");
        String tokenValidity = body.path("tokenValidity").asText("");
        if (tokenValidity.isBlank()) {
            throw new IllegalStateException("Dhan profile response did not include tokenValidity");
        }
        long expiryEpochMs = LocalDateTime.parse(tokenValidity, PROFILE_FORMAT).atZone(INDIA).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        return new DhanTokenInfo(true, expiryEpochMs, expiryEpochMs <= now + refreshBufferMs);
    }

    protected JsonNode send(HttpRequest request, String action) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            DhanExceptionUtil.verifyHttpSuccess(response.statusCode(), response.body(), action);
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            DhanExceptionUtil.rethrowIoError(action, ex);
        } catch (InterruptedException ex) {
            DhanExceptionUtil.rethrowInterruption(action, ex);
        }
        throw new IllegalStateException("Unreachable");
    }

    protected DhanTokenState mapTokenState(JsonNode body, String source) {
        String accessToken = body.path("accessToken").asText("");
        String expiryTime = body.path("expiryTime").asText("");
        if (accessToken.isBlank() || expiryTime.isBlank()) {
            throw new IllegalStateException("Dhan auth response did not contain accessToken and expiryTime");
        }
        long issuedAtEpochMs = System.currentTimeMillis();
        long expiryEpochMs = LocalDateTime.parse(expiryTime, EXPIRY_FORMAT).atZone(INDIA).toInstant().toEpochMilli();
        return new DhanTokenState(accessToken, expiryEpochMs, issuedAtEpochMs, source);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
