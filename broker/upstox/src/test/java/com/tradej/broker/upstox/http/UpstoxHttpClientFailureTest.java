package com.tradej.broker.upstox.http;

import com.tradej.broker.upstox.auth.UpstoxBearerTokenSource;
import com.tradej.broker.upstox.config.UpstoxApiEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class UpstoxHttpClientFailureTest {

    private static UpstoxHttpClient createClient(HttpClient httpClient) {
        UpstoxBearerTokenSource tokenSource = mock(UpstoxBearerTokenSource.class);
        when(tokenSource.bearerToken()).thenReturn("test-token");
        return new UpstoxHttpClient(httpClient, tokenSource, UpstoxApiEnvironment.LIVE.baseUrl());
    }

    @Test
    void throwsOnNetworkFailure() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        UpstoxHttpClient client = createClient(mockClient);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.get("/user/profile"));
        assertTrue(ex.getCause() instanceof IOException, "Expected IOException cause but got: " + ex.getCause());
    }

    @Test
    void restoresInterruptOnInterruptedException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("shutdown"));

        UpstoxHttpClient client = createClient(mockClient);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.get("/user/profile"));
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored");
        assertTrue(ex.getMessage().contains("interrupted"));
    }

    @Test
    void throwsOnPostNetworkFailure() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Broken pipe"));

        UpstoxHttpClient client = createClient(mockClient);
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> client.post("/order/place", "{}"));
        assertTrue(ex.getCause() instanceof IOException, "Expected IOException cause but got: " + ex.getCause());
    }
}
