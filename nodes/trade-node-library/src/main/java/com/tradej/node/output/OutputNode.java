package com.tradej.node.output;

import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeExecutor;
import com.tradej.node.NodeResult;
import com.tradej.node.NodeCategory;
import com.tradej.node.PortDescriptor;

import java.util.List;
import java.util.Map;

/** Terminal sink node that consumes pipeline artifacts. */
public class OutputNode implements NodeExecutor {

 public static final String NODE_TYPE = "output";

 @Override
 public NodeDescriptor descriptor() {
 return new NodeDescriptor(
 NODE_TYPE,
 "Output",
 "Writes pipeline output to the configured destination",
 NodeCategory.OUTPUT,
 List.of(),
 List.of(new PortDescriptor(
 "result", "any payload", PortDescriptor.PortType.SINGLE, true,
 List.of("*"), Map.of()
 )),
 List.of(),
 Map.of("family", "output")
 );
 }

 @Override
 public NodeResult execute(NodeContext context, Map<String, Object> config) {
 // Real implementation writes to persistence, analytics, or broker sink.
 return NodeResult.ok("result", null);
 }
}
