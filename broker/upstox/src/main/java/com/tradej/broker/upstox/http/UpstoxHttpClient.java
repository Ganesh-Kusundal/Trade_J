package com.tradej.broker.upstox.http;

import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.constants.UpstoxEndpoints;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Authenticated HTTP client for Upstox REST API.
 * <p>
 * Automatically injects the Bearer token header from the token manager.
 * Used by all REST client classes.
 */
public final class UpstoxHttpClient {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final UpstoxBearerTokenSource tokenSource;
    private final String baseUrl;

    public UpstoxHttpClient(HttpClient httpClient, UpstoxBearerTokenSource tokenSource, String baseUrl) {
        this.httpClient = httpClient;
        this.tokenSource = tokenSource;
        this.baseUrl = baseUrl;
    }

    /**
     * Executes an authenticated GET request.
     */
    public HttpResponse<String> get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(UpstoxEndpoints.HEADER_AUTHORIZATION, authorizationHeader())
                .header(UpstoxEndpoints.HEADER_ACCEPT, "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return send(request);
    }

    /**
     * Executes an authenticated POST request with a JSON body.
     */
    public HttpResponse<String> post(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(UpstoxEndpoints.HEADER_AUTHORIZATION, authorizationHeader())
                .header(UpstoxEndpoints.HEADER_CONTENT_TYPE, "application/json")
                .header(UpstoxEndpoints.HEADER_ACCEPT, "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(request);
    }

    /**
     * Executes an authenticated PUT request with a JSON body.
     */
    public HttpResponse<String> put(String path, String jsonBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(UpstoxEndpoints.HEADER_AUTHORIZATION, authorizationHeader())
                .header(UpstoxEndpoints.HEADER_CONTENT_TYPE, "application/json")
                .header(UpstoxEndpoints.HEADER_ACCEPT, "application/json")
                .timeout(REQUEST_TIMEOUT)
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return send(request);
    }

    /**
     * Executes an authenticated DELETE request.
     */
    public HttpResponse<String> delete(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header(UpstoxEndpoints.HEADER_AUTHORIZATION, authorizationHeader())
                .header(UpstoxEndpoints.HEADER_ACCEPT, "application/json")
                .timeout(REQUEST_TIMEOUT)
                .DELETE()
                .build();
        return send(request);
    }

    public String baseUrl() {
        return baseUrl;
    }

    private String authorizationHeader() {
        tokenSource.ensureValid();
        return UpstoxEndpoints.BEARER_PREFIX + tokenSource.bearerToken();
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new RuntimeException("Upstox HTTP request failed: " + request.uri(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Upstox HTTP request interrupted: " + request.uri(), e);
        }
    }
}
