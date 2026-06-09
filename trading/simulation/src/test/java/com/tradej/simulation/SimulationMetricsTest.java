package com.tradej.simulation;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SimulationMetricsTest {

    @Test
    void initialStateIsZero() {
        var metrics = new SimulationMetrics();
        assertEquals(0, metrics.ordersMatched());
        assertEquals(0, metrics.fillsGenerated());
        assertEquals(0, metrics.ordersRejected());
        assertEquals(0, metrics.simulationsRun());
        assertEquals(1.0, metrics.fillRate());
    }

    @Test
    void recordOperationsAccumulateCorrectly() {
        var metrics = new SimulationMetrics();
        metrics.recordOrderMatched();
        metrics.recordOrderMatched();
        metrics.recordFill();
        metrics.recordFill();
        metrics.recordFill();
        metrics.recordRejection();

        assertEquals(2, metrics.ordersMatched());
        assertEquals(3, metrics.fillsGenerated());
        assertEquals(1, metrics.ordersRejected());
    }

    @Test
    void fillRateCalculatesCorrectly() {
        var metrics = new SimulationMetrics();
        metrics.recordOrderMatched();
        metrics.recordOrderMatched();
        metrics.recordOrderMatched();
        metrics.recordRejection();
        assertEquals(0.75, metrics.fillRate(), 0.001);
    }

    @Test
    void slippageTrackingWorks() {
        var metrics = new SimulationMetrics();
        metrics.recordOrderMatched();
        metrics.recordSlippage(10);
        metrics.recordOrderMatched();
        metrics.recordSlippage(20);
        metrics.recordOrderMatched();
        metrics.recordSlippage(5);

        assertEquals(20, metrics.maxSlippageBps());
        assertEquals(11, metrics.averageSlippageBps());
    }

    @Test
    void simulationDurationTracking() {
        var metrics = new SimulationMetrics();
        metrics.recordSimulation(1000);
        metrics.recordSimulation(2000);

        assertEquals(2, metrics.simulationsRun());
        assertEquals(3000, metrics.totalDurationMs());
        assertEquals(1500, metrics.averageDurationMs());
    }

    @Test
    void resetClearsAllCounters() {
        var metrics = new SimulationMetrics();
        metrics.recordOrderMatched();
        metrics.recordSimulation(1000);
        metrics.recordSlippage(10);
        metrics.reset();

        assertEquals(0, metrics.ordersMatched());
        assertEquals(0, metrics.simulationsRun());
        assertEquals(0, metrics.maxSlippageBps());
    }

    @Test
    void toStringContainsKeyInfo() {
        var metrics = new SimulationMetrics();
        metrics.recordOrderMatched();
        metrics.recordSimulation(500);
        String str = metrics.toString();
        assertTrue(str.contains("simulations=1"));
        assertTrue(str.contains("matched=1"));
    }
}
