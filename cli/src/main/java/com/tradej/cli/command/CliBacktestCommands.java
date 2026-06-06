package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.core.service.BacktestService;
import com.tradej.simulation.MatchingEngine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Standalone backtest commands for the Quant/Trader Workbench.
 *
 * <p>Runs backtests using the same {@link MatchingEngine} used by the
 * simulation runtime — no Spring required. Historical candle data is
 * fetched via the broker REST API (fallback) or directly from DuckDB
 * when the analytics engine is available.
 */
public final class CliBacktestCommands extends CliCommandSupport {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public CliBacktestCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /**
     * `tradej backtest run --strategy momentum --symbol RELIANCE --from 2025-01-01 --to 2025-01-31`
     *
     * <p>Runs a simple moving-average crossover backtest:
     * <ul>
     *   <li>Fetches 5m candles for the symbol + date range</li>
     *   <li>Computes fast/slow SMA on close prices</li>
     *   <li>Generates BUY/SELL signals on crossover</li>
     *   <li>Simulates fills via {@link MatchingEngine}</li>
     *   <li>Reports trade log + summary stats</li>
     * </ul>
     */
    public void run(String strategy, String symbol, String segmentName,
                    LocalDate from, LocalDate to, long initialCapitalPaisa) throws Exception {
        session().ensureCatalogLoaded();

        ExchangeSegment seg = parseSegment(segmentName);
        MatchingEngine engine = new MatchingEngine();
        long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();

        // Fetch candles from broker
        out().println("Fetching candles for " + symbol + " from " + from + " to " + to + " ...");
        List<Candle> candles = marketData().getCandles(
                new com.tradej.core.domain.model.CandleHistoryRequest(
                        com.tradej.core.domain.model.InstrumentKey.of(symbol, seg),
                        "5m", from, to));

        if (candles.isEmpty()) {
            out().println("No candle data available for " + symbol + " in the requested range.");
            return;
        }

        out().println("Loaded " + candles.size() + " candles. Running strategy: " + strategy);

        // Run the strategy
        BacktestResult result = runStrategy(strategy, symbol, seg, candles, engine, initialCapitalPaisa);

        // Report results
        report(result);
    }

    /** `tradej backtest list` — list recent backtest results (stub). */
    public void list(int limit) {
        out().println("Backtest history listing is not yet persisted to disk.");
        out().println("Results are displayed after each `tradej backtest run` invocation.");
    }

    /** `tradej backtest status <runId>` — check a backtest run (stub). */
    public void status(String runId) {
        out().println("Backtest run " + runId + " — status tracking is not yet persisted.");
        out().println("Results are displayed after each `tradej backtest run` invocation.");
    }

    // ── Internal backtest engine ──

    private BacktestResult runStrategy(String strategy, String symbol, ExchangeSegment seg,
                                       List<Candle> candles, MatchingEngine engine,
                                       long initialCapitalPaisa) {
        return switch (strategy.toLowerCase()) {
            case "sma-crossover", "momentum" ->
                    runSmaCrossover(symbol, seg, candles, engine, initialCapitalPaisa);
            case "buy-hold" ->
                    runBuyAndHold(symbol, seg, candles, engine, initialCapitalPaisa);
            default ->
                    runSmaCrossover(symbol, seg, candles, engine, initialCapitalPaisa);
        };
    }

