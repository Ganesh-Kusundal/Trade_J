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

/**
 * Slack webhook alert channel.
 *
 * <p>Sends alerts to a Slack channel via incoming webhook URL.
 * Configure via: {@code tradej.alerts.slack-webhook-url}
 */
public final class SlackAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(SlackAlertChannel.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String webhookUrl;
    private final HttpClient httpClient;

    public SlackAlertChannel(String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public void send(String severity, String component, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        try {
            String emoji = switch (severity.toUpperCase()) {
                case "CRITICAL" -> ":red_circle:";
                case "WARNING" -> ":warning:";
                default -> ":information_source:";
            };

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("text", String.format("%s *[%s]* `%s` — %s\n%s",
                    emoji, severity, component, message,
                    "_" + Instant.now() + "_"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Slack webhook returned HTTP {}: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Failed to send Slack alert: {}", e.getMessage());
        }
    }
}
