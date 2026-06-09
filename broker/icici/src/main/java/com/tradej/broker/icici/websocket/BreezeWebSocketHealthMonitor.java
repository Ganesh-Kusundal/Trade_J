package com.tradej.broker.icici.websocket;

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
 * Health monitor for ICICI Breeze WebSocket feed.
 *
 * <p>Periodically checks feed liveness by tracking the last message timestamp.
 * Emits {@link StreamHealthChanged} events on stale/disconnect/connect transitions.
 */
public final class BreezeWebSocketHealthMonitor implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BreezeWebSocketHealthMonitor.class);

    private static final long STALE_FEED_THRESHOLD_MS = 30_000L;
    private static final long HEALTH_CHECK_INTERVAL_MS = 5_000L;

    @FunctionalInterface
    public interface Listener {
        void onStaleFeedDetected(long idleMs);
    }

    private final Listener listener;
    private final Consumer<DomainEvent> publishEvent;
    private final EventMetadataFactory metadataFactory;
    private final String brokerId;
    private final ScheduledExecutorService executor;

    private volatile long lastMarketEventAtMs;
    private volatile boolean staleFeedEmitted;
    private volatile boolean shutdown;

    public BreezeWebSocketHealthMonitor(
            Listener listener,
            Consumer<DomainEvent> publishEvent,
            EventMetadataFactory metadataFactory,
            String brokerId
    ) {
        this.listener = listener;
        this.publishEvent = publishEvent;
        this.metadataFactory = metadataFactory;
        this.brokerId = brokerId;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, brokerId + "-feed-health");
            thread.setDaemon(true);
            return thread;
        });
        this.lastMarketEventAtMs = System.currentTimeMillis();
    }

    public void start() {
        executor.scheduleAtFixedRate(
                this::checkFeedLiveness,
                HEALTH_CHECK_INTERVAL_MS,
                HEALTH_CHECK_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        shutdown = true;
        executor.shutdown();
    }

    @Override
    public void close() {
        stop();
    }

    public void recordMarketEvent() {
        lastMarketEventAtMs = System.currentTimeMillis();
        staleFeedEmitted = false;
    }

    public void resetTimestamps() {
        lastMarketEventAtMs = System.currentTimeMillis();
        staleFeedEmitted = false;
    }

    public void emitConnected() {
        resetTimestamps();
        publishEvent.accept(new StreamHealthChanged(metadataFactory.root(), brokerId, "CONNECTED", 0));
    }

    public void emitDisconnected() {
        publishEvent.accept(new StreamHealthChanged(metadataFactory.root(), brokerId, "DISCONNECTED", 0));
    }

    private void checkFeedLiveness() {
        if (shutdown) return;
        long now = System.currentTimeMillis();
        long idleMs = now - lastMarketEventAtMs;
        if (idleMs < STALE_FEED_THRESHOLD_MS) return;

        if (!staleFeedEmitted) {
            staleFeedEmitted = true;
            publishEvent.accept(new BrokerAdapterError(metadataFactory.root(), brokerId,
                    "feed-heartbeat", "No market payload received for " + idleMs + "ms"));
            publishEvent.accept(new StreamHealthChanged(metadataFactory.root(), brokerId, "STALE", (int) idleMs));
            log.warn("{} feed stale: no market payload for {}ms", brokerId, idleMs);
        }
        if (listener != null) {
            listener.onStaleFeedDetected(idleMs);
        }
    }
}
