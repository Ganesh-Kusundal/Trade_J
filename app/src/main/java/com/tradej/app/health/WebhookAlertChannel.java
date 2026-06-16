package com.tradej.app.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Sends alert payloads as JSON POST to a configurable webhook URL.
 * Uses Java 11+ HttpClient with a 5-second timeout to avoid blocking health checks.
 */
public final class WebhookAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(WebhookAlertChannel.class);
    private static final Duration TIMEOUT = AlertChannelDefaults.DEFAULT_TIMEOUT;

    private final String webhookUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WebhookAlertChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void send(String severity, String component, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.debug("Webhook URL not configured — skipping alert");
            return;
        }

        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("severity", severity);
            payload.put("component", component);
            payload.put("message", message);
            payload.put("timestamp", Instant.now().toString());
            payload.put("source", "trade-j");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Webhook alert returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to send webhook alert to {}: {}", webhookUrl, e.getMessage());
        }
    }
}
