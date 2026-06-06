package com.tradej.broker.upstox.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.upstox.constants.UpstoxEndpoints;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxResponseGuard;

import java.io.IOException;

/**
 * Handles Upstox WebSocket authorization for both market data and portfolio stream feeds.
 * <p>
 * <b>Market Data Feed:</b> {@code GET /v2/feed/market-data-feed/authorize} returns a one-time-use
 * authorized redirect URI for the binary market data WebSocket.
 * <p>
 * <b>Portfolio Stream Feed:</b> {@code GET /v2/feed/portfolio-stream-feed/authorize?update_types=...}
 * returns a one-time-use authorized redirect URI for order/position/holding JSON updates.
 */
public final class UpstoxFeedAuthorizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxHttpClient httpClient;

    public UpstoxFeedAuthorizer(UpstoxHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Authorizes a market data WebSocket connection.
     */
    public AuthorizedFeed authorize() {
        return authorizeFeed(UpstoxEndpoints.FEED_AUTHORIZE_PATH);
    }

    /**
     * Authorizes a portfolio stream WebSocket connection for order/position/holding updates.
     *
     * @param updateTypes comma-separated update types: order, gtt_order, position, holding
     */
    public AuthorizedFeed authorizePortfolioStream(String updateTypes) {
        return authorizeFeed(UpstoxEndpoints.PORTFOLIO_STREAM_AUTHORIZE_PATH
                + "?update_types=" + java.net.URLEncoder.encode(updateTypes, java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Authorizes a portfolio stream WebSocket with default update types (order,position,holding).
     */
    public AuthorizedFeed authorizePortfolioStream() {
        return authorizePortfolioStream("order,position,holding");
    }

    private AuthorizedFeed authorizeFeed(String path) {
        try {
            String body = UpstoxResponseGuard.requireSuccessBody(httpClient.get(path));
            JsonNode root = MAPPER.readTree(body);
            JsonNode data = root.get("data");
            if (data == null || !data.has("authorized_redirect_uri")) {
                throw new UpstoxFeedAuthorizationException(
                        "Missing authorized_redirect_uri in feed authorize response: " + body);
            }
            String wsUri = data.get("authorized_redirect_uri").asText();
            long expiryMs = data.has("expiry") ? data.get("expiry").asLong() * 1000L : -1L;
            return new AuthorizedFeed(wsUri, expiryMs);
        } catch (UpstoxApiException e) {
            throw new UpstoxFeedAuthorizationException(e.getMessage(), e);
        } catch (IOException e) {
            throw new UpstoxFeedAuthorizationException("Feed authorization request failed", e);
        }
    }

    /**
     * Result of a feed authorization request.
     */
    public record AuthorizedFeed(String wsUri, long expiryEpochMs) {
        public boolean isExpired(long nowMs) {
            return expiryEpochMs > 0 && nowMs >= expiryEpochMs;
        }
    }

    public static class UpstoxFeedAuthorizationException extends RuntimeException {
        public UpstoxFeedAuthorizationException(String message) {
            super(message);
        }

        public UpstoxFeedAuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
