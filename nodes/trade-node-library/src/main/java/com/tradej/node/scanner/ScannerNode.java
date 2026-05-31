package com.tradej.node.scanner;

import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeExecutor;
import com.tradej.node.NodeResult;
import com.tradej.node.NodeCategory;
import com.tradej.node.PortDescriptor;

import java.util.List;
import java.util.Map;

/** Applies a named criterion over a bar or signal stream. */
public class ScannerNode implements NodeExecutor {

    public static final String NODE_TYPE = "scanner";

    @Override
    public NodeDescriptor descriptor() {
        return new NodeDescriptor(
                NODE_TYPE,
                "Scanner",
                "Evaluates criteria against a stream of candidate records",
                NodeCategory.SCANNER,
                List.of(),
                List.of(new PortDescriptor(
                        "candidates", "incoming candidates", PortDescriptor.PortType.MULTI, true,
                        List.of("ScanCandidate"), Map.of()
                )),
                List.of(new PortDescriptor(
                        "hits", "scanner hits that matched criteria", PortDescriptor.PortType.MULTI, true,
                        List.of("ScanCandidate"), Map.of()
                )),
                Map.of("family", "scanner")
        );
    }

    @Override
    public NodeResult execute(NodeContext context, Map<String, Object> config) {
        // Real implementation delegates to trading/scanner module criterion engine.
        return NodeResult.ok("hits", List.of());
    }
}
