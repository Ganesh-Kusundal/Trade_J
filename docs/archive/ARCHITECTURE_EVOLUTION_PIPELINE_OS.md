# Trading Infrastructure OS — Architecture Evolution Plan

> **From Hardcoded Pipeline to Composable Graph Runtime**
>
> **Date:** 2026-05-28
> **Scope:** Full architectural redesign of Trade-J into a pipeline-driven, graph-based, event-processing trading platform with visual flow composition via React Flow
> **Cross-References:** [CODE_LEVEL_REVIEW.md](CODE_LEVEL_REVIEW.md), [docs/runtime-mode-audit.md](docs/runtime-mode-audit.md), [SYSTEM_ANALYSIS_CHECKLIST.md](SYSTEM_ANALYSIS_CHECKLIST.md)

---

## Table of Contents

1. [Executive Summary & Vision](#1-executive-summary--vision)
2. [Current State Assessment](#2-current-state-assessment)
3. [Runtime Graph Engine Design](#3-runtime-graph-engine-design)
4. [Node Abstraction Model](#4-node-abstraction-model)
5. [Disruptor + Reactive Streams Integration](#5-disruptor--reactive-streams-integration)
6. [Event Model Redesign](#6-event-model-redesign)
7. [Symbol Partitioning & Shard Strategy](#7-symbol-partitioning--shard-strategy)
8. [State Ownership Strategy](#8-state-ownership-strategy)
9. [Graph Persistence & Versioning](#9-graph-persistence--versioning)
10. [React Flow Integration Architecture](#10-react-flow-integration-architecture)
11. [Frontend Component Architecture](#11-frontend-component-architecture)
12. [Backend/Frontend Contract Design](#12-backendfrontend-contract-design)
13. [Replay/Backtest/Paper/Live Unification](#13-replaybacktestpaperlive-unification)
14. [Runtime Observability Architecture](#14-runtime-observability-architecture)
15. [Migration Roadmap](#15-migration-roadmap)
16. [Production Risks & Mitigations](#16-production-risks--mitigations)
17. [Distributed Future Scaling](#17-distributed-future-scaling)
18. [Implementation Phases](#18-implementation-phases)

---

## 1. Executive Summary & Vision

### 1.1 The "Trading Infrastructure OS" Concept

The current Trade-J codebase is a **partially event-driven modular trading backend** with LMAX Disruptor at its core, Dhan broker integration, event-sourced OMS, and replay/backtest support. The architectural review confirms it's structurally compatible with a full pipeline-driven evolution.

The **Trading Infrastructure OS** vision transforms this into a system where:

```
┌─────────────────────────────────────────────────────────┐
│              Trading Infrastructure OS                    │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐     ┌──────────────┐                   │
│  │  React Flow   │────▶│ Graph        │──┐               │
│  │  Frontend     │     │ Persistence  │  │               │
│  └──────────────┘     └──────────────┘  │               │
│                                          ▼               │
│  ┌──────────────────────────────────────────────┐       │
│  │         Graph Compilation Pipeline             │       │
│  │  JSON → PipelineDef → ExecutionPlan → Runtime  │       │
│  └──────────────────────────────────────────────┘       │
│                                          │               │
│                                          ▼               │
│  ┌──────────────────────────────────────────────┐       │
│  │         Runtime Graph Engine                   │       │
│  │  ┌─────┐ ┌─────┐ ┌─────┐ ┌─────┐             │       │
│  │  │Ingr.│→│Feat.│→│Scan │→│Sig. │ ...         │       │
│  │  └─────┘ └─────┘ └─────┘ └─────┘             │       │
│  │         ↓         ↓         ↓                 │       │
│  │    ┌──────────────────────────────┐            │       │
│  │    │   LMAX Disruptor Ring Buffer  │            │       │
│  │    │   (Hot path: sub-μs latency)  │            │       │
│  │    └──────────────────────────────┘            │       │
│  └──────────────────────────────────────────────┘       │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐   │
│  │ Live     │ │ Replay   │ │ Backtest │ │ Paper    │   │
│  │ Runtime  │ │ Runtime  │ │ Runtime  │ │ Runtime  │   │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘   │
│       All share the SAME graph engine                    │
└─────────────────────────────────────────────────────────┘
```

### 1.2 Guiding Principles

| Principle | Description |
|-----------|-------------|
| **Graph-native execution** | Every processing step is a node in a directed acyclic graph (DAG) |
| **Declarative pipeline composition** | Pipelines are defined as JSON graphs, not code |
| **Write once, run everywhere** | Same graph runs in live, replay, backtest, and paper modes |
| **Hot-path on Disruptor** | Sub-microsecond latency path through the ring buffer |
| **Cold-path on Reactor** | IO-bound operations via Project Reactor |  
| **Deterministic replay** | Same graph + same events = same output, always |
| **Visual inspectability** | Runtime graphs are viewable and debuggable through React Flow |
| **Dynamic wiring** | Nodes can be added, removed, or rerouted at runtime |

### 1.3 Evolution Not Rewrite

This plan **does not propose rewriting the codebase**. The existing components (`DisruptorEventBus`, `ShardedDisruptorEventBus`, `PositionRiskHandler`, `CandleAggregationService`, `StrategyEngine`, `ExecutionHandler`, `OMS`, `PortfolioEngine`, `MarketDataPipeline`, etc.) already form the foundation. The evolution is:

1. **Wrap** existing components in a `PipelineNode` abstraction
2. **Build** a `GraphCompiler` that translates graph JSON → execution plan
3. **Add** a new Frontend module with React Flow
4. **Extend** the existing pipeline to be runtime-configurable
5. **Preserve** all existing Disruptor infrastructure

---

## 2. Current State Assessment

### 2.1 What Exists (Already Verified in Code Review)

| Component | Status | Will Map To |
|-----------|--------|-------------|
| `DisruptorEventBus` (8192 slots, BusySpin, MULTI) | ✅ Production | Graph runtime backbone |
| `ShardedDisruptorEventBus` (per-symbol routing) | ✅ Ready | Partition-aware node lanes |
| `MarketDataPipeline` (CAS tick rate, token bucket) | ✅ Implemented | `TICK_INGRESS` node |
| `OrderPipeline` (signal/order routing) | ⚠️ Dead code paths | `SIGNAL_GEN` / `ORDER_EXEC` nodes |
| `CandleAggregationService` (multi-interval) | ✅ Implemented | `CANDLE_AGG` transformation node |
| `StrategySandbox` (virtual threads per plugin) | ✅ Implemented | `STRATEGY_EVAL` node |
| `PositionRiskHandler` (ConcurrentHashMap, risk checks) | ✅ Implemented | `RISK_QUALIFY` node |
| `PortfolioEngine` (capital allocation, exposure) | ✅ Implemented | `PORTFOLIO_CHECK` node |
| `ExecutionHandler` (single-threaded, OSM) | ✅ Implemented | `ORDER_EXECUTION` node |
| `TradingCircuitBreaker` (CLOSED/OPEN/HALF_OPEN) | ✅ Implemented | `CIRCUIT_BREAKER` node |
| `EventSourcedNetPositionProvider` | ⚠️ N-01 bug | `POSITION_TRACKER` node |
| `OrderIdentityRegistry` (4-way ID mapping) | ✅ Implemented | `ORDER_IDENTITY` node |
| `OrderStateMachine` (sealed interface, lookup table) | ✅ Implemented | OMS node internal |
| `EventBus` (pub-sub interface) | ✅ Implemented | Node communication API |
| `StageTimings` (per-stage latency metrics) | ✅ Implemented | Per-node latency tracking |
| `AsyncDispatchHandler` (off-thread dispatch) | ✅ Implemented | Cold-path node dispatch |
| `ReplayRunner` (Chronicle Queue) | ⚠️ RP-01 bug | `REPLAY_INGRESS` node |
| `HistoricalRangeService` (DuckDB query) | ✅ Implemented | `HISTORICAL_INGRESS` node |
| `ScanEngine` (batch scanning) | ❌ Batch only | `SCAN_EVAL` node (reactive) |
| `MatchingEngine` (backtest matching) | ⚠️ No slippage | `SIM_MATCHER` node |
| `SimulatedOrderService` | ✅ Implemented | `SIM_ORDER_SERVICE` node |
| `PnLLedger` (FIFO tracking) | ✅ Implemented | `SIM_PNL` node |
| `GatewayEventBridge` (WS to frontend) | ✅ Implemented | `WS_OUTPUT` node |
| `GatewayWebSocketHandler` (binary WS) | ✅ Implemented | Frontend stream |
| `DuckDbFeatureStore` / `InMemoryFeatureStore` | ✅ Implemented | `FEATURE_STORE` node |
| `DhanWebSocketMultiplexer` (dual WS) | ✅ Implemented | `DHAN_WS_INGRESS` node |
| `DhanRestOrderClient` (REST order execution) | ✅ Implemented | `DHAN_ORDER_EXEC` node |

### 2.2 What's Missing (Gap Analysis)

| Capability | Current Status | Target |
|------------|---------------|--------|
| **Node abstraction** | No `PipelineNode` interface exists | Every component implements `PipelineNode` |
| **Graph definition format** | Pipeline stages are hardcoded in `DisruptorEventBus` constructor | JSON graph definitions via `GraphCompiler` |
| **Dynamic graph reconfiguration** | Not possible — pipeline is fixed at startup | Hot-swap nodes via `GraphRuntime` |
| **Visual pipeline builder** | No frontend visualization | React Flow-based builder |
| **Runtime graph viewer** | `AdminController.pipeline()` provides text metrics | React Flow renders live node topology |
| **Graph persistence** | No versioned pipeline storage | Graph CRUD via `GraphRepository` |
| **Strategy-as-graph** | Strategies are `StrategyPlugin` (code) | Strategies are reusable graph sub-flows |
| **Scanner-as-graph** | `ScanEngine.run()` (batch) | Scanner is a sub-graph of nodes |
| **Reactive scanner pipeline** | Cron-based, batch | Tick-driven, per-symbol incremental |
| **Depth-based strategies** | `DepthUpdateEvent` exists but not consumed | `DEPTH_STREAM` node → `DEPTH_ANALYZER` node |
| **Tick-level strategies** | Only `CandleClosed` → plugins | `TICK_STREAM` node → `TICK_STRATEGY` node |
| **Indicator computation** | Not in pipeline (only candle storage) | `INDICATOR` node (VWAP, RSI, ATR, etc.) |
| **Graph-level observability** | Per-stage latencies only | Per-node tracing, event lineage, queue depth |
| **Live/replay isolation** | State corruption risk (AD-02) | Graph snapshot/restore per runtime mode |

---

## 3. Runtime Graph Engine Design

### 3.1 Core Abstractions

```java
// ─── Core Graph Type Hierarchy ────────────────────────────────────────────

/**
 * The complete pipeline graph — represents a compiled execution plan.
 */
public record PipelineGraph(
        String id,
        String name,
        String version,
        List<PipelineNode> nodes,
        List<PipelineEdge> edges,
        Map<String, Object> metadata,
        long createdAtEpochMs,
        GraphRuntimeMode mode  // LIVE, REPLAY, BACKTEST, PAPER
) {}

/**
 * A single node in the pipeline graph.
 */
public record PipelineNode(
        String id,                          // UUID
        NodeType type,                      // INGRESS, TRANSFORM, INDICATOR, SCANNER, SIGNAL, RISK, OMS, POSITION, EXIT
        String label,                       // Human-readable name
        NodeConfig config,                  // Type-specific configuration
        List<String> inputPorts,            // ["in-0", "in-1", ...]
        List<String> outputPorts,           // ["out-0", "out-1", ...]
        Map<String, Object> uiMetadata,     // React Flow position, size, color
        boolean enabled                     // Can be toggled at runtime
) {}

/**
 * An edge connecting two nodes in the graph.
 */
public record PipelineEdge(
        String id,
        String sourceNodeId,
        String sourcePort,
        String targetNodeId,
        String targetPort,
        EventTypeFilter eventFilter,        // What event types flow through this edge
        boolean animated                    // For React Flow visualization
) {}

/**
 * Compiled execution plan — an optimized form of PipelineGraph
 * that the runtime can execute efficiently.
 */
public sealed interface ExecutionPlan {
    record TopologicalOrder(
            List<String> nodeExecutionOrder  // Sorted by dependency analysis
    ) implements ExecutionPlan {}

    record ShardedPlan(
            int shardCount,
            Map<Integer, List<String>> shardToNodes  // Nodes assigned per shard
    ) implements ExecutionPlan {}
}

/**
 * Runtime context for executing a pipeline graph.
 */
public final class GraphRuntime {
    private final String graphId;
    private final PipelineGraph graph;
    private final Map<String, NodeInstance> nodeInstances;
    private final EventBus eventBus;
    private final ShardedDisruptorEventBus shardedBus;
    private final RuntimeModeHolder modeHolder;
    private final GraphMetrics metrics;

    // ── Lifecycle ──

    void start();           // Init all nodes, wire edges, start event flow
    void pause();           // Pause event processing (node state preserved)
    void resume();          // Resume event processing
    void stop();            // Graceful shutdown
    void reconfigure(PipelineGraph newGraph);  // Hot-swap nodes/edges

    // ── Mutation ──

    void addNode(PipelineNode node);
    void removeNode(String nodeId);
    void addEdge(PipelineEdge edge);
    void removeEdge(String edgeId);
    void toggleNode(String nodeId, boolean enabled);
    void updateNodeConfig(String nodeId, NodeConfig config);

    // ── Inspection ──

    GraphSnapshot snapshot();  // Immutable point-in-time state
    GraphMetrics metrics();    // Per-node latency, throughput, queue depth
}

/**
 * Immutable point-in-time snapshot of the graph runtime state.
 */
public record GraphSnapshot(
        long timestampEpochMs,
        PipelineGraph graph,
        Map<String, NodeState> nodeStates,
        Map<String, Long> nodeEventCounts,
        Map<String, Double> nodeLatencyMicros,
        int totalEventsProcessed,
        int droppedEvents,
        long ringBufferRemainingCapacity
) {}

/**
 * Base interface for all pipeline node types.
 */
public interface PipelineNode {
    String id();
    NodeType type();
    void onEvent(DomainEvent event, EventContext ctx);
    void onStart(EventBus eventBus);
    void onStop();
    void onReconfigure(NodeConfig config);
    NodeState state();
    NodeMetrics metrics();
}

/**
 * Context provided to each node during event processing.
 */
public final class EventContext {
    private final Consumer<DomainEvent> downstream;
    private final RuntimeMode mode;
    private final MDCContext mdc;
    private final SpanTimer timer;   // Per-node latency tracking

    // Send event to downstream nodes
    void emit(DomainEvent event);

    // Send event to specific output port
    void emitTo(String portName, DomainEvent event);

    // Send event to dead letter queue
    void deadLetter(String reason);

    // Access feature store for this symbol
    FeatureStore featureStore();

    // Signal, so the context chain is preserved
    String correlationId();
}
```

### 3.2 Node Types Enum

```java
public enum NodeType {
    // ── Ingress Nodes (sources of events) ──
    WS_FEED,                 // Dhan WebSocket market feed
    WS_ORDERS,               // Dhan WebSocket order stream
    REPLAY_FEED,             // Chronicle Queue replay
    HISTORICAL_FEED,         // DuckDB historical query
    SIMULATION_FEED,         // Simulated tick generator
    MANUAL_OVERRIDE,         // Manual order entry

    // ── Transformation Nodes ──
    TICK_NORMALIZER,         // Normalize tick data
    CANDLE_AGGREGATOR,       // Tick → Candle (multi-interval)
    DEPTH_EXTRACTOR,         // Extract depth from tick
    EVENT_FILTER,            // Drop events by criteria
    EVENT_ROUTER,            // Route events to different paths

    // ── Indicator Nodes ──
    VWAP_CALCULATOR,         // Volume-weighted average price
    RSI_CALCULATOR,          // Relative strength index
    ATR_CALCULATOR,          // Average true range
    HALF_TREND,              // HalfTrend indicator
    SUPER_TREND,             // SuperTrend indicator
    OBV_CALCULATOR,          // On-balance volume
    DELTA_CALCULATOR,        // Delta (buy-sell volume)
    ORDER_FLOW_METRICS,      // Order flow imbalance

    // ── Feature Nodes ──
    FEATURE_EXTRACTOR,       // Feature extraction
    FEATURE_STORE,           // Persist features to DuckDB
    FEATURE_SYNC,            // Sync to feature store

    // ── Scanner Nodes ──
    MOMENTUM_SCANNER,        // Momentum-based scan
    BREAKOUT_SCANNER,        // Breakout detection
    LIQUIDITY_SCANNER,       // Liquidity imbalance
    OI_ANALYZER,             // Open interest analysis
    GEX_ANALYZER,            // Gamma exposure
    VOL_COMPRESSION,         // Volatility compression

    // ── Signal Nodes ──
    SIGNAL_GENERATOR,        // Single strategy → signal
    SIGNAL_ENSEMBLE,         // Multi-strategy weighted
    SIGNAL_RANKER,           // Rank signals by confidence

    // ── Risk Nodes ──
    EXPOSURE_CHECK,          // Max exposure per symbol
    DAILY_LOSS_CHECK,        // Max daily loss
    KILL_SWITCH,             // Emergency stop
    MAX_POSITIONS_CHECK,     // Max concurrent positions
    MARGIN_CHECK,            // Margin validation
    CIRCUIT_BREAKER,         // Broker-level circuit breaker

    // ── OMS Nodes ──
    ORDER_PLACEMENT,         // Place order via broker
    ORDER_MODIFY,            // Modify existing order
    ORDER_CANCEL,            // Cancel order
    ORDER_RECONCILE,         // Reconcile order state

    // ── Position Nodes ──
    POSITION_TRACKER,        // Track open positions
    TRAILING_STOP,           // Trailing stop update
    TAKE_PROFIT,             // Take profit logic
    SCALING_MANAGER,         // Scale in/out
    PARTIAL_EXIT,            // Partial exit logic

    // ── Output Nodes ──
    WS_OUTPUT,               // WebSocket gateway
    PERSISTENCE_OUTPUT,      // Chronicle Queue / DuckDB
    DEAD_LETTER_OUTPUT,      // Dead letter queue
    METRICS_OUTPUT,          // Micrometer metrics
    LOG_OUTPUT,              // Structured logging
    SIM_PNL_OUTPUT           // Simulation P&L tracking
}
```

### 3.3 Topological Execution Model

```
Execution order is determined by topological sort of the graph DAG.

Example sub-graph:

  WS_FEED ──────────▶ CANDLE_AGG ──▶ VWAP_CALC ──▶ SIGNAL_GEN ──▶ EXPOSURE_CHECK ──▶ ORDER_PLACEMENT
       │                    │                                            │
       └─▶ TICK_NORMALIZER ─┘                                            │
                              └─▶ MOMENTUM_SCANNER ──▶ SIGNAL_RANKER ────┘

Topological sort gives execution order:
1. WS_FEED (source, no dependencies)
2. TICK_NORMALIZER (depends on WS_FEED)
3. CANDLE_AGG (depends on TICK_NORMALIZER)
4. VWAP_CALC (depends on CANDLE_AGG)
5. MOMENTUM_SCANNER (depends on WS_FEED + TICK_NORMALIZER + CANDLE_AGG)
6. SIGNAL_GEN (depends on VWAP_CALC)
7. SIGNAL_RANKER (depends on MOMENTUM_SCANNER)
8. EXPOSURE_CHECK (depends on SIGNAL_GEN + SIGNAL_RANKER)
9. ORDER_PLACEMENT (depends on EXPOSURE_CHECK)
```

### 3.4 Graph Compilation Pipeline

```java
/**
 * Compiles a PipelineGraph JSON into a runtime-executable ExecutionPlan.
 */
public final class GraphCompiler {

    public ExecutionPlan compile(PipelineGraph graph) {
        // Phase 1: Validate graph structure
        validate(graph);

        // Phase 2: Build dependency map (which nodes depend on which)
        DependencyGraph deps = buildDependencyGraph(graph);

        // Phase 3: Detect cycles (graph must be DAG)
        List<PipelineNode> cycle = detectCycle(deps);
        if (!cycle.isEmpty()) {
            throw new CycleDetectedException(cycle);
        }

        // Phase 4: Topological sort
        List<String> executionOrder = topologicalSort(deps);

        // Phase 5: Shard assignment
        Map<Integer, List<String>> shardAssignment = assignShards(executionOrder);

        // Phase 6: Optimize — merge consecutive light nodes
        ExecutionPlan plan = optimize(executionOrder, shardAssignment);

        // Phase 7: Wire event filters per edge
        Map<String, List<EventTypeFilter>> wirePlan = buildWirePlan(graph);

        return new CompiledPlan(
                graph.id(),
                executionOrder,
                shardAssignment,
                wirePlan
        );
    }
}
```

### 3.5 Dynamic Graph Reconfiguration

The runtime supports hot-swapping nodes without restart:

```java
public void reconfigure(PipelineGraph newGraph) {
    // 1. Compute diff between current and new graph
    GraphDiff diff = GraphDiffer.diff(this.graph, newGraph);

    // 2. For each removed node:
    for (String removedId : diff.removedNodes()) {
        // a. Pause event routing to this node
        eventBus.unsubscribe(removedNode.handledTypes(), nodeInstances.get(removedId));
        // b. Drain pending events from its input queue
        drainNodeQueue(removedId);
        // c. Call onStop()
        nodeInstances.get(removedId).onStop();
        // d. Remove from runtime
        nodeInstances.remove(removedId);
    }

    // 3. For each added node:
    for (PipelineNode added : diff.addedNodes()) {
        // a. Create instance via NodeFactory
        NodeInstance instance = NodeFactory.create(added);
        // b. Call onStart()
        instance.onStart(eventBus);
        // c. Add to runtime (will receive events from next cycle)
        nodeInstances.put(added.id(), instance);
        // d. Subscribe to upstream events
        subscribeNode(added, instance, newGraph);
    }

    // 4. For each modified edge:
    for (EdgeDiff edgeDiff : diff.modifiedEdges()) {
        // a. Unsubscribe old, subscribe new
        rerouteEdge(edgeDiff);
    }

    // 5. Update graph reference
    this.graph = newGraph;
}
```

---

## 4. Node Abstraction Model

### 4.1 Node Implementation Strategy

Each node is a thin adapter that wraps an existing component. The internal implementation remains unchanged — only the interface changes:

```java
/**
 * Adapter that wraps existing CandleAggregationService as a PipelineNode.
 */
public final class CandleAggregatorNode implements PipelineNode {

    private final CandleAggregationService inner;
    private final List<String> intervals;
    private Consumer<DomainEvent> downstream;
    private final NodeMetricsImpl metrics = new NodeMetricsImpl();

    public CandleAggregatorNode(NodeConfig config) {
        this.intervals = config.getList("intervals", List.of("1s", "5m"));
        this.inner = new CandleAggregationService(intervals);
    }

    @Override
    public NodeType type() { return NodeType.CANDLE_AGGREGATOR; }

    @Override
    public void onEvent(DomainEvent event, EventContext ctx) {
        long start = System.nanoTime();
        try {
            inner.onDomainEvent(event, ctx::emit);
        } finally {
            metrics.recordLatency(System.nanoTime() - start);
        }
    }

    @Override
    public void onStart(EventBus eventBus) {
        // No-op for this node; CandleAggregationService is stateless at start
    }

    @Override
    public void onStop() {
        // No-op
    }

    @Override
    public void onReconfigure(NodeConfig config) {
        // Could change intervals at runtime
        this.intervals = config.getList("intervals", this.intervals);
    }

    @Override
    public NodeState state() { return NodeState.RUNNING; }

    @Override
    public NodeMetrics metrics() { return metrics.snapshot(); }
}
```

### 4.2 Node Factory

```java
/**
 * Factory that maps NodeType → PipelineNode implementation.
 * Uses ServiceLoader for plugin-style extensibility.
 */
public final class NodeFactory {

    private static final Map<NodeType, Supplier<PipelineNode>> BUILTIN = Map.of(
        NodeType.WS_FEED,            WsFeedNode::new,
        NodeType.REPLAY_FEED,        ReplayFeedNode::new,
        NodeType.HISTORICAL_FEED,    HistoricalFeedNode::new,
        NodeType.CANDLE_AGGREGATOR,  () -> new CandleAggregatorNode(...),
        NodeType.VWAP_CALCULATOR,    VwapCalculatorNode::new,
        NodeType.RSI_CALCULATOR,     RsiCalculatorNode::new,
        NodeType.HALF_TREND,         HalfTrendNode::new,
        NodeType.SUPER_TREND,        SuperTrendNode::new,
        NodeType.SIGNAL_GENERATOR,   SignalGeneratorNode::new,
        NodeType.EXPOSURE_CHECK,     ExposureCheckNode::new,
        NodeType.DAILY_LOSS_CHECK,   DailyLossCheckNode::new,
        NodeType.KILL_SWITCH,        KillSwitchNode::new,
        NodeType.ORDER_PLACEMENT,    OrderPlacementNode::new,
        NodeType.POSITION_TRACKER,   PositionTrackerNode::new,
        NodeType.TRAILING_STOP,      TrailingStopNode::new,
        NodeType.TAKE_PROFIT,        TakeProfitNode::new,
        NodeType.WS_OUTPUT,          WsOutputNode::new,
        NodeType.PERSISTENCE_OUTPUT, PersistenceOutputNode::new
    );

    public static PipelineNode create(PipelineNode nodeDef) {
        Supplier<PipelineNode> supplier = BUILTIN.get(nodeDef.type());
        if (supplier == null) {
            // Try ServiceLoader for plugin-provided nodes
            for (NodePlugin plugin : ServiceLoader.load(NodePlugin.class)) {
                PipelineNode candidate = plugin.createNode(nodeDef);
                if (candidate != null) return candidate;
            }
            throw new UnknownNodeTypeException(nodeDef.type());
        }
        PipelineNode instance = supplier.get();
        return instance;
    }

    public static Set<NodeType> availableTypes() {
        return BUILTIN.keySet();
    }
}
```

### 4.3 Node Configuration Schema

Each node type defines its configuration schema via a `NodeConfigSchema`:

```java
public record NodeConfigSchema(
        Map<String, ConfigField> fields
) {
    public record ConfigField(
            String type,            // "string", "int", "long", "double", "boolean", "list", "string-list"
            Object defaultValue,
            String description,
            boolean required,
            String[] enumValues     // For string enums
    ) {}
}

// Example: CandleAggregatorNode schema
NodeConfigSchema CANDLE_AGG_SCHEMA = new NodeConfigSchema(Map.of(
    "intervals",    new ConfigField("string-list", List.of("1s", "5m"), "Candle intervals", true, null),
    "timezone",     new ConfigField("string", "UTC", "Time zone for candle alignment", false, null)
));

// Example: VwapCalculatorNode schema
NodeConfigSchema VWAP_SCHEMA = new NodeConfigSchema(Map.of(
    "windowSize",   new ConfigField("int", 14, "Window size for rolling VWAP", true, null),
    "source",       new ConfigField("string", "CANDLE_CLOSED", "Source event type", true, new String[]{"TICK", "CANDLE_CLOSED"})
));
```

### 4.4 Node Library (Pre-built Nodes)

The system will ship with a standard library of nodes:

| Category | Nodes | Implementation Strategy |
|----------|-------|------------------------|
| **Ingress** | `WsFeedNode`, `ReplayFeedNode`, `HistoricalFeedNode`, `SimFeedNode`, `ManualOrderNode` | Wrap existing `DhanWebSocketMultiplexer`, `ReplayRunner`, `HistoricalRangeService` |
| **Transform** | `CandleAggregatorNode`, `TickNormalizerNode`, `DepthExtractorNode`, `EventFilterNode` | Wrap `CandleAggregationService`, extract from `MarketDataPipeline` |
| **Indicator** | `VwapNode`, `RsiNode`, `AtrNode`, `HalfTrendNode`, `SuperTrendNode`, `ObvNode`, `DeltaNode` | New implementations using `FeatureStore` API |
| **Scanner** | `MomentumScannerNode`, `BreakoutScannerNode`, `LiquidityScannerNode`, `OiAnalyzerNode`, `GexAnalyzerNode` | New reactive implementations (replace batch `ScanEngine`) |
| **Signal** | `SignalGeneratorNode`, `SignalEnsembleNode`, `SignalRankerNode` | Wrap `StrategySandbox` + `StrategyPlugin` |
| **Risk** | `ExposureCheckNode`, `DailyLossCheckNode`, `KillSwitchNode`, `MaxPositionsNode`, `CircuitBreakerNode` | Wrap `PositionRiskHandler`, `TradingCircuitBreaker`, `PortfolioEngine` |
| **OMS** | `OrderPlacementNode`, `OrderModifyNode`, `OrderCancelNode`, `OrderReconcileNode` | Wrap `ExecutionHandler`, `OrderManagementService`, `OrderIdentityRegistry` |
| **Position** | `PositionTrackerNode`, `TrailingStopNode`, `TakeProfitNode`, `ScalingManagerNode` | Wrap `EventSourcedNetPositionProvider` (after N-01 fix) |
| **Output** | `WsOutputNode`, `PersistenceOutputNode`, `DeadLetterOutputNode`, `MetricsOutputNode`, `PnlOutputNode` | Wrap `GatewayEventBridge`, `DuckDbFeatureStore`, `ChronicleAuditLogWriter`, `PnLLedger` |

---

## 5. Disruptor + Reactive Streams Integration

### 5.1 Hybrid Architecture Strategy

The existing LMAX Disruptor stays as the **hot path backbone**. Reactor is added only where it adds value:

```
                          Graph Runtime
                              │
               ┌──────────────┼──────────────┐
               │              │              │
          Hot Path        Warm Path      Cold Path
       (Disruptor)    (Direct call)   (Reactor Flux)
               │              │              │
               ▼              ▼              ▼
      ┌───────────────┐ ┌──────────┐ ┌──────────────┐
      │ Ring Buffer    │ │ Node     │ │ Flux.from()  │
      │ (8192 slots,   │ │ direct   │ │ .subscribeOn(│
      │  BusySpin)     │ │ dispatch │ │  Schedulers.)│
      ├────────────────┤ ├──────────┤ ├──────────────┤
      │ CandleAgg      │ │ KillSw   │ │ Broker REST  │
      │ VWAPCalc       │ │ Switch   │ │ DuckDB write │
      │ RsiCalc        │ │ MaxPos   │ │ Chronicle Q  │
      │ SignalGen      │ │ DailyLoss│ │ WS broadcast │
      │ ExposureCheck  │ │ Filter   │ │ OrderRecon   │
      │ OrderPlacement │ │ Router   │ │ │
      │ TrackingStop   │ │          │ │              │
      └────────────────┘ └──────────┘ └──────────────┘
```

### 5.2 Hot Path: Disruptor Ring Buffer

Nodes that run on the Disruptor ring buffer:

| Criterion | Explanation |
|-----------|-------------|
| **Sub-100μs latency required** | Any node in the critical path from tick → signal → order |
| **Stateful per symbol** | Candle state, indicator rolling windows, position state |
| **High throughput** | Thousands of events per second |
| **Deterministic** | Same events → same output, always |

**Nodes assigned to Disruptor:**
- `CandleAggregatorNode` (every tick → candle)
- `VwapCalculatorNode` (every candle → VWAP)
- `RsiCalculatorNode` (every candle → RSI)
- `SignalGeneratorNode` (indicator values → signal)
- `ExposureCheckNode` (signal → risk pass/fail)
- `OrderPlacementNode` (risk pass → order)
- `TrailingStopNode` (position update → SL update)
- `TakeProfitNode` (position update → TP check)

### 5.3 Warm Path: Direct Dispatch

Nodes that are **stateless** or **cheap** (no queue, no ring buffer):

| Criterion | Explanation |
|-----------|-------------|
| **Stateless operation** | Pure check, no internal state |
| **Sub-millisecond latency OK** | Not in the most critical path |
| **Simple filter/logic** | Kill switch is a single boolean check |

**Nodes assigned to direct dispatch:**
- `KillSwitchNode` (check a boolean)
- `DailyLossCheckNode` (check a counter)
- `MaxPositionsCheckNode` (check a count)
- `EventFilterNode` (predicate check)
- `EventRouterNode` (simple routing decision)

These nodes are called **directly on the Disruptor consumer thread** before/after the ring buffer stages. No additional queuing overhead.

### 5.4 Cold Path: Reactor Flux

Nodes that involve **blocking I/O** or **asynchronous operations**:

| Criterion | Explanation |
|-----------|-------------|
| **Blocking I/O** | JDBC, HTTP, file writes |
| **Unreliable latency** | Broker REST calls can take 100ms+ |
| **Not in hot path** | Persistence, reconciliation, WebSocket broadcast |

**Nodes assigned to Reactor Flux:**
- `OrderPlacementNode.brokerCall()` (broker REST API)
- `PersistenceOutputNode` (DuckDB JDBC write)
- `WsOutputNode` (serialize + broadcast to WebSocket clients)
- `OrderReconcileNode` (fetch positions, compare)
- `HistoricalFeedNode` (DuckDB query)

**Integration pattern:**

```java
public final class OrderPlacementNode implements PipelineNode {

    private final DhanRestOrderClient orderClient;
    private final ReactorBridge reactorBridge; // Connects to Reactor scheduler

    @Override
    public void onEvent(DomainEvent event, EventContext ctx) {
        if (event instanceof SignalPendingExecution signal) {
            // Disruptor thread: construct order, emit immediately
            OrderRequest request = buildOrderRequest(signal);

            // Delegate broker call to Reactor cold path
            reactorBridge.schedule(() -> {
                return orderClient.placeOrder(request);
            }).subscribe(
                response -> ctx.emit(new OrderSubmitted(...response)),
                error -> ctx.deadLetter("Broker error: " + error.getMessage())
            );
        }
    }
}
```

### 5.5 Threading Model

| Thread Pool | Used By | Count | Priority |
|-------------|---------|-------|----------|
| **Disruptor consumer** | Ring buffer event processors | 1 per shard (configurable: 2–8) | MAX |
| **Disruptor publisher** | WS feed → ring buffer | 1–2 (WS handler threads) | MAX |
| **Drainer** | downstreamQueue → ring buffer | 1 | HIGH |
| **Async dispatch** | `AsyncDispatchHandler` | 1 | NORMAL |
| **Reactor schedulers** | Cold-path Flux | bounded elastic (default: 10) | NORMAL |
| **Execution handler** | OMS order processing | 1 (single-threaded) | HIGH |
| **Fill retry** | Scheduled fill processing | 1 | NORMAL |
| **Strategy sandbox** | Virtual threads per plugin | unbounded (virtual) | NORMAL |
| **Replay** | Chronicle Queue tailer | 1 | NORMAL |

### 5.6 Reactor ↔ Disruptor Bridge

```java
/**
 * Bridges between Reactor Flux and the Disruptor event bus.
 * Allows Reactor-based nodes to emit events back into the ring buffer.
 */
public final class ReactorBridge {

    private final ShardedDisruptorEventBus eventBus;
    private final Scheduler scheduler;

    public ReactorBridge(ShardedDisruptorEventBus eventBus, Scheduler scheduler) {
        this.eventBus = eventBus;
        this.scheduler = scheduler;
    }

    /**
     * Schedule an async task on the Reactor scheduler.
     * Results are published back to the Disruptor ring buffer.
     */
    public <T extends DomainEvent> Mono<Void> schedule(Supplier<Mono<T>> task) {
        return Mono.defer(task)
                .subscribeOn(scheduler)
                .doOnNext(eventBus::publish)
                .then();
    }

    /**
     * Create a Flux from a reactive stream and bridge it to the Disruptor.
     * Used for source nodes like HistoricalFeedNode.
     */
    public Disposable bridge(Flux<? extends DomainEvent> flux) {
        return flux
                .subscribeOn(scheduler)
                .subscribe(eventBus::publish);
    }

    /**
     * Wrap a blocking call (like JDBC query) into a Mono.
     */
    public <T extends DomainEvent> Mono<T> blocking(Callable<T> blockingCall) {
        return Mono.fromCallable(blockingCall)
                .subscribeOn(scheduler);
    }
}
```

---

## 6. Event Model Redesign

### 6.1 Event Type Classification

Events are classified into two categories for the graph runtime:

```java
/**
 * Base interface for all domain events — unchanged from existing codebase.
 * All existing event records (TickReceived, CandleClosed, etc.) remain as-is.
 */
public interface DomainEvent { /* ... existing interface ... */ }

/**
 * NEW: Marker interface for events that carry routing metadata.
 * Used by the graph runtime to route events to specific nodes.
 */
public sealed interface RoutedEvent permits
        TickReceived,
        CandleClosed,
        CandleDeveloping,
        DepthUpdateEvent,
        SignalGenerated,
        SignalPendingExecution,
        TradeOpened,
        TradeClosed,
        OrderSubmitted,
        OrderAccepted,
        OrderPartiallyFilled,
        OrderFullyFilled,
        OrderRejected,
        PositionMismatch {

    EventMetadata metadata();
}

/**
 * NEW: Control events used by the graph runtime itself.
 * These are not domain events — they're runtime control signals.
 */
public sealed interface ControlEvent {
    record NodePaused(String nodeId) implements ControlEvent {}
    record NodeResumed(String nodeId) implements ControlEvent {}
    record NodeFailed(String nodeId, String error, Throwable cause) implements ControlEvent {}
    record GraphReconfigured(String graphId) implements ControlEvent {}
    record RuntimeModeSwitched(RuntimeMode oldMode, RuntimeMode newMode) implements ControlEvent {}
    record RingBufferBackpressure(int shard, int utilizationPct) implements ControlEvent {}
}
```

### 6.2 Event Filtering Per Edge

Each `PipelineEdge` can specify which event types it carries:

```java
public record EventTypeFilter(
        Set<Class<? extends DomainEvent>> includeTypes,
        Set<Class<? extends DomainEvent>> excludeTypes,
        Predicate<DomainEvent> predicate   // Custom filter (serialized as JSON)
) {
    public static EventTypeFilter all() {
        return new EventTypeFilter(Set.of(DomainEvent.class), Set.of(), e -> true);
    }

    public static EventTypeFilter of(Class<? extends DomainEvent> type) {
        return new EventTypeFilter(Set.of(type), Set.of(), e -> true);
    }

    public boolean matches(DomainEvent event) {
        if (!includeTypes.isEmpty()) {
            boolean matched = false;
            for (Class<?> type : includeTypes) {
                if (type.isInstance(event)) { matched = true; break; }
            }
            if (!matched) return false;
        }
        if (excludeTypes.stream().anyMatch(t -> t.isInstance(event))) return false;
        return predicate.test(event);
    }
}
```

### 6.3 Event Enrichment

Events flowing through the graph carry enrichment context:

```java
/**
 * Enrichment attached to events as they flow through the graph.
 * Each node can add metadata.
 */
public record EventTrace(
        String eventId,
        String correlationId,
        String rootEventId,              // Original event that started this chain
        long createdAtEpochMs,
        List<NodeHop> hops               // Nodes this event has passed through
) {
    public record NodeHop(
            String nodeId,
            String nodeType,
            long enteredAtNanos,
            long exitedAtNanos
    ) {}
}
```

### 6.4 Event Routing Implementation

```java
/**
 * Wires edges between nodes at runtime.
 * Each edge subscribes the target node to events from the source node.
 */
public final class EventRouter {

    private final EventBus eventBus;
    private final Map<String, Map<String, List<PipelineEdge>>> routeTable;

    public EventRouter(EventBus eventBus) {
        this.eventBus = eventBus;
        this.routeTable = new ConcurrentHashMap<>();
    }

    public void wireEdge(PipelineEdge edge, PipelineNode source, PipelineNode target) {
        // Subscribe target to source's output
        eventBus.subscribe(RoutedEvent.class, event -> {
            if (edge.eventFilter().matches(event)) {
                target.onEvent(event, new EventContext(/* ... */));
            }
        });
    }

    public void unwireEdge(PipelineEdge edge) {
        // Remove subscription
        eventBus.unsubscribe(RoutedEvent.class, /* ... */);
    }

    public void addNode(PipelineNode node) {
        // Register node for routing
    }
}
```

---

## 7. Symbol Partitioning & Shard Strategy

### 7.1 Existing Sharded Disruptor Bus

The `ShardedDisruptorEventBus` already provides symbol-based partitioning. The graph runtime leverages this:

```java
/**
 * Extended shard-aware node execution.
 * Each symbol gets a deterministic shard assignment.
 */
public final class ShardAwareRuntime {

    private final ShardedDisruptorEventBus[] shards;
    private final SymbolShardRouter router;

    public void routeToNode(PipelineNode node, DomainEvent event) {
        int shard = router.shardFor(symbolOf(event), shards.length);
        shards[shard].publish(event);  // → node.onEvent() on that shard's consumer thread
    }

    /**
     * Assign nodes to shards based on symbol affinity.
     * Nodes with per-symbol state get assigned to the symbol's shard.
     */
    public Map<Integer, List<PipelineNode>> assignNodesToShards(
            List<PipelineNode> nodes, int shardCount) {

        Map<Integer, List<PipelineNode>> assignment = new HashMap<>();
        for (int i = 0; i < shardCount; i++) {
            assignment.put(i, new ArrayList<>());
        }

        for (PipelineNode node : nodes) {
            if (node instanceof ShardAwareNode shardAware) {
                // Node has per-symbol state → assign to shard 0 (symbol-specific)
                // In practice, each shard has its own instance of the node
                for (int i = 0; i < shardCount; i++) {
                    assignment.get(i).add(node.copyForShard(i));
                }
            } else {
                // Stateless node → assign to shard 0 only (single instance)
                assignment.get(0).add(node);
            }
        }

        return assignment;
    }
}
```

### 7.2 Per-Symbol State Isolation

```java
/**
 * Nodes with per-symbol state implement this interface.
 * Each shard maintains its own instance of the node.
 */
public interface ShardAwareNode {
    PipelineNode copyForShard(int shardId);
    String symbol();
}

/**
 * Example: VwapCalculatorNode with per-symbol state.
 * Each shard gets its own instance with its own symbol's rolling window.
 */
public final class VwapCalculatorNode implements PipelineNode, ShardAwareNode {

    private final Map<String, VwapState> vwapBySymbol;
    private final int windowSize;
    private final int shardId;

    public VwapCalculatorNode(int windowSize, int shardId) {
        this.vwapBySymbol = new ConcurrentHashMap<>();
        this.windowSize = windowSize;
        this.shardId = shardId;
    }

    @Override
    public PipelineNode copyForShard(int newShardId) {
        return new VwapCalculatorNode(this.windowSize, newShardId);
    }

    @Override
    public void onEvent(DomainEvent event, EventContext ctx) {
        if (event instanceof CandleClosed candle) {
            VwapState state = vwapBySymbol.computeIfAbsent(
                    candle.symbol(), k -> new VwapState(windowSize));
            state.addCandle(candle);
            double vwap = state.calculate();

            ctx.emit(new VwapUpdated(candle.metadata(), candle.symbol(), vwap));
        }
    }
}
```

### 7.3 Actor-Like Node Execution Model

Each shard's consumer thread behaves like an actor:

```
Shard 0 consumer thread:
    ┌────────────────────────────────────────────┐
    │ Disruptor ring buffer → event processor    │
    │                                            │
    │ Events for symbol "NIFTY":                 │
    │   → CandleAggregatorNode[NIFTY].onEvent()  │
    │   → VwapCalculatorNode[NIFTY].onEvent()    │
    │   → RsiCalculatorNode[NIFTY].onEvent()     │
    │   → SignalGeneratorNode.onEvent()          │
    │                                            │
    │ Events for symbol "SBIN":                  │
    │   → CandleAggregatorNode[SBIN].onEvent()   │
    │   → VwapCalculatorNode[SBIN].onEvent()     │
    │   → ...                                    │
    └────────────────────────────────────────────┘
```

**Guarantees:**
- Events for the same symbol are processed sequentially
- No locks needed for per-symbol state
- Thread-safe concurrent data structures for cross-symbol state

---

## 8. State Ownership Strategy

### 8.1 State Categories

| State Type | Ownership | Persistence | Example |
|------------|-----------|-------------|---------|
| **Node-local** | Inside node instance | Not persisted | Rolling VWAP window |
| **Node-local but persisted** | Inside node + periodic snapshot | Chronicle Queue | Candle aggregates |
| **Shared (event-sourced)** | Central store | Event-sourced via Chronicle | OMS state, positions |
| **Analytical** | DuckDB | Long-term, queryable | Historical candles, trades |
| **Runtime config** | Graph runtime | Graph persistence | Node config, active/inactive |

### 8.2 Node-Local State

```
┌─────────────────────────────────────┐
│  VwapCalculatorNode                  │
│                                     │
│  Map<String, VwapState> state       │
│  ├─ "NIFTY" → { window: [...],     │
│  │               sumPv: 12345,      │
│  │               sumV: 100 }        │
│  ├─ "SBIN"  → { window: [...],     │
│  │               sumPv: 6789,       │
│  │               sumV: 50 }         │
│  └─ ...                             │
│                                     │
│  Thread: Disruptor consumer #3      │
│  (single writer, ConcurrentHashMap) │
└─────────────────────────────────────┘
```

**Rules:**
- Node-local state is always a `ConcurrentHashMap` keyed by symbol
- Single writer per shard (the Disruptor consumer thread)
- Node-local state is NOT persisted by default
- Optional: snapshot to Chronicle Queue for replay recovery

### 8.3 Replay State Recovery

For replay to work correctly, nodes must be able to rebuild their state from events:

```java
/**
 * Interface for nodes that can snapshot and restore their state.
 */
public interface Snapshotable {
    /**
     * Capture current state as a serializable snapshot.
     */
    NodeStateSnapshot snapshot();

    /**
     * Restore state from a snapshot.
     * Called before replay starts.
     */
    void restore(NodeStateSnapshot snapshot);

    /**
     * Drain all events — process any pending events.
     * Called before snapshotting.
     */
    void drain();

    /**
     * Whether this node's state affects execution determinism.
     * If false, the node doesn't need snapshotting for replay.
     */
    boolean isDeterministic();
}

public record NodeStateSnapshot(
        String nodeId,
        long timestampEpochMs,
        byte[] stateBytes,          // JSON-serialized state
        String stateType            // Class name for deserialization
) {}
```

### 8.4 Snapshot/Restore Flow for Replay

```
Live → Replay switch:

1.  Pause event processing
2.  Drain all nodes (flush pending events)
3.  Snapshot all Snapshotable nodes → Chronicle Queue
4.  Clear all node-local state
5.  Start replay ingression
6.  Events flow through nodes as normal
7.  State is rebuilt event-by-event (deterministic)

Replay → Live switch:

1.  Stop replay
2.  Restore snapshots → node-local state
3.  Resume live event processing
4.  (Events missed during snapshot are replayed as catch-up)
```

---

## 9. Graph Persistence & Versioning

### 9.1 Graph Repository

```java
/**
 * Repository for pipeline graph definitions.
 * Graphs are stored as JSON in a dedicated Chronicle Queue or DuckDB table.
 */
public interface GraphRepository {

    // CRUD
    PipelineGraph save(PipelineGraph graph);
    Optional<PipelineGraph> load(String graphId);
    List<PipelineGraph> list(int limit, int offset);
    void delete(String graphId);

    // Versioning
    PipelineGraph createVersion(String graphId);
    List<PipelineGraph> listVersions(String graphId);
    PipelineGraph loadVersion(String graphId, String version);

    // Templates
    PipelineGraph loadTemplate(String templateId);
    List<PipelineGraph> listTemplates();
    PipelineGraph saveTemplate(String templateId, PipelineGraph graph);

    // Active graph
    void setActiveGraph(String graphId);
    Optional<String> getActiveGraphId();
}
```

### 9.2 Graph JSON Format (Example)

```json
{
  "id": "graph-intraday-momentum",
  "name": "Intraday Momentum Strategy v3",
  "version": "3.2.1",
  "metadata": {
    "author": "quant-team",
    "created": "2026-05-28T10:00:00Z",
    "description": "Momentum breakout with VWAP filter and trailing stop"
  },
  "nodes": [
    {
      "id": "n1",
      "type": "WS_FEED",
      "label": "Dhan Market Feed",
      "config": {
        "symbols": ["NIFTY", "BANKNIFTY", "SBIN", "RELIANCE"],
        "feedMode": "QUOTE"
      },
      "inputPorts": [],
      "outputPorts": ["tick-out"],
      "uiMetadata": {
        "position": { "x": 50, "y": 100 },
        "color": "#3b82f6",
        "icon": "activity"
      },
      "enabled": true
    },
    {
      "id": "n2",
      "type": "CANDLE_AGGREGATOR",
      "label": "1m Candle Builder",
      "config": {
        "intervals": ["1m"],
        "timezone": "Asia/Kolkata"
      },
      "inputPorts": ["tick-in"],
      "outputPorts": ["candle-out", "dev-candle-out"],
      "uiMetadata": {
        "position": { "x": 250, "y": 100 },
        "color": "#10b981"
      },
      "enabled": true
    },
    {
      "id": "n3",
      "type": "VWAP_CALCULATOR",
      "label": "VWAP (14-period)",
      "config": {
        "windowSize": 14,
        "source": "CANDLE_CLOSED"
      },
      "inputPorts": ["candle-in"],
      "outputPorts": ["vwap-out"],
      "uiMetadata": {
        "position": { "x": 450, "y": 50 },
        "color": "#8b5cf6"
      },
      "enabled": true
    },
    {
      "id": "n4",
      "type": "SIGNAL_GENERATOR",
      "label": "VWAP Breakout Signal",
      "config": {
        "condition": "PRICE_ABOVE_VWAP",
        "threshold": 0.5
      },
      "inputPorts": ["vwap-in", "candle-in"],
      "outputPorts": ["signal-out"],
      "uiMetadata": {
        "position": { "x": 650, "y": 100 },
        "color": "#f59e0b"
      },
      "enabled": true
    },
    {
      "id": "n5",
      "type": "EXPOSURE_CHECK",
      "label": "Max ₹50k Exposure",
      "config": {
        "maxExposurePaisa": 5000000,
        "maxPositionCount": 5
      },
      "inputPorts": ["signal-in"],
      "outputPorts": ["approved-out", "rejected-out"],
      "uiMetadata": {
        "position": { "x": 850, "y": 100 },
        "color": "#ef4444"
      },
      "enabled": true
    },
    {
      "id": "n6",
      "type": "ORDER_PLACEMENT",
      "label": "Dhan Order Placement",
      "config": {
        "orderType": "LIMIT",
        "validity": "DAY",
        "productType": "INTRADAY",
        "broker": "DHAN"
      },
      "inputPorts": ["approved-in"],
      "outputPorts": ["order-out", "exec-out"],
      "uiMetadata": {
        "position": { "x": 1050, "y": 100 },
        "color": "#6366f1"
      },
      "enabled": true
    },
    {
      "id": "n7",
      "type": "TRAILING_STOP",
      "label": "0.5% Trailing Stop",
      "config": {
        "trailPercent": 0.5,
        "activationPercent": 0.2
      },
      "inputPorts": ["position-in"],
      "outputPorts": ["stop-out"],
      "uiMetadata": {
        "position": { "x": 1050, "y": 250 },
        "color": "#f43f5e"
      },
      "enabled": true
    }
  ],
  "edges": [
    { "id": "e1", "source": "n1", "sourcePort": "tick-out", "target": "n2", "targetPort": "tick-in",
      "eventFilter": { "include": ["TickReceived"] }, "animated": true },
    { "id": "e2", "source": "n2", "sourcePort": "candle-out", "target": "n3", "targetPort": "candle-in",
      "eventFilter": { "include": ["CandleClosed"] }, "animated": true },
    { "id": "e3", "source": "n2", "sourcePort": "candle-out", "target": "n4", "targetPort": "candle-in",
      "eventFilter": { "include": ["CandleClosed"] }, "animated": true },
    { "id": "e4", "source": "n3", "sourcePort": "vwap-out", "target": "n4", "targetPort": "vwap-in",
      "eventFilter": { "include": ["VwapUpdated"] }, "animated": true },
    { "id": "e5", "source": "n4", "sourcePort": "signal-out", "target": "n5", "targetPort": "signal-in",
      "eventFilter": { "include": ["SignalGenerated"] }, "animated": false },
    { "id": "e6", "source": "n5", "sourcePort": "approved-out", "target": "n6", "targetPort": "approved-in",
      "eventFilter": { "include": ["SignalPendingExecution"] }, "animated": true },
    { "id": "e7", "source": "n6", "sourcePort": "exec-out", "target": "n7", "targetPort": "position-in",
      "eventFilter": { "include": ["TradeOpened"] }, "animated": true }
  ]
}
```

### 9.3 Graph Templates

Pre-built graph templates shipped with the system:

| Template | Description | Nodes |
|----------|-------------|-------|
| `basic-candle-watch` | Minimal: WS feed → candle → WebSocket output | 3 nodes |
| `vwap-breakout-intraday` | VWAP breakout with trailing stop | 7 nodes (as shown above) |
| `momentum-scanner` | Multi-symbol momentum scanner with ranking | 5 nodes |
| `option-chain-watch` | Option chain streaming with Greeks | 6 nodes |
| `replay-verify` | Replay feed → all nodes → compare output | 4 nodes |
| `paper-trade-basic` | Simulated execution of any strategy | Strategy graph + sim nodes |

---

## 10. React Flow Integration Architecture

### 10.1 Frontend Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                    React Frontend                            │
│                                                              │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐   │
│  │ Pipeline Editor │  │ Graph Viewer   │  │ Runtime      │   │
│  │ (React Flow)    │  │ (React Flow)   │  │ Dashboard    │   │
│  ├────────────────┤  ├────────────────┤  ├──────────────┤   │
│  │ • Strategy     │  │ • Node metrics │  │ • System     │   │
│  │   builder      │  │ • Event flow   │  │   health     │   │
│  │ • Scanner      │  │ • Queue depth  │  │ • Broker     │   │
│  │   builder      │  │ • Latency      │  │   status     │   │
│  │ • Pipeline     │  │   heat map     │  │ • Positions  │   │
│  │   designer     │  │ • Symbol       │  │ • P&L        │   │
│  └────────────────┘  │   partitioning │  └──────────────┘   │
│                       └────────────────┘                     │
│  ┌────────────────────────────────────────────────────────┐  │
│  │                Shared State (Zustand)                    │  │
│  │  graphStore | runtimeStore | nodeLibraryStore            │  │
│  └────────────────────────────────────────────────────────┘  │
│                               │                               │
│                               ▼                               │
│  ┌────────────────────────────────────────────────────────┐  │
│  │              Backend API Client (REST + SSE)             │  │
│  │  /api/v1/pipelines/* | /api/v1/runtime/* | /api/v1/    │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      Backend (Spring Boot)                    │
│  /api/v1/pipelines/graphs/*   → GraphRepository              │
│  /api/v1/pipelines/runtime/*  → GraphRuntime                 │
│  /api/v1/pipelines/nodes/*    → NodeFactory                  │
│  /api/v1/runtime/snapshot     → GraphRuntime.snapshot()      │
│  /api/v1/runtime/stream       → SSE event stream             │
└─────────────────────────────────────────────────────────────┘
```

### 10.2 React Flow Component Tree

```tsx
// ─── Top-level component ──────────────────────────────────

function PipelineDesignerApp() {
  return (
    <div className="pipeline-os">
      <Sidebar>
        <NodePalette />          {/* Draggable node types */}
        <TemplateLibrary />      {/* Saved graphs */}
        <GraphVersionHistory />  {/* Version timeline */}
      </Sidebar>
      <main>
        <PipelineToolbar />      {/* Save, deploy, validate, export */}
        <PipelineCanvas />       {/* React Flow canvas */}
        <NodeInspector />        {/* Selected node config panel */}
      </main>
      <RuntimePanel>             {/* Live runtime metrics */}
        <NodeMetricsView />
        <EventFlowView />
      </RuntimePanel>
      <StatusBar>
        <ConnectionIndicator />
        <RuntimeModeIndicator />
        <GraphStatusIndicator />
      </StatusBar>
    </div>
  );
}
```

### 10.3 Custom React Flow Nodes

Each pipeline node type maps to a custom React Flow node component:

```tsx
// ─── Custom Node Base ──────────────────────────────────────

interface PipelineNodeData {
  nodeId: string;
  type: string;
  label: string;
  config: Record<string, unknown>;
  metrics: NodeMetrics | null;     // Populated when runtime is connected
  state: 'running' | 'paused' | 'error' | 'disabled';
  onConfigChange: (config: Record<string, unknown>) => void;
}

function PipelineNode({ data, selected }: NodeProps<PipelineNodeData>) {
  return (
    <div className={`pipeline-node ${data.state} ${selected ? 'selected' : ''}`}>
      <Handle type="target" position={Position.Left} />
      <div className="node-header" style={{ backgroundColor: getColor(data.type) }}>
        <NodeIcon type={data.type} />
        <span className="node-label">{data.label}</span>
        <NodeStateIndicator state={data.state} />
      </div>
      <div className="node-body">
        {data.metrics && (
          <div className="node-metrics">
            <MetricBar label="latency" value={`${data.metrics.latencyMicros}µs`} />
            <MetricBar label="throughput" value={`${data.metrics.eventsPerSec}/s`} />
            <MetricBar label="queue" value={`${data.metrics.queueDepth}`} />
          </div>
        )}
      </div>
      <Handle type="source" position={Position.Right} />
    </div>
  );
}

// ─── Registered with React Flow ───────────────────────────

const nodeTypes = {
  WS_FEED:            WsFeedNode,
  CANDLE_AGGREGATOR:  CandleAggNode,
  VWAP_CALCULATOR:    VwapNode,
  RSI_CALCULATOR:     RsiNode,
  SIGNAL_GENERATOR:   SignalNode,
  EXPOSURE_CHECK:     RiskNode,
  ORDER_PLACEMENT:    OrderNode,
  TRAILING_STOP:      PositionNode,
  WS_OUTPUT:          OutputNode,
  // ... all 40+ types
};
```

### 10.4 Animated Event Flow Edges

```tsx
// ─── Edge with animated data flow indicator ────────────────

function PipelineEdge({
  source, target, data, selected, animated, style
}: EdgeProps) {
  const [flowAnim, setFlowAnim] = useState(false);

  // When the source node emits an event, this component
  // receives a signal via the shared store to animate
  useEffect(() => {
    return eventBus.on(`edge:${source}-${target}`, () => {
      setFlowAnim(true);
      setTimeout(() => setFlowAnim(false), 300);
    });
  }, [source, target]);

  return (
    <BaseEdge
      path={data.path}
      style={{
        ...style,
        stroke: flowAnim ? '#58a6ff' : '#30363d',
        strokeWidth: flowAnim ? 3 : 1.5,
        transition: 'all 0.3s ease',
      }}
    >
      {animated && <EdgeLabel label={data.eventType} />}
    </BaseEdge>
  );
}
```

### 10.5 Frontend Pages

| Route | Component | Description |
|-------|-----------|-------------|
| `/` | `Dashboard` | System overview, active graph, key metrics |
| `/pipeline` | `PipelineEditor` | Full pipeline graph designer |
| `/pipeline/:id` | `PipelineEditor` | Edit specific saved graph |
| `/scanner` | `ScannerBuilder` | Visual scanner flow builder |
| `/strategy` | `StrategyBuilder` | Visual strategy flow builder |
| `/runtime` | `RuntimeViewer` | Live graph execution view |
| `/runtime/:graphId` | `RuntimeViewer` | View specific graph execution |
| `/templates` | `TemplateLibrary` | Browse and manage graph templates |
| `/monitor` | `SystemMonitor` | System health, broker status, positions |

---

## 11. Frontend Component Architecture

### 11.1 React + TypeScript + Vite Setup

The existing frontend uses vanilla TypeScript with Vite. The React Flow integration requires adding React:

```json
{
  "name": "trade-j-pipeline-os",
  "private": true,
  "version": "1.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc --noEmit && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "reactflow": "^11.11.0",
    "zustand": "^5.0.0",
    "lightweight-charts": "^4.2.2"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "typescript": "^5.7.0",
    "vite": "^6.2.0",
    "@vitejs/plugin-react": "^4.3.0"
  }
}
```

### 11.2 Zustand Stores

```ts
// ─── Graph Store ───────────────────────────────────────────

interface GraphStore {
  // State
  graphs: PipelineGraph[];
  activeGraphId: string | null;
  isLoading: boolean;
  error: string | null;

  // Actions
  loadGraphs: () => Promise<void>;
  saveGraph: (graph: PipelineGraph) => Promise<void>;
  deleteGraph: (id: string) => Promise<void>;
  setActiveGraph: (id: string) => void;

  // Live editing
  addNode: (node: PipelineNode) => void;
  removeNode: (id: string) => void;
  updateNodeConfig: (id: string, config: Record<string, unknown>) => void;
  addEdge: (edge: PipelineEdge) => void;
  removeEdge: (id: string) => void;
  toggleNode: (id: string, enabled: boolean) => void;
}

// ─── Runtime Store ─────────────────────────────────────────

interface RuntimeStore {
  // State
  snapshot: GraphSnapshot | null;
  isConnected: boolean;
  mode: 'LIVE' | 'REPLAY' | 'BACKTEST' | 'PAPER';

  // SSE stream
  connect: (graphId: string) => void;
  disconnect: () => void;

  // Actions
  startGraph: (graphId: string) => Promise<void>;
  stopGraph: () => Promise<void>;
  pauseGraph: () => Promise<void>;
  resumeGraph: () => Promise<void>;
  reconfigureGraph: (graph: PipelineGraph) => Promise<void>;
}

// ─── Node Library Store ────────────────────────────────────

interface NodeLibraryStore {
  availableNodes: NodeDefinition[];
  nodeSchemas: Map<string, NodeConfigSchema>;
  nodeTemplates: PipelineNode[];

  loadLibrary: () => Promise<void>;
  getSchema: (type: string) => NodeConfigSchema | undefined;
  createNode: (type: string) => PipelineNode;
}
```

### 11.3 Backend API Client

```ts
// ─── API Client ────────────────────────────────────────────

class PipelineApiClient {
  private baseUrl = '/api/v1/pipelines';

  // Graphs
  async listGraphs(): Promise<PipelineGraph[]> {
    return fetch(`${this.baseUrl}/graphs`).then(r => r.json());
  }

  async getGraph(id: string): Promise<PipelineGraph> {
    return fetch(`${this.baseUrl}/graphs/${id}`).then(r => r.json());
  }

  async saveGraph(graph: PipelineGraph): Promise<PipelineGraph> {
    return fetch(`${this.baseUrl}/graphs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(graph),
    }).then(r => r.json());
  }

  async deleteGraph(id: string): Promise<void> {
    return fetch(`${this.baseUrl}/graphs/${id}`, { method: 'DELETE' }).then();
  }

  // Node Library
  async getNodeTypes(): Promise<NodeDefinition[]> {
    return fetch(`${this.baseUrl}/nodes`).then(r => r.json());
  }

  async getNodeSchema(type: string): Promise<NodeConfigSchema> {
    return fetch(`${this.baseUrl}/nodes/${type}/schema`).then(r => r.json());
  }

  // Templates
  async listTemplates(): Promise<PipelineGraph[]> {
    return fetch(`${this.baseUrl}/templates`).then(r => r.json());
  }

  async saveTemplate(template: PipelineGraph): Promise<void> {
    return fetch(`${this.baseUrl}/templates`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(template),
    }).then();
  }

  // Runtime
  async startGraph(graphId: string): Promise<void> {
    return fetch(`/api/v1/runtime/start/${graphId}`, { method: 'POST' }).then();
  }

  async stopRuntime(): Promise<void> {
    return fetch('/api/v1/runtime/stop', { method: 'POST' }).then();
  }

  async getSnapshot(): Promise<GraphSnapshot> {
    return fetch('/api/v1/runtime/snapshot').then(r => r.json());
  }
}

export const pipelineApi = new PipelineApiClient();
```

---

## 12. Backend/Frontend Contract Design

### 12.1 REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/pipelines/graphs` | List all graphs |
| `POST` | `/api/v1/pipelines/graphs` | Create/save graph |
| `GET` | `/api/v1/pipelines/graphs/{id}` | Get graph by ID |
| `PUT` | `/api/v1/pipelines/graphs/{id}` | Update graph |
| `DELETE` | `/api/v1/pipelines/graphs/{id}` | Delete graph |
| `POST` | `/api/v1/pipelines/graphs/{id}/version` | Create new version |
| `GET` | `/api/v1/pipelines/graphs/{id}/versions` | List all versions |
| `GET` | `/api/v1/pipelines/graphs/{id}/versions/{ver}` | Load specific version |
| `GET` | `/api/v1/pipelines/nodes` | List available node types |
| `GET` | `/api/v1/pipelines/nodes/{type}/schema` | Get node config schema |
| `GET` | `/api/v1/pipelines/templates` | List graph templates |
| `POST` | `/api/v1/pipelines/templates` | Save template |
| `POST` | `/api/v1/pipelines/compile` | Compile graph → execution plan |
| `POST` | `/api/v1/runtime/start/{graphId}` | Start executing graph |
| `POST` | `/api/v1/runtime/stop` | Stop graph execution |
| `POST` | `/api/v1/runtime/pause` | Pause execution |
| `POST` | `/api/v1/runtime/resume` | Resume execution |
| `POST` | `/api/v1/runtime/reconfigure` | Hot-swap graph |
| `GET` | `/api/v1/runtime/snapshot` | Get full runtime snapshot |
| `GET` | `/api/v1/runtime/stream` | SSE stream for live updates |
| `GET` | `/api/v1/runtime/nodes/{id}/metrics` | Get specific node metrics |
| `GET` | `/api/v1/runtime/nodes/{id}/log` | Get node log stream |

### 12.2 SSE Stream Format

```json
// Server-Sent Events from /api/v1/runtime/stream

event: snapshot
data: { "timestampMs": 1779999999999, "nodes": {...}, "edges": {...} }

event: node-metrics
data: { "nodeId": "n3", "latencyMicros": 12.5, "eventsPerSec": 1420, "queueDepth": 0 }

event: node-event
data: { "nodeId": "n2", "eventType": "CandleClosed", "eventId": "abc-123", "timestampMs": 1779999999999 }

event: edge-event
data: { "edgeId": "e2", "sourceNode": "n2", "targetNode": "n3", "eventType": "CandleClosed", "eventId": "abc-123" }

event: node-state-change
data: { "nodeId": "n4", "oldState": "running", "newState": "error", "error": "VWAP window not yet populated" }

event: graph-metrics
data: { "totalEventsProcessed": 148392, "droppedEvents": 0, "ringBufferUtilizationPct": 12.3 }
```

---

## 13. Replay/Backtest/Paper/Live Unification

### 13.1 Single Graph, Four Modes

```
            ┌────────────────────────────────────────────────┐
            │              Graph Definition                    │
            │  (Same JSON, same nodes, same edges)             │
            └────────────────────────────────────────────────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
            ▼              ▼              ▼
    ┌──────────────┐ ┌──────────┐ ┌──────────────┐
    │   LIVE       │ │  REPLAY  │ │  BACKTEST    │  PAPER
    │              │ │          │ │              │
    │ Ingress:     │ │ Ingress: │ │ Ingress:     │
    │ WS_FEED      │ │REPLAY_FE │ │HISTORICAL_F  │
    │              │ │         │ │              │
    │ OMS:         │ │ OMS:     │ │ OMS:         │
    │ DHAN_ORDER   │ │ REJECTED │ │ SIM_MATCHER  │
    │              │ │(no exec) │ │              │
    │ Output:      │ │ Output:  │ │ Output:      │
    │ WS_OUTPUT    │ │ WS_OUTPUT│ │ SIM_PNL      │
    │ PERSISTENCE  │ │          │ │              │
    └──────────────┘ └──────────┘ └──────────────┘
```

### 13.2 Runtime Mode Resolution

```java
/**
 * Maps a pipeline graph to the correct node instances based on runtime mode.
 * Same JSON graph, different concrete node implementations.
 */
public final class ModeResolver {

    public PipelineNode resolveIngressNode(NodeType type, RuntimeMode mode) {
        return switch (mode) {
            case LIVE ->     NodeFactory.create(type);           // WS_FEED → Dhan WS
            case REPLAY ->   NodeFactory.create(NodeType.REPLAY_FEED);  // Chronicle Queue
            case BACKTEST -> NodeFactory.create(NodeType.HISTORICAL_FEED); // DuckDB
            case PAPER ->    NodeFactory.create(NodeType.WS_FEED);        // Live feed, sim exec
        };
    }

    public PipelineNode resolveOmsNode(RuntimeMode mode) {
        return switch (mode) {
            case LIVE ->     NodeFactory.create(NodeType.ORDER_PLACEMENT);   // Real broker
            case REPLAY ->   NodeFactory.create(NodeType.EVENT_FILTER);      // Drop orders
            case BACKTEST -> NodeFactory.create(NodeType.SIM_MATCHER);       // Simulation
            case PAPER ->    NodeFactory.create(NodeType.SIM_MATCHER);       // Simulation
        };
    }

    public PipelineNode resolveOutputNode(RuntimeMode mode) {
        return switch (mode) {
            case LIVE ->     NodeFactory.create(NodeType.PERSISTENCE_OUTPUT); // Chronicle + DuckDB
            case REPLAY ->   NodeFactory.create(NodeType.WS_OUTPUT);          // Visualize replay
            case BACKTEST -> NodeFactory.create(NodeType.SIM_PNL_OUTPUT);     // Track P&L
            case PAPER ->    NodeFactory.create(NodeType.PERSISTENCE_OUTPUT); // Track paper trades
        };
    }
}
```

### 13.3 Deterministic Replay for Backtest

```
Backtest execution model:

1. Load graph definition (same as live)
2. Compile to execution plan
3. Create backtest-specific node instances:
   - Ingress: HISTORICAL_FEED (DuckDB / CSV)
   - OMS: SIM_MATCHER (in-memory matching engine)
   - Position: POSITION_TRACKER (event-sourced)
   - Output: SIM_PNL_OUTPUT (P&L tracking)
4. Start execution:
   - Events flow through the same Disruptor ring buffer
   - Same candle aggregation, indicator computation
   - Same signal generation, risk checks
   - Orders go to SIM_MATCHER instead of broker
5. Snapshot state periodically for backtracking
6. Generate backtest report from SIM_PNL_OUTPUT
```

### 13.4 Virtual Time for Backtest

```java
/**
 * For replay/backtest, use virtual time instead of wall clock.
 * This ensures deterministic execution regardless of processing speed.
 */
public final class VirtualClock {

    private long currentTimeMs;

    public VirtualClock(long startTimeMs) {
        this.currentTimeMs = startTimeMs;
    }

    public long now() { return currentTimeMs; }

    public void advance(long deltaMs) { currentTimeMs += deltaMs; }

    public void set(long timestampMs) { currentTimeMs = timestampMs; }

    /**
     * Injectable clock for all nodes that need System.currentTimeMillis().
     */
    public static Clock asClock(VirtualClock virtual) {
        return new Clock() {
            @Override
            public long millis() { return virtual.now(); }
            @Override
            public Instant instant() { return Instant.ofEpochMilli(virtual.now()); }
            // ...
        };
    }
}
```

---

## 14. Runtime Observability Architecture

### 14.1 Per-Node Metrics

Each node collects:

```java
public record NodeMetrics(
        String nodeId,
        NodeType type,
        long eventsProcessed,
        long eventsDropped,
        double latencyMicros,        // EMA of processing time
        double maxLatencyMicros,
        long eventsPerSec,           // EMA of throughput
        int queueDepth,
        int queueRemainingCapacity,
        NodeState state,
        long lastEventTimestampMs,
        long upTimeMs
) {}
```

### 14.2 Graph-Level Metrics

```java
public record GraphMetrics(
        long totalEventsProcessed,
        long totalEventsDropped,
        double endToEndLatencyMicros,   // From tick ingress to order egress
        Map<String, NodeMetrics> nodeMetrics,
        Map<String, EdgeMetrics> edgeMetrics,
        int shardCount,
        long[] shardUtilization,         // Per-shard ring buffer usage
        int activeNodeCount,
        int enabledNodeCount
) {}

public record EdgeMetrics(
        String edgeId,
        long eventsPassed,
        long eventsFiltered,
        double throughputPerSec,
        double avgLagMicros             // Time from source emit to target process
) {}
```

### 14.3 Event Tracing

```java
/**
 * Event tracing that records every hop an event takes through the graph.
 * Sampling rate is configurable (default: 1% of events).
 */
public final class EventTracer {

    private final double samplingRate;
    private final Map<String, EventTrace> traces = new ConcurrentHashMap<>();

    public void recordHop(DomainEvent event, String nodeId, long enteredAtNanos, long exitedAtNanos) {
        if (!shouldSample(event)) return;

        EventTrace trace = traces.computeIfAbsent(event.eventId(), id ->
                new EventTrace(id, event.correlationId(), null, System.currentTimeMillis(), new ArrayList<>()));

        trace.hops().add(new EventTrace.NodeHop(nodeId, nodeTypeOf(nodeId), enteredAtNanos, exitedAtNanos));
    }

    public EventTrace getTrace(String eventId) {
        return traces.get(eventId);
    }

    public void pruneOlderThan(long ageMs) {
        long cutoff = System.currentTimeMillis() - ageMs;
        traces.entrySet().removeIf(entry -> entry.getValue().createdAtEpochMs() < cutoff);
    }
}
```

### 14.4 Admin Controller Extensions (Existing + New)

Refactor the existing `AdminController` to expose pipeline runtime data:

```java
@RestController
@RequestMapping("/api/v1/runtime")
public class RuntimeController {

    private final GraphRuntime runtime;
    private final GraphRepository repository;
    private final EventTracer tracer;

    @GetMapping("/snapshot")
    ResponseEntity<GraphSnapshot> snapshot() {
        return ResponseEntity.ok(runtime.snapshot());
    }

    @GetMapping("/stream")
    SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(60_000L);
        // Subscribe to runtime events, forward as SSE
        runtime.onMetricsUpdate(metrics -> {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(runtime.snapshot()));
        });
        return emitter;
    }

    @GetMapping("/nodes/{id}/trace")
    ResponseEntity<List<EventTrace>> nodeTraces(@PathVariable String id) {
        return ResponseEntity.ok(tracer.tracesForNode(id));
    }

    @GetMapping("/graphs/{id}/compile")
    ResponseEntity<ExecutionPlan> compile(@PathVariable String id) {
        PipelineGraph graph = repository.load(id).orElseThrow();
        ExecutionPlan plan = new GraphCompiler().compile(graph);
        return ResponseEntity.ok(plan);
    }
}
```

---

## 15. Migration Roadmap

### 15.1 Phase 0: Foundation Fixes (2 weeks)

Before any graph work, fix the known bugs from CODE_LEVEL_REVIEW.md:

| Task | Effort | Dependencies |
|------|--------|--------------|
| Fix N-01: multi-trade position tracking | 1 hour | None |
| Fix E-01: execution queue capacity | 30 min | None |
| Fix C-03: dedup cache → LRU | 2 hours | None |
| Fix C-05: stop sequence ordering | 1 hour | None |
| Fix A-01: 100ms poll → take() | 1 hour | None |
| Fix RP-01: Chronicle Queue type discrimination | 4 hours | None |
| Fix ST-02: subscribe to specific event types | 2 hours | None |

**Exit criteria:**
- All 🔴 high-severity bugs fixed
- All 🟡 medium-severity bugs fixed

### 15.2 Phase 1: Graph Runtime Core (4–6 weeks)

| Task | Effort | Dependencies |
|------|--------|--------------|
| Design `PipelineNode` interface + `NodeType` enum | 2 days | Phase 0 |
| Implement `NodeFactory` | 2 days | Phase 1a |
| Implement `GraphCompiler` (validation, sort, topo) | 1 week | Phase 1a |
| Implement `GraphRuntime` (start/stop/pause/reconfigure) | 2 weeks | Phase 1b |
| Implement `EventRouter` (edge wiring, event filtering) | 1 week | Phase 1b |
| Implement `GraphRepository` (Chronicle-backed persistence) | 1 week | Phase 1b |
| Implement `ShardAwareRuntime` (symbol partitioning) | 3 days | Phase 1b |
| Implement `ModeResolver` (live/replay/backtest/paper) | 3 days | Phase 1b |
| Implement `NodeMetrics` + `GraphSnapshot` | 3 days | Phase 1b |

**Exit criteria:**
- Can define a graph as JSON, compile it, and execute it
- Graph supports start/stop/pause/resume
- Snapshots are inspectable

### 15.3 Phase 2: Node Implementation (4–6 weeks)

| Task | Effort | Dependencies |
|------|--------|--------------|
| Wrap CandleAggregationService → CandleAggregatorNode | 2 days | Phase 1 |
| Wrap MarketDataPipeline → WsFeedNode | 2 days | Phase 1 |
| Implement VwapCalculatorNode | 2 days | Phase 2a |
| Implement RsiCalculatorNode | 1 day | Phase 2a |
| Implement HalfTrendNode | 2 days | Phase 2a |
| Implement SuperTrendNode | 1 day | Phase 2a |
| Wrap StrategySandbox → SignalGeneratorNode | 3 days | Phase 1 |
| Wrap PositionRiskHandler → ExposureCheckNode, DailyLossCheckNode | 3 days | Phase 1 |
| Wrap TradingCircuitBreaker → CircuitBreakerNode | 1 day | Phase 1 |
| Wrap ExecutionHandler → OrderPlacementNode | 3 days | Phase 1 |
| Wrap EventSourcedNetPositionProvider → PositionTrackerNode | 2 days | Phase 1 |
| Implement TrailingStopNode | 2 days | Phase 2i |
| Implement TakeProfitNode | 1 day | Phase 2i |
| Rebuild ScanEngine as reactive ScannerNode | 2 weeks | Phase 1 |
| Implement DepthAnalyzerNode | 3 days | Phase 2a |
| Implement LiquidityScannerNode | 3 days | Phase 2a |

**Exit criteria:**
- All node types from Section 4.4 implemented
- Existing strategies work as graph nodes
- Scanner works reactively (per-tick, not batch)

### 15.4 Phase 3: Reactor Integration (2–3 weeks)

| Task | Effort | Dependencies |
|------|--------|--------------|
| Implement ReactorBridge | 3 days | Phase 2 |
| Convert PersistenceOutputNode to Reactor | 2 days | Phase 3a |
| Convert WsOutputNode to Reactor | 2 days | Phase 3a |
| Convert OrderPlacementNode broker calls to Reactor | 3 days | Phase 3a |
| Configure Scheduler boundaries | 1 day | Phase 3a |
| Performance test: Disruptor vs Reactor hot path | 2 days | Phase 3a |

**Exit criteria:**
- All IO-bound nodes run on Reactor schedulers
- Disruptor hot path is unaffected
- Backpressure works correctly

### 15.5 Phase 4: React Flow Frontend (6–8 weeks)

| Task | Effort | Dependencies |
|------|--------|--------------|
| Set up React + Vite + React Flow in `trade-app/frontend/` | 1 day | Phase 0 |
| Implement PipelineCanvas (React Flow wrapper) | 1 week | Phase 4a |
| Implement NodePalette (drag from sidebar) | 3 days | Phase 4a |
| Implement custom node renderers (40+ types) | 2 weeks | Phase 4a |
| Implement animated edges | 3 days | Phase 4a |
| Implement NodeInspector config panel | 1 week | Phase 4a |
| Implement Zustand stores (graph, runtime, node library) | 1 week | Phase 4a |
| Implement API client | 2 days | Phase 4b |
| Implement RuntimeViewer (live metrics) | 1 week | Phase 4b |
| Implement TemplateLibrary | 3 days | Phase 4c |
| Implement ScannerBuilder | 1 week | Phase 4c |
| Implement StrategyBuilder | 1 week | Phase 4c |
| Implement Dashboard | 1 week | Phase 4d |
| Implement SSE stream for live updates | 3 days | Phase 4d |
| End-to-end testing | 1 week | Phase 4d |

**Exit criteria:**
- Full pipeline editor working (add/remove/connect nodes)
- Runtime viewer shows live metrics
- Strategy builder works end-to-end

### 15.6 Phase 5: Replay/Backtest/Paper Parity (4–6 weeks)

| Task | Effort | Dependencies |
|------|--------|--------------|
| Implement VirtualClock | 3 days | Phase 2 |
| Add Clock injection to all time-dependent nodes | 1 week | Phase 5a |
| Implement Snapshotable interface + state serialization | 1 week | Phase 2 |
| Add snapshot/restore for all ShardAware nodes | 1 week | Phase 5b |
| Implement replay mode (REPLAY_FEED node) | 3 days | Phase 5b |
| Implement backtest mode (SIM_MATCHER node) | 1 week | Phase 5b |
| Implement paper trading mode | 3 days | Phase 5b |
| Implement backtest report generator | 1 week | Phase 5b |
| Snapshot/restore live/replay switch | 1 week | Phase 5c |
| Test: replay of 5 days of NIFTY data | 3 days | Phase 5d |

**Exit criteria:**
- Same graph runs identically in all 4 modes
- Backtest P&L matches replay P&L (deterministic)
- Live ↔ Replay switch works without state corruption

### 15.7 Phase 6: Production Hardening (ongoing)

| Task | Effort | Dependencies |
|------|--------|--------------|
| GC tuning (reduce per-tick allocation) | 1 week | Phase 2 |
| Gradle build for frontend + Spring Boot | 2 days | Phase 4 |
| Integration tests for graph runtime | 2 weeks | Phase 5 |
| Load test: 500 symbols, 5000 ticks/sec | 1 week | Phase 5 |
| Documentation | 1 week | Phase 5 |
| Performance profiling and tuning | 2 weeks | Phase 5 |

---

## 16. Production Risks & Mitigations

### 16.1 Risk Matrix

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Re-entrant ring buffer deadlock** via graph reconfiguration | Low | Critical | Keep `downstreamQueue` + drainer pattern; reconfigure only from control thread |
| **Reactor scheduler starvation** blocking Disruptor consumer | Medium | High | Isolate thread pools; Reactor schedulers never share threads with Disruptor |
| **Memory leak from snapshot storage** | Medium | Medium | Bound snapshot history to N versions; use Chronicle for persistence |
| **Event filter mismatch** — edge filters events that don't exist | Low | Medium | Validate event types at graph compile time |
| **Cycle in graph** | Low | Critical | Cycle detection in `GraphCompiler` (DAG validation) |
| **Node failure propagation** | Low | High | Each node has independent try/catch; failures go to dead letter queue |
| **Snapshot/restore corruption** during replay switch | Low | High | Checkpoint validation after restore |
| **React Flow performance** with 1000+ nodes | Medium | Medium | Virtualization + viewport culling built into React Flow |
| **Race condition** between graph reconfigure and event dispatch | Low | Critical | Reconfiguration is gated by event processing pause |
| **Backward compatibility** with existing pipeline | Low | High | Phase 0-1 preserves existing `DisruptorEventBus` as-is |

### 16.2 Anti-Patterns to Avoid

| Anti-Pattern | Why | What Instead |
|-------------|-----|--------------|
| **Making every node a Reactor Flux** | Adds overhead for simple operations | Use Disruptor for hot path, Reactor only for IO |
| **Global state in nodes** | Breaks replay determinism | Event-sourced state only |
| **Asynchronous edges** (e.g., Kafka between nodes) | Adds latency + complexity | In-process ring buffer for all hot-path edges |
| **Dynamic graph without pause** | Race conditions | Pause → reconfigure → resume |
| **Generic node parameters** | Configuration nightmare | Schema-validated config per node type |
| **Monolithic node** that does too much | Defeats graph composition | Single responsibility per node |
| **Reactor on Disruptor consumer thread** | Blocks ring buffer | All blocking I/O off the consumer thread |

---

## 17. Distributed Future Scaling

### 17.1 Current Limitations (Single Process)

The current architecture is **single-process**. All nodes run in the same JVM. This is the right design for today:

- Bounded by a single ring buffer of 8192 slots
- Throughput limited to ~100K events/sec (Disruptor theoretical max: millions/sec)
- Memory bound by JVM heap (~4–8 GB)

### 17.2 Future: Multi-Process Distribution

When the system needs to scale beyond a single process:

```
Node A (Process 1)               Node B (Process 2)
┌─────────────────────┐          ┌─────────────────────┐
│  WS Feed → Candle   │  Aeron   │  Strategy Engine     │
│  → VWAP → Signal    │ ───────▶ │  → Risk → Position   │
│                     │  UDP     │                     │
│  Disruptor Ring     │          │  Disruptor Ring      │
│  Buffer (hot path)  │          │  Buffer (hot path)   │
└─────────────────────┘          └─────────────────────┘
```

**Key design decisions for distributed future:**

1. **Graph JSON is the distribution contract** — The same graph definition can be partitioned across processes
2. **Aeron for inter-process transport** — Sub-microsecond latency, lossless UDP
3. **Symbol partitioning across processes** — Each process owns a shard of symbols
4. **Chronicle Queue for state persistence** — Each process writes to its own queue
5. **Graph is the unit of deployment** — Deploy a sub-graph to a process
6. **Cluster manager** — Coordinates which processes run which graph partitions

### 17.3 Distributed Graph Partitioning

```
Graph partition example for multi-process deployment:

Process 1 (Market Data):          Process 2 (Index Strategies):
  WS_FEED (NIFTY, BANKNIFTY)       CANDLE_AGG → VWAP → SIGNAL → RISK → ORDER

Process 3 (Stock Strategies):     Process 4 (Options Strategies):
  CANDLE_AGG → MOMENTUM → SIGNAL    CANDLE_AGG → GEX → SIGNAL → RISK → ORDER

Process 5 (OMS):
  ORDER_PLACEMENT (all symbols)
```

The graph runtime handles partitioning via the `SymbolShardRouter` — same algorithm, just different processes owning different shards.

---

## 18. Implementation Phases

### Summary Timeline

| Phase | Duration | Total | Key Deliverable |
|-------|----------|-------|-----------------|
| **Phase 0:** Foundation Fixes | 2 weeks | 2 weeks | Known bugs fixed |
| **Phase 1:** Graph Runtime Core | 6 weeks | 8 weeks | `GraphCompiler` + `GraphRuntime` |
| **Phase 2:** Node Implementation | 6 weeks | 14 weeks | All node types implemented |
| **Phase 3:** Reactor Integration | 3 weeks | 17 weeks | Hybrid Disruptor/Reactor hot/cold path |
| **Phase 4:** React Flow Frontend | 8 weeks | 25 weeks | Visual pipeline editor |
| **Phase 5:** Replay/Backtest Parity | 6 weeks | 31 weeks | Uniform graph execution across all modes |
| **Phase 6:** Production Hardening | Ongoing | — | Performance, tests, docs |

### MVP vs Institutional-Grade

| Feature | MVP (Phase 0–4) | Institutional (Phase 5–6) |
|---------|-----------------|--------------------------|
| Graph compilation | ✅ Basic: JSON → execution | ✅ With optimization passes |
| Node library | ✅ 20 core nodes | ✅ 50+ nodes including custom plugins |
| React Flow builder | ✅ Create/edit/save graphs | ✅ Version history, template sharing |
| Runtime graph viewer | ✅ Live metrics per node | ✅ Event tracing, heat maps, timeline |
| Dynamic reconfiguration | ✅ Add/remove nodes at runtime | ✅ Zero-downtime reconfiguration |
| Replay | ✅ Chronicle Queue replay | ✅ State snapshot/restore, any time range |
| Backtest | ✅ Basic matching engine | ✅ Slippage model, partial fills, multiple scenarios |
| Paper trading | ✅ Same graph, sim execution | ✅ Full audit trail, P&L attribution |
| Real-time metrics | ✅ Per-node latency/throughput | ✅ End-to-end tracing, percentile distributions |
| Distributed support | — | ✅ Aeron-based multi-process |
| Distributed cluster | — | ✅ Partitioned graph across processes |

---

## Appendix A: Existing Code Integration Map

| Existing File | Maps To | Changes Required |
|--------------|---------|------------------|
| `DisruptorEventBus.java` | Graph runtime backbone | Add `PipelineStage<T>` abstraction for node registration |
| `ShardedDisruptorEventBus.java` | Shard-aware graph runtime | Already correct, add `ShardAwareNode` support |
| `MarketDataPipeline.java` | WsFeedNode | Wrap as `PipelineNode`; keep existing token bucket logic |
| `OrderPipeline.java` | (dead code) | Remove dead paths; wrap remaining as OrderRoutingNode |
| `CandleAggregationService.java` | CandleAggregatorNode | Add `Snapshotable` for replay recovery |
| `StrategySandbox.java` | SignalGeneratorNode | Add virtual thread timeout cancellation |
| `PositionRiskHandler.java` | ExposureCheckNode, DailyLossCheckNode | Add `shardAware` support |
| `PortfolioEngine.java` | PortfolioCheckNode | Already `ConcurrentHashMap`-based, correct |
| `ExecutionHandler.java` | OrderPlacementNode | Fix E-01 (queue capacity) first |
| `TradingCircuitBreaker.java` | CircuitBreakerNode | Refactor to CAS-based (lock-free) |
| `EventSourcedNetPositionProvider.java` | PositionTrackerNode | Fix N-01 first |
| `OrderIdentityRegistry.java` | OrderIdentityNode | Add eviction (I-01) |
| `OrderStateMachine.java` | (internal to OMS node) | Already correct |
| `StageTimings.java` | Per-node metrics | Already the right abstraction |
| `AsyncDispatchHandler.java` | Cold-path dispatch | Add to ReactorBridge |
| `ReplayRunner.java` | ReplayFeedNode | Fix RP-01 (type discrimination) |
| `HistoricalRangeService.java` | HistoricalFeedNode | Wrap as `PipelineNode` |
| `ScanEngine.java` | ScannerNode | Rewrite as reactive per-tick |
| `MatchingEngine.java` | SimMatcherNode | Add slippage model |
| `PnLLedger.java` | SimPnlOutputNode | Add incremental P&L update |
| `GatewayEventBridge.java` | WsOutputNode | Add symbol-level filtering |
| `DuckDbFeatureStore.java` | PersistenceOutputNode | Fix FS-01 (connection validation) |
| `InMemoryFeatureStore.java` | FeatureStoreNode | Add TTL eviction |

## Appendix B: NPM Dependencies for Frontend

```json
{
  "dependencies": {
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "reactflow": "^11.11.0",
    "zustand": "^5.0.0",
    "lightweight-charts": "^4.2.2"
  },
  "devDependencies": {
    "@types/react": "^19.0.0",
    "@types/react-dom": "^19.0.0",
    "typescript": "^5.7.0",
    "vite": "^6.2.0",
    "@vitejs/plugin-react": "^4.3.0",
    "tailwindcss": "^4.0.0",
    "@tailwindcss/vite": "^4.0.0"
  }
}
```

## Appendix C: Key Java Package Structure

```
com.tradej.pipeline
├── graph/
│   ├── PipelineGraph.java          (record: id, nodes, edges, metadata)
│   ├── PipelineNode.java           (record: id, type, config, ports, uiMetadata)
│   ├── PipelineEdge.java           (record: id, source, target, ports, eventFilter)
│   ├── NodeType.java               (enum: WS_FEED, CANDLE_AGGREGATOR, ...)
│   └── NodeConfig.java             (Map<String, Object> with schema validation)
│
├── runtime/
│   ├── GraphRuntime.java           (main execution engine)
│   ├── GraphCompiler.java          (validate, topo-sort, optimize, plan)
│   ├── ExecutionPlan.java          (sealed interface: TopologicalOrder, ShardedPlan)
│   ├── EventRouter.java            (wire edges between nodes)
│   ├── ModeResolver.java           (live/replay/backtest/paper resolution)
│   ├── GraphDiffer.java            (diff current vs new graph for hot-reload)
│   └── GraphSnapshot.java          (immutable point-in-time state)
│
├── node/
│   ├── PipelineNode.java           (interface: onEvent, onStart, onStop, metrics)
│   ├── NodeFactory.java            (NodeType → implementation)
│   ├── NodeMetrics.java            (record: latency, throughput, queue depth)
│   ├── NodeState.java              (enum: CREATED, RUNNING, PAUSED, ERROR)
│   ├── EventContext.java           (context for event processing)
│   ├── ShardAwareNode.java         (interface for per-symbol state nodes)
│   └── Snapshotable.java           (interface for state persistence)
│
├── persistence/
│   └── GraphRepository.java        (Chronicle-backed graph CRUD + versioning)
│
├── reactor/
│   └── ReactorBridge.java          (Disruptor ↔ Reactor bridge)
│
├── clock/
│   └── VirtualClock.java           (deterministic time for backtest/replay)
│
├── observer/
│   ├── EventTracer.java            (per-event hop tracking)
│   └── GraphMetrics.java           (aggregate graph-level metrics)
│
└── api/
    ├── PipelineController.java     (REST: /api/v1/pipelines/*)
    └── RuntimeController.java      (REST + SSE: /api/v1/runtime/*)
```

---

## Appendix D: React Flow Frontend Directory Structure

```
trade-app/frontend/
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
└── src/
    ├── main.tsx                     (React entry point)
    ├── App.tsx                      (Root component with routing)
    ├── style.css                    (Global styles + dark theme)
    │
    ├── pages/
    │   ├── Dashboard.tsx            (/)
    │   ├── PipelineEditor.tsx       (/pipeline, /pipeline/:id)
    │   ├── ScannerBuilder.tsx       (/scanner)
    │   ├── StrategyBuilder.tsx      (/strategy)
    │   ├── RuntimeViewer.tsx        (/runtime, /runtime/:graphId)
    │   ├── TemplateLibrary.tsx      (/templates)
    │   └── SystemMonitor.tsx        (/monitor)
    │
    ├── components/
    │   ├── pipeline/
    │   │   ├── PipelineCanvas.tsx       (React Flow wrapper)
    │   │   ├── NodePalette.tsx          (Draggable sidebar)
    │   │   ├── NodeInspector.tsx        (Config panel)
    │   │   ├── TemplateLibrary.tsx      (Template selector)
    │   │   └── GraphVersionHistory.tsx  (Version timeline)
    │   │
    │   ├── nodes/
    │   │   ├── WsFeedNode.tsx
    │   │   ├── CandleAggNode.tsx
    │   │   ├── VwapNode.tsx
    │   │   ├── RsiNode.tsx
    │   │   ├── SignalNode.tsx
    │   │   ├── RiskNode.tsx
    │   │   ├── OrderNode.tsx
    │   │   ├── PositionNode.tsx
    │   │   ├── OutputNode.tsx
    │   │   └── index.ts                 (nodeTypes map)
    │   │
    │   ├── edges/
    │   │   └── PipelineEdge.tsx         (Animated edge)
    │   │
    │   └── shared/
    │       ├── StatusBar.tsx
    │       ├── MetricBar.tsx
    │       ├── NodeIcon.tsx
    │       ├── NodeStateIndicator.tsx
    │       └── ConfigField.tsx          (Dynamic config form renderer)
    │
    ├── stores/
    │   ├── graphStore.ts                (Zustand: graph CRUD, editing)
    │   ├── runtimeStore.ts              (Zustand: runtime snapshot, SSE)
    │   └── nodeLibraryStore.ts          (Zustand: available node types)
    │
    ├── services/
    │   ├── pipelineApi.ts               (API client)
    │   └── sseClient.ts                 (SSE event stream client)
    │
    └── types/
        ├── graph.ts                     (PipelineGraph, PipelineNode, PipelineEdge)
        ├── runtime.ts                   (GraphSnapshot, NodeMetrics)
        └── nodeTypes.ts                 (NodeType enum for frontend)
```

---

*End of Architecture Evolution Plan. See [CODE_LEVEL_REVIEW.md](CODE_LEVEL_REVIEW.md) for the baseline code review that informed this document.*
