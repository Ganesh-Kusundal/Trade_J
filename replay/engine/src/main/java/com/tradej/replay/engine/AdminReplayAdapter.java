package com.tradej.replay.engine;

import com.tradej.core.domain.event.DomainEvent;
import com.tradej.persistence.replay.ReplayResult;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reconciles the two replay paths through {@link ScenarioRunner}.
 *
 * <p>Before this class the platform had two divergent replay
 * surfaces:
 * <ul>
 *   <li>{@code /admin/historical/replay/ticks|candles|fills|orders}
 *       — driven by {@code ReplayOrchestrator}, publishes
 *       reconstructed {@code OrderAccepted}, {@code OrderFilled},
 *       {@code TradeOpened} events from DuckDB through the bus.
 *       Blocked in LIVE mode.</li>
 *   <li>{@code /api/v1/replay/*} — driven by
 *       {@code CandleReplaySession}, publishes {@code CandleClosed}
 *       and {@code ReplayTimeChangedEvent} events. Operates in
 *       LIVE mode.</li>
 * </ul>
 *
 * <p>{@link AdminReplayAdapter} is the bridge: each admin replay
 * request is converted to a {@link Scenario} and run through
 * {@link ScenarioRunner}. The returned {@link Scenario.Result} is
 * then mapped back to the legacy {@link ReplayResult} shape so the
 * existing controller responses and integration tests continue to
 * work unchanged.
 *
 * <h2>Why a bridge and not a refactor</h2>
 * The four admin paths are exercised by integration tests that
 * assert the {@link ReplayResult} shape ({@code totalRead},
 * {@code replayed}, {@code failed}, {@code complete}). A
 * controller-level refactor would change those responses and
 * break the tests. The adapter keeps the controller and tests
 * intact while moving the actual work onto
 * {@link ScenarioRunner}, so a future refactor can remove the
 * adapter and the controller calls without touching the
 * integration surface.
 */
public final class AdminReplayAdapter {

    private final ScenarioRunner runner;

    public AdminReplayAdapter(ScenarioRunner runner) {
        this.runner = runner;
    }

    public ReplayResult replayTicks(String symbol, long fromMs, long toMs,
                                    int offset, int batchSize) {
        return run(buildTickScenario("admin-ticks-" + symbol, "Admin: ticks " + symbol,
                symbol, fromMs, toMs, batchSize));
    }

    public ReplayResult replayCandles(String symbol, String interval,
                                      long fromMs, long toMs) {
        return run(buildScenario("admin-candles-" + symbol, "Admin: candles " + symbol,
                symbol, interval, fromMs, toMs, Scenario.Kind.REPLAY_CANDLES));
    }

    public ReplayResult replayFillEvents(String symbolOrNull, long fromMs, long toMs) {
        String sym = (symbolOrNull == null || symbolOrNull.isBlank()) ? "*" : symbolOrNull;
        return run(buildScenario("admin-fills-" + sym, "Admin: fills " + sym,
                sym, null, fromMs, toMs, Scenario.Kind.REPLAY_EVENTS));
    }

    public ReplayResult replayOrders(String symbolOrNull, long fromMs, long toMs) {
        String sym = (symbolOrNull == null || symbolOrNull.isBlank()) ? "*" : symbolOrNull;
        return run(buildScenario("admin-orders-" + sym, "Admin: orders " + sym,
                sym, null, fromMs, toMs, Scenario.Kind.REPLAY_EVENTS));
    }

    /**
     * The tick path carries a {@code batchSize} tag the runner
     * uses for the {@code LIMIT} on the tick query.
     */
    private Scenario buildTickScenario(String id, String label, String symbol,
                                       long fromMs, long toMs, int batchSize) {
        return buildScenario(id, label, symbol, null, fromMs, toMs,
                Scenario.Kind.REPLAY_TICKS, Map.of("batchSize", Integer.toString(batchSize)));
    }

    private Scenario buildScenario(String id, String label, String symbol, String interval,
                                   long fromMs, long toMs, Scenario.Kind kind) {
        return buildScenario(id, label, symbol, interval, fromMs, toMs, kind, Map.of());
    }

    private Scenario buildScenario(String id, String label, String symbol, String interval,
                                   long fromMs, long toMs, Scenario.Kind kind,
                                   Map<String, String> extraTags) {
        return new Scenario() {
            @Override
            public String id() {
                return id;
            }
            @Override
            public String label() {
                return label;
            }
            @Override
            public Kind kind() {
                // Each admin path maps to a specific runner kind:
                //   ticks     → REPLAY_TICKS   (runTickReplay)
                //   candles   → REPLAY_CANDLES (candle session facade)
                //   fills     → REPLAY_EVENTS  (runEventReplay)
                //   orders    → REPLAY_EVENTS  (runEventReplay)
                return kind;
            }
            @Override
            public Window window() {
                return new Window(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs), interval);
            }
            @Override
            public Map<String, SymbolConfig> symbols() {
                Map<String, SymbolConfig> m = new LinkedHashMap<>();
                m.put(symbol, new SymbolConfig("NSE_EQ", null, SymbolConfig.FeedMode.FULL));
                return m;
            }
            @Override
            public Optional<TimePolicy> timePolicy() {
                // Admin replays run in REPLAY mode (the orchestrator
                // calls virtualClock.enterReplayMode()); the runner
                // installs the same virtual clock.
                return Optional.of(TimePolicy.virtual(1.0));
            }
            @Override
            public Map<String, String> tags() {
                Map<String, String> t = new LinkedHashMap<>();
                t.put("source", "admin-controller");
                t.put("symbol", symbol);
                if (interval != null) t.put("interval", interval);
                t.putAll(extraTags);
                return t;
            }
        };
    }

    private ReplayResult run(Scenario scenario) {
        Scenario.Result result = runner.run(scenario);
        long replayed = result.eventsPublished() == null ? 0L : result.eventsPublished().size();
        long failed = result.errors() == null ? 0L : result.errors().size();
        // The legacy shape: totalRead, replayed, failed. The
        // 3-arg constructor (skipped=0) is the form the controller
        // and integration tests expect; the 4-arg canonical
        // constructor adds `skipped` for replays that observe
        // events but can't publish them.
        long totalRead = replayed;
        return new ReplayResult(totalRead, replayed, failed);
    }

    /**
     * Read-only access to the {@link ScenarioRunner} so the test
     * suite can verify what the adapter actually invoked.
     */
    public ScenarioRunner runner() {
        return runner;
    }
}
