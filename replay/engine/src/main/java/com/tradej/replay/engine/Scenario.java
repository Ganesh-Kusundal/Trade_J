package com.tradej.replay.engine;

import com.tradej.core.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Scenario is a declarative description of a time-windowed run through
 * the platform's event bus. The same scenario descriptor drives a
 * replay, a backtest, and a scanner-on-replay. Only the consumer set
 * differs.
 *
 * <h2>Why this exists</h2>
 * The platform currently has two divergent replay paths:
 * <ul>
 *   <li>{@code /admin/historical/replay/*} (AdminController) — driven
 *       by {@code ReplayOrchestrator}, publishes reconstructed
 *       {@code OrderAccepted}, {@code OrderFilled}, {@code TradeOpened}
 *       events from DuckDB through the bus. Blocked in LIVE mode.</li>
 *   <li>{@code /api/v1/replay/*} (ReplayStudioController) — driven by
 *       {@code CandleReplaySession}, publishes {@code CandleClosed} and
 *       {@code ReplayTimeChangedEvent} events. Operates in LIVE mode.</li>
 * </ul>
 * The two paths have different event shapes, different lifecycle
 * semantics, and different consumer sets. This is a known source of
 * confusion ("replay doesn't see my strategy's signals").
 *
 * <h2>Target state</h2>
 * A Scenario is the unified entry point. Consumers subscribe to the
 * bus as usual. The ScenarioRunner is the only producer of scenario
 * events; it knows whether the source is a DuckDB replay, a candle
 * session, or a backtest, but downstream code does not.
 *
 * <p>This file is the <strong>interface</strong> only. The ScenarioRunner
 * implementation that subsumes both paths is a focused refactor; see
 * the improvement plan, Week 5.
 */
public interface Scenario {

    String id();

    /**
     * Human-readable label, shown in the UI as the scenario title.
     */
    String label();

    /**
     * What kind of scenario this is. The same engine drives all three;
     * the UI uses this to choose which controls to expose.
     */
    Kind kind();

    /**
     * The window of data the scenario consumes.
     */
    Window window();

    /**
     * Per-symbol configuration (e.g. feed mode, subscription).
     */
    Map<String, SymbolConfig> symbols();

    /**
     * Optional time-policy. If absent, the runner uses real time
     * (paper-trader friendly). If present, the runner installs a
     * virtual clock and advances it according to the policy.
     */
    Optional<TimePolicy> timePolicy();

    /**
     * Tags for filtering in the UI and for log correlation.
     */
    Map<String, String> tags();

    enum Kind {
        /** Candle-based replay with play/pause/step/speed controls. */
        REPLAY_CANDLES,
        /** Domain-event replay from the projection store. */
        REPLAY_EVENTS,
        /** Tick replay — query {@code market_ticks} and publish
         *  {@code MarketTickEvent}s into the bus. The admin
         *  {@code /admin/historical/replay/ticks} path used to
         *  route through {@code HistoricalEventReplayService}
         *  directly; with this kind the path goes through
         *  {@link ScenarioRunner} like every other scenario. */
        REPLAY_TICKS,
        /** Fill-event replay — query {@code fill_events} and
         *  publish reconstructed events into the bus. The admin
         *  {@code /admin/historical/replay/fills} path. */
        REPLAY_FILL_EVENTS,
        /** Order replay — query {@code orders} and publish
         *  reconstructed events into the bus. The admin
         *  {@code /admin/historical/replay/orders} path. */
        REPLAY_ORDERS,
        /** Backtest with deterministic fill model. */
        BACKTEST,
        /** Live scanner using real-time ticks. */
        SCANNER_LIVE,
        /** Scanner on a replay window. */
        SCANNER_REPLAY
    }

    record Window(Instant from, Instant to, String interval) {}

    record SymbolConfig(String exchangeSegment, String securityId, FeedMode feedMode) {
        public enum FeedMode { TICKER, QUOTE, FULL }
    }

    record TimePolicy(double speedMultiplier, boolean step) {
        public static TimePolicy realTime() {
            return new TimePolicy(1.0, false);
        }
        public static TimePolicy virtual(double speedMultiplier) {
            return new TimePolicy(speedMultiplier, false);
        }
    }

    /**
     * The result of a scenario run. Reproducible: given the same
     * scenario, the same seed, the same data snapshot, the same
     * strategy hash, the result is bit-for-bit identical. This is
     * what makes a backtest trustworthy.
     */
    record Result(
        String scenarioId,
        long seed,
        String dataSnapshotId,
        String strategyHash,
        List<DomainEvent> eventsPublished,
        Map<String, Long> consumerCounts,
        ResultHash resultHash,
        boolean success,
        List<String> errors
    ) {}

    /**
     * Hash of the deterministic output of the scenario. Computed from
     * the PnL series, the trade list, and the signal log. Two runs
     * of the same scenario with the same inputs must produce the
     * same hash.
     */
    record ResultHash(String hex) {
        public static ResultHash of(String hex) {
            return new ResultHash(hex);
        }
    }
}
