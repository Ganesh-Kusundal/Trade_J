package com.tradej.node;

/** Implemented by every executable node in the runtime. */
public interface NodeExecutor {

    NodeDescriptor descriptor();

    NodeResult execute(NodeContext context, java.util.Map<String, Object> config);
}
