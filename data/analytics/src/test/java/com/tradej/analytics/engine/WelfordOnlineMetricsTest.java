package com.tradej.analytics.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WelfordOnlineMetricsTest {

    @Test
    public void testOnlineMetricsAgainstStandardFormulas() {
        double[] returns = {
            0.015, -0.005, 0.02, -0.01, 0.005, 0.03, -0.025, 0.01, -0.015, 0.022
        };

        WelfordOnlineMetrics metrics = new WelfordOnlineMetrics();
        for (double r : returns) {
            metrics.update(r);
        }

        // 1. Check Count
        assertEquals(returns.length, metrics.getCount());

        // 2. Check Mean
        double sum = 0.0;
        for (double r : returns) {
            sum += r;
        }
        double expectedMean = sum / returns.length;
        assertEquals(expectedMean, metrics.getMean(), 1e-9);
        assertEquals(sum, metrics.getSum(), 1e-9);

        // 3. Check Variance (Sample)
        double sumSqDiff = 0.0;
        for (double r : returns) {
            double diff = r - expectedMean;
            sumSqDiff += diff * diff;
        }
        double expectedSampleVariance = sumSqDiff / (returns.length - 1);
        assertEquals(expectedSampleVariance, metrics.getSampleVariance(), 1e-9);

        // 4. Check Std Dev (Sample)
        double expectedSampleStdDev = Math.sqrt(expectedSampleVariance);
        assertEquals(expectedSampleStdDev, metrics.getSampleStandardDeviation(), 1e-9);

        // 5. Check Downside Deviation (MAR = 0.0)
        double downsideSqSum = 0.0;
        for (double r : returns) {
            if (r < 0.0) {
                downsideSqSum += r * r;
            }
        }
        double expectedDownsideDeviation = Math.sqrt(downsideSqSum / returns.length);
        assertEquals(expectedDownsideDeviation, metrics.getDownsideDeviation(), 1e-9);

        // 6. Check Sharpe and Sortino ratios
        double rf = 0.02;
        double periods = 252;
        double expectedSharpe = ((expectedMean - (rf / periods)) / expectedSampleStdDev) * Math.sqrt(periods);
        assertEquals(expectedSharpe, metrics.calculateSharpeRatio(rf, periods), 1e-9);

        double expectedSortino = ((expectedMean - (rf / periods)) / expectedDownsideDeviation) * Math.sqrt(periods);
        assertEquals(expectedSortino, metrics.calculateSortinoRatio(rf, periods), 1e-9);
    }

    @Test
    public void testEmptyAndSingleElementEdgeCases() {
        WelfordOnlineMetrics emptyMetrics = new WelfordOnlineMetrics();
        assertEquals(0, emptyMetrics.getCount());
        assertEquals(0.0, emptyMetrics.getMean());
        assertEquals(0.0, emptyMetrics.getSampleVariance());
        assertEquals(0.0, emptyMetrics.getDownsideDeviation());

        WelfordOnlineMetrics singleMetrics = new WelfordOnlineMetrics();
        singleMetrics.update(0.05);
        assertEquals(1, singleMetrics.getCount());
        assertEquals(0.05, singleMetrics.getMean());
        assertEquals(0.0, singleMetrics.getSampleVariance());
        assertEquals(0.0, singleMetrics.getDownsideDeviation());
    }
}
