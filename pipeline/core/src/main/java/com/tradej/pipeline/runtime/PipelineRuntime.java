package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.pipeline.clock.VirtualClock;
import com.tradej.pipeline.graph.PipelineGraph;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Unified runtime orchestrator that wraps {@link GraphRuntime} with mode-aware event
 * routing, clock management, and hot-swap deployment.
 * <p>
 * Single entry point for LIVE, REPLAY, and BACKTEST execution.
 */
public final class PipelineRuntime {

    private final AtomicReference<GraphRuntime> activeRuntime = new AtomicReference<>();
    private final VirtualClock clock;
    private final PipelineContext rootContext;
    private final AtomicReference<RuntimeMode> mode = new AtomicReference<>(RuntimeMode.LIVE);

    public PipelineRuntime(VirtualClock clock, PipelineContext rootContext) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rootContext = Objects.requireNonNull(rootContext, "rootContext");
    }

    // ── Deployment ────────────────────────────────────────────────────────

    /**
     * Compiles and deploys a pipeline graph, atomically swapping the active runtime.
     * The old runtime (if any) is shut down after the swap.
     *
     * @param graph      the declarative pipeline graph
     * @param compiler   compiler used to produce the execution plan
     * @param publisher  hot-path publisher (Disruptor downstream queue) or null for DAG mode
     */
    public void deploy(PipelineGraph graph, GraphCompiler compiler, Consumer<DomainEvent> publisher) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(compiler, "compiler");

        ExecutionPlan plan = compiler.compile(graph, rootContext, publisher);
        GraphRuntime newRuntime = new GraphRuntime(plan, graph);
        GraphRuntime old = activeRuntime.getAndSet(newRuntime);
        if (old != null) {
            old.shutdown();
        }
    }

    // ── Event Injection ───────────────────────────────────────────────────

    /**
     * Injects a single event into the active graph runtime.
     * In REPLAY mode the virtual clock is advanced before dispatch.
     */
    public void onEvent(DomainEvent event) {
        if (event == null) {
            return;
        }

        RuntimeMode currentMode = mode.get();
        if (currentMode == RuntimeMode.REPLAY) {
            clock.advanceVirtualTimeMs(
                    com.tradej.pipeline.clock.EventTimestamps.exchangeOrEventTimeMs(event)
            );
        }

        GraphRuntime rt = activeRuntime.get();
        if (rt != null) {
            rt.onEvent(event);
        }
    }

    /**
     * Processes an event sequentially through all nodes (hot-path semantics).
     * In REPLAY mode the virtual clock is advanced before dispatch.
     */
    public void processSequential(DomainEvent event) {
        if (event == null) {
            return;
        }

        RuntimeMode currentMode = mode.get();
        if (currentMode == RuntimeMode.REPLAY) {
            clock.advanceVirtualTimeMs(
                    com.tradej.pipeline.clock.EventTimestamps.exchangeOrEventTimeMs(event)
            );
        }

        GraphRuntime rt = activeRuntime.get();
        if (rt != null) {
            rt.processSequential(event);
        }
    }

    /**
     * Runs a backtest by processing a sequence of events with virtual time advancement
     * and simulated fills.
     *
     * @param events    ordered historical events
     * @param fillModel fill model that produces simulated trades
     */
    public void backtestSequence(List<DomainEvent> events, BacktestFillModel fillModel) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(fillModel, "fillModel");

        RuntimeMode previous = mode.getAndSet(RuntimeMode.BACKTEST);
        clock.enterReplayMode();
        try {
            for (DomainEvent event : events) {
                clock.advanceVirtualTimeMs(
                        com.tradej.pipeline.clock.EventTimestamps.exchangeOrEventTimeMs(event)
                );
                onEvent(event);
            }
        } finally {
            clock.enterLiveMode();
            mode.set(previous);
        }
    }

    // ── Mode Management ───────────────────────────────────────────────────

    public void switchMode(RuntimeMode newMode) {
        RuntimeMode old = mode.getAndSet(Objects.requireNonNull(newMode, "newMode"));
        if (newMode == RuntimeMode.REPLAY && old != RuntimeMode.REPLAY) {
            clock.enterReplayMode();
        } else if (newMode == RuntimeMode.LIVE && old != RuntimeMode.LIVE) {
            clock.enterLiveMode();
        }
    }

    public RuntimeMode currentMode() {
        return mode.get();
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    /**
     * Returns the stable {@link AtomicReference} that the Disruptor handler
     * uses for hot-swap visibility. Consumers (e.g. {@code GraphPipelineDisruptorHandler})
     * capture this reference at construction time and read {@code ref.get()} on each event.
     */
    public AtomicReference<GraphRuntime> runtimeRef() {
        return activeRuntime;
    }

    public GraphRuntime active() {
        return activeRuntime.get();
    }

    public ExecutionPlan executionPlan() {
        GraphRuntime rt = activeRuntime.get();
        return rt != null ? rt.getExecutionPlan() : null;
    }

    public VirtualClock clock() {
        return clock;
    }

    public PipelineContext context() {
        return rootContext;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    public void shutdown() {
        GraphRuntime rt = activeRuntime.getAndSet(null);
        if (rt != null) {
            rt.shutdown();
        }
    }
}
