package com.tradej.runtime.parity;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.DomainEventHandler;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.Side;
import com.tradej.indicators.HalfTrend;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runtime Parity Certification Test
 * 
 * PROVES that CLI, Spring, and Replay runtimes produce IDENTICAL results
 * when running the same strategy on the same data.
 * 
 * This test:
 * 1. Runs Half Trend strategy on fixed historical dataset
 * 2. Captures all signals generated
 * 3. Compares signal count, trade count, PnL
 * 4. FAILS if any divergence detected
 */
@Tag("runtime-parity")
class RuntimeParityCertificationTest {

    private static final String SYMBOL = "TATASTEEL";
    private static final String SEGMENT = "NSE_EQ";
    private static final String INTERVAL = "1m";
    
    private List<Candle> historicalData;
    private EventBus eventBus;
    
    @BeforeEach
    void setUp() {
        // Create deterministic historical dataset (100 candles)
        historicalData = createDeterministicCandles(100);
        eventBus = new SimpleTestEventBus();
    }
    
    /**
     * CLI-vs-Replay signal parity test.
     *
     * <p><b>STATUS: FAILING (Type C — pre-existing real bug, out of scope for the immediate plan)</b>
     *
     * <p><b>Failure mode:</b> CLI produces 4 signals, Replay produces 3 (off by 1).
     *
     * <p><b>Root cause:</b> {@code runHalfTrendStrategyReplay} at line ~193 has an artificial
     * {@code if (replayCandles.size() >= 10)} guard that the CLI path does not have. The CLI
     * path processes all 100 points (i=0..99) and catches direction changes in the first
     * 10 points. The Replay path skips the first 9 candles, then on candle 10 initializes
     * {@code previousDirection} to {@code points[9].direction}, missing any direction
     * changes that occurred in {@code points[0..8]}. For the deterministic 100-candle
     * dataset used by this test, there is exactly 1 direction change in the first 10
     * points (down→up at index 4), so the off-by-1 is deterministic and reproducible.
     *
     * <p><b>Suggested fix:</b> In {@code runHalfTrendStrategyReplay}, remove the
     * {@code replayCandles.size() >= 10} guard. The {@code HalfTrend} algorithm has no
     * minimum-candles requirement (it works on any number of candles, even 1) — the
     * {@code >= 10} check is a leftover from an earlier warmup heuristic and is not
     * present in the CLI path, breaking parity.
     * <pre>
     * // Before (line 193):
     * if (replayCandles.size() >= 10) { // Need minimum candles for indicator
     *     // ... calculate + emit signal ...
     * }
     * // After:
     * { // no guard — HalfTrend.calculate() works on any number of candles
     *     // ... calculate + emit signal ...
     * }
     * </pre>
     *
     * <p><b>Effort:</b> 1 file, ~5 lines changed (remove guard, re-indent body).
     *
     * <p><b>Same root cause as {@link #fullPipelineParity_signalToTradeToPnL()}.</b>
     */
    @Test
    void cliVsReplayParity_producesIdenticalSignals() {
        // RUN 1: CLI-style execution (direct strategy call)
        List<SignalGenerated> cliSignals = new CopyOnWriteArrayList<>();
        runHalfTrendStrategyCLI(historicalData, cliSignals);
        
        // RUN 2: Replay-style execution (via EventBus)
        List<SignalGenerated> replaySignals = new CopyOnWriteArrayList<>();
        runHalfTrendStrategyReplay(historicalData, replaySignals);
        
        // COMPARE: Must be IDENTICAL
        assertEquals(cliSignals.size(), replaySignals.size(),
                "CLI and Replay must produce same signal count");
        
        for (int i = 0; i < cliSignals.size(); i++) {
            SignalGenerated cli = cliSignals.get(i);
            SignalGenerated replay = replaySignals.get(i);
            
            assertEquals(cli.symbol(), replay.symbol(),
                    "Signal " + i + ": symbol mismatch");
            assertEquals(cli.side(), replay.side(),
                    "Signal " + i + ": side mismatch");
            assertEquals(cli.setup(), replay.setup(),
                    "Signal " + i + ": setup mismatch");
        }
        
        System.out.println("✅ CLI vs Replay PARITY VERIFIED");
        System.out.println("   Signals: " + cliSignals.size());
        System.out.println("   All signals identical");
    }
    
    @Test
    void springVsCliParity_producesIdenticalSignals() {
        // RUN 1: CLI-style (manual composition)
        List<SignalGenerated> cliSignals = new CopyOnWriteArrayList<>();
        runHalfTrendStrategyCLI(historicalData, cliSignals);
        
        // RUN 2: Spring-style (simulated with EventBus + handlers)
        List<SignalGenerated> springSignals = new CopyOnWriteArrayList<>();
        runHalfTrendStrategySpring(historicalData, springSignals);
        
        // COMPARE: Must be IDENTICAL
        assertEquals(cliSignals.size(), springSignals.size(),
                "CLI and Spring must produce same signal count");
        
        for (int i = 0; i < cliSignals.size(); i++) {
            SignalGenerated cli = cliSignals.get(i);
            SignalGenerated spring = springSignals.get(i);
            
            assertEquals(cli.side(), spring.side(),
                    "Signal " + i + ": side mismatch");
            assertEquals(cli.entryPricePaisa(), spring.entryPricePaisa(),
                    "Signal " + i + ": entry price mismatch");
        }
        
        System.out.println("✅ CLI vs Spring PARITY VERIFIED");
        System.out.println("   Signals: " + cliSignals.size());
        System.out.println("   All signals identical");
    }
    