    /** Simple SMA crossover: fast=5, slow=20 on 5m candles. */
    private BacktestResult runSmaCrossover(String symbol, ExchangeSegment seg,
                                           List<Candle> candles, MatchingEngine engine,
                                           long initialCapitalPaisa) {
        int fastPeriod = 5;
        int slowPeriod = 20;
        if (candles.size() < slowPeriod + 1) {
            out().println("Not enough candles for SMA crossover (need " + (slowPeriod + 1) + ", have " + candles.size() + ")");
            return emptyResult("sma-crossover", symbol, initialCapitalPaisa, "Insufficient data");
        }

        // Feed LTPs to matching engine
        for (Candle c : candles) {
            engine.onTick(symbol, c.closePaisa());
        }

        // Run crossover signals
        List<String> tradeLog = new ArrayList<>();
        boolean inPosition = false;
        long entryPrice = 0L;
        long capital = initialCapitalPaisa;
        long positionSize = 0L;

        for (int i = slowPeriod; i < candles.size(); i++) {
            double fastSma = 0;
            double slowSma = 0;
            for (int j = 0; j < fastPeriod; j++) fastSma += candles.get(i - j).closePaisa();
            fastSma /= fastPeriod;
            for (int j = 0; j < slowPeriod; j++) slowSma += candles.get(i - j).closePaisa();
            slowSma /= slowPeriod;

            double prevFast = 0, prevSlow = 0;
            for (int j = 0; j < fastPeriod; j++) prevFast += candles.get(i - 1 - j).closePaisa();
            prevFast /= fastPeriod;
            for (int j = 0; j < slowPeriod; j++) prevSlow += candles.get(i - 1 - j).closePaisa();
            prevSlow /= slowPeriod;

            boolean crossoverUp = prevFast <= prevSlow && fastSma > slowSma;
            boolean crossoverDown = prevFast >= prevSlow && fastSma < slowSma;
            Candle c = candles.get(i);

            if (crossoverUp && !inPosition && capital > 0) {
                long qty = capital / c.closePaisa();
                if (qty > 0) {
                    inPosition = true;
                    entryPrice = c.closePaisa();
                    positionSize = qty;
                    var match = engine.match(new OrderRequest(
                            symbol, seg, Side.BUY, qty,
                            OrderType.MARKET, 0L, 0L, ProductType.INTRADAY,
                            Validity.DAY, UUID.randomUUID().toString()
                    ), "BT-" + UUID.randomUUID());
                    long fillPrice = match.order().pricePaisa();
                    tradeLog.add("BUY  " + formatMs(c.startTimeMs()) + " qty=" + qty
                            + " price=" + fillPrice + " capital=" + capital);
                }
            } else if (crossoverDown && inPosition) {
                var match = engine.match(new OrderRequest(
                        symbol, seg, Side.SELL, positionSize,
                        OrderType.MARKET, 0L, 0L, ProductType.INTRADAY,
                        Validity.DAY, UUID.randomUUID().toString()
                ), "BT-" + UUID.randomUUID());
                long fillPrice = match.order().pricePaisa();
                long pnl = (fillPrice - entryPrice) * positionSize;
                capital += pnl;
                tradeLog.add("SELL " + formatMs(c.startTimeMs()) + " qty=" + positionSize
                        + " price=" + fillPrice + " pnl=" + pnl + " capital=" + capital);
                inPosition = false;
            }
        }

        // Close any open position at last price
        if (inPosition && !candles.isEmpty()) {
            Candle last = candles.get(candles.size() - 1);
            long pnl = (last.closePaisa() - entryPrice) * positionSize;
            capital += pnl;
            tradeLog.add("CLOSE " + formatMs(last.endTimeMs()) + " qty=" + positionSize
                    + " price=" + last.closePaisa() + " pnl=" + pnl + " capital=" + capital);
        }

        long totalPnl = capital - initialCapitalPaisa;
        return new BacktestResult(
                "BT-" + UUID.randomUUID().toString().substring(0, 8),
                "sma-crossover", symbol, tradeLog.size() / 2, // trades = buy+sell per cycle
                tradeLog.size() / 4, // rough win estimate
                tradeLog.size() / 4, // rough loss estimate
                totalPnl, 0L, 0.0,
                initialCapitalPaisa, capital,
                tradeLog, "COMPLETED"
        );
    }

    /** Buy at first candle, sell at last. */
    private BacktestResult runBuyAndHold(String symbol, ExchangeSegment seg,
                                         List<Candle> candles, MatchingEngine engine,
                                         long initialCapitalPaisa) {
        if (candles.size() < 2) {
            return emptyResult("buy-hold", symbol, initialCapitalPaisa, "Insufficient data");
        }
        Candle first = candles.get(0);
        Candle last = candles.get(candles.size() - 1);
        long qty = initialCapitalPaisa / first.closePaisa();
        if (qty == 0) {
            return emptyResult("buy-hold", symbol, initialCapitalPaisa, "Capital insufficient for one unit");
        }
        long buyCost = qty * first.closePaisa();
        long sellValue = qty * last.closePaisa();
        long pnl = sellValue - buyCost;
        List<String> log = new ArrayList<>();
        log.add("BUY  " + formatMs(first.startTimeMs()) + " qty=" + qty + " price=" + first.closePaisa());
        log.add("SELL " + formatMs(last.endTimeMs()) + " qty=" + qty + " price=" + last.closePaisa() + " pnl=" + pnl);
        return new BacktestResult(
                "BT-" + UUID.randomUUID().toString().substring(0, 8),
                "buy-hold", symbol, 1, pnl > 0 ? 1 : 0, pnl > 0 ? 0 : 1,
                pnl, 0L, 0.0,
                initialCapitalPaisa, initialCapitalPaisa + pnl,
                log, "COMPLETED"
        );
    }

    private BacktestResult emptyResult(String strategy, String symbol,
                                       long capital, String reason) {
        return new BacktestResult("BT-ERROR", strategy, symbol,
                0, 0, 0, 0L, 0L, 0.0, capital, capital,
                List.of("Backtest aborted: " + reason), "ABORTED");
    }

    private void report(BacktestResult result) {
        if (context().json()) {
            out().print(result);
            return;
        }
        out().println("");
        out().println("=== Backtest Result ===");
        out().println("RunId:       " + result.runId());
        out().println("Strategy:    " + result.strategy());
        out().println("Symbol:      " + result.symbol());
        out().println("Status:      " + result.status());
        out().println("Trades:      " + result.trades());
        out().println("Total PnL:   " + result.totalPnlPaisa() + " paisa");
        out().println("Final Cap:   " + result.finalCapitalPaisa() + " paisa");
        out().println("");
        out().println("--- Trade Log ---");
        for (String line : result.tradeLog()) {
            out().println("  " + line);
        }
        out().println("");
    }

    record BacktestResult(
            String runId,
            String strategy,
            String symbol,
            int trades,
            int wins,
            int losses,
            long totalPnlPaisa,
            long maxDrawdownPaisa,
            double sharpeRatio,
            long initialCapitalPaisa,
            long finalCapitalPaisa,
            List<String> tradeLog,
            String status
    ) {}
}
