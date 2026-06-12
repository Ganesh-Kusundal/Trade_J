package com.tradej.core.domain.event;

import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Runtime catalog of all domain event types in the platform.
 * Enables dynamic discovery and introspection of the event schema.
 */
public final class EventRegistry {

    private final Map<String, EventCatalogEntry> catalog;
    private final Map<String, Class<? extends DomainEvent>> classMap;

    private EventRegistry(Map<String, EventCatalogEntry> catalog, Map<String, Class<? extends DomainEvent>> classMap) {
        this.catalog = Collections.unmodifiableMap(catalog);
        this.classMap = Collections.unmodifiableMap(classMap);
    }

    /**
     * Build an EventRegistry from an explicit list of event classes.
     */
    public static EventRegistry of(List<Class<? extends DomainEvent>> eventClasses) {
        Map<String, EventCatalogEntry> catalogMap = new LinkedHashMap<>();
        Map<String, Class<? extends DomainEvent>> classMap = new LinkedHashMap<>();
        for (Class<? extends DomainEvent> cls : eventClasses) {
            EventCatalogEntry entry = EventCatalogEntry.fromClass(cls);
            catalogMap.put(entry.name(), entry);
            classMap.put(entry.name(), cls);
        }
        return new EventRegistry(catalogMap, classMap);
    }

    public List<EventCatalogEntry> all() {
        return List.copyOf(catalog.values());
    }

    public List<EventCatalogEntry> byCategory(String category) {
        return catalog.values().stream()
                .filter(e -> e.category().equals(category))
                .toList();
    }

    public Optional<EventCatalogEntry> get(String name) {
        return Optional.ofNullable(catalog.get(name));
    }

    public Optional<Class<? extends DomainEvent>> eventClass(String name) {
        return Optional.ofNullable(classMap.get(name));
    }

    public List<String> categories() {
        return catalog.values().stream()
                .map(EventCatalogEntry::category)
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> names() {
        return List.copyOf(catalog.keySet());
    }

    public int size() {
        return catalog.size();
    }

    /**
     * Returns field names for a given event class using record component introspection.
     */
    public List<String> fields(String eventName) {
        Class<? extends DomainEvent> cls = classMap.get(eventName);
        if (cls == null || !cls.isRecord()) {
            return List.of();
        }
        List<String> fields = new java.util.ArrayList<>();
        for (RecordComponent rc : cls.getRecordComponents()) {
            fields.add(rc.getName() + ":" + rc.getType().getSimpleName());
        }
        return fields;
    }
}
