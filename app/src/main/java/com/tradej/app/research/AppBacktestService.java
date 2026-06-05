package com.tradej.app.research;

import com.tradej.app.pipeline.DagPipelineRuntimeService;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.runtime.DefaultBacktestFillModel;
import com.tradej.pipeline.runtime.PipelineRuntime;
import com.tradej.persistence.replay.ReplayStateManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Runs {@link PipelineRuntime#backtestSequence} on the active DAG pipeline graph.
 */
@Service
public class AppBacktestService {

    private final DagPipelineRuntimeService dagPipelineRuntimeService;
    private final ReplayStateManager replayStateManager;

    public AppBacktestService(
            DagPipelineRuntimeService dagPipelineRuntimeService,
            ReplayStateManager replayStateManager
    ) {
        this.dagPipelineRuntimeService = dagPipelineRuntimeService;
        this.replayStateManager = replayStateManager;
    }

    public Map<String, Object> runBacktest(String graphId, List<DomainEvent> events) {
        PipelineRuntime runtime = dagPipelineRuntimeService.pipelineRuntime(graphId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive graph: " + graphId));
        int count = events == null ? 0 : events.size();
        
        replayStateManager.beforeReplay();
        try {
            runtime.backtestSequence(events == null ? List.of() : events, new DefaultBacktestFillModel());
        } finally {
            replayStateManager.afterReplay();
        }
        
        return Map.of(
                "graphId", graphId,
                "eventsProcessed", count,
                "mode", runtime.currentMode().name());
    }
}
