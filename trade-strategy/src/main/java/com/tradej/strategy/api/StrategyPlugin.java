package com.tradej.strategy.api;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.Optional;

public interface StrategyPlugin {
    String name();

    Optional<SignalGenerated> onCandleClosed(CandleClosed candleClosed);
}
