package com.tradej.broker.dhan.websocket;

import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.core.domain.event.BrokerAdapterError;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.StreamHealthChanged;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Periodic monitor for Dhan WebSocket feed health.
 *
 * <p>Runs two checks on a fixed schedule:
 * <ol>
 *   <li><b>Token validity</b> — ensures the access token hasn't expired, triggering a refresh
 *       if needed via {@link DhanClientHolder#ensureValidToken()}.</li>
 *   <li><b>Feed liveness</b> — detects stale market data by comparing the time since the last
 *       recorded market event against a configurable threshold. When a stale feed is detected,
 *       the registered {@link Listener} is notified so the caller can trigger reconnection.</li>
 * </ol>
 *
 * <p>Health and error events are published to the supplied {@code publishMarket} consumer
 * so they reach the multiplexer's market data listeners.
 */
public final class DhanWebSocketHealthMonitor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanWebSocketHealthMonitor.class);

    private static final long STALE_FEED_THRESHOLD_MS = DhanProtocolConstants.STALE_FEED_THRESHOLD_MS;
    private static final long TOKEN_CHECK_INTERVAL_MS = DhanProtocolConstants.TOKEN_CHECK_INTERVAL_MS;
    private static final long HEALTH_CHECK_INTERVAL_MS = DhanProtocolConstants.FEED_HEALTH_CHECK_INTERVAL_MS;

    @FunctionalInterface
    public interface Listener {
        /** Called when the feed has been idle longer than the stale threshold. */
        void onStaleFeedDetected(long idleMs);
    }

    private final DhanClientHolder clientHolder;
    private final Listener listener;
    private final Consumer<DomainEvent> publishMarket;
    private final EventMetadataFactory metadataFactory;
    private final String brokerId;
    private final ScheduledExecutorService executor;

    private volatile long lastMarketEventAtMs;
    private volatile long lastTokenCheckAtMs;
    private volatile boolean staleFeedEmitted;
    private volatile boolean shutdown;

    /**
     * @param clientHolder   provides access tokens and {@code ensureValidToken()}
     * @param listener       notified when the feed is stale
     * @param publishMarket  consumer for health/error domain events
     * @param metadataFactory  event metadata factory
     * @param brokerId       broker identifier for event source (e.g. {@code "dhan"})
     */
    public DhanWebSocketHealthMonitor(
            DhanClientHolder clientHolder,
            Listener listener,
            Consumer<DomainEvent> publishMarket,
            EventMetadataFactory metadataFactory,
            String brokerId
    ) {
        this.clientHolder = clientHolder;
        this.listener = listener;
        this.publishMarket = publishMarket;
        this.metadataFactory = metadataFactory;
        this.brokerId = brokerId;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, brokerId + "-feed-health");
            thread.setDaemon(true);
            return thread;
        });
        long now = System.currentTimeMillis();
        this.lastMarketEventAtMs = now;
        this.lastTokenCheckAtMs = now;
    }

    // ---- Lifecycle ----

    /** Start the periodic health checks. */
    public void start() {
        executor.scheduleAtFixedRate(
                this::run,
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    /** Stop all health checks. */
    public void stop() {
        shutdown = true;
        executor.shutdown();
    }

    @Override
    public void close() {
        stop();
    }

    // ---- Event recording ----

    /** Record that a market event was just received (resets stale detection). */
    public void recordMarketEvent() {
        lastMarketEventAtMs = System.currentTimeMillis();
        staleFeedEmitted = false;
    }

    /** Reset all timestamps to now (used on connect/reconnect). */
    public void resetTimestamps() {
        long now = System.currentTimeMillis();
        lastMarketEventAtMs = now;
        lastTokenCheckAtMs = now;
        staleFeedEmitted = false;
    }

    // ---- Health check logic ----

    private void run() {
        if (shutdown) {
            return;
        }
        long now = System.currentTimeMillis();
        checkToken(now);
        checkFeedLiveness(now);
    }

    private void checkToken(long now) {
        if (now - lastTokenCheckAtMs < TOKEN_CHECK_INTERVAL_MS) {
            return;
        }
        try {
            clientHolder.ensureValidToken();
        } catch (RuntimeException ex) {
            publishMarket.accept(brokerError("token-refresh", ex.getMessage()));
        } finally {
            lastTokenCheckAtMs = now;
        }
    }

    private void checkFeedLiveness(long now) {
        long idleMs = now - lastMarketEventAtMs;
        if (idleMs < STALE_FEED_THRESHOLD_MS) {
            return;
        }
        if (!staleFeedEmitted) {
            staleFeedEmitted = true;
            publishMarket.accept(brokerError("feed-heartbeat",
                    "No market payload received for " + idleMs + "ms"));
            publishMarket.accept(healthEvent("STALE", (int) idleMs));
        }
        listener.onStaleFeedDetected(idleMs);
    }

    private StreamHealthChanged healthEvent(String status, int detail) {
        return new StreamHealthChanged(metadataFactory.root(), brokerId, status, detail);
    }

    private BrokerAdapterError brokerError(String source, String message) {
        return new BrokerAdapterError(metadataFactory.root(), brokerId, source, message);
    }
}