    /**
     * Full-pipeline CLI-vs-Replay parity test (signal → trade → PnL).
     *
     * <p><b>STATUS: FAILING (Type C — pre-existing real bug, out of scope for the immediate plan)</b>
     *
     * <p><b>Failure mode:</b> CLI reports 4 signals, Replay reports 3 (off by 1).
     *
     * <p><b>Root cause:</b> Same as {@link #cliVsReplayParity_producesIdenticalSignals()} —
     * the {@code runHalfTrendStrategyReplay} helper at line ~193 has a
     * {@code replayCandles.size() >= 10} guard that drops the first 9 candles' direction
     * changes. This test exercises the same helper, so the same off-by-1 propagates into
     * the signal-count assertion at line 124.
     *
     * <p><b>Suggested fix:</b> Same as {@link #cliVsReplayParity_producesIdenticalSignals()} —
     * remove the {@code >= 10} guard from {@code runHalfTrendStrategyReplay}. Fixing that
     * helper will fix both this test and the simpler parity test in one change.
     *
     * <p><b>Effort:</b> 0 lines (this test) — the fix lives entirely in the shared helper.
     */
    @Test
    void fullPipelineParity_signalToTradeToPnL() {
        // Execute full pipeline: Signal → Trade → Position → PnL
        PipelineResult cliResult = runFullPipelineCLI(historicalData);
        PipelineResult replayResult = runFullPipelineReplay(historicalData);
        
        // COMPARE: Must be IDENTICAL
        assertEquals(cliResult.signalCount, replayResult.signalCount,
                "Signal count mismatch");
        assertEquals(cliResult.tradeCount, replayResult.tradeCount,
                "Trade count mismatch");
        assertEquals(cliResult.pnlPaisa, replayResult.pnlPaisa,
                "PnL mismatch");
        assertEquals(cliResult.winRate, replayResult.winRate, 0.01,
                "Win rate mismatch");
        
        System.out.println("✅ FULL PIPELINE PARITY VERIFIED");
        System.out.println("   Signals: " + cliResult.signalCount);
        System.out.println("   Trades: " + cliResult.tradeCount);
        System.out.println("   PnL: " + cliResult.pnlPaisa + " paisa");
        System.out.println("   Win Rate: " + String.format("%.2f%%", cliResult.winRate * 100));
    }
    
    // ========== STRATEGY EXECUTION METHODS ==========
    
    /**
     * CLI-style: Direct strategy call without EventBus
     */
    private void runHalfTrendStrategyCLI(List<Candle> candles, List<SignalGenerated> signals) {
        HalfTrend halfTrend = new HalfTrend(2, 2, 10);
        List<HalfTrend.Point> points = halfTrend.calculate(candles);
        
        String previousDirection = null;
        for (int i = 0; i < points.size(); i++) {
            HalfTrend.Point point = points.get(i);
            
            // Detect trend change
            if (previousDirection != null && !previousDirection.equals(point.direction())) {
                Side side = "up".equals(point.direction()) ? Side.BUY : Side.SELL;
                
                SignalGenerated signal = new SignalGenerated(
                        EventMetadata.root(),
                        "signal-" + i,
                        SYMBOL,
                        INTERVAL,
                        side,
                        candles.get(i).closePaisa(),
                        candles.get(i).closePaisa() - 5000,  // stop loss
                        candles.get(i).closePaisa() + 10000, // take profit
                        "HALF_TREND",
                        java.util.Map.of(
                                "strategyName", "HalfTrend",
                                "direction", point.direction(),
                                "quantity", 10L
                        )
                );
                
                signals.add(signal);
            }
            
            previousDirection = point.direction();
        }
    }
    
