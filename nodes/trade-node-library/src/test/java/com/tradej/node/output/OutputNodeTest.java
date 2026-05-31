package com.tradej.node.output;

import com.tradej.node.NodeCategory;
import com.tradej.node.NodeContext;
import com.tradej.node.NodeDescriptor;
import com.tradej.node.NodeResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class OutputNodeTest {

    @Test
    void descriptorReturnsCorrectType() {
        OutputNode node = new OutputNode();
        NodeDescriptor descriptor = node.descriptor();

        assertEquals(OutputNode.NODE_TYPE, descriptor.nodeType());
        assertEquals("Output", descriptor.displayName());
        assertEquals(NodeCategory.OUTPUT, descriptor.category());
        assertEquals(1, descriptor.inputPorts().size());
        assertEquals(0, descriptor.outputPorts().size());
        assertEquals("result", descriptor.inputPorts().get(0).name());
    }

    @Test
    void executeReturnsSuccessfulResult() {
        OutputNode node = new OutputNode();
        NodeContext context = new NodeContext(UUID.randomUUID(), "test", Instant.now(), Map.of());

        NodeResult result = node.execute(context, Map.of("payload", "test-data"));

        assertTrue(result.success());
        assertEquals("result", result.outputPortName());
        assertNull(result.payload());
    }
}