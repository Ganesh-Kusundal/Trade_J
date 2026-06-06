package com.tradej.cli.command;

import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Broker certification CLI — comprehensive smoke test against a live broker.
 * Reports PASS/FAIL/PARTIAL with per-check evidence.
 *
 * <p>{@code tradej broker-validate --symbol RELIANCE --segment IDX_I}
 */
public final class CliBrokerCertCommands extends CliCommandSupport {

    public CliBrokerCertCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void validate(String symbol, String segmentName, boolean skipOrderOps) {
        session().ensureCatalogLoaded();
        MarketDataProvider md = marketData();
        PortfolioProvider pf = portfolio();
        OptionsProvider opt = options();
        OrderQuery oq = orderQuery();

        ExchangeSegment seg = parseSegment(segmentName);
        InstrumentKey key = instrument(symbol, segmentName);

        List<String[]> results = new ArrayList<>();
        int passed = 0, failed = 0;

        results.addAll(runCheck("ltp", () -> {
            long ltp = md.getLtpPaisa(key);
            return "PASS (ltp=" + ltp + ")";
        }));
        passed += "PASS".equals(results.get(results.size()-1)[1]) ? 1 : 0;
        failed += "FAIL".equals(results.get(results.size()-1)[1]) ? 1 : 0;

        results.addAll(runCheck("quote", () -> {
            Quote q = md.getQuote(key);
            return "PASS (ltp=" + q.ltpPaisa() + " open=" + q.openPaisa() + ")";
        }));

        results.addAll(runCheck("depth", () -> {
            var d = md.getDepth(key);
            return "PASS (bids=" + d.bids().size() + " asks=" + d.asks().size() + ")";
        }));

        results.addAll(runCheck("ohlc", () -> {
            Quote ohlc = md.getOhlcSnapshot(key);
            return "PASS (o=" + ohlc.openPaisa() + " h=" + ohlc.highPaisa()
                    + " l=" + ohlc.lowPaisa() + " c=" + ohlc.closePaisa() + ")";
        }));

        results.addAll(runCheck("candles", () -> {
            List<Candle> candles = md.getCandles(
                    new CandleHistoryRequest(key, "5m", LocalDate.now().minusDays(5), LocalDate.now()));
            return "PASS (count=" + candles.size() + ")";
        }));

        results.addAll(runCheck("portfolio", () -> {
            var bal = pf.getBalance();
            return "PASS (cash=" + bal.cashPaisa() + ")";
        }));

        results.addAll(runCheck("positions", () -> {
            var pos = pf.getPositions();
            return "PASS (count=" + pos.size() + ")";
        }));

        results.addAll(runCheck("holdings", () -> {
            var h = pf.getHoldings();
            return "PASS (count=" + h.size() + ")";
        }));

        results.addAll(runCheck("options", () -> {
            var expiries = opt.getExpiries(symbol, ExchangeSegment.IDX_I);
            return "PASS (expiries=" + expiries.size() + ")";
        }));

        results.addAll(runCheck("orderBook", () -> {
            var orders = oq.getOrderBook();
            return "PASS (count=" + orders.size() + ")";
        }));

        // Compute summary
        int passCount = (int) results.stream().filter(r -> "PASS".equals(r[1])).count();
        int failCount = (int) results.stream().filter(r -> "FAIL".equals(r[1])).count();
        int total = results.size();
        String overall = failCount == 0 ? "PASS" : (passCount > 0 ? "PARTIAL" : "FAIL");

        if (context().json()) {
            out().print(Map.of(
                    "broker", context().brokerType().name(),
                    "profile", context().profile().name(),
                    "symbol", symbol,
                    "results", results,
                    "summary", Map.of("overall", overall, "passed", passCount, "failed", failCount, "total", total)
            ));
            return;
        }

        out().println("");
        out().println("=== Broker Validation Report ===");
        out().println("  Broker:  " + context().brokerType());
        out().println("  Profile: " + context().profile());
        out().println("  Symbol:  " + symbol);
        out().println("");
        TablePrinter.print(new String[]{"Check", "Result"},
                results.stream().map(r -> new String[]{r[0], r[1]}).toList());
        out().println("");
        out().println("  Result: " + overall + " (" + passCount + "/" + total + " passed)");
    }

    private List<String[]> runCheck(String name, CheckRunner runner) {
        try {
            String result = runner.run();
            return List.<String[]>of(new String[]{name, result});
        } catch (Exception e) {
            return List.<String[]>of(new String[]{name, "FAIL: " + e.getMessage()});
        }
    }

    @FunctionalInterface
    private interface CheckRunner {
        String run() throws Exception;
    }
}
