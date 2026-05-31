package com.tradej.pipeline.runtime;

/**
 * Canonical pipeline node type identifiers used by the visual builder and runtime factory.
 */
public final class PipelineNodeTypes {

    public static final String RISK = "Risk";
    public static final String CANDLE = "Candle";
    public static final String FEATURE = "Feature";
    public static final String STRATEGY = "Strategy";
    public static final String PORTFOLIO = "Portfolio";
    public static final String OMS = "OMS";
    public static final String REACTOR = "Reactor";
    public static final String INGRESS = "Ingress";
    public static final String SCAN = "Scan";

    // Decomposed execution nodes
    public static final String SIGNAL_GATE = "SignalGate";
    public static final String ORDER_PLACEMENT = "OrderPlacement";
    public static final String FILL_RECONCILIATION = "FillReconciliation";

    // Scanner streaming nodes
    public static final String SCAN_CRITERION = "ScanCriterion";
    public static final String SCAN_AGGREGATOR = "ScanAggregator";

    private PipelineNodeTypes() {
    }
}
