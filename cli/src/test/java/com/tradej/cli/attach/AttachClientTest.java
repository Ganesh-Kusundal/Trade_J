package com.tradej.cli.attach;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class AttachClientTest {
    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/actuator/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        server.createContext("/admin/summary", exchange -> write(exchange, 200,
                "{\"startupCompleted\":true,\"catalogSize\":42,\"tickRate\":12.5}"));
        server.createContext("/admin/runtime", exchange -> write(exchange, 200,
                "{\"websocketConnected\":true,\"catalogLoaded\":true}"));
        server.createContext("/api/v1/read-model", exchange -> write(exchange, 200,
                "{\"version\":1,\"orders\":[],\"positions\":[]}"));
        server.createContext("/admin/historical/stats", exchange -> write(exchange, 200,
                "{\"symbol\":\"NIFTY\",\"tickCount\":10,\"hasData\":true}"));
        server.createContext("/admin/risk/kill-switch/true", exchange -> write(exchange, 200,
                "{\"enabled\":true,\"acknowledged\":true}"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void parsesHealthSummaryAndReadModel() {
        AttachClient client = new AttachClient("http://127.0.0.1:" + port);
        assertTrue(client.isReachable());
        assertEquals("UP", client.health().get("status").asText());
        assertEquals(42, client.summary().get("catalogSize").asInt());
        assertTrue(client.runtime().get("websocketConnected").asBoolean());
        JsonNode readModel = client.readModel();
        assertEquals(1, readModel.get("version").asInt());
    }

    @Test
    void historicalStatsQueryParameters() {
        AttachClient client = new AttachClient("http://127.0.0.1:" + port);
        JsonNode stats = client.historicalStats("NIFTY", 1L, 2L);
        assertEquals("NIFTY", stats.get("symbol").asText());
        assertEquals(10, stats.get("tickCount").asInt());
    }

    @Test
    void killSwitchPost() {
        AttachClient client = new AttachClient("http://127.0.0.1:" + port);
        JsonNode response = client.killSwitch(true);
        assertTrue(response.get("enabled").asBoolean());
    }

    @Test
    void unreachableHostReturnsFalse() {
        AttachClient client = new AttachClient("http://127.0.0.1:9");
        assertFalse(client.isReachable());
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
