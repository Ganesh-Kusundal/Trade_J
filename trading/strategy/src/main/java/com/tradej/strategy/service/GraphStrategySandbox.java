package com.tradej.strategy.service;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyError;
import com.tradej.core.domain.port.PositionSizer;
import com.tradej.strategy.api.GraphStrategyPlugin;
import com.tradej.strategy.position.DefaultPositionSizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Isolated execution sandbox for {@link GraphStrategyPlugin} instances.
 * <p>
 * Each plugin runs in its own virtual thread with a bounded timeout, ensuring
 * that a crashing or hanging plugin cannot block the hot path.
 * <p>
 * Unlike {@link StrategySandbox} (candle-only), this sandbox dispatches
 * any event type that the plugin subscribes to via {@link GraphStrategyPlugin#subscribedEventTypes()}.
 */
public final class GraphStrategySandbox {

    private static final Logger log = LoggerFactory.getLogger(GraphStrategySandbox.class);
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;
    private static final String ATTR_QUANTITY = "quantity";

    private final List<GraphStrategyPlugin> plugins;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final PositionSizer positionSizer;
    private final EventMetadataFactory eventMetadataFactory;
    private final com.tradej.strategy.observability.StrategyMetrics metrics =
            new com.tradej.strategy.observability.StrategyMetrics();

    public GraphStrategySandbox(List<GraphStrategyPlugin> plugins, EventMetadataFactory eventMetadataFactory) {
        this(plugins, DEFAULT_TIMEOUT_MS, new DefaultPositionSizer(), eventMetadataFactory);
    }

    public GraphStrategySandbox(List<GraphStrategyPlugin> plugins, long timeoutMs, EventMetadataFactory eventMetadataFactory) {
        this(plugins, timeoutMs, new DefaultPositionSizer(), eventMetadataFactory);
    }

    public GraphStrategySandbox(List<GraphStrategyPlugin> plugins, long timeoutMs, PositionSizer positionSizer, EventMetadataFactory eventMetadataFactory) {
        this.plugins = new ArrayList<>();
        if (plugins != null) {
            this.plugins.addAll(plugins);
        }
        ServiceLoader.load(GraphStrategyPlugin.class).forEach(this.plugins::add);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.timeoutMs = timeoutMs;
        this.positionSizer = positionSizer != null ? positionSizer : new DefaultPositionSizer();
        this.eventMetadataFactory = eventMetadataFactory;
        this.plugins.forEach(GraphStrategyPlugin::onStart);
    }

    /** Per-strategy observability counters. */
    public com.tradej.strategy.observability.StrategyMetrics metrics() {
        return metrics;
    }

    /**
     * Evaluates a domain event against all registered graph strategy plugins.
     * Only plugins that subscribe to the event's type are invoked.
     */
    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (event == null) {
            return;
        }

        for (GraphStrategyPlugin plugin : plugins) {
            if (!isSubscribed(plugin, event)) {
                continue;
            }

            String pluginName = plugin.getClass().getSimpleName();
            long correlationSeq = event.sequenceId();
            String correlationId = event.correlationId();

            CompletableFuture<Optional<SignalGenerated>> future =
                    CompletableFuture.supplyAsync(() -> runPlugin(plugin, event), executor);

            future
                    .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .handle((optSignal, error) -> {
                        if (error == null && optSignal != null) {
                            optSignal.ifPresent(signal -> {
                                var enrichedAttrs = new HashMap<>(signal.attributes());
                                enrichedAttrs.put("strategyName", pluginName);
                                enrichedAttrs.put("triggerEvent", event.getClass().getSimpleName());
                                if (!enrichedAttrs.containsKey(ATTR_QUANTITY)) {
                                    long computedQty = positionSizer.computeQuantity(signal);
                                    if (computedQty > 0) {
                                        enrichedAttrs.put(ATTR_QUANTITY, computedQty);
                                    }
                                }
                                metrics.recordOk(pluginName, event.getClass().getSimpleName());
                                downstream.accept(new SignalGenerated(
                                        signal.metadata(),
                                        signal.signalId(),
                                        signal.symbol(),
                                        signal.interval(),
                                        signal.side(),
                                        signal.entryPricePaisa(),
                                        signal.stopLossPaisa(),
                                        signal.takeProfitPaisa(),
                                        signal.setup(),
                                        Collections.unmodifiableMap(enrichedAttrs)
                                ));
                            });
                        } else if (error != null) {
                            if (error instanceof CancellationException) {
                                return null;
                            }
                            String detail;
                            Throwable cause = error instanceof CompletionException ce
                                    ? ce.getCause() : error;
                            if (cause instanceof TimeoutException) {
                                detail = "Timed out after " + timeoutMs + "ms";
                                log.warn("Graph strategy plugin {} timed out", pluginName);
                                metrics.recordTimeout(pluginName, event.getClass().getSimpleName());
                            } else {
                                detail = cause.getMessage() != null
                                        ? cause.getMessage()
                                        : cause.getClass().getSimpleName();
                                log.error("Graph strategy plugin {} failed: {}", pluginName, detail, cause);
                                metrics.recordError(pluginName, event.getClass().getSimpleName());
                            }
                            downstream.accept(new StrategyError(
                                    eventMetadataFactory.correlated(correlationId, correlationSeq),
                                    pluginName, "", detail));
                        }
                        return null;
                    });
        }
    }

    public void shutdown() {
        plugins.forEach(GraphStrategyPlugin::onStop);
        executor.shutdownNow();
    }

    public int pluginCount() {
        return plugins.size();
    }

    public List<String> pluginNames() {
        return plugins.stream().map(GraphStrategyPlugin::name).toList();
    }

    private static boolean isSubscribed(GraphStrategyPlugin plugin, DomainEvent event) {
        List<Class<? extends DomainEvent>> types = plugin.subscribedEventTypes();
        if (types == null || types.isEmpty()) {
            return true;
        }
        for (Class<? extends DomainEvent> type : types) {
            if (type.isInstance(event)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<SignalGenerated> runPlugin(
            GraphStrategyPlugin plugin,
            DomainEvent event
    ) {
        String pluginName = plugin.getClass().getSimpleName();
        long start = System.nanoTime();
        try {
            Optional<SignalGenerated> result = plugin.onEvent(event);
            long elapsedNs = System.nanoTime() - start;
            if (result.isPresent()) {
                log.info("Graph signal generated plugin={} eventType={} latencyUs={}",
                        pluginName, event.getClass().getSimpleName(), elapsedNs / 1_000);
            }
            return result;
        } catch (Exception e) {
            log.error("Graph strategy plugin {} threw during execution: {}", pluginName, e.getMessage());
            return Optional.empty();
        }
    }
}
