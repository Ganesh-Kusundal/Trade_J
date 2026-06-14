package com.tradej.pipeline.spi.builtin;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ScanHitProduced;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.pipeline.graph.PipelineNodeDef;
import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.node.StreamingScanCriterionNode;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Built-in provider for the SCAN_CRITERION node type.
 * <p>
 * The criterion itself is resolved by a {@code Function<String, ScanCriterion>}
 * supplied in the wiring config under
 * {@link PipelineNodeProvider#CONFIG_KEY_SCAN_CRITERION_RESOLVER}. This
 * preserves the legacy {@code resolveCriterion} behaviour without touching
 * the private method on {@code PipelineNodeFactory}.
 */
public final class ScanCriterionNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.SCAN_CRITERION;
    }

    @Override
    public String displayName() {
        return "Scan Criterion";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "scanner",
                "Evaluates a single scan criterion against incoming events",
                List.of(
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Tick data (deprecated)"),
                        new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")
                ),
                List.of(new NodeTypeDescriptor.EventType(ScanHitProduced.class, "Matched hit")),
                Map.of(
                        "criterionType", NodeTypeDescriptor.ConfigField.of("criterionType",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Criterion Type", "volume-spike"),
                        "threshold", NodeTypeDescriptor.ConfigField.of("threshold",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Match Threshold", 2.0)
                )));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        @SuppressWarnings("unchecked")
        Function<String, ScanCriterion> resolver = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_SCAN_CRITERION_RESOLVER, Function.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "scanner",
                "Evaluates a single scan criterion against incoming events",
                List.of(
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Canonical tick"),
                        new NodeTypeDescriptor.EventType(MarketTickEvent.class, "Tick data (deprecated)"),
                        new NodeTypeDescriptor.EventType(CandleClosed.class, "Completed candle")
                ),
                List.of(new NodeTypeDescriptor.EventType(ScanHitProduced.class, "Matched hit")),
                Map.of(
                        "criterionType", NodeTypeDescriptor.ConfigField.of("criterionType",
                                NodeTypeDescriptor.ConfigField.FieldType.STRING,
                                "Criterion Type", "volume-spike"),
                        "threshold", NodeTypeDescriptor.ConfigField.of("threshold",
                                NodeTypeDescriptor.ConfigField.FieldType.NUMBER,
                                "Match Threshold", 2.0)
                ),
                def -> {
                    String criterionType = stringConfig(def, "criterionType", "volume-spike");
                    ScanCriterion criterion = resolver.apply(criterionType);
                    return new StreamingScanCriterionNode(
                            criterion,
                            dummyAsset(def),
                            stringConfig(def, "profileId", "default"));
                }));
    }

    private static String stringConfig(PipelineNodeDef def, String key, String defaultValue) {
        if (def.config() == null) return defaultValue;
        Object v = def.config().get(key);
        return v instanceof String s && !s.isBlank() ? s : defaultValue;
    }

    private static ScanAsset dummyAsset(PipelineNodeDef def) {
        String symbol = stringConfig(def, "symbol", "");
        Instrument instrument = new Instrument(
                symbol, symbol,
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                "EQUITY", symbol, null, 0L, null, 1, 0);
        return new ScanAsset(instrument, AssetClass.EQUITY, symbol);
    }
}
