package com.tradej.broker.dhan.websocket;

import com.tradej.broker.core.resilience.BackoffStrategy;
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
 * Reconnection state machine with exponential backoff, circuit breaker,
 * and periodic subscription reconciliation.
 *
 * <p>Thread-safe: all mutable state is synchronized on {@code lock}.
 */
public final class DhanReconnectController implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DhanReconnectController.class);

    private final int failureThreshold;
    private final long circuitOpenMs;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final Consumer<DomainEvent> publishMarket;
    private final EventMetadataFactory metadataFactory;
    private final String brokerId;
    private final ScheduledExecutorService reconnectScheduler;
    private final ScheduledExecutorService reconciliationScheduler;
    private final Object lock = new Object();

    private int reconnectAttempts;
    private long circuitOpenUntilMs;
    private volatile boolean shutdown;

    /**
     * @param publishMarket    consumer for health/error domain events
     * @param metadataFactory  event metadata factory
     * @param brokerId         broker identifier for event source
     */
    public DhanReconnectController(
            Consumer<DomainEvent> publishMarket,
            EventMetadataFactory metadataFactory,
            String brokerId
    ) {
        this.publishMarket = publishMarket;
        this.metadataFactory = metadataFactory;
        this.brokerId = brokerId;
        this.failureThreshold = DhanProtocolConstants.WS_RECONNECT_FAILURE_THRESHOLD;
        this.circuitOpenMs = DhanProtocolConstants.WS_RECONNECT_CIRCUIT_OPEN_MS;
        this.baseDelayMs = DhanProtocolConstants.WS_RECONNECT_BASE_DELAY_MS;
        this.maxDelayMs = DhanProtocolConstants.WS_RECONNECT_MAX_DELAY_MS;
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, brokerId + "-reconnect");
            t.setDaemon(true);
            return t;
        });
        this.reconciliationScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, brokerId + "-sub-reconcile");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────

    /**
     * Attempts to start the reconnection sequence with exponential backoff.
     * Must be called under the caller's lock so that client close+rebind is atomic.
     *
     * @param clientRebinder callback that closes old clients and creates new ones
     * @param reconnector    callback that attempts the actual reconnect
     * @return the computed delay before {@code reconnector} will be invoked (for logging),
     *         or -1 if the circuit is open or shutdown
     */
    public long startBackoff(Runnable clientRebinder, Runnable reconnector) {
        synchronized (lock) {
            if (shutdown) {
                return -1;
            }
            long now = System.currentTimeMillis();
            if (circuitOpenUntilMs > now) {
                return -1;
            }
            reconnectAttempts++;
            if (reconnectAttempts >= failureThreshold) {
                circuitOpenUntilMs = now + circuitOpenMs;
                publishMarket.accept(brokerError("reconnect-circuit",
                        "Reconnect circuit opened after " + reconnectAttempts + " consecutive failures"));
                publishMarket.accept(healthEvent("CIRCUIT_OPEN", reconnectAttempts));
                return -1;
            }
            long delayMs = BackoffStrategy.computeDelayMs(reconnectAttempts, baseDelayMs, maxDelayMs);
            clientRebinder.run();
            reconnectScheduler.schedule(() -> {
                synchronized (lock) {
                    if (!shutdown) {
                        reconnector.run();
                    }
                }
            }, delayMs, TimeUnit.MILLISECONDS);
            return delayMs;
        }
    }

    /** Resets the reconnect circuit (call on successful connect/reconnect). */
    public void resetCircuit() {
        synchronized (lock) {
            reconnectAttempts = 0;
            circuitOpenUntilMs = 0L;
        }
    }

    /** Marks the controller as shut down — prevents future reconnection attempts. */
    public void shutdown() {
        shutdown = true;
        shutdownScheduler(reconnectScheduler);
        shutdownScheduler(reconciliationScheduler);
    }

    @Override
    public void close() {
        shutdown();
    }

    // ── Reconciliation scheduler ──────────────────────────────────────

    /**
     * Schedules a periodic subscription reconciliation task.
     *
     * @param task the reconciliation task (resubscribes all active subscriptions)
     */
    public void scheduleReconciliation(Runnable task) {
        reconciliationScheduler.scheduleAtFixedRate(task, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Schedules a one-shot resubscribe (called immediately after reconnect).
     *
     * @param task the resubscribe task
     */
    public void scheduleResubscribe(Runnable task) {
        reconnectScheduler.schedule(task, 0, TimeUnit.MILLISECONDS);
    }

    // ── Accessors ─────────────────────────────────────────────────────

    /** Whether this controller has been shut down. */
    public boolean isShutdown() {
        return shutdown;
    }

    // ── Internal ──────────────────────────────────────────────────────

    private void shutdownScheduler(ScheduledExecutorService scheduler) {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private StreamHealthChanged healthEvent(String status, int detail) {
        return new StreamHealthChanged(metadataFactory.root(), brokerId, status, detail);
    }

    private BrokerAdapterError brokerError(String source, String message) {
        return new BrokerAdapterError(metadataFactory.root(), brokerId, source, message);
    }
}
