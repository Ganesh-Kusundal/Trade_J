package com.tradej.broker.upstox.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxOAuthClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer mockServer;
    private UpstoxOAuthClient client;
    private int port;

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 19181;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        port = findFreePort();
        mockServer = HttpServer.create(new InetSocketAddress(port), 0);
        mockServer.setExecutor(null);
        mockServer.start();
        client = new UpstoxOAuthClient(HttpClient.newHttpClient(), "http://localhost:" + port);
    }

    @AfterEach
    void tearDown() {
        mockServer.stop(0);
    }

    @Test
    void exchangesCodeForToken() throws Exception {
        mockServer.createContext("/login/authorization/token", exchange -> {
            byte[] body = """
                    {"access_token":"test_access","refresh_token":"test_refresh","expires_in":86400}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        var response = client.exchangeCode("code123", "client1", "secret1", "http://localhost/cb", "verifier123");
        assertNotNull(response);
        assertEquals("test_access", response.accessToken());
        assertEquals("test_refresh", response.refreshToken());
        assertEquals(86400L, response.expiresInSeconds());
    }

    @Test
    void refreshesToken() throws Exception {
        mockServer.createContext("/login/authorization/token", exchange -> {
            byte[] body = """
                    {"access_token":"refreshed_access","refresh_token":"new_refresh","expires_in":86400}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        var response = client.refreshToken("old_refresh", "client1", "secret1");
        assertEquals("refreshed_access", response.accessToken());
        assertEquals("new_refresh", response.refreshToken());
    }

    @Test
    void throwsOnErrorResponse() throws Exception {
        mockServer.createContext("/login/authorization/token", exchange -> {
            byte[] body = "{\"error\":\"invalid_grant\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });

        assertThrows(UpstoxAuthException.class, () ->
                client.exchangeCode("bad_code", "client1", "secret1", "http://localhost/cb", "verifier"));
    }

    @Test
    void fetchProfileReturnsNegOneOnError() throws Exception {
        mockServer.createContext("/user/profile", exchange -> {
            exchange.sendResponseHeaders(401, -1);
        });

        long result = client.fetchProfile("invalid_token");
        assertEquals(-1L, result);
    }
}
