package com.tradej.strategy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyError;
import com.tradej.core.domain.port.PositionSizer;
import com.tradej.core.support.MdcHelper;
import com.tradej.strategy.api.StrategyPlugin;
import com.tradej.strategy.position.DefaultPositionSizer;

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
 * Isolated strategy execution sandbox. Each strategy plugin runs in its own
 * virtual thread without blocking the Disruptor consumer thread.
 * <p>
 * Uses {@link CompletableFuture#handle} for atomic result coordination,
 * ensuring that a plugin produces either a signal OR an error, never both
 * (fixes the signal+error race where timeout and completion both fire).
 * <p>
 *
 * @deprecated Use {@link GraphStrategySandbox} which supports all event types
 * (tick, depth, candle, ML) through the {@link com.tradej.strategy.api.GraphStrategyPlugin} interface.
 * Scheduled for removal after all plugins are migrated to GraphStrategyPlugin.
 */
@Deprecated(since = "2.0", forRemoval = true)
public final class StrategySandbox {
    private static final Logger log = LoggerFactory.getLogger(StrategySandbox.class);
    private static final long DEFAULT_TIMEOUT_MS = 5_000L;
    private static final String ATTR_QUANTITY = "quantity";

    private final List<StrategyPlugin> plugins;
    private final ExecutorService executor;
    private final long timeoutMs;
    private final PositionSizer positionSizer;
    private final EventMetadataFactory eventMetadataFactory;

    public StrategySandbox(List<StrategyPlugin> plugins, EventMetadataFactory eventMetadataFactory) {
        this(plugins, DEFAULT_TIMEOUT_MS, new DefaultPositionSizer(), eventMetadataFactory);
    }

    public StrategySandbox(List<StrategyPlugin> plugins, long timeoutMs, EventMetadataFactory eventMetadataFactory) {
        this(plugins, timeoutMs, new DefaultPositionSizer(), eventMetadataFactory);
    }

    public StrategySandbox(List<StrategyPlugin> plugins, long timeoutMs, PositionSizer positionSizer, EventMetadataFactory eventMetadataFactory) {
        this.plugins = new ArrayList<>();
        if (plugins != null) {
            this.plugins.addAll(plugins);
        }
        ServiceLoader.load(StrategyPlugin.class).forEach(this.plugins::add);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.timeoutMs = timeoutMs;
        this.positionSizer = positionSizer != null ? positionSizer : new DefaultPositionSizer();
        this.eventMetadataFactory = eventMetadataFactory;
    }

    /**
     * Evaluates a domain event against all registered strategy plugins without
     * blocking the caller. Only {@link CandleClosed} events are processed.
     */
    public void onDomainEvent(DomainEvent event, Consumer<DomainEvent> downstream) {
        if (!(event instanceof CandleClosed candleClosed)) {
            return;
        }

        MdcHelper.enrich(event, "strategy-sandbox");
        try {
            log.info("Evaluating {} strategy plugins (non-blocking) for symbol={} interval={}",
                    plugins.size(), candleClosed.candle().symbol(), candleClosed.candle().interval());

            for (StrategyPlugin plugin : plugins) {
                String pluginName = plugin.getClass().getSimpleName();
                String symbol = candleClosed.candle().symbol();
                long correlationSeq = candleClosed.sequenceId();
                String correlationId = candleClosed.correlationId();

                CompletableFuture<Optional<SignalGenerated>> future =
                        CompletableFuture.supplyAsync(() -> runPlugin(plugin, candleClosed), executor);

                future
                        .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .handle((optSignal, error) -> {
                            if (error == null && optSignal != null && optSignal.isPresent()) {
                                emitEnrichedSignal(optSignal.get(), pluginName, downstream);
                            } else if (error != null) {
                                if (error instanceof CancellationException) {
                                    return null; // shutdown
                                }
                                String detail;
                                Throwable cause = error instanceof CompletionException ce
                                        ? ce.getCause() : error;
                                if (cause instanceof TimeoutException) {
                                    detail = "Timed out after " + timeoutMs + "ms";
                                    log.warn("Strategy plugin {} timed out for symbol={}",
                                            pluginName, symbol);
                                    // Note: the virtual thread continues running until
                                    // runPlugin() returns. Java 21 virtual threads don't
                                    // support forced interruption of CompletableFuture tasks.
                                    // Future enhancement: use StructuredTaskScope with
                                    // ShutdownOnTimeout for proper cancellation.
                                } else {
                                    detail = cause.getMessage() != null
                                            ? cause.getMessage()
                                            : cause.getClass().getSimpleName();
                                    log.error("Strategy plugin {} failed for symbol={}: {}",
                                            pluginName, symbol, detail, cause);
                                }
                                downstream.accept(new StrategyError(
                                        eventMetadataFactory.correlated(correlationId, correlationSeq),
                                        pluginName, symbol, detail));
                            }
                            return null;
                        });
            }
        } finally {
            MdcHelper.clear();
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public int pluginCount() {
        return plugins.size();
    }

    public List<String> pluginNames() {
        List<String> names = new ArrayList<>(plugins.size());
        for (StrategyPlugin plugin : plugins) {
            names.add(plugin.name());
        }
        return names;
    }

    /**
     * Run a single plugin and return its signal result.
     * Runs on a virtual thread via {@link CompletableFuture#supplyAsync}.
     * Exceptions propagate to the future's error path.
     */
    private static Optional<SignalGenerated> runPlugin(
            StrategyPlugin plugin,
            CandleClosed candleClosed
    ) {
        String pluginName = plugin.getClass().getSimpleName();
        String symbol = candleClosed.candle().symbol();
        MdcHelper.enrich(candleClosed, pluginName);
        long start = System.nanoTime();
        try {
            Optional<SignalGenerated> result = plugin.onCandleClosed(candleClosed);
            long elapsedNs = System.nanoTime() - start;
            if (result.isPresent()) {
                log.info("Signal generated plugin={} symbol={} side={} latencyUs={}",
                        pluginName, symbol, result.get().side(), elapsedNs / 1_000);
            }
            return result;
        } finally {
            MdcHelper.clear();
        }
    }

    /** Enrich a signal with the strategy name and position size, then emit it. */
    private void emitEnrichedSignal(
            SignalGenerated generated,
            String pluginName,
            Consumer<DomainEvent> downstream
    ) {
        var enrichedAttrs = new HashMap<>(generated.attributes());
        enrichedAttrs.put("strategyName", pluginName);
        if (!enrichedAttrs.containsKey(ATTR_QUANTITY)) {
            long computedQty = positionSizer.computeQuantity(generated);
            if (computedQty > 0) {
                enrichedAttrs.put(ATTR_QUANTITY, computedQty);
            }
        }
        SignalGenerated enriched = new SignalGenerated(
                generated.metadata(),
                generated.signalId(),
                generated.symbol(),
                generated.interval(),
                generated.side(),
                generated.entryPricePaisa(),
                generated.stopLossPaisa(),
                generated.takeProfitPaisa(),
                generated.setup(),
                Collections.unmodifiableMap(enrichedAttrs)
        );
        downstream.accept(enriched);
    }
}
