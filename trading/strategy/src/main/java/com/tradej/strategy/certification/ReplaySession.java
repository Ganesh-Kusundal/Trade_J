package com.tradej.strategy.certification;

import com.tradej.core.domain.model.Candle;

import java.util.List;

/**
 * Replay surface used by {@link StrategyReplayParityReporter}. Defined
 * as an interface in {@code trading-strategy} so the reporter does not
 * need a direct dependency on {@code replay-engine} (which would
 * create a cycle).
 *
 * <p>The actual implementation lives in
 * {@code app}'s {@code StrategyParityConfiguration} — it wraps
 * {@code ReplayController} from the {@code replay-engine} module.
 */
public interface ReplaySession {
    void start(List<Candle> candles);
    void play();
    boolean step();
    void stop();
}
