package com.tradej.cli.command;

import com.tradej.brokergateway.MarketGateway;
import com.tradej.brokergateway.query.OptionAnalytics;
import com.tradej.brokergateway.query.QueryEngine;
import com.tradej.brokergateway.result.GatewayResult;
import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CLI commands for market-level analytics: PCR, top OI, max pain, support/resistance.
 * These commands use the {@link QueryEngine} to fetch option chain data and compute analytics.
 */
public final class CliMarketCommands extends CliCommandSupport {

    public CliMarketCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void pcr(String underlying, String segmentName) {
        QueryEngine qe = createQueryEngine();
        ExchangeSegment segment = parseSegment(segmentName);
        OptionAnalytics.PcrResult result = qe.pcr(underlying, segment);
        if (context().json()) {
            out().print(Map.of(
                    "underlying", underlying,
                    "pcr", result.ratio(),
                    "totalCallOi", result.totalCallOi(),
                    "totalPutOi", result.totalPutOi(),
                    "totalCallVolume", result.totalCallVolume(),
                    "totalPutVolume", result.totalPutVolume()
            ));
            return;
        }
        out().println("PCR: " + underlying);
        out().println("  Ratio:        " + String.format("%.2f", result.ratio()));
        out().println("  Call OI:      " + result.totalCallOi());
        out().println("  Put OI:       " + result.totalPutOi());
        out().println("  Call Volume:  " + result.totalCallVolume());
        out().println("  Put Volume:   " + result.totalPutVolume());
    }

    public void topOi(String underlying, String segmentName, int top) {
        QueryEngine qe = createQueryEngine();
        ExchangeSegment segment = parseSegment(segmentName);
        List<OptionAnalytics.StrikeOi> results = qe.topOi(underlying, segment, top);
        if (context().json()) {
            out().print(Map.of("underlying", underlying, "topOi", results));
            return;
        }
        out().println("Top " + top + " OI: " + underlying);
        List<String[]> rows = new ArrayList<>();
        for (OptionAnalytics.StrikeOi s : results) {
            rows.add(new String[]{
                    String.valueOf(s.strikePricePaisa()),
                    String.valueOf(s.openInterest()),
                    s.side()
            });
        }
        TablePrinter.print(new String[]{"Strike", "OI", "Side"}, rows);
    }

    public void topVolume(String underlying, String segmentName, int top) {
        QueryEngine qe = createQueryEngine();
        ExchangeSegment segment = parseSegment(segmentName);
        List<OptionAnalytics.StrikeVolume> results = qe.topVolume(underlying, segment, top);
        if (context().json()) {
            out().print(Map.of("underlying", underlying, "topVolume", results));
            return;
        }
        out().println("Top " + top + " Volume: " + underlying);
        List<String[]> rows = new ArrayList<>();
        for (OptionAnalytics.StrikeVolume s : results) {
            rows.add(new String[]{
                    String.valueOf(s.strikePricePaisa()),
                    String.valueOf(s.volume()),
                    s.side()
            });
        }
        TablePrinter.print(new String[]{"Strike", "Volume", "Side"}, rows);
    }

    public void maxPain(String underlying, String segmentName) {
        QueryEngine qe = createQueryEngine();
        ExchangeSegment segment = parseSegment(segmentName);
        long maxPainStrike = qe.maxPain(underlying, segment);
        if (context().json()) {
            out().print(Map.of("underlying", underlying, "maxPainStrikePaisa", maxPainStrike));
            return;
        }
        out().println("Max Pain: " + underlying + " = " + maxPainStrike + " paisa");
    }

    public void support(String underlying, String segmentName) {
        QueryEngine qe = createQueryEngine();
        ExchangeSegment segment = parseSegment(segmentName);
        OptionAnalytics.SupportResistance sr = qe.supportResistance(underlying, segment);
        if (context().json()) {
            out().print(Map.of(
                    "underlying", underlying,
                    "supportStrike", sr.supportStrikePaisa(),
                    "supportOi", sr.supportOi(),
                    "resistanceStrike", sr.resistanceStrikePaisa(),
                    "resistanceOi", sr.resistanceOi()
            ));
            return;
        }
        out().println("Support/Resistance: " + underlying);
        out().println("  Support:    " + sr.supportStrikePaisa() + " paisa (OI=" + sr.supportOi() + ")");
        out().println("  Resistance: " + sr.resistanceStrikePaisa() + " paisa (OI=" + sr.resistanceOi() + ")");
    }

    private QueryEngine createQueryEngine() {
        MarketGateway marketGateway = MarketGateway.create(context().gateway());
        return new QueryEngine(marketGateway);
    }
}
