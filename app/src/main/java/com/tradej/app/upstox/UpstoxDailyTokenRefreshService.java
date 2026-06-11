package com.tradej.app.upstox;

import com.tradej.broker.upstox.auth.UpstoxOAuthClient;
import com.tradej.broker.upstox.auth.UpstoxTokenManager;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Daily scheduler that triggers the Upstox Access Token Request API (Flow 2).
 * <p>
 * Default cron: 4:00 AM IST daily (after 3:30 AM IST expiry, before market open).
 * Configure via Spring property {@code trade.upstox.token-refresh-cron}.
 * <p>
 * On each tick, calls {@code POST /v3/login/auth/token/request/{clientId}}
 * to push a notification to the user. The user approves via the Upstox app,
 * and the token is delivered to {@link UpstoxNotifierWebhookController}.
 */
@Component
@ConditionalOnExpression("'${trade.broker-type:}' == 'upstox'")
public class UpstoxDailyTokenRefreshService {

    private static final Logger log = LoggerFactory.getLogger(UpstoxDailyTokenRefreshService.class);

    private final UpstoxConnectionSettings connectionSettings;
    private final UpstoxTokenManager upstoxTokenManager;

    public UpstoxDailyTokenRefreshService(UpstoxConnectionSettings connectionSettings,
                                          UpstoxTokenManager upstoxTokenManager) {
        this.connectionSettings = connectionSettings;
        this.upstoxTokenManager = upstoxTokenManager;
    }

    /**
     * Triggered by Spring @Scheduled. Calls Upstox token request API.
     * <p>
     * If the token manager already has a valid token, this is a no-op from
     * the scheduler's perspective — the request is still sent to prompt the user.
     */
    @Scheduled(cron = "${trade.upstox.token-refresh-cron:0 0 4 * * *}")
    public void triggerDailyRefresh() {
        if (connectionSettings.analyticsOnly()) {
            log.debug("Skipping Upstox daily token refresh: analyticsOnly=true");
            return;
        }

        String clientId = connectionSettings.clientId();
        String clientSecret = connectionSettings.clientSecret();

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            log.warn("Cannot trigger Upstox daily refresh: clientId/clientSecret not configured");
            return;
        }

        try {
            UpstoxOAuthClient client = new UpstoxOAuthClient(
                    HttpClient.newHttpClient(),
                    connectionSettings.isSandbox()
                            ? "https://sandbox-api.upstox.com/v2"
                            : "https://api.upstox.com/v2"
            );
            Map<String, Object> result = client.triggerTokenRequest(clientId, clientSecret);
            String status = result.containsKey("status") ? result.get("status").toString() : "unknown";
            String authExpiry = result.containsKey("authorizationExpiry")
                    ? result.get("authorizationExpiry").toString() : "unknown";

            log.info("Upstox daily token request sent: status={}, authorizationExpiry={}", status, authExpiry);

            if (!"success".equalsIgnoreCase(status)) {
                log.warn("Upstox token request returned non-success status: {}", result);
            }
        } catch (Exception ex) {
            log.error("Upstox daily token refresh failed: {}", ex.getMessage(), ex);
            // Do not throw — let the scheduler run again next day.
            // In production, alert after N consecutive failures.
        }
    }
}
