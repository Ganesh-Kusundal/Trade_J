package com.tradej.pipeline.reactor;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.pipeline.runtime.BasePipelineNode;
import com.tradej.pipeline.runtime.ReactivePipelineNode;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Project Reactor Offload Bridge.
 * <p>
 * Aggregates output streams from all {@link ReactivePipelineNode} instances in the graph
 * and exposes a unified {@link Flux} for cold-path subscribers (DuckDB, audit, analytics).
 */
public final class ReactorBridge extends BasePipelineNode implements ReactivePipelineNode {

    private final List<ReactivePipelineNode> reactiveSources = new CopyOnWriteArrayList<>();
    private final AtomicReference<Flux<DomainEvent>> mergedFluxRef = new AtomicReference<>();

    @Override
    protected void onInit() {
        // no-op; sources are registered during graph init
    }

    @Override
    protected void processEvent(DomainEvent event) {
        // ReactorBridge itself does not process events in the hot path.
        // Per-node Flux instances carry events out-of-band to cold-path subscribers.
    }

    /**
     * Legacy cold-path candidate check.
     * Returns true for event types that should be offloaded to reactive subscribers.
     */
    public static boolean isColdPathCandidate(DomainEvent event) {
        return event instanceof CandleClosed
                || event instanceof SignalGenerated
                || event instanceof OrderFilled;
    }

    /**
     * Register a reactive node source. Called during graph initialization.
     * Thread-safe; sources can be added dynamically.
     */
    public void registerSource(ReactivePipelineNode source) {
        reactiveSources.add(source);
        mergedFluxRef.set(null); // invalidate cache
    }

    /**
     * Remove all registered sources. Called before re-wiring on graph reload
     * to prevent duplicate registrations.
     */
    public void clearSources() {
        reactiveSources.clear();
        mergedFluxRef.set(null);
    }

    /**
     * Returns the merged reactive stream from all registered sources.
     * Events are processed asynchronously on the boundedElastic scheduler.
     */
    @Override
    public Flux<DomainEvent> outputEvents() {
        Flux<DomainEvent> existing = mergedFluxRef.get();
        if (existing != null) {
            return existing;
        }
        Flux<DomainEvent> fresh = buildMergedFlux();
        return mergedFluxRef.compareAndSet(null, fresh) ? fresh : mergedFluxRef.get();
    }

    private Flux<DomainEvent> buildMergedFlux() {
        if (reactiveSources.isEmpty()) {
            return Flux.empty();
        }
        @SuppressWarnings("unchecked")
        Flux<DomainEvent>[] fluxes = reactiveSources.stream()
                .map(ReactivePipelineNode::outputEvents)
                .toArray(Flux[]::new);
        return Flux.merge(fluxes).publishOn(Schedulers.boundedElastic());
    }

    /**
     * Legacy accessor for backward compatibility.
     * Returns the merged flux from all registered reactive sources.
     */
    public Flux<DomainEvent> events() {
        return outputEvents();
    }

    @Override
    protected void onDestroy() {
        reactiveSources.clear();
        mergedFluxRef.set(null);
    }
}
