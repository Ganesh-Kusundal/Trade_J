package com.tradej.strategy.portfolio;

import com.tradej.core.domain.event.SignalGenerated;

import java.util.Map;

public interface CapitalReservationService {

    String ATTR_STRATEGY_NAME = "strategyName";
    String ATTR_QUANTITY = "quantity";

    long DEFAULT_CAPITAL_PER_STRATEGY_PAISA = 1_000_000L;

    /**
     * Checks strategy capital limit and reserves estimated capital.
     *
     * @return null if approved, or a rejection reason string
     */
    String reserveSignal(SignalGenerated signal);

    /**
     * Releases reserved capital for a signal.
     *
     * @return the strategy name if the signal was tracked, null otherwise
     */
    String freeSignalCapital(String signalId, String symbol);

    long usedCapitalPaisa(String strategyName);

    long allocatedCapitalPaisa(String strategyName);

    Map<String, PortfolioEngine.StrategyAllocation> allocationsSnapshot();

    void reset();
}
