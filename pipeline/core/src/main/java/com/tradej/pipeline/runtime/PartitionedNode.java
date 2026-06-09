package com.tradej.pipeline.runtime;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.core.routing.SymbolShardRouter;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Decorator that wraps a {@link PipelineNode} with per-shard instances for
 * symbol-level partitioning.
 * <p>
 * Each shard owns its own node state (candles, indicators, positions), enabling
 * lock-free per-symbol processing. Events are routed via {@link SymbolShardRouter}.
 * Metrics are aggregated across all shards.
 */
public final class PartitionedNode implements PipelineNode {

    private final Function<Integer, PipelineNode> factory;
    private final int shardCount;
    private final PipelineNodeDef def;

    // Created lazily in init(), not in constructor, because the
    // PipelineContext depends on the routing table built by GraphCompiler.
    private volatile PipelineNode[] shards;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * @param factory    creates a node instance for the given shard index
     * @param shardCount number of shards (must be >= 1)
     * @param def        node definition
     */
    public PartitionedNode(
            Function<Integer, PipelineNode> factory,
            int shardCount,
            PipelineNodeDef def
    ) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be >= 1, got " + shardCount);
        }
        this.shardCount = shardCount;
        this.factory = Objects.requireNonNull(factory, "factory");
        this.def = Objects.requireNonNull(def, "def");
    }

    @Override
    public void init(PipelineNodeDef definition, PipelineContext context) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        PipelineNode[] instances = new PipelineNode[shardCount];
        for (int i = 0; i < shardCount; i++) {
            instances[i] = Objects.requireNonNull(factory.apply(i), "factory returned null for shard " + i);
            instances[i].init(def, context);
        }
        this.shards = instances;
    }

    @Override
    public void onEvent(DomainEvent event) {
        if (!initialized.get()) {
            return;
        }
        int shard = SymbolShardRouter.shardFor(event, shardCount);
        shards[shard].onEvent(event);
    }

    @Override
    public NodeState getState() {
        if (!initialized.get()) {
            return NodeState.PENDING;
        }
        boolean anyRunning = false;
        boolean allFailed = true;
        for (PipelineNode shard : shards) {
            NodeState s = shard.getState();
            if (s == NodeState.RUNNING) {
                anyRunning = true;
            }
            if (s != NodeState.FAILED) {
                allFailed = false;
            }
        }
        if (allFailed) {
            return NodeState.FAILED;
        }
        if (anyRunning) {
            return NodeState.RUNNING;
        }
        return NodeState.HALTED;
    }

    @Override
    public NodeMetrics getMetrics() {
        if (!initialized.get()) {
            return NodeMetrics.ZERO;
        }
        long totalProcessed = 0;
        long totalErrors = 0;
        long lastTs = 0;
        long lastNs = 0;
        long totalNs = 0;
        for (PipelineNode shard : shards) {
            NodeMetrics m = shard.getMetrics();
            totalProcessed += m.processedCount();
            totalErrors += m.errorCount();
            lastTs = Math.max(lastTs, m.lastProcessedTimestampMs());
            lastNs = Math.max(lastNs, m.lastExecutionNs());
            totalNs += m.averageExecutionNs();
        }
        return new NodeMetrics(
                totalProcessed,
                totalErrors,
                lastTs,
                lastNs,
                shardCount > 0 ? (double) totalNs / shardCount : 0.0
        );
    }

    @Override
    public void destroy() {
        if (!initialized.get()) {
            return;
        }
        initialized.set(false);
        PipelineNode[] currentShards = this.shards;
        if (currentShards != null) {
            for (PipelineNode shard : currentShards) {
                try {
                    shard.destroy();
                } catch (Exception e) {
                    // swallow per-shard destruction failures
                }
            }
        }
        this.shards = null;
    }

    /** Number of shards. */
    public int shardCount() {
        return shardCount;
    }

    /** Node instance for a specific shard index. */
    public PipelineNode shard(int index) {
        if (!initialized.get()) {
            throw new IllegalStateException("PartitionedNode not initialized");
        }
        return shards[index];
    }
}
