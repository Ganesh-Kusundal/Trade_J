package com.tradej.app.health;

/**
 * Pluggable alert channel SPI. Implementations deliver alerts to external
 * systems (webhooks, Slack, PagerDuty, log, etc.).
 */
public interface AlertChannel {

    /**
     * Send an alert.
     *
     * @param severity  CRITICAL, WARNING, or INFO
     * @param component the subsystem that raised the alert (e.g. "broker", "event-bus")
     * @param message   human-readable alert message
     */
    void send(String severity, String component, String message);

    /** No-op implementation for tests and disabled alerting. */
    static AlertChannel noop() {
        return (severity, component, message) -> { };
    }
}
