package com.tradej.broker.upstox.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.upstox.constants.UpstoxEndpoints;
import com.tradej.broker.upstox.http.UpstoxApiException;
import com.tradej.broker.upstox.http.UpstoxHttpClient;
import com.tradej.broker.upstox.http.UpstoxResponseGuard;

import java.io.IOException;

/**
 * Handles the Upstox WebSocket feed authorization step.
 * <p>
 * Before connecting to the market data WebSocket, Upstox requires a call to
 * {@code GET /v2/feed/market-data-feed/authorize} which returns a one-time-use
 * authorized redirect URI for the WebSocket connection.
 */
public final class UpstoxFeedAuthorizer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxHttpClient httpClient;

    public UpstoxFeedAuthorizer(UpstoxHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Authorizes a WebSocket connection and returns the connection URI.
     *
     * @return the authorized WebSocket URI
     * @throws UpstoxFeedAuthorizationException if authorization fails
     */
    public AuthorizedFeed authorize() {
        try {
            String body = UpstoxResponseGuard.requireSuccessBody(httpClient.get(UpstoxEndpoints.FEED_AUTHORIZE_PATH));
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
        /**
         * Returns {@code true} if the feed authorization has expired
         * relative to the given timestamp.
         *
         * @param nowMs current time in epoch milliseconds
         * @return true if expired, false if still valid or no expiry was set
         */
        public boolean isExpired(long nowMs) {
            return expiryEpochMs > 0 && nowMs >= expiryEpochMs;
        }
    }

    /**
     * Exception thrown when feed authorization fails.
     */
    public static class UpstoxFeedAuthorizationException extends RuntimeException {
        public UpstoxFeedAuthorizationException(String message) {
            super(message);
        }

        public UpstoxFeedAuthorizationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
