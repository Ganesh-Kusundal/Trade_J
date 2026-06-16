package com.tradej.broker.dhan.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanApiEndpoints;
import com.tradej.broker.dhan.exceptions.DhanExceptionUtil;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;

public final class DhanAuthenticatedHttpClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DhanTokenProvider tokenProvider;
    private final DhanConnectionSettings settings;

    public DhanAuthenticatedHttpClient(DhanTokenProvider tokenProvider, DhanConnectionSettings settings) {
        this(HttpClient.newBuilder()
                .executor(Executors.newFixedThreadPool(4, r -> {
                    Thread t = new Thread(r, "dhan-http");
                    t.setDaemon(true);
                    return t;
                }))
                .build(),
                new ObjectMapper(), tokenProvider, settings);
    }

    public DhanAuthenticatedHttpClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            DhanTokenProvider tokenProvider,
            DhanConnectionSettings settings
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
        this.settings = settings;
    }

    public DhanJsonResponse postJson(String url, ObjectNode payload) {
        return sendJson(url, "POST", payload == null ? "{}" : serialize(payload));
    }

    public DhanJsonResponse getJson(String url) {
        return sendJson(url, "GET", null);
    }

    public DhanJsonResponse deleteJson(String url) {
        return sendJson(url, "DELETE", null);
    }

    public DhanJsonResponse putJson(String url, ObjectNode payload) {
        return sendJson(url, "PUT", payload == null ? "{}" : serialize(payload));
    }

    private DhanJsonResponse sendJson(String url, String method, String body) {
        try {
            tokenProvider.ensureValid();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", DhanApiEndpoints.HEADER_ACCEPT_JSON)
                    .header("Content-Type", DhanApiEndpoints.HEADER_CONTENT_TYPE_JSON)
                    .header(DhanApiEndpoints.HEADER_ACCESS_TOKEN, tokenProvider.getAccessToken())
                    .header(DhanApiEndpoints.HEADER_CLIENT_ID, settings.clientId());
            HttpRequest request;
            if ("POST".equals(method)) {
                request = builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)).build();
            } else if ("PUT".equals(method)) {
                request = builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)).build();
            } else if ("DELETE".equals(method)) {
                request = builder.DELETE().build();
            } else {
                request = builder.GET().build();
            }
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            DhanExceptionUtil.verifyHttpSuccess(response.statusCode(), response.body(), method + " " + url);
            return new DhanJsonResponse(objectMapper.readTree(response.body()));
        } catch (IOException ex) {
            DhanExceptionUtil.rethrowIoError(method + " " + url, ex);
        } catch (InterruptedException ex) {
            DhanExceptionUtil.rethrowInterruption(method + " " + url, ex);
        }
        throw new IllegalStateException("Unreachable");
    }

    private String serialize(ObjectNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (IOException ex) {
            DhanExceptionUtil.rethrowIoError("serialize payload", ex);
            return "{}";
        }
    }
}
