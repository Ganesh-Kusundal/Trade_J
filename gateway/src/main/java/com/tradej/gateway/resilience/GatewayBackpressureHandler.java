package com.tradej.gateway.resilience;

import com.tradej.core.domain.event.EventBusBackpressure;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Monitors the {@link GatewayTopicRouter} send-queue depth and publishes
 * {@link EventBusBackpressure} domain events when the backpressure level
 * transitions between {@link Level NORMAL}, {@link Level WARN} and
 * {@link Level CRITICAL}.
 *
 * <p>Callers can also query {@link #shouldThrottle(GatewayTopic)} to decide
 * whether non-priority topics should be dropped early (before reaching the
 * router) when the system is under critical load.
 *
 * <p>This class is fully thread-safe. The current status is stored in an
 * {@link AtomicReference} so concurrent callers always see a consistent
 * snapshot.
 */
public final class GatewayBackpressureHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayBackpressureHandler.class);

    /** Priority topics that are never throttled regardless of queue pressure. */
    private static final Set<GatewayTopic> PRIORITY_TOPICS = Set.of(
            GatewayTopic.ORDER_UPDATE,
            GatewayTopic.POSITION_UPDATE,
            GatewayTopic.PNL_UPDATE
    );

    // ── Thresholds (configurable) ──────────────────────────────────────
    private final int warnThreshold;
    private final int criticalThreshold;
    private final int maxQueueCapacity;

    // ── Dependencies ───────────────────────────────────────────────────
    private final GatewayTopicRouter router;
    private final EventBus eventBus;

    // ── State ──────────────────────────────────────────────────────────
    private final AtomicReference<BackpressureStatus> currentStatus;

    // ── BackpressureStatus record ──────────────────────────────────────

    /** Backpressure severity level. */
    public enum Level {
        NORMAL,
        WARN,
        CRITICAL
    }

    /**
     * Immutable snapshot of the current backpressure state.
     *
     * @param level           current severity level
     * @param queueDepth      number of entries currently in the router send queue
     * @param droppedEvents   cumulative count of events dropped by the router
     * @param maxCapacity     maximum queue capacity
     * @param utilizationPct  queue utilization as a percentage (0–100)
     */
    public record BackpressureStatus(
            Level level,
            int queueDepth,
            long droppedEvents,
            int maxCapacity,
            double utilizationPct
    ) {}

    // ── Constructors ───────────────────────────────────────────────────

    /**
     * Creates a handler with default thresholds (warn=512, critical=900, max=1024).
     */
    public GatewayBackpressureHandler(GatewayTopicRouter router, EventBus eventBus) {
        this(router, eventBus, 512, 900, 1024);
    }

    /**
     * Creates a handler with custom thresholds.
     *
     * @param router             the gateway topic router to monitor
     * @param eventBus           the event bus on which to publish backpressure events
     * @param warnThreshold      queue depth at which WARN level is triggered
     * @param criticalThreshold  queue depth at which CRITICAL level is triggered
     * @param maxQueueCapacity   the absolute maximum queue capacity (used for utilization %)
     */
    public GatewayBackpressureHandler(GatewayTopicRouter router,
                                      EventBus eventBus,
                                      int warnThreshold,
                                      int criticalThreshold,
                                      int maxQueueCapacity) {
        this.router = router;
        this.eventBus = eventBus;
        this.warnThreshold = warnThreshold;
        this.criticalThreshold = criticalThreshold;
        this.maxQueueCapacity = maxQueueCapacity;
        this.currentStatus = new AtomicReference<>(initialStatus());
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Evaluates the current router queue metrics, determines the backpressure
     * level, and publishes an {@link EventBusBackpressure} event if the level
     * has changed since the last check.
     *
     * @return the current {@link BackpressureStatus} snapshot
     */
    public BackpressureStatus checkStatus() {
        int depth = router.queueDepth();
        long dropped = router.droppedEventCount();
        double utilization = (double) depth / maxQueueCapacity * 100.0;

        Level newLevel = evaluateLevel(depth);

        BackpressureStatus newStatus = new BackpressureStatus(
                newLevel, depth, dropped, maxQueueCapacity, utilization);

        BackpressureStatus previous = currentStatus.getAndSet(newStatus);

        if (newLevel != previous.level()) {
            logTransition(previous.level(), newLevel, depth, dropped, utilization);
            publishBackpressureEvent(newStatus);
        }

        return newStatus;
    }

    /**
     * Returns {@code true} if the router queue is above the critical threshold
     * <em>and</em> the given topic is <strong>not</strong> a priority topic.
     *
     * <p>Priority topics ({@code ORDER_UPDATE}, {@code POSITION_UPDATE},
     * {@code PNL_UPDATE}) are never throttled because they carry
     * time-sensitive trading state that must reach the client.
     *
     * @param topic the gateway topic to check
     * @return whether the caller should throttle (drop) events for this topic
     */
    public boolean shouldThrottle(GatewayTopic topic) {
        if (PRIORITY_TOPICS.contains(topic)) {
            return false;
        }
        return router.queueDepth() >= criticalThreshold;
    }

    /**
     * Returns the most recently computed status without re-evaluating the
     * router metrics.
     */
    public BackpressureStatus lastStatus() {
        return currentStatus.get();
    }

    // ── Internal ───────────────────────────────────────────────────────

    private Level evaluateLevel(int depth) {
        if (depth >= criticalThreshold) {
            return Level.CRITICAL;
        } else if (depth >= warnThreshold) {
            return Level.WARN;
        }
        return Level.NORMAL;
    }

    private void logTransition(Level from, Level to, int depth, long dropped, double utilization) {
        if (to == Level.CRITICAL) {
            log.error("Backpressure CRITICAL: queueDepth={} dropped={} utilization={}%",
                    depth, dropped, String.format("%.1f", utilization));
        } else if (to == Level.WARN) {
            log.warn("Backpressure WARN: queueDepth={} dropped={} utilization={}%",
                    depth, dropped, String.format("%.1f", utilization));
        } else {
            log.info("Backpressure NORMAL: queueDepth={} dropped={} utilization={}%",
                    depth, dropped, String.format("%.1f", utilization));
        }
    }

    private void publishBackpressureEvent(BackpressureStatus status) {
        try {
            EventBusBackpressure event = new EventBusBackpressure(
                    EventMetadata.root(),
                    status.queueDepth(),
                    status.maxCapacity(),
                    status.utilizationPct()
            );
            eventBus.publish(event);
        } catch (Exception e) {
            log.warn("Failed to publish EventBusBackpressure event: {}", e.getMessage());
        }
    }

    private BackpressureStatus initialStatus() {
        return new BackpressureStatus(Level.NORMAL, 0, 0, maxQueueCapacity, 0.0);
    }
}
