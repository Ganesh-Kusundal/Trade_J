package com.tradej.app.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central alert manager with per-component cooldown to prevent alert spam.
 *
 * <p>When a component fires an alert, subsequent alerts from the same component
 * within the cooldown window are suppressed. This prevents health-check loops
 * from flooding external systems during sustained outages.
 *
 * <p>Thread-safe. Designed to be called from Spring Boot health indicator callbacks.
 */
public final class AlertManager {

    private static final Logger log = LoggerFactory.getLogger(AlertManager.class);

    private final List<AlertChannel> channels;
    private final Duration cooldown;
    private final ConcurrentHashMap<String, Instant> lastAlertTime = new ConcurrentHashMap<>();

    public AlertManager(List<AlertChannel> channels, Duration cooldown) {
        this.channels = channels == null || channels.isEmpty()
                ? List.of(new LoggingAlertChannel()) : List.copyOf(channels);
        this.cooldown = cooldown;
    }

    /**
     * Fire an alert if the cooldown for this component has elapsed.
     *
     * @param severity  CRITICAL, WARNING, or INFO
     * @param component the subsystem (e.g. "broker-dhan", "event-bus")
     * @param message   human-readable message
     * @return true if the alert was sent, false if suppressed by cooldown
     */
    public boolean alert(String severity, String component, String message) {
        Instant now = Instant.now();
        Instant lastTime = lastAlertTime.get(component);

        if (lastTime != null && Duration.between(lastTime, now).compareTo(cooldown) < 0) {
            log.debug("Alert suppressed for {} (cooldown {}s remaining)",
                    component, cooldown.minus(Duration.between(lastTime, now)).toSeconds());
            return false;
        }

        lastAlertTime.put(component, now);
        for (AlertChannel channel : channels) {
            try {
                channel.send(severity, component, message);
            } catch (Exception e) {
                log.warn("Alert channel {} failed: {}", channel.getClass().getSimpleName(), e.getMessage());
            }
        }
        return true;
    }

    /**
     * Fire a CRITICAL alert.
     */
    public boolean critical(String component, String message) {
        return alert("CRITICAL", component, message);
    }

    /**
     * Fire a WARNING alert.
     */
    public boolean warning(String component, String message) {
        return alert("WARNING", component, message);
    }

    public Duration cooldown() {
        return cooldown;
    }

    public int channelCount() {
        return channels.size();
    }
}
