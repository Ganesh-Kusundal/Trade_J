package com.tradej.pipeline.platform;

import com.tradej.pipeline.graph.PipelineGraph;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class PipelineTemplateService {

    private final Map<UUID, PipelineTemplate> templates = new ConcurrentHashMap<>();
    private final List<Consumer<PipelineTemplate>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public PipelineTemplate createTemplate(String name, String description, PipelineType type,
                                            PipelineGraph graph, PipelineVersion version,
                                            Map<String, String> defaults) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(graph, "graph must not be null");
        Objects.requireNonNull(version, "version must not be null");

        PipelineTemplate template = new PipelineTemplate(
                UUID.randomUUID(),
                name,
                description,
                type,
                graph,
                version,
                defaults == null ? Map.of() : Map.copyOf(defaults)
        );
        templates.put(template.templateId(), template);
        notifyListeners(template);
        return template;
    }

    public Optional<PipelineTemplate> getTemplate(UUID templateId) {
        return Optional.ofNullable(templates.get(templateId));
    }

    public List<PipelineTemplate> getAllTemplates() {
        return List.copyOf(templates.values());
    }

    public List<PipelineTemplate> getTemplatesByType(PipelineType type) {
        return templates.values().stream()
                .filter(t -> t.type() == type)
                .toList();
    }

    public PipelineTemplate updateTemplate(UUID templateId, String name, String description,
                                            PipelineGraph graph, Map<String, String> defaults) {
        PipelineTemplate existing = templates.get(templateId);
        if (existing == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }

        PipelineTemplate updated = new PipelineTemplate(
                existing.templateId(),
                name != null ? name : existing.name(),
                description != null ? description : existing.description(),
                existing.type(),
                graph != null ? graph : existing.graph(),
                existing.version(),
                defaults != null ? Map.copyOf(defaults) : existing.defaults()
        );
        templates.put(templateId, updated);
        notifyListeners(updated);
        return updated;
    }

    public void deleteTemplate(UUID templateId) {
        templates.remove(templateId);
    }

    public PipelineDefinition instantiateTemplate(UUID templateId) {
        PipelineTemplate template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("Template not found: " + templateId);
        }
        return template.instantiate();
    }

    public void subscribe(Consumer<PipelineTemplate> listener) {
        listeners.add(listener);
    }

    private void notifyListeners(PipelineTemplate template) {
        for (Consumer<PipelineTemplate> listener : listeners) {
            listener.accept(template);
        }
    }
}