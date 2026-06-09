package com.tradej.app.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs alerts at the appropriate severity level.
 * Useful as a fallback when no external webhook is configured.
 */
public final class LoggingAlertChannel implements AlertChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlertChannel.class);

    @Override
    public void send(String severity, String component, String message) {
        String formatted = "[ALERT] [{}] [{}] {}";
        switch (severity.toUpperCase()) {
            case "CRITICAL" -> log.error(formatted, severity, component, message);
            case "WARNING" -> log.warn(formatted, severity, component, message);
            default -> log.info(formatted, severity, component, message);
        }
    }
}
