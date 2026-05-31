package com.tradej.node;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class NodeResultTest {

    @Test
    void okCreatesSuccessfulResult() {
        NodeResult result = NodeResult.ok("output", Map.of("key", "value"));

        assertTrue(result.success());
        assertEquals("output", result.outputPortName());
        assertEquals(Map.of("key", "value"), result.payload());
        assertNull(result.errorMessage());
        assertNotNull(result.completedAt());
    }

    @Test
    void failCreatesFailedResult() {
        NodeResult result = NodeResult.fail("Something went wrong");

        assertFalse(result.success());
        assertNull(result.outputPortName());
        assertNull(result.payload());
        assertEquals("Something went wrong", result.errorMessage());
        assertNotNull(result.completedAt());
    }

    @Test
    void okWithNullPayloadIsValid() {
        NodeResult result = NodeResult.ok("default", null);

        assertTrue(result.success());
        assertEquals("default", result.outputPortName());
        assertNull(result.payload());
    }
}