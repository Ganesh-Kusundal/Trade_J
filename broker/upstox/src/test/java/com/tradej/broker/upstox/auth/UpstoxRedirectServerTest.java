package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxRedirectServerTest {

    private UpstoxRedirectServer server;

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 19282;
        }
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.close();
    }

    @Test
    void capturesAuthorizationCode() throws Exception {
        int port = findFreePort();
        server = new UpstoxRedirectServer(port, "/callback");
        Thread waiter = new Thread(() -> {
            try {
                String code = server.waitForAuthorization(5000);
                assertNotNull(code);
                assertEquals("AUTH_CODE_123", code);
            } catch (Exception e) {
                fail("Should have received code, got: " + e.getMessage());
            }
        });
        waiter.start();

        Thread.sleep(200);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/callback?code=AUTH_CODE_123&state=test_state"))
                .GET()
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
        waiter.join(5000);
    }

    @Test
    void returnsErrorWhenNoCode() throws Exception {
        int port = findFreePort();
        server = new UpstoxRedirectServer(port, "/callback");
        Thread waiter = new Thread(() -> {
            assertThrows(UpstoxAuthException.class, () ->
                    server.waitForAuthorization(3000));
        });
        waiter.start();

        Thread.sleep(200);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/callback?error=access_denied"))
                .GET()
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
        waiter.join(5000);
    }

    @Test
    void timesOutWhenNoCallback() throws Exception {
        int port = findFreePort();
        server = new UpstoxRedirectServer(port, "/callback");
        assertThrows(UpstoxAuthException.class, () ->
                server.waitForAuthorization(500));
    }

    @Test
    void redirectUriIsCorrect() throws Exception {
        int port = findFreePort();
        server = new UpstoxRedirectServer(port, "/callback");
        String uri = server.redirectUri();
        assertEquals("http://localhost:" + port + "/callback", uri);
    }
}
