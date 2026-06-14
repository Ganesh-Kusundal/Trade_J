package com.tradej.replay.engine;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.persistence.replay.HistoricalQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Unified scenario runner. One entry point for replay, backtest, and
 * scanner-on-replay. The {@link Scenario} descriptor drives the run;
 * the underlying engine is a thin facade over
 * {@link CandleReplaySession} (candle path) and
 * {@link BacktestExecutionService} (backtest path) for now; the
 * replay-event path is implemented directly here.
 *
 * <h2>Why</h2>
 * Before this class there were two divergent replay paths
 * ({@code /admin/historical/replay/*} via
 * {@code HistoricalEventReplayService} and {@code /api/v1/replay/*} via
 * {@code CandleReplaySession}) and a separate backtest path
 * ({@code /api/v1/backtest/run} via
 * {@code BacktestExecutionService}). They have different event
 * shapes, different lifecycle semantics, and different consumer
 * sets. {@link ScenarioRunner} is the single entry point that
 * picks the right path and yields a uniform result with a
 * reproducibility hash.
 */
public class ScenarioRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRunner.class);

    private final EventBus eventBus;
    private final CandleReplaySession candleSession;
    private final BacktestExecutionService backtestService;
    private final HistoricalBarRepository barRepository;
    private final HistoricalQueryService queryService;
    private final String strategyHash;
    private final long seed;

    public ScenarioRunner(
            EventBus eventBus,
            CandleReplaySession candleSession,
            BacktestExecutionService backtestService,
            HistoricalBarRepository barRepository,
            String strategyHash,
            long seed
    ) {
        this(eventBus, candleSession, backtestService, barRepository, null, strategyHash, seed);
    }

    /**
     * Full constructor: includes the {@link HistoricalQueryService}
     * needed by the {@link Scenario.Kind#REPLAY_TICKS} path. The
     * 6-arg constructor above remains for callers that don't
     * need tick replay (backtests, candle replays, scanners).
     */
    public ScenarioRunner(
            EventBus eventBus,
            CandleReplaySession candleSession,
            BacktestExecutionService backtestService,
            HistoricalBarRepository barRepository,
            HistoricalQueryService queryService,
            String strategyHash,
            long seed
    ) {
        this.eventBus = eventBus;
        this.candleSession = candleSession;
        this.backtestService = backtestService;
        this.barRepository = barRepository;
        this.queryService = queryService;
        this.strategyHash = strategyHash == null ? "" : strategyHash;
        this.seed = seed;
    }

    public Scenario.Result run(Scenario scenario) {
        long startMs = System.currentTimeMillis();
        AtomicLong eventCount = new AtomicLong();
        Optional<EventBus> bus = Optional.ofNullable(eventBus);
        // EventBus.subscribe is typed, so we count by subscribing to a
        // broad set of lifecycle events. DomainEvent.class itself is
        // not a concrete event, so we cannot subscribe to "all" — the
        // bus dispatches in single-arg publish(DomainEvent) which
        // would route to the typed handlers.
        bus.ifPresent(b -> {
            b.subscribe(com.tradej.core.domain.event.CandleClosed.class, e -> eventCount.incrementAndGet());
            b.subscribe(com.tradej.core.domain.event.OrderAccepted.class, e -> eventCount.incrementAndGet());
            b.subscribe(com.tradej.core.domain.event.OrderFullyFilled.class, e -> eventCount.incrementAndGet());
            b.subscribe(com.tradej.core.domain.event.TradeOpened.class, e -> eventCount.incrementAndGet());
        });

        try {
            return switch (scenario.kind()) {
                case REPLAY_CANDLES -> runCandleReplay(scenario, bus, eventCount, startMs);
                case BACKTEST -> runBacktest(scenario, bus, eventCount, startMs);
                case REPLAY_TICKS -> runTickReplay(scenario, bus, eventCount, startMs);
                case REPLAY_EVENTS, SCANNER_REPLAY, SCANNER_LIVE ->
                        runEventReplay(scenario, bus, eventCount, startMs);
            };
        } finally {
            bus.ifPresent(b -> {
                b.unsubscribe(com.tradej.core.domain.event.CandleClosed.class, e -> {});
                b.unsubscribe(com.tradej.core.domain.event.OrderAccepted.class, e -> {});
                b.unsubscribe(com.tradej.core.domain.event.OrderFullyFilled.class, e -> {});
                b.unsubscribe(com.tradej.core.domain.event.TradeOpened.class, e -> {});
            });
        }
    }

    private Scenario.Result runCandleReplay(
            Scenario scenario, Optional<EventBus> bus, AtomicLong eventCount, long startMs
    ) {
        if (candleSession == null) {
            return error(scenario, "CandleReplaySession not configured", eventCount, startMs);
        }
        if (barRepository == null) {
            return error(scenario, "HistoricalBarRepository not configured", eventCount, startMs);
        }
        try {
            Scenario.SymbolConfig cfg = firstSymbol(scenario);
            ExchangeSegment segment = ExchangeSegment.valueOf(cfg.exchangeSegment());
            var localFrom = scenario.window().from().atZone(java.time.ZoneId.of("UTC")).toLocalDate();
            var localTo = scenario.window().to().atZone(java.time.ZoneId.of("UTC")).toLocalDate();
            List<Candle> candles = barRepository.queryCandles(
                    com.tradej.core.domain.model.InstrumentKey.of(
                            firstSymbolName(scenario), segment),
                    scenario.window().interval(),
                    localFrom, localTo
            );
            if (candles.isEmpty()) {
                return error(scenario, "No candles for window", eventCount, startMs);
            }
            candleSession.start(candles);
            while (candleSession.step()) {
                if (Thread.currentThread().isInterrupted()) break;
            }
            String hash = resultHash(scenario, eventCount, candles.size(), true);
            return new Scenario.Result(
                    scenario.id(), seed, "scenario-" + scenario.id(),
                    strategyHash, List.of(), Map.of("candles", (long) candles.size()),
                    Scenario.ResultHash.of(hash), true, List.of()
            );
        } catch (Exception e) {
            return error(scenario, e.getMessage(), eventCount, startMs);
        }
    }

    private Scenario.Result runBacktest(
            Scenario scenario, Optional<EventBus> bus, AtomicLong eventCount, long startMs
    ) {
        if (backtestService == null) {
            return error(scenario, "BacktestExecutionService not configured", eventCount, startMs);
        }
        try {
            var backtestResult = backtestService.runBacktest(scenario.id(), List.of());
            String hash = resultHash(scenario, eventCount, 0, true);
            return new Scenario.Result(
                    scenario.id(), seed, "scenario-" + scenario.id(),
                    strategyHash, List.of(),
                    Map.of("backtestMode", 1L),
                    Scenario.ResultHash.of(hash), true,
                    List.of(backtestResult.getOrDefault("error", "ok").toString())
            );
        } catch (Exception e) {
            return error(scenario, e.getMessage(), eventCount, startMs);
        }
    }

    /**
     * Direct event replay — drives the bus with a synthetic time
     * machine. For a paper-broker test, this is the most honest path
     * because the same consumers that run in production see the
     * replayed events with no broker involvement.
     */
    private Scenario.Result runEventReplay(
            Scenario scenario, Optional<EventBus> bus, AtomicLong eventCount, long startMs
    ) {
        if (barRepository == null) {
            return error(scenario, "HistoricalBarRepository not configured", eventCount, startMs);
        }
        try {
            Scenario.SymbolConfig cfg = firstSymbol(scenario);
            ExchangeSegment segment = ExchangeSegment.valueOf(cfg.exchangeSegment());
            var localFrom = scenario.window().from().atZone(java.time.ZoneId.of("UTC")).toLocalDate();
            var localTo = scenario.window().to().atZone(java.time.ZoneId.of("UTC")).toLocalDate();
            List<Candle> candles = barRepository.queryCandles(
                    com.tradej.core.domain.model.InstrumentKey.of(
                            firstSymbolName(scenario), segment),
                    scenario.window().interval(),
                    localFrom, localTo
            );
            if (candles.isEmpty()) {
                return error(scenario, "No candles for window", eventCount, startMs);
            }
            List<DomainEvent> eventsPublished = new ArrayList<>();
            for (Candle c : candles) {
                CandleClosed closed = new CandleClosed(EventMetadata.root(), c);
                eventsPublished.add(closed);
                bus.ifPresent(b -> b.publish(closed));
                bus.ifPresent(b -> b.publish(new ReplayTimeChangedEvent(
                        EventMetadata.root(), c.endTimeMs(), 1_000_000_000L)));
            }
            String hash = resultHash(scenario, eventCount, eventsPublished.size(), true);
            return new Scenario.Result(
                    scenario.id(), seed, "scenario-" + scenario.id(),
                    strategyHash, eventsPublished,
                    Map.of("eventsPublished", (long) eventsPublished.size()),
                    Scenario.ResultHash.of(hash), true, List.of()
            );
        } catch (Exception e) {
            return error(scenario, e.getMessage(), eventCount, startMs);
        }
    }

    /**
     * Tick replay. Queries {@code market_ticks} for the window and
     * publishes each {@link MarketTickEvent} on the bus. The
     * semantics match the old
     * {@code HistoricalEventReplayService.replayTicks(ticks, symbol, bus)}
     * path: every read tick is published exactly once; a publish
     * failure increments the failure count but does not abort the
     * loop. Used by the {@code /admin/historical/replay/ticks} path
     * via {@link AdminReplayAdapter}.
     */
    private Scenario.Result runTickReplay(
            Scenario scenario, Optional<EventBus> bus, AtomicLong eventCount, long startMs
    ) {
        if (queryService == null) {
            return error(scenario, "HistoricalQueryService not configured", eventCount, startMs);
        }
        try {
            String symbol = firstSymbolName(scenario);
            long fromMs = scenario.window().from().toEpochMilli();
            long toMs = scenario.window().to().toEpochMilli();
            int limit = resolveTickLimit(scenario);
            List<MarketTickEvent> ticks = queryService.queryTicks(symbol, fromMs, toMs, limit);
            List<DomainEvent> eventsPublished = new ArrayList<>();
            long failed = 0L;
            for (MarketTickEvent tick : ticks) {
                try {
                    bus.ifPresent(b -> b.publish(tick));
                    eventsPublished.add(tick);
                } catch (RuntimeException ex) {
                    failed++;
                }
            }
            log.info("Tick replay for {}: {}/{} replayed, {} failed",
                    symbol, eventsPublished.size(), ticks.size(), failed);
            return success(scenario, eventsPublished, eventCount, startMs, failed);
        } catch (RuntimeException ex) {
            return error(scenario, "Tick replay failed: " + ex.getMessage(), eventCount, startMs);
        }
    }

    /**
     * The tick batch size is a tag (admin path passes
     * {@code batchSize=N}). If absent, default to 50_000 — the
     * orchestrator's default.
     */
    private int resolveTickLimit(Scenario scenario) {
        String bs = scenario.tags().get("batchSize");
        if (bs == null) return 50_000;
        try { return Math.max(1, Integer.parseInt(bs)); }
        catch (NumberFormatException ex) { return 50_000; }
    }

    private Scenario.Result error(Scenario scenario, String message, AtomicLong eventCount, long startMs) {
        log.warn("Scenario {} failed: {}", scenario.id(), message);
        return new Scenario.Result(
                scenario.id(), seed, "scenario-" + scenario.id(),
                strategyHash, List.of(),
                Map.of(),
                Scenario.ResultHash.of("0".repeat(64)),
                false,
                List.of(message)
        );
    }

    private Scenario.Result success(
            Scenario scenario, List<DomainEvent> eventsPublished,
            AtomicLong eventCount, long startMs, long failed
    ) {
        return new Scenario.Result(
                scenario.id(), seed, "scenario-" + scenario.id(),
                strategyHash, eventsPublished,
                Map.of("events", (long) eventsPublished.size(), "failed", failed),
                Scenario.ResultHash.of("0".repeat(64)),
                true,
                List.of()
        );
    }

    private Scenario.SymbolConfig firstSymbol(Scenario scenario) {
        if (scenario.symbols().isEmpty()) {
            throw new IllegalArgumentException("Scenario has no symbols");
        }
        return scenario.symbols().values().iterator().next();
    }

    private String firstSymbolName(Scenario scenario) {
        return scenario.symbols().keySet().iterator().next();
    }

    /**
     * Hash of the deterministic output: scenario id + strategy hash +
     * event count + result size + status. Two runs of the same
     * scenario with the same inputs produce the same hash.
     */
    private String resultHash(Scenario scenario, AtomicLong eventCount, long resultSize, boolean success) {
        String input = scenario.id() + "|" + strategyHash + "|" + eventCount.get()
                + "|" + resultSize + "|" + success + "|" + seed;
        return sha256(input);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes());
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            return UUID.randomUUID().toString();
        }
    }
}
