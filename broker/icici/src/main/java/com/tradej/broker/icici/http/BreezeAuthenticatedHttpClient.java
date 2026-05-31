package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class BreezeAuthenticatedHttpClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BreezeTokenProvider tokenProvider;
    private final String baseUrl;
    private final Clock clock;

    public BreezeAuthenticatedHttpClient(BreezeTokenProvider tokenProvider) {
        this(HttpClient.newHttpClient(), new ObjectMapper(), tokenProvider, BreezeApiEndpoints.API_BASE, Clock.systemUTC());
    }

    public BreezeAuthenticatedHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            BreezeTokenProvider tokenProvider,
            String baseUrl
    ) {
        this(httpClient, objectMapper, tokenProvider, baseUrl, Clock.systemUTC());
    }

    BreezeAuthenticatedHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            BreezeTokenProvider tokenProvider,
            String baseUrl,
            Clock clock
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
        this.baseUrl = baseUrl;
        this.clock = clock;
    }

    public BreezeJsonResponse getJson(String endpoint, ObjectNode payload) {
        return sendJson(endpoint, "GET", BreezeRequestSigner.jsonBody(objectMapper, payload));
    }

    public BreezeJsonResponse postJson(String endpoint, ObjectNode payload) {
        return sendJson(endpoint, "POST", BreezeRequestSigner.jsonBody(objectMapper, payload));
    }

    public BreezeJsonResponse putJson(String endpoint, ObjectNode payload) {
        return sendJson(endpoint, "PUT", BreezeRequestSigner.jsonBody(objectMapper, payload));
    }

    public BreezeJsonResponse deleteJson(String endpoint, ObjectNode payload) {
        return sendJson(endpoint, "DELETE", BreezeRequestSigner.jsonBody(objectMapper, payload));
    }

    /**
     * v2 API: GET with query parameters, {@code X-SessionToken} + {@code apikey} headers (no checksum).
     * Used by {@code get_historical_data_v2} for {@code 1second} intervals.
     */
    public BreezeJsonResponse getV2Json(String endpoint, Map<String, String> queryParams) {
        tokenProvider.ensureValid();
        String query = queryParams.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> encodeQueryParam(entry.getKey()) + "=" + encodeQueryParam(entry.getValue()))
                .collect(Collectors.joining("&"));
        URI uri = query.isBlank()
                ? URI.create(BreezeApiEndpoints.API_V2_BASE + endpoint)
                : URI.create(BreezeApiEndpoints.API_V2_BASE + endpoint + "?" + query);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header(BreezeApiEndpoints.HEADER_CONTENT_TYPE, "application/json")
                .header(BreezeApiEndpoints.HEADER_SESSION_TOKEN, tokenProvider.session().headerSessionToken())
                .header(BreezeApiEndpoints.HEADER_API_KEY, tokenProvider.appKey())
                .header(BreezeApiEndpoints.HEADER_USER_AGENT, BreezeApiEndpoints.USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BreezeJsonResponse parsed = BreezeJsonResponse.parse(objectMapper, response.statusCode(), response.body());
            if (!parsed.isSuccess()) {
                throw new BreezeHttpException(
                        response.statusCode(),
                        parsed.errorMessage(),
                        "GET v2 " + endpoint
                );
            }
            return parsed;
        } catch (IOException ex) {
            throw new IllegalStateException("ICICI v2 request failed: GET " + endpoint, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ICICI v2 request interrupted: GET " + endpoint, ex);
        }
    }

    private static String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private BreezeJsonResponse sendJson(String endpoint, String method, String body) {
        tokenProvider.ensureValid();
        String timestamp = BreezeRequestSigner.timestamp(clock.instant());
        String checksumHeader = BreezeRequestSigner.checksumHeaderValue(timestamp, body, tokenProvider.secretKey());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header(BreezeApiEndpoints.HEADER_CONTENT_TYPE, "application/json")
                .header(BreezeApiEndpoints.HEADER_ACCEPT, "application/json")
                .header(BreezeApiEndpoints.HEADER_CHECKSUM, checksumHeader)
                .header(BreezeApiEndpoints.HEADER_TIMESTAMP, timestamp)
                .header(BreezeApiEndpoints.HEADER_APP_KEY, tokenProvider.appKey())
                .header(BreezeApiEndpoints.HEADER_SESSION_TOKEN, tokenProvider.session().headerSessionToken())
                .header(BreezeApiEndpoints.HEADER_USER_AGENT, BreezeApiEndpoints.USER_AGENT);
        HttpRequest request = switch (method) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(body)).build();
            case "DELETE" -> builder.method("DELETE", HttpRequest.BodyPublishers.ofString(body)).build();
            default -> builder.method("GET", HttpRequest.BodyPublishers.ofString(body)).build();
        };
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            BreezeJsonResponse parsed = BreezeJsonResponse.parse(objectMapper, response.statusCode(), response.body());
            if (!parsed.isSuccess()) {
                throw new BreezeHttpException(
                        response.statusCode(),
                        parsed.errorMessage(),
                        method + " " + endpoint
                );
            }
            return parsed;
        } catch (IOException ex) {
            throw new IllegalStateException("ICICI request failed: " + method + " " + endpoint, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ICICI request interrupted: " + method + " " + endpoint, ex);
        }
    }

    ObjectMapper objectMapper() {
        return objectMapper;
    }
}
