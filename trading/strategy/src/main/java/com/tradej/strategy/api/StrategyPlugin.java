package com.tradej.strategy.api;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.SignalGenerated;

import java.util.Optional;

@Deprecated(since = "2.0", forRemoval = true)
public interface StrategyPlugin {
    String name();

    Optional<SignalGenerated> onCandleClosed(CandleClosed candleClosed);
}
