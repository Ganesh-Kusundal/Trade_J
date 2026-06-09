package com.tradej.app.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
 * PagerDuty Events API v2 alert channel.
 *
 * <p>Sends alerts to PagerDuty via the Events API v2 integration.
 * Configure via: {@code tradej.alerts.pagerduty-routing-key}
 *
 * <p>Severity mapping:
 * <ul>
 *   <li>CRITICAL → PagerDuty "critical" trigger</li>
 *   <li>WARNING → PagerDuty "warning" trigger</li>
 *   <li>INFO → PagerDuty "info" trigger</li>
 * </ul>
 */
public final class PagerDutyAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(PagerDutyAlertChannel.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String EVENTS_API = "https://events.pagerduty.com/v2/enqueue";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String routingKey;
    private final HttpClient httpClient;

    public PagerDutyAlertChannel(String routingKey) {
        this.routingKey = routingKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public void send(String severity, String component, String message) {
        if (routingKey == null || routingKey.isBlank()) return;

        try {
            String pdSeverity = switch (severity.toUpperCase()) {
                case "CRITICAL" -> "critical";
                case "WARNING" -> "warning";
                default -> "info";
            };

            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("routing_key", routingKey);
            payload.put("event_action", "trigger");

            ObjectNode eventPayload = payload.putObject("payload");
            eventPayload.put("summary", String.format("[%s] %s: %s", severity, component, message));
            eventPayload.put("severity", pdSeverity);
            eventPayload.put("source", "trade-j");
            eventPayload.put("component", component);
            eventPayload.put("timestamp", Instant.now().toString());
            eventPayload.put("group", "trading-platform");
            eventPayload.put("class", severity.toLowerCase());

            ObjectNode customDetails = eventPayload.putObject("custom_details");
            customDetails.put("alert_severity", severity);
            customDetails.put("component", component);
            customDetails.put("message", message);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EVENTS_API))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("PagerDuty returned HTTP {}: {}", response.statusCode(), response.body());
            } else {
                log.debug("PagerDuty alert sent for {}/{}", component, severity);
            }
        } catch (Exception e) {
            log.warn("Failed to send PagerDuty alert: {}", e.getMessage());
        }
    }
}
