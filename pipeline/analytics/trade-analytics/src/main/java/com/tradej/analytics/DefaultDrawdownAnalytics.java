package com.tradej.analytics;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultDrawdownAnalytics implements DrawdownAnalytics {

    private static final MathContext MC = new MathContext(10, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Override
    public DrawdownReport analyze(List<EquityPoint> equityCurve) {
        Objects.requireNonNull(equityCurve, "equityCurve");

        if (equityCurve.isEmpty()) {
            return new DrawdownReport(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of(),
                    Duration.ZERO
            );
        }

        if (equityCurve.size() == 1) {
            EquityPoint first = equityCurve.get(0);
            return new DrawdownReport(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    equityCurve,
                    Duration.ZERO
            );
        }

        BigDecimal peak = equityCurve.get(0).equityValue();
        BigDecimal maxDrawdownPct = BigDecimal.ZERO;
        BigDecimal maxDrawdownAbsolute = BigDecimal.ZERO;
        BigDecimal currentDrawdownPct = BigDecimal.ZERO;

        int peakIndex = 0;
        int maxDrawdownStartIndex = 0;
        int maxDrawdownEndIndex = 0;
        int currentPeakIndex = 0;

        List<EquityPoint> underwaterCurve = new ArrayList<>();
        List<EquityPoint> maxDrawdownUnderwater = new ArrayList<>();

        for (int i = 0; i < equityCurve.size(); i++) {
            EquityPoint point = equityCurve.get(i);
            BigDecimal value = point.equityValue();

            if (value.compareTo(peak) >= 0) {
                peak = value;
                peakIndex = i;
                currentPeakIndex = i;
                underwaterCurve.clear();
            } else {
                BigDecimal drawdownAbs = peak.subtract(value);
                BigDecimal drawdownPct = drawdownAbs.divide(peak, MC).multiply(HUNDRED);

                underwaterCurve.add(point);

                if (drawdownAbs.compareTo(maxDrawdownAbsolute) > 0) {
                    maxDrawdownAbsolute = drawdownAbs;
                    maxDrawdownPct = drawdownPct;
                    maxDrawdownStartIndex = currentPeakIndex;
                    maxDrawdownEndIndex = i;
                    maxDrawdownUnderwater = new ArrayList<>(underwaterCurve);
                }

                currentDrawdownPct = drawdownPct;
            }
        }

        Duration maxDrawdownDuration = Duration.ZERO;
        if (maxDrawdownEndIndex > maxDrawdownStartIndex) {
            maxDrawdownDuration = Duration.between(
                    equityCurve.get(maxDrawdownStartIndex).timestamp(),
                    equityCurve.get(maxDrawdownEndIndex).timestamp()
            );
        }

        return new DrawdownReport(
                maxDrawdownPct.negate(),
                maxDrawdownAbsolute.negate(),
                currentDrawdownPct.negate(),
                maxDrawdownUnderwater.isEmpty() ? equityCurve : maxDrawdownUnderwater,
                maxDrawdownDuration
        );
    }
}