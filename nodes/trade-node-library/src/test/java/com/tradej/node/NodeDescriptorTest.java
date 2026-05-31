package com.tradej.node;

import com.tradej.node.PortDescriptor;
import com.tradej.pipeline.platform.model.PropertyDescriptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class NodeDescriptorTest {

    @Test
    void descriptorCreatesCorrectly() {
        NodeDescriptor descriptor = new NodeDescriptor(
                "test-node",
                "Test Node",
                "A test node description",
                NodeCategory.SCANNER,
                List.of(PropertyDescriptor.of("param1", PropertyDescriptor.PropertyType.STRING, "Param 1")),
                List.of(new PortDescriptor("input", "input port", PortDescriptor.PortType.SINGLE, true, List.of("String"), Map.of())),
                List.of(new PortDescriptor("output", "output port", PortDescriptor.PortType.MULTI, false, List.of("Integer"), Map.of())),
                Map.of("version", "1.0")
        );

        assertEquals("test-node", descriptor.nodeType());
        assertEquals("Test Node", descriptor.displayName());
        assertEquals("A test node description", descriptor.description());
        assertEquals(NodeCategory.SCANNER, descriptor.category());
        assertEquals(1, descriptor.properties().size());
        assertEquals(1, descriptor.inputPorts().size());
        assertEquals(1, descriptor.outputPorts().size());
        assertEquals("1.0", descriptor.metadata().get("version"));
    }

    @Test
    void propertyReturnsCorrectDescriptor() {
        PropertyDescriptor prop1 = PropertyDescriptor.of("param1", PropertyDescriptor.PropertyType.STRING, "Param 1");
        PropertyDescriptor prop2 = PropertyDescriptor.of("param2", PropertyDescriptor.PropertyType.INTEGER, "Param 2");

        NodeDescriptor descriptor = new NodeDescriptor(
                "test", "Test", "", NodeCategory.UTILITY,
                List.of(prop1, prop2),
                List.of(),
                List.of(),
                Map.of()
        );

        assertSame(prop1, descriptor.property("param1"));
        assertSame(prop2, descriptor.property("param2"));
        assertNull(descriptor.property("nonexistent"));
    }

    @Test
    void nullCollectionsAreDefensivelyCopied() {
        NodeDescriptor descriptor = new NodeDescriptor(
                "test", "Test", "", NodeCategory.FEATURE,
                null, null, null, null
        );

        assertNotNull(descriptor.properties());
        assertNotNull(descriptor.inputPorts());
        assertNotNull(descriptor.outputPorts());
        assertNotNull(descriptor.metadata());
        assertTrue(descriptor.properties().isEmpty());
    }

    @Test
    void requiresNonNullNodeType() {
        assertThrows(NullPointerException.class, () ->
                new NodeDescriptor(null, "Test", "", NodeCategory.UTILITY, List.of(), List.of(), List.of(), Map.of())
        );
    }
}