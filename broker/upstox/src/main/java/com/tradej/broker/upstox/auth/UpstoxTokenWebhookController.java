package com.tradej.broker.upstox.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * HTTP controller for Upstox's "Access Token Request" webhook (Flow 2).
 * <p>
 * Per the V3 docs, the user configures a notifier URL in their Upstox
 * developer console. When they approve a daily token request on the
 * Upstox mobile app, Upstox POSTs the new access token (and an
 * authorization_expiry epoch millis) to that URL.
 * <p>
 * This controller accepts the standard payload shape:
 * <pre>{@code
 *   POST /upstox/token-webhook
 *   {
 *     "access_token": "...",
 *     "authorization_expiry": "1740729366039"
 *   }
 * }</pre>
 * <p>
 * The controller is intentionally framework-free: it takes a raw JSON body
 * and returns a {@link WebhookResult}. The composition layer wires it to
 * a Spring {@code @PostMapping} or equivalent servlet endpoint.
 * <p>
 * <b>Security:</b> the Upstox notifier URL is a secret. The composition
 * layer should require a shared HMAC header (e.g. {@code X-Upstox-Signature})
 * on incoming webhook requests and reject any request that doesn't match.
 * That glue lives outside this module.
 */
public final class UpstoxTokenWebhookController {

    private static final Logger log = LoggerFactory.getLogger(UpstoxTokenWebhookController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxTokenManager tokenManager;
    private final String sourceLabel;

    public UpstoxTokenWebhookController(UpstoxTokenManager tokenManager, String sourceLabel) {
        this.tokenManager = Objects.requireNonNull(tokenManager, "tokenManager");
        this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
    }

    public UpstoxTokenWebhookController(UpstoxTokenManager tokenManager) {
        this(tokenManager, "upstox");
    }

    /**
     * Handles a webhook POST. The body must be a JSON object with at least
     * {@code access_token} and {@code authorization_expiry}.
     *
     * @return a {@link WebhookResult} indicating success and whether the
     *         token was actually replaced
     */
    public WebhookResult handle(String jsonBody) {
        if (jsonBody == null || jsonBody.isBlank()) {
            return WebhookResult.rejected("empty body");
        }
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            String accessToken = root.has("access_token") ? root.get("access_token").asText() : null;
            String authExpiry = root.has("authorization_expiry")
                    ? root.get("authorization_expiry").asText() : null;
            if (accessToken == null || accessToken.isBlank()) {
                return WebhookResult.rejected("missing access_token");
            }
            if (authExpiry == null || authExpiry.isBlank()) {
                return WebhookResult.rejected("missing authorization_expiry");
            }
            long expiresAtMs;
            try {
                expiresAtMs = Long.parseLong(authExpiry);
            } catch (NumberFormatException nfe) {
                return WebhookResult.rejected("authorization_expiry is not a numeric epoch ms");
            }
            tokenManager.upgradeFromWebhook(accessToken, expiresAtMs);
            log.info("Upstox token upgraded via webhook[{}]: expiresAt={}", sourceLabel, expiresAtMs);
            return WebhookResult.applied(expiresAtMs);
        } catch (Exception e) {
            log.warn("Upstox webhook[{}] rejected: {}", sourceLabel, e.getMessage());
            return WebhookResult.rejected("parse error: " + e.getMessage());
        }
    }

    /**
     * Result of a webhook invocation.
     */
    public record WebhookResult(boolean applied, long expiresAtMs, String reason) {
        public static WebhookResult applied(long expiresAtMs) {
            return new WebhookResult(true, expiresAtMs, "");
        }
        public static WebhookResult rejected(String reason) {
            return new WebhookResult(false, 0L, reason);
        }
    }
}
