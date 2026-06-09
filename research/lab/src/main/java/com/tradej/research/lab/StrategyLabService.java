package com.tradej.research.lab;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.analytics.engine.WelfordOnlineMetrics;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.Side;
import com.tradej.research.core.RunResult;
import com.tradej.research.core.StrategyConfig;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Executes a high-fidelity strategy backtest sandbox using historical data,
 * logging performance results dynamically to DuckDB.
 */
public class StrategyLabService {
    private static final Logger log = LoggerFactory.getLogger(StrategyLabService.class);

    private final DuckDbAnalyticsEngine analyticsEngine;
    private final DuckDbResearchStore researchStore;
    private final boolean usePipelineExecution;

    public StrategyLabService(DuckDbAnalyticsEngine analyticsEngine, DuckDbResearchStore researchStore) {
        this(analyticsEngine, researchStore, false);
    }

    public StrategyLabService(
            DuckDbAnalyticsEngine analyticsEngine,
            DuckDbResearchStore researchStore,
            boolean usePipelineExecution
    ) {
        this.analyticsEngine = analyticsEngine;
        this.researchStore = researchStore;
        this.usePipelineExecution = usePipelineExecution;
    }

    /**
     * Executes backtest for a strategy config and session.
     */
    public RunResult executeBacktest(
        UUID sessionId,
        StrategyConfig config,
        GraphStrategyPlugin strategy,
        long fromMs,
        long toMs
    ) throws SQLException {
        log.info("Executing strategy backtest: strategy={} config={} pipelineMode={}",
                strategy.name(), config.configHash(), usePipelineExecution);
        if (usePipelineExecution) {
            log.warn("Pipeline execution mode requested but StrategyLab still uses direct plugin loop — wire AppBacktestService for full parity");
        }

        String runId = UUID.randomUUID().toString();
        List<Candle> candles = new ArrayList<>();

        // 1. Load candles from analytics engine for symbols
        List<String> symbols = List.of("SBIN"); // Default/assumed symbol for test runs
        for (String symbol : symbols) {
            List<Map<String, Object>> rows = analyticsEngine.queryEquityCandles(symbol, fromMs, toMs, 10000);
            for (Map<String, Object> row : rows) {
                candles.add(new Candle(
                    (String) row.get("symbol"),
                    (String) row.get("interval"),
                    (Long) row.get("barTimeMs"),
                    (Long) row.get("barTimeMs") + 59999,
                    (Long) row.get("openPaisa"),
                    (Long) row.get("highPaisa"),
                    (Long) row.get("lowPaisa"),
                    (Long) row.get("closePaisa"),
                    (Long) row.get("volume"),
                    true
                ));
            }
        }

        if (candles.isEmpty()) {
            log.warn("No candle data found for backtest runId={}", runId);
            return new RunResult(runId, sessionId.toString(), config.configHash(), fromMs, toMs, 0, 0, 0, 0, 0, 0, Map.of());
        }

        // Sort by time
        candles.sort((c1, c2) -> Long.compare(c1.startTimeMs(), c2.startTimeMs()));

        // 2. Roll through candles
        boolean inPosition = false;
        Side positionSide = null;
        long entryPrice = 0;
        long entryTime = 0;
        long stopLoss = 0;
        long takeProfit = 0;
        String activeTradeId = null;

        long totalTrades = 0;
        long winningTrades = 0;
        double totalPnl = 0.0;
        double maxDrawdown = 0.0;
        double peakEquity = 1000000.0; // Starting 10,000.00 Rs virtual balance
        double currentEquity = peakEquity;

        WelfordOnlineMetrics returnsMetrics = new WelfordOnlineMetrics();

        for (Candle candle : candles) {
            if (inPosition) {
                // Check stop loss / take profit
                boolean exitTriggered = false;
                long exitPrice = 0;

                if (positionSide == Side.BUY) {
                    if (candle.lowPaisa() <= stopLoss) {
                        exitTriggered = true;
                        exitPrice = stopLoss;
                    } else if (candle.highPaisa() >= takeProfit) {
                        exitTriggered = true;
                        exitPrice = takeProfit;
                    }
                } else { // SELL/Short
                    if (candle.highPaisa() >= stopLoss) {
                        exitTriggered = true;
                        exitPrice = stopLoss;
                    } else if (candle.lowPaisa() <= takeProfit) {
                        exitTriggered = true;
                        exitPrice = takeProfit;
                    }
                }

                if (exitTriggered) {
                    long realizedPnl = (positionSide == Side.BUY) 
                        ? (exitPrice - entryPrice) 
                        : (entryPrice - exitPrice);

                    currentEquity += realizedPnl / 100.0;
                    if (currentEquity > peakEquity) {
                        peakEquity = currentEquity;
                    }
                    double drawdown = (peakEquity - currentEquity) / peakEquity;
                    if (drawdown > maxDrawdown) {
                        maxDrawdown = drawdown;
                    }

                    returnsMetrics.update(realizedPnl / 100.0);
                    totalTrades++;
                    if (realizedPnl > 0) {
                        winningTrades++;
                    }

                    // Save trade log
                    researchStore.saveTradeLog(
                        runId,
                        activeTradeId,
                        candle.symbol(),
                        positionSide.name(),
                        entryTime,
                        entryPrice,
                        candle.endTimeMs(),
                        exitPrice,
                        1, // qty
                        realizedPnl
                    );

                    inPosition = false;
                    positionSide = null;
                }
            } else {
                // Feed candle to strategy
                CandleClosed event = new CandleClosed(EventMetadata.root(), candle);
                Optional<SignalGenerated> signal = strategy.onEvent(event);
                if (signal.isPresent()) {
                    SignalGenerated sig = signal.get();
                    inPosition = true;
                    positionSide = sig.side();
                    entryPrice = sig.entryPricePaisa() > 0 ? sig.entryPricePaisa() : candle.closePaisa();
                    entryTime = candle.endTimeMs();
                    stopLoss = sig.stopLossPaisa();
                    takeProfit = sig.takeProfitPaisa();
                    activeTradeId = sig.signalId();
                }
            }
        }

        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.0;
        double sharpe = returnsMetrics.calculateSharpeRatio(0.05, 252);
        double sortino = returnsMetrics.calculateSortinoRatio(0.05, 252);

        RunResult runResult = new RunResult(
            runId,
            sessionId.toString(),
            config.configHash(),
            fromMs,
            toMs,
            totalTrades,
            winRate,
            currentEquity - 1000000.0,
            sharpe,
            sortino,
            maxDrawdown,
            Map.of()
        );

        researchStore.saveRunResult(runResult);
        log.info("Strategy backtest completed. Trades={} WinRate={} Sharpe={}", totalTrades, winRate, sharpe);

        return runResult;
    }
}
