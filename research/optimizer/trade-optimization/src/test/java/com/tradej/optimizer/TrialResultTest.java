package com.tradej.optimizer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TrialResultTest {

    @Test
    void successfulTrialResult() {
        UUID trialId = UUID.randomUUID();
        Instant completed = Instant.now();

        TrialResult result = new TrialResult(
                trialId,
                1,
                Map.of("lr", 0.01, "period", 20),
                BigDecimal.valueOf(1.85),
                true,
                null,
                completed
        );

        assertEquals(trialId, result.trialId());
        assertEquals(1, result.trialNumber());
        assertEquals(0.01, result.parameterValues().get("lr"));
        assertEquals(BigDecimal.valueOf(1.85), result.objectiveValue());
        assertTrue(result.success());
        assertNull(result.errorMessage());
        assertEquals(completed, result.completedAt());
    }

    @Test
    void failedTrialResult() {
        UUID trialId = UUID.randomUUID();
        Instant completed = Instant.now();

        TrialResult result = new TrialResult(
                trialId,
                2,
                Map.of("lr", 0.001),
                null,
                false,
                "Timeout exceeded",
                completed
        );

        assertFalse(result.success());
        assertEquals("Timeout exceeded", result.errorMessage());
        assertNull(result.objectiveValue());
    }
}