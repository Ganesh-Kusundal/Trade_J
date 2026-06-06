package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone portfolio commands for the Quant/Trader Workbench.
 *
 * <p>All commands work without {@code trade-app} — they delegate to the
 * broker session's portfolio provider. No Spring required.
 */
public final class CliPortfolioCommands extends CliCommandSupport {

    public CliPortfolioCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /** `tradej portfolio summary` — balance + position count + P&L. */
    public void summary() {
        Balance balance = portfolio().getBalance();
        List<Position> positions = portfolio().getPositions();
        List<Holding> holdings = portfolio().getHoldings();

        if (context().json()) {
            out().print(Map.of(
                    "balance", balance,
                    "positionCount", positions.size(),
                    "holdingsCount", holdings.size()
            ));
            return;
        }

        out().println("=== Portfolio Summary ===");
        out().println("Cash:         " + balance.cashPaisa() + " paisa");
        out().println("Utilized:     " + balance.utilizedPaisa() + " paisa");
        out().println("Withdrawable: " + balance.withdrawablePaisa() + " paisa");
        out().println("Open Positions: " + positions.size());
        out().println("Holdings:       " + holdings.size());
        out().println("");

        if (!positions.isEmpty()) {
            long grossExposure = 0L;
            for (Position pos : positions) {
                grossExposure += Math.abs(pos.quantity() * pos.lastPricePaisa());
            }
            out().println("Gross Exposure: " + grossExposure + " paisa");
        }
    }

    /** `tradej portfolio positions` — open positions with mark-to-market. */
    public void positions() {
        List<Position> positions = portfolio().getPositions();
        if (context().json()) {
            out().print(positions);
            return;
        }
        if (positions.isEmpty()) {
            out().println("No open positions.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Position pos : positions) {
            long mtm = (pos.lastPricePaisa() - pos.averagePricePaisa()) * pos.quantity();
            rows.add(new String[]{
                    pos.symbol(),
                    pos.exchangeSegment().name(),
                    String.valueOf(pos.quantity()),
                    String.valueOf(pos.averagePricePaisa()),
                    String.valueOf(pos.lastPricePaisa()),
                    String.valueOf(mtm)
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Segment", "Qty", "AvgPrice", "LTP", "MTM"}, rows);
    }

    /** `tradej portfolio holdings` — long-term holdings. */
    public void holdings() {
        List<Holding> h = portfolio().getHoldings();
        if (context().json()) {
            out().print(h);
            return;
        }
        if (h.isEmpty()) {
            out().println("No holdings.");
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Holding holding : h) {
            rows.add(new String[]{
                    holding.symbol(),
                    String.valueOf(holding.totalQuantity()),
                    String.valueOf(holding.averagePricePaisa())
            });
        }
        TablePrinter.print(new String[]{"Symbol", "Qty", "AvgPrice"}, rows);
    }

    /** `tradej portfolio pnl` — live P&L snapshot from positions. */
    public void pnl() {
        session().ensureCatalogLoaded();
        List<Position> positions = portfolio().getPositions();
        if (positions.isEmpty()) {
            if (context().json()) {
                out().print(Map.of("unrealizedPnlPaisa", 0L, "realizedPnlPaisa", 0L));
            } else {
                out().println("No positions — P&L is 0.");
            }
            return;
        }

        List<com.tradej.core.domain.model.InstrumentKey> keys = positions.stream()
                .map(p -> new com.tradej.core.domain.model.InstrumentKey(p.symbol(), p.exchangeSegment()))
                .toList();
        java.util.Map<com.tradej.core.domain.model.InstrumentKey, Long> ltps = marketData().getLtpBatch(keys);

        long unrealizedPnl = 0L;
        long netExposure = 0L;
        for (Position pos : positions) {
            var key = new com.tradej.core.domain.model.InstrumentKey(pos.symbol(), pos.exchangeSegment());
            long last = ltps.getOrDefault(key, pos.lastPricePaisa());
            unrealizedPnl += (last - pos.averagePricePaisa()) * pos.quantity();
            netExposure += Math.abs(last * pos.quantity());
        }

        if (context().json()) {
            out().print(Map.of(
                    "unrealizedPnlPaisa", unrealizedPnl,
                    "netExposurePaisa", netExposure,
                    "positionCount", positions.size()
            ));
        } else {
            out().println("Unrealized P&L: " + unrealizedPnl + " paisa");
            out().println("Net Exposure:   " + netExposure + " paisa");
            out().println("Positions:      " + positions.size());
        }
    }
}
