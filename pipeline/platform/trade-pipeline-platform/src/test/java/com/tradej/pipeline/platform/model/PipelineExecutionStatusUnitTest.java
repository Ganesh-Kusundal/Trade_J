package com.tradej.pipeline.platform.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class PipelineExecutionStatusUnitTest {

    @Test
    void hasAllExpectedStates() {
        assertEquals(6, PipelineExecutionStatus.values().length);
    }
}
