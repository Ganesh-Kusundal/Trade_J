package com.tradej.replay.engine;

import com.tradej.core.domain.event.TradeExecutionEvent;
import com.tradej.core.domain.value.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-function fold over a list of {@link TradeExecutionEvent}s that
 * produces the structured backtest output: trades, pnl series, and
 * summary metrics.
 *
 * <p>The ledger is a class, not a Spring bean, so it is trivially
 * testable. The class is deterministic: same fills in → same
 * ledger out. Slippage and fees are not deducted here — they are
 * applied upstream by the fill model.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Walk fills in chronological order.</li>
 *   <li>For each fill, match against the open position (FIFO).
 *       If side matches the open side, extend the position; if
 *       opposite, realize P&L on the matched quantity; if
 *       flat, open a new position.</li>
 *   <li>Track running realized P&L, open position size, and a
 *       per-fill ledger entry for the pnl series.</li>
 *   <li>Compute summary metrics at the end: trade count, win
 *       rate, max drawdown, net P&L.</li>
 * </ol>
 */
public final class BacktestLedger {

    public record Trade(
            String symbol,
            Side entrySide,
            long entryPricePaisa,
            long exitPricePaisa,
            long quantity,
            long entryTimestampMs,
            long exitTimestampMs,
            long realizedPnlPaisa
    ) {
        public boolean isWin() {
            return realizedPnlPaisa > 0;
        }
    }

    public record PnlPoint(long timestampMs, long cumulativePnlPaisa) {}

    public record Metrics(
            int tradeCount,
            int winCount,
            int lossCount,
            double winRate,
            long netPnlPaisa,
            long maxDrawdownPaisa,
            long grossProfitPaisa,
            long grossLossPaisa
    ) {}

    public record Result(
            List<Trade> trades,
            List<PnlPoint> pnlSeries,
            Metrics metrics
    ) {}

    /**
     * Fold a list of fills into a {@link Result}. The list is
     * sorted by exchangeTimestampMs so callers don't have to
     * pre-sort.
     */
    public Result fold(List<TradeExecutionEvent> fills) {
        if (fills == null || fills.isEmpty()) {
            return new Result(List.of(), List.of(), emptyMetrics());
        }
        List<TradeExecutionEvent> sorted = new ArrayList<>(fills);
        sorted.sort((a, b) -> Long.compare(a.exchangeTimestampMs(), b.exchangeTimestampMs()));

        List<Trade> trades = new ArrayList<>();
        List<PnlPoint> pnlSeries = new ArrayList<>();
        long runningPnl = 0L;
        long maxDrawdown = 0L;
        long peakPnl = 0L;
        long grossProfit = 0L;
        long grossLoss = 0L;
        int winCount = 0;

        // Per-symbol open position tracking (FIFO).
        Map<String, OpenPosition> open = new LinkedHashMap<>();

        for (TradeExecutionEvent fill : sorted) {
            OpenPosition pos = open.computeIfAbsent(fill.symbol(), k -> new OpenPosition());

            if (pos.isEmpty()) {
                pos.open(fill.side(), fill.executedPricePaisa(), fill.executedQuantity(), fill.exchangeTimestampMs());
            } else if (pos.side == fill.side()) {
                // Adding to position (weighted-average price).
                long totalQty = pos.quantity + fill.executedQuantity();
                long totalCost = pos.avgPricePaisa * pos.quantity
                        + fill.executedPricePaisa() * fill.executedQuantity();
                pos.avgPricePaisa = totalQty > 0 ? totalCost / totalQty : 0L;
                pos.quantity = totalQty;
            } else {
                // Closing or partially closing.
                long closeQty = Math.min(pos.quantity, fill.executedQuantity());
                long pnlPerUnit = fill.side() == Side.BUY
                        ? pos.avgPricePaisa - fill.executedPricePaisa()
                        : fill.executedPricePaisa() - pos.avgPricePaisa;
                long tradePnl = pnlPerUnit * closeQty;
                runningPnl += tradePnl;
                if (runningPnl > peakPnl) peakPnl = runningPnl;
                long dd = peakPnl - runningPnl;
                if (dd > maxDrawdown) maxDrawdown = dd;
                if (tradePnl > 0) {
                    grossProfit += tradePnl;
                    winCount++;
                } else if (tradePnl < 0) {
                    grossLoss += -tradePnl;
                }
                trades.add(new Trade(
                        pos.symbol, pos.side, pos.avgPricePaisa,
                        fill.executedPricePaisa(), closeQty,
                        pos.entryTimestampMs, fill.exchangeTimestampMs(),
                        tradePnl
                ));
                pos.quantity -= closeQty;
                if (pos.quantity == 0) {
                    pos.side = null;
                    pos.avgPricePaisa = 0L;
                    pos.entryTimestampMs = 0L;
                }
                long remaining = fill.executedQuantity() - closeQty;
                if (remaining > 0) {
                    // The remainder opens a position on the opposite side.
                    pos.open(fill.side(), fill.executedPricePaisa(), remaining, fill.exchangeTimestampMs());
                }
            }
            pnlSeries.add(new PnlPoint(fill.exchangeTimestampMs(), runningPnl));
        }

        int totalTrades = trades.size();
        int lossCount = totalTrades - winCount;
        double winRate = totalTrades > 0 ? (double) winCount / totalTrades : 0.0;
        Metrics metrics = new Metrics(
                totalTrades, winCount, lossCount, winRate,
                runningPnl, maxDrawdown, grossProfit, grossLoss
        );
        return new Result(
                Collections.unmodifiableList(trades),
                Collections.unmodifiableList(pnlSeries),
                metrics
        );
    }

    private static Metrics emptyMetrics() {
        return new Metrics(0, 0, 0, 0.0, 0L, 0L, 0L, 0L);
    }

    private static final class OpenPosition {
        String symbol;
        Side side;
        long avgPricePaisa;
        long quantity;
        long entryTimestampMs;

        boolean isEmpty() {
            return side == null || quantity == 0L;
        }

        void open(Side s, long pricePaisa, long qty, long ts) {
            this.side = s;
            this.avgPricePaisa = pricePaisa;
            this.quantity = qty;
            this.entryTimestampMs = ts;
        }
    }
}
