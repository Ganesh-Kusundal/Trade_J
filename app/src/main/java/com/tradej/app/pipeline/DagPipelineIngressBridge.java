package com.tradej.app.pipeline;

import com.tradej.core.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Routes async-dispatch domain events into active DAG pipeline graphs via ingress filters.
 * Uses {@link com.tradej.pipeline.runtime.PipelineRuntime} for mode-aware event routing
 * (clock advancement in REPLAY mode, simulated fills in BACKTEST mode).
 */
@Component
public final class DagPipelineIngressBridge {

    private static final Logger log = LoggerFactory.getLogger(DagPipelineIngressBridge.class);

    private final DagPipelineRuntimeService dagPipelineRuntimeService;
    private final ExecutorService executor;

    public DagPipelineIngressBridge(DagPipelineRuntimeService dagPipelineRuntimeService) {
        this.dagPipelineRuntimeService = dagPipelineRuntimeService;
        this.executor = Executors.newFixedThreadPool(2, daemonThreadFactory("dag-pipeline-executor"));
    }

    public void onEvent(DomainEvent event) {
        if (event == null || !dagPipelineRuntimeService.hasActiveGraphs()) {
            return;
        }
        List<DagPipelineRuntimeService.IngressBinding> bindings = dagPipelineRuntimeService.ingressBindings();
        if (bindings.isEmpty()) {
            return;
        }
        executor.execute(() -> dispatch(event, bindings));
    }

    private void dispatch(DomainEvent event, List<DagPipelineRuntimeService.IngressBinding> bindings) {
        for (DagPipelineRuntimeService.IngressBinding binding : bindings) {
            if (!binding.config().accepts(event)) {
                continue;
            }
            try {
                log.trace("Dispatching event type={} to DAG graph={} ingress={}",
                        event.getClass().getSimpleName(), binding.graphId(), binding.ingressNodeId());
                // Route through PipelineRuntime for mode-aware clock management
                binding.pipelineRuntime().onEvent(event);
            } catch (Exception e) {
                log.warn("DAG ingress dispatch failed graph={} eventType={}: {}",
                        binding.graphId(), event.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private static ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
