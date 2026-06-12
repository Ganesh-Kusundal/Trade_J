package com.tradej.app.api;

import com.tradej.core.domain.event.EventCatalogEntry;
import com.tradej.core.domain.event.EventRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/events")
public class EventCatalogController {

    private final EventRegistry eventRegistry;

    public EventCatalogController(EventRegistry eventRegistry) {
        this.eventRegistry = eventRegistry;
    }

    @GetMapping
    public List<Map<String, Object>> listEvents() {
        return eventRegistry.all().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/categories")
    public List<String> listCategories() {
        return eventRegistry.categories();
    }

    @GetMapping("/category/{category}")
    public List<Map<String, Object>> byCategory(@PathVariable String category) {
        return eventRegistry.byCategory(category).stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<String, Object> toResponse(EventCatalogEntry entry) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", entry.name());
        result.put("className", entry.simpleClassName());
        result.put("category", entry.category());
        result.put("schemaVersion", entry.schemaVersion());
        result.put("fields", eventRegistry.fields(entry.name()));
        return result;
    }
}
