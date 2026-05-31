package com.tradej.pipeline.graph;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.TickReceived;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parsed ingress filter configuration from a pipeline node's {@code config} map.
 */
public record IngressNodeConfig(
        List<String> eventTypes,
        List<String> intervals,
        List<String> symbols
) {
    private static final List<String> DEFAULT_EVENT_TYPES = List.of(CandleClosed.class.getSimpleName());

    public static IngressNodeConfig fromMap(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return new IngressNodeConfig(DEFAULT_EVENT_TYPES, List.of(), List.of());
        }
        return new IngressNodeConfig(
                readStringList(config.get("eventTypes"), DEFAULT_EVENT_TYPES),
                readStringList(config.get("intervals"), List.of()),
                readStringList(config.get("symbols"), List.of())
        );
    }

    public boolean accepts(DomainEvent event) {
        if (event == null || !matchesEventType(event)) {
            return false;
        }
        if (event instanceof CandleClosed closed) {
            if (!intervals.isEmpty() && intervals.stream().noneMatch(i -> i.equalsIgnoreCase(closed.candle().interval()))) {
                return false;
            }
            if (!symbols.isEmpty() && symbols.stream().noneMatch(s -> s.equalsIgnoreCase(closed.candle().symbol()))) {
                return false;
            }
        }
        if (event instanceof TickReceived tick) {
            if (!symbols.isEmpty() && symbols.stream().noneMatch(s -> s.equalsIgnoreCase(tick.symbol()))) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesEventType(DomainEvent event) {
        Set<String> allowed = new HashSet<>();
        for (String type : eventTypes) {
            allowed.add(type);
            allowed.add(simpleName(type));
        }
        String eventName = event.getClass().getSimpleName();
        return allowed.contains(eventName) || allowed.contains(event.getClass().getName());
    }

    private static String simpleName(String type) {
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    private static List<String> readStringList(Object raw, List<String> defaultValue) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return defaultValue;
        }
        return list.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
