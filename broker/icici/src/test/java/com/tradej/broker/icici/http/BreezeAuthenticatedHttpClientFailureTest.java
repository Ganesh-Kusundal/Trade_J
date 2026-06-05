package com.tradej.broker.icici.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.auth.BreezeSession;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
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
class BreezeAuthenticatedHttpClientFailureTest {

    private static BreezeAuthenticatedHttpClient createClient(HttpClient httpClient) {
        BreezeTokenProvider tokenProvider = mock(BreezeTokenProvider.class);
        when(tokenProvider.appKey()).thenReturn("app-key");
        when(tokenProvider.secretKey()).thenReturn("secret");
        BreezeSession session = BreezeSession.fromUserAndKey("user", "sess", 0, Long.MAX_VALUE);
        when(tokenProvider.session()).thenReturn(session);
        return new BreezeAuthenticatedHttpClient(httpClient, new ObjectMapper(), tokenProvider, "https://api.icicidirect.com/apiuser/");
    }

    @Test
    void throwsOn401() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = stubResponse(401, "{\"Status\":401,\"Error\":\"Unauthorized\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BreezeAuthenticatedHttpClient client = createClient(mockClient);
        BreezeHttpException ex = assertThrows(BreezeHttpException.class,
                () -> client.getJson("/portfolio", new ObjectMapper().createObjectNode()));
        assertEquals(401, ex.httpStatus());
    }

    @Test
    void throwsOn500() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = stubResponse(500, "{\"Status\":500,\"Error\":\"Internal Server Error\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BreezeAuthenticatedHttpClient client = createClient(mockClient);
        BreezeHttpException ex = assertThrows(BreezeHttpException.class,
                () -> client.postJson("/order", new ObjectMapper().createObjectNode()));
        assertEquals(500, ex.httpStatus());
    }

    @Test
    void throwsOnNetworkFailure() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection timeout"));

        BreezeAuthenticatedHttpClient client = createClient(mockClient);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.getJson("/portfolio", new ObjectMapper().createObjectNode()));
        assertTrue(ex.getCause() instanceof IOException, "Expected IOException cause but got: " + ex.getCause());
    }

    @Test
    void restoresInterruptOnInterruptedException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("shutdown"));

        BreezeAuthenticatedHttpClient client = createClient(mockClient);
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> client.getJson("/portfolio", new ObjectMapper().createObjectNode()));
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored");
        assertTrue(ex.getMessage().contains("interrupted"));
    }

    @Test
    void v2ThrowsOn401() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = stubResponse(401, "{\"error\":\"Unauthorized\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        BreezeAuthenticatedHttpClient client = createClient(mockClient);
        BreezeHttpException ex = assertThrows(BreezeHttpException.class,
                () -> client.getV2Json("/historical", java.util.Map.of("stock", "TCS")));
        assertEquals(401, ex.httpStatus());
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stubResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
