package com.tradej.pipeline.spi.builtin;

import com.tradej.pipeline.registry.NodeRegistry;
import com.tradej.pipeline.registry.NodeTypeDescriptor;
import com.tradej.pipeline.runtime.PipelineNode;
import com.tradej.pipeline.runtime.PipelineNodeTypes;
import com.tradej.pipeline.spi.PipelineNodeProvider;
import com.tradej.strategy.node.PortfolioNode;
import com.tradej.strategy.portfolio.PortfolioEngine;

import java.util.List;
import java.util.Map;

/**
 * Built-in provider for the PORTFOLIO node type.
 * <p>
 * Note: PORTFOLIO was registered in {@code registerNodeFactories} but not
 * in the metadata-only {@code nodeRegistry()}. A stub descriptor is
 * registered for symmetry.
 */
public final class PortfolioNodeProvider implements PipelineNodeProvider {

    @Override
    public String typeId() {
        return PipelineNodeTypes.PORTFOLIO;
    }

    @Override
    public String displayName() {
        return "Portfolio";
    }

    @Override
    public void registerMetadata(NodeRegistry registry) {
        registry.register(NodeProviderSupport.descriptorWith(
                typeId(),
                displayName(),
                "portfolio",
                "Portfolio-level risk and sizing",
                List.of(),
                List.of(),
                Map.of()));
    }

    @Override
    public void registerFactory(NodeRegistry registry, Map<String, Object> config) {
        PortfolioEngine engine = NodeProviderSupport.typed(
                config, PipelineNodeProvider.CONFIG_KEY_PORTFOLIO_ENGINE, PortfolioEngine.class);
        registry.register(new NodeTypeDescriptor(
                typeId(),
                displayName(),
                "portfolio",
                "Portfolio-level risk and sizing",
                List.of(),
                List.of(),
                Map.of(),
                def -> new PortfolioNode(engine)));
    }
}