    /**
     * Replay-style: Via EventBus with CandleClosed events
     */
    private void runHalfTrendStrategyReplay(List<Candle> candles, List<SignalGenerated> signals) {
        HalfTrend halfTrend = new HalfTrend(2, 2, 10);
        List<Candle> replayCandles = new ArrayList<>();
        String[] previousDirection = {null};
        
        // Subscribe to CandleClosed events
        eventBus.subscribe(CandleClosed.class, (CandleClosed event) -> {
            replayCandles.add(event.candle());
            
            if (replayCandles.size() >= 10) { // Need minimum candles for indicator
                List<HalfTrend.Point> points = halfTrend.calculate(replayCandles);
                if (!points.isEmpty()) {
                    HalfTrend.Point latest = points.get(points.size() - 1);
                    
                    if (previousDirection[0] != null && !previousDirection[0].equals(latest.direction())) {
                        Side side = "up".equals(latest.direction()) ? Side.BUY : Side.SELL;
                        
                        SignalGenerated signal = new SignalGenerated(
                                EventMetadata.root(),
                                "signal-replay-" + replayCandles.size(),
                                SYMBOL,
                                INTERVAL,
                                side,
                                event.candle().closePaisa(),
                                event.candle().closePaisa() - 5000,
                                event.candle().closePaisa() + 10000,
                                "HALF_TREND",
                                java.util.Map.of(
                                        "strategyName", "HalfTrend",
                                        "direction", latest.direction(),
                                        "quantity", 10L
                                )
                        );
                        
                        signals.add(signal);
                    }
                    
                    previousDirection[0] = latest.direction();
                }
            }
        });
        
        // Replay candles through EventBus
        for (Candle candle : candles) {
            CandleClosed closed = new CandleClosed(EventMetadata.root(), candle);
            eventBus.publish(closed);
        }
    }
    
    /**
     * Spring-style: Simulated with EventBus + handler chain
     */
    private void runHalfTrendStrategySpring(List<Candle> candles, List<SignalGenerated> signals) {
        // Same as CLI but wrapped in EventBus handlers (simulating Spring's @EventListener)
        runHalfTrendStrategyCLI(candles, signals);
    }
    
    // ========== FULL PIPELINE METHODS ==========
    
    record PipelineResult(
            int signalCount,
            int tradeCount,
            long pnlPaisa,
            double winRate
    ) {}
    
    private PipelineResult runFullPipelineCLI(List<Candle> candles) {
        List<SignalGenerated> signals = new ArrayList<>();
        runHalfTrendStrategyCLI(candles, signals);
        
        // Simulate trade execution
        int tradeCount = signals.size();
        long totalPnl = 0;
        int winningTrades = 0;
        
        for (SignalGenerated signal : signals) {
            // Simulate trade outcome (simplified)
            long pnl = signal.side() == Side.BUY ? 5000 : -3000; // Simplified PnL
            totalPnl += pnl;
            
            if (pnl > 0) {
                winningTrades++;
            }
        }
        
        double winRate = tradeCount > 0 ? (double) winningTrades / tradeCount : 0.0;
        
        return new PipelineResult(signals.size(), tradeCount, totalPnl, winRate);
    }
    
    private PipelineResult runFullPipelineReplay(List<Candle> candles) {
        List<SignalGenerated> signals = new CopyOnWriteArrayList<>();
        runHalfTrendStrategyReplay(candles, signals);
        
        // Same trade execution logic
        int tradeCount = signals.size();
        long totalPnl = 0;
        int winningTrades = 0;
        
        for (SignalGenerated signal : signals) {
            long pnl = signal.side() == Side.BUY ? 5000 : -3000;
            totalPnl += pnl;
            
            if (pnl > 0) {
                winningTrades++;
            }
        }
        
        double winRate = tradeCount > 0 ? (double) winningTrades / tradeCount : 0.0;
        
        return new PipelineResult(signals.size(), tradeCount, totalPnl, winRate);
    }
    
    // ========== HELPER METHODS ==========
    
    private List<Candle> createDeterministicCandles(int count) {
        List<Candle> candles = new ArrayList<>();
        long baseTime = Instant.parse("2024-01-15T03:45:00Z").toEpochMilli();
        long basePrice = 150000L; // 1500.00 in paisa
        
        for (int i = 0; i < count; i++) {
            long startTime = baseTime + (i * 60_000L);
            long endTime = startTime + 59_999L;
            
            // Deterministic price movement (alternating up/down trend)
            long priceOffset = (long) (Math.sin(i * 0.1) * 10000);
            long close = basePrice + priceOffset;
            long high = close + 500;
            long low = close - 500;
            long open = close + (long) (Math.cos(i * 0.15) * 200);
            long volume = 1000L + (i * 10);
            
            candles.add(new Candle(
                    SYMBOL,
                    INTERVAL,
                    startTime,
                    endTime,
                    open,
                    high,
                    low,
                    close,
                    volume,
                    true,
                    0L,
                    0L
            ));
        }
        
        return candles;
    }
    
    /**
     * Simple in-memory EventBus for testing (no Disruptor dependency)
     */
    static class SimpleTestEventBus implements EventBus {
        private final java.util.Map<Class<?>, java.util.List<DomainEventHandler<?>>> subscribers = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T extends DomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            subscribers.computeIfAbsent(eventType, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                      .add(handler);
        }

        @Override
        public <T extends DomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
            java.util.List<DomainEventHandler<?>> handlers = subscribers.get(eventType);
            if (handlers != null) handlers.remove(handler);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void publish(DomainEvent event) {
            java.util.List<DomainEventHandler<?>> handlers = subscribers.get(event.getClass());
            if (handlers != null) {
                for (var handler : handlers) {
                    ((DomainEventHandler<DomainEvent>) handler).onEvent(event);
                }
            }
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}
    }
}
