package com.tradej.pipeline.spi;

import com.tradej.pipeline.registry.NodeRegistry;

import java.util.Map;

/**
 * SPI for pluggable pipeline node types. Implementations are auto-discovered
 * via {@link java.util.ServiceLoader} using
 * {@code META-INF/services/com.tradej.pipeline.spi.PipelineNodeProvider}.
 * <p>
 * Each provider registers a stable {@link #typeId()} plus a metadata descriptor
 * (via {@link #registerMetadata(NodeRegistry)}) and a factory function
 * (via {@link #registerFactory(NodeRegistry, Map)}). Adding a new node type
 * requires only adding a {@code PipelineNodeProvider} implementation and
 * listing it in the META-INF/services file. No switch edit required.
 */
public interface PipelineNodeProvider {

    /** Stable identifier used in pipeline graph definitions. Must be unique. */
    String typeId();

    /** Human-readable display name. */
    default String displayName() { return typeId(); }

    /**
     * Register the metadata descriptor for this node type. The implementation
     * should call {@code registry.register(...)} with a {@code NodeTypeDescriptor}
     * carrying the typeId, category, input/output event types, and config fields.
     * <p>
     * If the provider has no metadata to register (factory-only type), this
     * method may be a no-op.
     */
    void registerMetadata(NodeRegistry registry);

    /**
     * Register the factory for this node type. The implementation should call
     * {@code registry.register(...)} with a {@code NodeTypeDescriptor} that
     * carries the real {@code Function<PipelineNodeDef, PipelineNode>} factory.
     * <p>
     * The {@code config} map is provided by the caller (typically the
     * {@code PipelineNodeFactory} which holds the wiring collaborators such as
     * the feature store, scan engine, etc.). Providers should read any
     * collaborators they need from this map (using the well-known keys below)
     * and ignore the rest. Missing keys should be tolerated: a provider that
     * cannot construct its node should register a no-op factory, matching the
     * behaviour of the legacy fallback path.
     */
    void registerFactory(NodeRegistry registry, Map<String, Object> config);

    // ---- Well-known config keys used by built-in providers ----
    String CONFIG_KEY_POSITION_RISK_HANDLER = "positionRiskHandler";
    String CONFIG_KEY_CANDLE_AGGREGATION_SERVICE = "candleAggregationService";
    String CONFIG_KEY_GRAPH_STRATEGY_SANDBOX = "graphStrategySandbox";
    String CONFIG_KEY_EXECUTION_HANDLER = "executionHandler";
    String CONFIG_KEY_PORTFOLIO_ENGINE = "portfolioEngine";
    String CONFIG_KEY_FEATURE_STORE = "hotPathFeatureStore";
    String CONFIG_KEY_REACTOR_BRIDGE = "reactorBridge";
    String CONFIG_KEY_SCAN_ENGINE = "scanEngine";
    String CONFIG_KEY_SCAN_PROFILES = "scanProfilesById";
    String CONFIG_KEY_SCAN_CRITERION_RESOLVER = "scanCriterionResolver";
}
