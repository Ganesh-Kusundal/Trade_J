package com.tradej.broker.dhan.websocket;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.config.DhanConnectionSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of the two Dhan WebSocket clients:
 * market feed ({@link DhanMarketFeedWebSocketClient}) and
 * order stream ({@link DhanOrderStreamWebSocketClient}).
 *
 * <p>Handles client creation, binding, connection, and disconnection.
 * All state transitions are guarded by a lock provided by the caller.
 *
 * <p>Thread-safe when all calls are made under the same external lock.
 */
public final class DhanWebSocketConnectionManager implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanWebSocketConnectionManager.class);

    private final DhanConnectionSettings settings;
    private final DhanTokenProvider tokenProvider;

    private DhanMarketFeedWebSocketClient marketFeedClient;
    private DhanOrderStreamWebSocketClient orderStreamClient;
    private volatile boolean connected;

    /**
     * @param settings      Dhan connection settings
     * @param tokenProvider token provider for authentication
     */
    public DhanWebSocketConnectionManager(
            DhanConnectionSettings settings,
            DhanTokenProvider tokenProvider
    ) {
        this.settings = settings;
        this.tokenProvider = tokenProvider;
    }

    // ── Client lifecycle ──────────────────────────────────────────────

    /** Ensures both WebSocket clients exist (creates them if null). */
    public void ensureClients() {
        if (marketFeedClient != null && orderStreamClient != null) {
            return;
        }
        bindClients();
    }

    /** Creates fresh market feed and order stream clients and wires them. */
    public void bindClients() {
        DhanMarketFeedWebSocketClient newMarketFeedClient =
                new DhanMarketFeedWebSocketClient(settings, tokenProvider);
        DhanOrderStreamWebSocketClient newOrderStreamClient =
                new DhanOrderStreamWebSocketClient(settings, tokenProvider);
        closeCurrentClients();
        this.marketFeedClient = newMarketFeedClient;
        this.orderStreamClient = newOrderStreamClient;
    }

    /** Closes and nulls out both clients. */
    public void closeCurrentClients() {
        if (marketFeedClient != null) {
            marketFeedClient.close();
            marketFeedClient = null;
        }
        if (orderStreamClient != null) {
            orderStreamClient.close();
            orderStreamClient = null;
        }
    }

    // ── Connection ────────────────────────────────────────────────────

    /** Connects the market feed WebSocket. May throw on failure. */
    public void connectMarketFeed() {
        if (marketFeedClient != null) {
            marketFeedClient.connect();
        }
    }

    /** Connects the order stream WebSocket. May throw on failure. */
    public void connectOrderStream() {
        if (orderStreamClient != null) {
            orderStreamClient.connect();
        }
    }

    /** Disconnects both WebSocket clients. */
    public void disconnectAll() {
        connected = false;
        if (marketFeedClient != null) {
            marketFeedClient.disconnect();
        }
        if (orderStreamClient != null) {
            orderStreamClient.disconnect();
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Returns the current market feed client, or {@code null}. */
    public DhanMarketFeedWebSocketClient marketFeedClient() {
        return marketFeedClient;
    }

    /** Returns the current order stream client, or {@code null}. */
    public DhanOrderStreamWebSocketClient orderStreamClient() {
        return orderStreamClient;
    }

    /** Sets the connected state. */
    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    /** Whether the connection manager believes it is connected. */
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        closeCurrentClients();
    }
}
