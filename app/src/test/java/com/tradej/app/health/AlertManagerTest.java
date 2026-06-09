package com.tradej.app.health;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class AlertManagerTest {

    private static class RecordingChannel implements AlertChannel {
        final List<String> alerts = new ArrayList<>();
        @Override public void send(String severity, String component, String message) {
            alerts.add(severity + ":" + component + ":" + message);
        }
    }

    @Test
    void alert_firesToAllChannels() {
        RecordingChannel ch1 = new RecordingChannel();
        RecordingChannel ch2 = new RecordingChannel();
        AlertManager manager = new AlertManager(List.of(ch1, ch2), Duration.ofMinutes(5));

        assertTrue(manager.critical("broker-dhan", "Connection lost"));
        assertEquals(1, ch1.alerts.size());
        assertEquals(1, ch2.alerts.size());
        assertTrue(ch1.alerts.get(0).contains("CRITICAL"));
    }

    @Test
    void cooldown_suppressesDuplicateAlerts() {
        RecordingChannel channel = new RecordingChannel();
        AlertManager manager = new AlertManager(List.of(channel), Duration.ofMinutes(5));

        assertTrue(manager.warning("event-bus", "Queue overflow"));
        assertFalse(manager.warning("event-bus", "Queue overflow again"),
                "Second alert within cooldown should be suppressed");
        assertEquals(1, channel.alerts.size());
    }

    @Test
    void cooldown_differentComponents_independent() {
        RecordingChannel channel = new RecordingChannel();
        AlertManager manager = new AlertManager(List.of(channel), Duration.ofMinutes(5));

        assertTrue(manager.critical("broker-dhan", "Timeout"));
        assertTrue(manager.critical("broker-upstox", "Timeout"),
                "Different component should not be suppressed");
        assertEquals(2, channel.alerts.size());
    }

    @Test
    void cooldown_expired_allowsNewAlert() {
        RecordingChannel channel = new RecordingChannel();
        AlertManager manager = new AlertManager(List.of(channel), Duration.ofMillis(1));

        assertTrue(manager.warning("test", "first"));
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertTrue(manager.warning("test", "second"),
                "Alert after cooldown expiry should be sent");
        assertEquals(2, channel.alerts.size());
    }

    @Test
    void warning_usesCorrectSeverity() {
        RecordingChannel channel = new RecordingChannel();
        AlertManager manager = new AlertManager(List.of(channel), Duration.ofMinutes(5));

        manager.warning("scanner", "Slow scan");
        assertTrue(channel.alerts.get(0).startsWith("WARNING:"));
    }

    @Test
    void channelCount_returnsCorrectCount() {
        AlertManager manager = new AlertManager(
                List.of(new RecordingChannel(), new RecordingChannel()),
                Duration.ofMinutes(5));
        assertEquals(2, manager.channelCount());
    }

    @Test
    void nullChannels_defaultsToLogging() {
        AlertManager manager = new AlertManager(null, Duration.ofMinutes(5));
        assertEquals(1, manager.channelCount());
        assertDoesNotThrow(() -> manager.critical("test", "message"));
    }
}
