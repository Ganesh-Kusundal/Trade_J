package com.tradej.node.scanner;

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
class ScannerNodeTest {

    @Test
    void descriptorReturnsCorrectType() {
        ScannerNode node = new ScannerNode();
        NodeDescriptor descriptor = node.descriptor();

        assertEquals(ScannerNode.NODE_TYPE, descriptor.nodeType());
        assertEquals("Scanner", descriptor.displayName());
        assertEquals(NodeCategory.SCANNER, descriptor.category());
        assertTrue(descriptor.properties().isEmpty());
        assertEquals(1, descriptor.inputPorts().size());
        assertEquals(1, descriptor.outputPorts().size());
        assertEquals("candidates", descriptor.inputPorts().get(0).name());
        assertEquals("hits", descriptor.outputPorts().get(0).name());
    }

    @Test
    void executeReturnsSuccessfulResult() {
        ScannerNode node = new ScannerNode();
        NodeContext context = new NodeContext(UUID.randomUUID(), "test", Instant.now(), Map.of());

        NodeResult result = node.execute(context, Map.of());

        assertTrue(result.success());
        assertEquals("hits", result.outputPortName());
        assertNotNull(result.completedAt());
    }

    @Test
    void executeHandlesNullConfig() {
        ScannerNode node = new ScannerNode();
        NodeContext context = new NodeContext(UUID.randomUUID(), "test", Instant.now(), Map.of());

        NodeResult result = node.execute(context, null);

        assertTrue(result.success());
    }
}