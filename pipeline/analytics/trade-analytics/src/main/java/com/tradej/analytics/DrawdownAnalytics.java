package com.tradej.analytics;

/** Computes drawdown statistics from an equity curve. */
public interface DrawdownAnalytics {

    DrawdownReport analyze(java.util.List<EquityPoint> equityCurve);
}
