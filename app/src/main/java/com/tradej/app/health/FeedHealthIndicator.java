package com.tradej.app.health;

import com.tradej.core.domain.event.StreamHealthChanged;
import com.tradej.core.domain.port.EventBus;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeedHealthIndicator implements HealthIndicator {

    private final EventBus eventBus;
    private final AlertManager alertManager;
    private final ConcurrentHashMap<String, String> brokerStatus = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastEventMs = new ConcurrentHashMap<>();

    public FeedHealthIndicator(EventBus eventBus, AlertManager alertManager) {
        this.eventBus = eventBus;
        this.alertManager = alertManager;
    }

    @PostConstruct
    void subscribe() {
        eventBus.subscribe(StreamHealthChanged.class, this::onStreamHealth);
    }

    private void onStreamHealth(StreamHealthChanged event) {
        brokerStatus.put(event.broker(), event.status());
        lastEventMs.put(event.broker(), event.metadata().timestampMs());
    }

    @Override
    public Health health() {
        boolean anyError = brokerStatus.values().stream()
                .anyMatch(s -> "ERROR".equals(s) || "CIRCUIT_OPEN".equals(s) || "STALE".equals(s));
        boolean allConnected = !brokerStatus.isEmpty() && brokerStatus.values().stream()
                .allMatch(s -> "CONNECTED".equals(s) || "RECONNECTED".equals(s));
        Health.Builder builder = anyError ? Health.down() : (allConnected || brokerStatus.isEmpty() ? Health.up() : Health.down());

        if (anyError) {
            alertManager.warning("feed", "Feed error detected: " + brokerStatus);
        }

        return builder
                .withDetail("brokers", Map.copyOf(brokerStatus))
                .withDetail("lastEventMs", Map.copyOf(lastEventMs))
                .build();
    }
}
