package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.OptionChainEntry;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.value.ExchangeSegment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone data commands for the Quant/Trader Workbench.
 *
 * <p>All commands work without {@code trade-app} — they delegate to the
 * broker session for live data and to the analytics engine for historical data.
 * No Spring required.
 */
public final class CliDataCommands extends CliCommandSupport {

    public CliDataCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /** `tradej data ltp RELIANCE` — last traded price. */
    public void ltp(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        long price = marketData().getLtpPaisa(instrument(symbol, segmentName));
        if (context().json()) {
            out().print(Map.of("symbol", symbol, "segment", segmentName, "ltpPaisa", price));
        } else {
            out().println(symbol + " LTP = " + price + " paisa");
        }
    }

    /** `tradej data quote RELIANCE` — full quote snapshot. */
    public void quote(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote q = marketData().getQuote(instrument(symbol, segmentName));
        out().print(q);
    }

    /** `tradej data depth RELIANCE` — market depth (bid/ask ladder). */
    public void depth(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        MarketDepth d = marketData().getDepth(instrument(symbol, segmentName));
        out().print(d);
    }

    /** `tradej data ohlc RELIANCE` — OHLC snapshot. */
    public void ohlc(String symbol, String segmentName) {
        session().ensureCatalogLoaded();
        Quote q = marketData().getOhlcSnapshot(instrument(symbol, segmentName));
        out().print(q);
    }

    /** `tradej data candles RELIANCE --interval 5m --from 2025-01-01 --to 2025-01-31` */
    public void candles(String symbol, String segmentName, String interval, LocalDate from, LocalDate to) {
        session().ensureCatalogLoaded();
        List<Candle> candles = marketData().getCandles(
                new com.tradej.core.domain.model.CandleHistoryRequest(
                        instrument(symbol, segmentName), interval, from, to));
        if (context().json()) {
            out().print(candles);
            return;
        }
        List<String[]> rows = new ArrayList<>();
        for (Candle c : candles) {
            rows.add(new String[]{
                    formatMs(c.startTimeMs()),
                    String.valueOf(c.openPaisa()),
                    String.valueOf(c.highPaisa()),
                    String.valueOf(c.lowPaisa()),
                    String.valueOf(c.closePaisa()),
                    String.valueOf(c.volume())
            });
        }
        TablePrinter.print(new String[]{"Time", "Open", "High", "Low", "Close", "Vol"}, rows);
    }

    /** `tradej data chain NIFTY --expiry 2025-01-30 --segment IDX_I` */
    public void optionChain(String underlying, String segmentName, LocalDate expiry) {
        session().ensureCatalogLoaded();
        ExchangeSegment seg = parseSegment(segmentName);
        OptionChainSnapshot chain = options().getOptionChain(underlying, seg, expiry);
        if (context().json()) {
            out().print(chain);
            return;
        }
        out().println("Underlying " + underlying + " expiry " + expiry + " spot=" + chain.spotPricePaisa());
        List<String[]> rows = new ArrayList<>();
        for (OptionChainEntry entry : chain.strikes()) {
            String callLtp = entry.call() == null ? "-" : String.valueOf(entry.call().ltpPaisa());
            String putLtp = entry.put() == null ? "-" : String.valueOf(entry.put().ltpPaisa());
            rows.add(new String[]{
                    String.valueOf(entry.strikePricePaisa()),
                    String.valueOf(entry.strikePricePaisa() / 100.0),
                    callLtp, putLtp
            });
        }
        TablePrinter.print(new String[]{"StrikePaisa", "StrikeRs", "CallLTP", "PutLTP"}, rows);
    }
}
