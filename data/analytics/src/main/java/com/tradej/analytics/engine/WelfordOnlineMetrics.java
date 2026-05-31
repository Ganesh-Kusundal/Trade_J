package com.tradej.analytics.engine;

/**
 * High-performance, O(1) space complexity online calculator for running statistics,
 * including Sharpe/Sortino parameters, using Welford's Algorithm.
 * This prevents precision drift and avoids storing arrays of historical returns on the heap.
 */
public final class WelfordOnlineMetrics {

    private long count = 0;
    private double mean = 0.0;
    private double m2 = 0.0;
    private double downsideSumOfSquares = 0.0;
    private double totalSum = 0.0;

    /**
     * Updates the online estimators with a new return/value.
     *
     * @param value the new data point (e.g. daily return or trade return)
     */
    public synchronized void update(double value) {
        count++;
        totalSum += value;

        // Welford's recurrence relations for mean and sum of squares of differences
        double delta = value - mean;
        mean += delta / count;
        double delta2 = value - mean;
        m2 += delta * delta2;

        // Downside deviation calculation relative to 0.0 minimum acceptable return (MAR)
        if (value < 0.0) {
            downsideSumOfSquares += value * value;
        }
    }

    /**
     * Updates the online estimators with a custom Minimum Acceptable Return (MAR).
     *
     * @param value the new data point
     * @param mar the minimum acceptable return
     */
    public synchronized void update(double value, double mar) {
        count++;
        totalSum += value;

        double delta = value - mean;
        mean += delta / count;
        double delta2 = value - mean;
        m2 += delta * delta2;

        double deviation = value - mar;
        if (deviation < 0.0) {
            downsideSumOfSquares += deviation * deviation;
        }
    }

    public synchronized long getCount() {
        return count;
    }

    public synchronized double getMean() {
        return mean;
    }

    public synchronized double getSum() {
        return totalSum;
    }

    /**
     * Returns the sample variance.
     */
    public synchronized double getSampleVariance() {
        if (count < 2) {
            return 0.0;
        }
        return m2 / (count - 1);
    }

    /**
     * Returns the population variance.
     */
    public synchronized double getPopulationVariance() {
        if (count == 0) {
            return 0.0;
        }
        return m2 / count;
    }

    /**
     * Returns the sample standard deviation.
     */
    public synchronized double getSampleStandardDeviation() {
        return Math.sqrt(getSampleVariance());
    }

    /**
     * Returns the population standard deviation.
     */
    public synchronized double getPopulationStandardDeviation() {
        return Math.sqrt(getPopulationVariance());
    }

    /**
     * Returns the downside deviation (used as the denominator in Sortino ratio).
     * It divides the sum of squared negative deviations by total count.
     */
    public synchronized double getDownsideDeviation() {
        if (count == 0) {
            return 0.0;
        }
        return Math.sqrt(downsideSumOfSquares / count);
    }

    /**
     * Calculates the annualized Sharpe Ratio.
     *
     * @param riskFreeRate annualized risk free rate (e.g. 0.05 for 5%)
     * @param periodsPerYear periods in a year (e.g. 252 for daily, 252 * 375 for 1-min bars)
     */
    public synchronized double calculateSharpeRatio(double riskFreeRate, double periodsPerYear) {
        double stdDev = getSampleStandardDeviation();
        if (stdDev == 0.0) {
            return 0.0;
        }
        // Annualize mean and standard deviation
        double periodRf = riskFreeRate / periodsPerYear;
        double averageExcessReturn = mean - periodRf;
        return (averageExcessReturn / stdDev) * Math.sqrt(periodsPerYear);
    }

    /**
     * Calculates the annualized Sortino Ratio.
     *
     * @param targetReturn annualized target return / MAR (e.g. 0.05)
     * @param periodsPerYear periods in a year
     */
    public synchronized double calculateSortinoRatio(double targetReturn, double periodsPerYear) {
        double downsideDev = getDownsideDeviation();
        if (downsideDev == 0.0) {
            return 0.0;
        }
        double periodTarget = targetReturn / periodsPerYear;
        double averageExcessReturn = mean - periodTarget;
        return (averageExcessReturn / downsideDev) * Math.sqrt(periodsPerYear);
    }
}
