package com.tradej.node.feature;

import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeExecutor;
import com.tradej.node.NodeResult;
import com.tradej.node.NodeCategory;
import com.tradej.node.PortDescriptor;

import java.util.List;
import java.util.Map;

/** Abstract base for indicator-style feature nodes. */
public abstract class FeatureNode implements NodeExecutor {

    @Override
    public NodeDescriptor descriptor() {
        return new NodeDescriptor(
                nodeType(),
                displayName(),
                description(),
                NodeCategory.FEATURE,
                List.of(),
                List.of(new PortDescriptor(
                        "bars", "input bars", PortDescriptor.PortType.SINGLE, true,
                        List.of("MarketBar"), Map.of()
                )),
                List.of(new PortDescriptor(
                        "features", "computed feature series", PortDescriptor.PortType.SINGLE, true,
                        List.of("FeatureSeries"), Map.of()
                )),
                Map.of("family", "feature")
        );
    }

    protected abstract String nodeType();
    protected abstract String displayName();
    protected abstract String description();
}
