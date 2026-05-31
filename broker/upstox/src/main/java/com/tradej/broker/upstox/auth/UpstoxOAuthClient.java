package com.tradej.broker.upstox.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Low-level HTTP client for Upstox OAuth 2.0 endpoints.
 * <p>
 * No SDK dependency — uses Java {@link HttpClient}.
 */
public final class UpstoxOAuthClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String baseUrl;

    public UpstoxOAuthClient(HttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    /**
     * Exchanges an authorization code for access+refresh tokens.
     */
    public TokenResponse exchangeCode(String code, String clientId, String clientSecret,
                                      String redirectUri, String codeVerifier) {
        String body = formBody(
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", redirectUri,
                "grant_type", "authorization_code",
                "code_verifier", codeVerifier
        );
        return postToken(body);
    }

    /**
     * Refreshes an access token using a refresh token.
     */
    public TokenResponse refreshToken(String refreshToken, String clientId, String clientSecret) {
        String body = formBody(
                "refresh_token", refreshToken,
                "client_id", clientId,
                "client_secret", clientSecret,
                "grant_type", "refresh_token"
        );
        return postToken(body);
    }

    /**
     * Validates the current access token by hitting the user profile endpoint.
     * <p>
     * Upstox profile response: {@code {"status":"success","data":{"token_expiry":"2024-05-29T12:00:00+05:30", ...}}}
     *
     * @return the token expiry epoch millis, or -1 if the endpoint doesn't provide it
     */
    /**
     * Validates a read-only token (analytics or access) via market status.
     *
     * @return {@code true} when the API returns HTTP 200
     */
    public boolean validateReadOnlyToken(String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + com.tradej.broker.upstox.constants.UpstoxEndpoints.MARKET_STATUS_PATH))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public long fetchProfile(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/user/profile"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return -1;
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode data = root.get("data");
            if (data == null) return -1;
            // Upstox returns token_expiry as ISO datetime string
            if (data.has("token_expiry")) {
                return java.time.Instant.parse(data.get("token_expiry").asText()).toEpochMilli();
            }
            return -1;
        } catch (IOException | InterruptedException e) {
            return -1;
        }
    }

    private TokenResponse postToken(String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login/authorization/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            String responseBody = response.body();
            if (status < 200 || status >= 300) {
                throw new UpstoxAuthException("Token endpoint returned " + status + ": " + responseBody);
            }
            JsonNode root = MAPPER.readTree(responseBody);
            return new TokenResponse(
                    root.get("access_token").asText(),
                    root.has("refresh_token") ? root.get("refresh_token").asText() : null,
                    root.has("expires_in") ? root.get("expires_in").asLong() : 86400L,
                    System.currentTimeMillis()
            );
        } catch (IOException | InterruptedException e) {
            throw new UpstoxAuthException("Token request failed", e);
        }
    }

    private static String formBody(String... params) {
        if (params.length % 2 != 0) throw new IllegalArgumentException("params must be key-value pairs");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < params.length; i += 2) {
            if (i > 0) sb.append('&');
            sb.append(URLEncoder.encode(params[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(params[i + 1] == null ? "" : params[i + 1], StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /**
     * Response from the token endpoint.
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            long expiresInSeconds,
            long issuedAtMs
    ) {}
}
