package com.tradej.broker.dhan.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.dhan.auth.DhanAuthenticationException;
import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
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
class DhanAuthenticatedHttpClientFailureTest {

    private static DhanAuthenticatedHttpClient createClient(HttpClient httpClient) {
        DhanTokenProvider tokenProvider = mock(DhanTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("test-token");
        DhanConnectionSettings settings = DhanConnectionSettings.sandboxWithDefaults("client-1", "test-token");
        return new DhanAuthenticatedHttpClient(httpClient, new ObjectMapper(), tokenProvider, settings);
    }

    @Test
    void throwsAuthExceptionOn401() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = stubResponse(401, "{\"error\":\"unauthorized\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DhanAuthenticatedHttpClient client = createClient(mockClient);
        DhanAuthenticationException ex = assertThrows(DhanAuthenticationException.class,
                () -> client.getJson("https://api.dhan.co/orders"));
        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    void throwsHttpExceptionOn500() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> response = stubResponse(500, "{\"error\":\"internal server error\"}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        DhanAuthenticatedHttpClient client = createClient(mockClient);
        DhanHttpException ex = assertThrows(DhanHttpException.class,
                () -> client.getJson("https://api.dhan.co/orders"));
        assertTrue(ex.getMessage().contains("500"));
    }

    @Test
    void throwsHttpExceptionOnNetworkFailure() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection reset"));

        DhanAuthenticatedHttpClient client = createClient(mockClient);
        DhanHttpException ex = assertThrows(DhanHttpException.class,
                () -> client.getJson("https://api.dhan.co/orders"));
        assertTrue(ex.getMessage().contains("Connection reset"));
    }

    @Test
    void restoresInterruptOnInterruptedException() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("shutdown"));

        DhanAuthenticatedHttpClient client = createClient(mockClient);
        DhanHttpException ex = assertThrows(DhanHttpException.class,
                () -> client.getJson("https://api.dhan.co/orders"));
        assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag must be restored");
        assertTrue(ex.getMessage().contains("interrupted"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> stubResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
