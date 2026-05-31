package com.tradej.broker.upstox.http;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpstoxResponseGuardTest {

    @Test
    void rejectsNon2xxStatus() {
        HttpResponse<String> response = stubResponse(401,
                "{\"status\":\"error\",\"errors\":[{\"errorCode\":\"UDAPI100050\",\"message\":\"Invalid token\"}]}");

        UpstoxApiException ex = assertThrows(UpstoxApiException.class,
                () -> UpstoxResponseGuard.requireSuccessBody(response));
        assertEquals(401, ex.httpStatus());
        assertEquals("UDAPI100050", ex.errorCode());
    }

    @Test
    void rejectsErrorStatusInBody() {
        HttpResponse<String> response = stubResponse(200,
                "{\"status\":\"error\",\"errors\":[{\"errorCode\":\"UDAPI100001\",\"message\":\"bad request\"}]}");

        assertThrows(UpstoxApiException.class, () -> UpstoxResponseGuard.requireSuccessBody(response));
    }

    @Test
    void acceptsSuccessBody() {
        HttpResponse<String> response = stubResponse(200, "{\"status\":\"success\",\"data\":{}}");

        String body = UpstoxResponseGuard.requireSuccessBody(response);
        assertEquals("{\"status\":\"success\",\"data\":{}}", body);
    }

    private static HttpResponse<String> stubResponse(int status, String body) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return HttpRequest.newBuilder(URI.create("https://api.upstox.com/v2/market/status/NSE")).GET().build();
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(java.util.Map.of(), (a, b) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://api.upstox.com/v2/market/status/NSE");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_2;
            }
        };
    }
}
