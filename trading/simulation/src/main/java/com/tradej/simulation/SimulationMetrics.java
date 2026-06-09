package com.tradej.simulation;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics for the simulation/backtest engine.
 * Tracks orders matched, fills generated, slippage, and PnL accuracy.
 */
public final class SimulationMetrics {

    private final AtomicLong ordersMatched = new AtomicLong();
    private final AtomicLong fillsGenerated = new AtomicLong();
    private final AtomicLong ordersRejected = new AtomicLong();
    private final AtomicLong simulationsRun = new AtomicLong();
    private final AtomicLong totalSlippageBps = new AtomicLong();
    private final AtomicLong maxSlippageBps = new AtomicLong();
    private final AtomicLong totalDurationMs = new AtomicLong();

    public void recordOrderMatched() { ordersMatched.incrementAndGet(); }
    public void recordFill() { fillsGenerated.incrementAndGet(); }
    public void recordRejection() { ordersRejected.incrementAndGet(); }
    public void recordSimulation(long durationMs) {
        simulationsRun.incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
    }

    public void recordSlippage(long slippageBps) {
        totalSlippageBps.addAndGet(slippageBps);
        updateMax(slippageBps);
    }

    private void updateMax(long slippageBps) {
        long current;
        do {
            current = maxSlippageBps.get();
            if (slippageBps <= current) return;
        } while (!maxSlippageBps.compareAndSet(current, slippageBps));
    }

    public long ordersMatched() { return ordersMatched.get(); }
    public long fillsGenerated() { return fillsGenerated.get(); }
    public long ordersRejected() { return ordersRejected.get(); }
    public long simulationsRun() { return simulationsRun.get(); }
    public long totalDurationMs() { return totalDurationMs.get(); }
    public long maxSlippageBps() { return maxSlippageBps.get(); }

    public long averageSlippageBps() {
        long matched = ordersMatched.get();
        return matched == 0 ? 0 : totalSlippageBps.get() / matched;
    }

    public long averageDurationMs() {
        long runs = simulationsRun.get();
        return runs == 0 ? 0 : totalDurationMs.get() / runs;
    }

    public double fillRate() {
        long total = ordersMatched.get() + ordersRejected.get();
        return total == 0 ? 1.0 : (double) ordersMatched.get() / total;
    }

    public void reset() {
        ordersMatched.set(0);
        fillsGenerated.set(0);
        ordersRejected.set(0);
        simulationsRun.set(0);
        totalSlippageBps.set(0);
        maxSlippageBps.set(0);
        totalDurationMs.set(0);
    }

    @Override
    public String toString() {
        return String.format(
            "SimulationMetrics{simulations=%d, matched=%d, fills=%d, rejected=%d, " +
            "fillRate=%.1f%%, avgSlippage=%dbps, maxSlippage=%dbps, avgDuration=%dms}",
            simulationsRun(), ordersMatched(), fillsGenerated(), ordersRejected(),
            fillRate() * 100, averageSlippageBps(), maxSlippageBps(), averageDurationMs());
    }
}
