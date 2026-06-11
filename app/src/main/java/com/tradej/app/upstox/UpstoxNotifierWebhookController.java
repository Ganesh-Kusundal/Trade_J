package com.tradej.app.upstox;

import com.tradej.broker.upstox.auth.UpstoxTokenManager;
import com.tradej.broker.upstox.auth.UpstoxTokenExpiry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Public endpoint that receives the Upstox notifier webhook (Flow 2).
 * <p>
 * Configure this URL in Upstox Developer Apps → Notifier Webhook Endpoint.
 * Upstox sends a POST with no authentication headers. The endpoint must
 * return HTTP 200 promptly.
 *
 * <p>Security: in production, validate a shared secret or HMAC returned via
 * Upstox developer console rather than accepting unauthenticated POSTs from
 * any source.
 */
@RestController
@RequestMapping("/upstox")
@ConditionalOnExpression("'${trade.broker-type:}' == 'upstox'")
public class UpstoxNotifierWebhookController {

    private static final Logger log = LoggerFactory.getLogger(UpstoxNotifierWebhookController.class);

    private final UpstoxTokenManager upstoxTokenManager;

    public UpstoxNotifierWebhookController(UpstoxTokenManager upstoxTokenManager) {
        this.upstoxTokenManager = upstoxTokenManager;
    }

    /**
     * Receives the access token delivery from Upstox notifier webhook.
     * <p>
     * Expected payload (simplified):
     * <pre>
     * {
     *   "client_id": "615b1297-...",
     *   "user_id": "ABCDEF",
     *   "access_token": "eyJ...",
     *   "token_type": "Bearer",
     *   "expires_at": "1731448800000",
     *   "issued_at": "1731412800000",
     *   "message_type": "access_token"
     * }
     * </pre>
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> receiveToken(@RequestBody Map<String, Object> payload) {
        String messageType = asString(payload.get("message_type"));
        if (!"access_token".equals(messageType)) {
            log.debug("Ignoring non-token notifier message: type={}", messageType);
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }

        String accessToken = asString(payload.get("access_token"));
        String expiresAtStr = asString(payload.get("expires_at"));

        if (accessToken == null || accessToken.isBlank()) {
            log.warn("Notifier webhook received without access_token: {}", payload);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("status", "error", "reason", "missing access_token"));
        }

        long expiresAtMs;
        try {
            expiresAtMs = Long.parseLong(expiresAtStr);
        } catch (Exception ex) {
            // Fallback: calculate next 3:30 AM IST if Upstox didn't provide expiry
            expiresAtMs = UpstoxTokenExpiry.nextExpiryEpochMs();
            log.warn("Notifier webhook missing expires_at; falling back to next 3:30 AM IST = {}", expiresAtMs);
        }

        try {
            upstoxTokenManager.upgradeFromWebhook(accessToken, expiresAtMs);
            log.info("Upstox token upgraded via notifier webhook for user_id={}", asString(payload.get("user_id")));
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception ex) {
            log.error("Failed to process notifier webhook token: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "error"));
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
