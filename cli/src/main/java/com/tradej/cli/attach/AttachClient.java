package com.tradej.cli.attach;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.function.Consumer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AttachClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient;

    public AttachClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    public boolean isReachable() {
        try {
            getJson("/actuator/health");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public JsonNode health() {
        return getJson("/actuator/health");
    }

    public JsonNode runtime() {
        return getJson("/admin/runtime");
    }

    public JsonNode pipeline() {
        return getJson("/admin/pipeline");
    }

    public JsonNode strategies() {
        return getJson("/admin/strategies");
    }

    /**
     * Calls the per-strategy replay-parity endpoint.
     */
    public JsonNode parity(String pluginId, String symbol, long fromMs, long toMs) {
        return getJson("/api/v1/strategies/parity/" + pluginId, Map.of(
                "symbol", symbol,
                "fromMs", Long.toString(fromMs),
                "toMs", Long.toString(toMs)
        ));
    }

    public JsonNode summary() {
        return getJson("/admin/summary");
    }

    public JsonNode readModel() {
        return getJson("/api/v1/read-model");
    }

    public JsonNode historicalCandles(String symbol, String interval, long from, long to, int limit) {
        return getJson("/admin/historical/candles", Map.of(
                "symbol", symbol,
                "interval", interval,
                "from", Long.toString(from),
                "to", Long.toString(to),
                "limit", Integer.toString(limit)
        ));
    }

    public JsonNode historicalTicks(String symbol, long from, long to, int limit) {
        return getJson("/admin/historical/ticks", Map.of(
                "symbol", symbol,
                "from", Long.toString(from),
                "to", Long.toString(to),
                "limit", Integer.toString(limit)
        ));
    }

    public JsonNode historicalOrders(String symbol, long from, long to, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        params.put("from", Long.toString(from));
        params.put("to", Long.toString(to));
        params.put("limit", Integer.toString(limit));
        return getJson("/admin/historical/orders", params);
    }

    public JsonNode historicalFills(String symbol, long from, long to, int limit) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        params.put("from", Long.toString(from));
        params.put("to", Long.toString(to));
        params.put("limit", Integer.toString(limit));
        return getJson("/admin/historical/fills", params);
    }

    public JsonNode historicalStats(String symbol, long from, long to) {
        return getJson("/admin/historical/stats", Map.of(
                "symbol", symbol,
                "from", Long.toString(from),
                "to", Long.toString(to)
        ));
    }

    public JsonNode killSwitch(boolean enabled) {
        return postJson("/admin/risk/kill-switch/" + enabled, Map.of());
    }

    public JsonNode reconcile(Map<String, Long> expectedNetPositions) {
        return postJsonBody("/admin/reconcile", expectedNetPositions);
    }

    public JsonNode replayTicks(String symbol, long from, long to) {
        return postJson("/admin/historical/replay/ticks", Map.of(
                "symbol", symbol,
                "from", Long.toString(from),
                "to", Long.toString(to)
        ));
    }

    public JsonNode replayCandles(String symbol, String interval, long from, long to) {
        return postJson("/admin/historical/replay/candles", Map.of(
                "symbol", symbol,
                "interval", interval,
                "from", Long.toString(from),
                "to", Long.toString(to)
        ));
    }

    public JsonNode replayFills(String symbol, long from, long to) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        params.put("from", Long.toString(from));
        params.put("to", Long.toString(to));
        return postJson("/admin/historical/replay/fills", params);
    }

    public JsonNode replayOrders(String symbol, long from, long to) {
        Map<String, String> params = new LinkedHashMap<>();
        if (symbol != null && !symbol.isBlank()) {
            params.put("symbol", symbol);
        }
        params.put("from", Long.toString(from));
        params.put("to", Long.toString(to));
        return postJson("/admin/historical/replay/orders", params);
    }

    public JsonNode replayChronicle(String eventType) {
        return postJson("/admin/chronicle/replay", Map.of("eventType", eventType));
    }

    public JsonNode scanRun(String profileId) {
        return postJson("/api/v1/scans/run", Map.of("profile", profileId));
    }

    public JsonNode scanList(String profileId, int limit) {
        return getJson("/api/v1/scans", Map.of(
                "profile", profileId,
                "limit", Integer.toString(limit)
        ));
    }

    public JsonNode scanLatest(String profileId) {
        return getJson("/api/v1/scans/latest", Map.of("profile", profileId));
    }

    public JsonNode optionsScan(
            String underlying,
            String segment,
            String expiryPolicy,
            LocalDate explicitExpiry,
            String side,
            int top,
            long minOi,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread
    ) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("underlying", underlying);
        params.put("segment", segment);
        if (expiryPolicy != null && !expiryPolicy.isBlank()) {
            params.put("expiry", expiryPolicy);
        }
        if (explicitExpiry != null) {
            params.put("expiryDate", explicitExpiry.toString());
        }
        params.put("side", side);
        params.put("top", Integer.toString(top));
        params.put("minOi", Long.toString(minOi));
        params.put("minVolume", Long.toString(minVolume));
        params.put("maxSpreadBps", Double.toString(maxSpreadBps));
        params.put("strictSpread", Boolean.toString(strictSpread));
        return postJson("/api/v1/options/scan", params);
    }

    public JsonNode getJson(String path) {
        return getJson(path, Map.of());
    }

    public JsonNode getJson(String path, Map<String, String> query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + toQuery(query)))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException ex) {
            throw new IllegalStateException("GET " + path + " failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GET " + path + " interrupted", ex);
        }
    }

    private JsonNode postJson(String path, Map<String, String> query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path + toQuery(query)))
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException ex) {
            throw new IllegalStateException("POST " + path + " failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("POST " + path + " interrupted", ex);
        }
    }

    private JsonNode postJsonBody(String path, Object body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(120))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response);
        } catch (IOException ex) {
            throw new IllegalStateException("POST " + path + " failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("POST " + path + " interrupted", ex);
        }
    }

    private static JsonNode parseResponse(HttpResponse<String> response) throws IOException {
        JsonNode node = MAPPER.readTree(response.body() == null ? "{}" : response.body());
        if (response.statusCode() >= 400) {
            String error = node.has("error") ? node.get("error").asText() : response.body();
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + error);
        }
        return node;
    }

    private static String toQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("?");
        boolean first = true;
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    public Map<String, Object> readMap(JsonNode node) {
        return MAPPER.convertValue(node, new TypeReference<>() {
        });
    }

    public void streamReadModel(int seconds, Consumer<String> lineConsumer) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/stream/read-model"))
                    .header("Accept", "text/event-stream")
                    .GET()
                    .timeout(Duration.ofSeconds(seconds + 5L))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("SSE stream failed: HTTP " + response.statusCode());
            }
            long endMs = System.currentTimeMillis() + (seconds * 1000L);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while (System.currentTimeMillis() < endMs && (line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith(":")) {
                        continue;
                    }
                    lineConsumer.accept(line);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("SSE stream failed: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SSE stream interrupted", ex);
        }
    }
}
