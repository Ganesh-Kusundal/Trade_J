package com.tradej.cli.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.cli.scan.ScanProfileJsonLoader;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.option.OptionContractHit;
import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionLiquidityScanner;
import com.tradej.scanner.option.OptionScanRequest;
import com.tradej.scanner.option.OptionScanResult;
import com.tradej.scanner.option.OptionSideFilter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CliScanCommands extends CliCommandSupport {

    public CliScanCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    public void scanRun(String profileId) throws Exception {
        if (context().attachReachable()) {
            JsonNode result = context().attach().scanRun(profileId);
            if (context().json()) {
                out().print(result);
                return;
            }
            printScanResult(result);
            return;
        }
        session().ensureCatalogLoaded();
        var broker = session().connection();
        ScanProfile profile = ScanProfileJsonLoader.load(profileId);
        ScanResult result = profile.isOptionLiquidityProfile()
                ? new OptionLiquidityScanner(broker.options()).scanProfile(profile)
                : new ScanEngine(new ScanDependencies(
                        broker.instruments(),
                        broker.marketData(),
                        broker.options(),
                        broker.futures()
                )).run(profile);
        if (context().json()) {
            out().print(Map.of(
                    "runId", result.run().runId(),
                    "profileId", result.run().profileId(),
                    "status", result.run().status().name(),
                    "hits", result.hits()
            ));
            return;
        }
        out().println("Scan " + result.run().runId() + " status=" + result.run().status()
                + " hits=" + result.hits().size());
        printScanHits(result);
    }

    public void optionsScan(
            String underlying,
            String segmentName,
            String expiryPolicy,
            LocalDate explicitExpiry,
            String side,
            int top,
            long minOi,
            long minVolume,
            double maxSpreadBps,
            boolean strictSpread
    ) throws Exception {
        if (context().attachReachable()) {
            JsonNode result = context().attach().optionsScan(
                    underlying,
                    segmentName,
                    expiryPolicy,
                    explicitExpiry,
                    side,
                    top,
                    minOi,
                    minVolume,
                    maxSpreadBps,
                    strictSpread
            );
            if (context().json()) {
                out().print(result);
                return;
            }
            printOptionScanResult(result);
            return;
        }
        session().ensureCatalogLoaded();
        ExchangeSegment segment = parseSegment(segmentName);
        OptionScanSpec spec = new OptionScanSpec(
                OptionExpiryPolicy.parse(expiryPolicy),
                explicitExpiry,
                OptionSideFilter.parse(side),
                minOi,
                minVolume,
                maxSpreadBps,
                strictSpread,
                top,
                0
        );
        OptionScanResult result = new OptionLiquidityScanner(options())
                .scan(new OptionScanRequest(underlying, segment, spec));
        if (context().json()) {
            out().print(result);
            return;
        }
        printOptionScanResultStandalone(result);
    }

    public void scanList(String profileId, int last) throws Exception {
        if (context().attachReachable()) {
            JsonNode result = context().attach().scanList(profileId, last);
            if (context().json()) {
                out().print(result);
                return;
            }
            printJsonArrayTable(result.get("runs"), "runId", "status", "hitCount", "startedAtMs");
            return;
        }
        out().println("scan list requires --attach to a running trade-app instance");
    }

    private void printScanResult(JsonNode result) {
        out().println("runId=" + result.path("run").path("runId").asText()
                + " status=" + result.path("run").path("status").asText()
                + " hits=" + result.path("hits").size());
        printJsonArrayTable(result.get("hits"), "symbol", "exchangeSegment", "score", "assetClass");
    }

    private void printScanHits(ScanResult result) {
        List<String[]> rows = new ArrayList<>();
        for (ScanHit hit : result.hits()) {
            rows.add(new String[]{
                    hit.symbol(),
                    hit.exchangeSegment().name(),
                    hit.assetClass().name(),
                    String.format("%.2f", hit.score()),
                    String.join("; ", hit.reasons())
            });
        }
        TablePrinter.print(new String[]{"symbol", "segment", "class", "score", "reasons"}, rows);
    }

    private void printOptionScanResult(JsonNode result) {
        out().println("underlying=" + result.path("underlying").asText()
                + " expiry=" + result.path("expiry").asText()
                + " status=" + result.path("status").asText()
                + " contracts=" + result.path("contracts").size());
        printJsonArrayTable(
                result.get("contracts"),
                "symbol", "optionType", "strikePaisa", "openInterest", "volume", "spreadBps", "score"
        );
    }

    private void printOptionScanResultStandalone(OptionScanResult result) {
        out().println("underlying=" + result.underlying()
                + " expiry=" + result.expiry()
                + " status=" + result.run().status()
                + " contracts=" + result.contracts().size());
        List<String[]> rows = new ArrayList<>();
        for (OptionContractHit hit : result.contracts()) {
            rows.add(new String[]{
                    hit.instrumentKey().symbol(),
                    hit.optionType().name(),
                    String.valueOf(hit.strikePricePaisa()),
                    String.valueOf(hit.openInterest()),
                    String.valueOf(hit.volume()),
                    String.format("%.1f", hit.spreadBps()),
                    String.format("%.2f", hit.liquidityScore())
            });
        }
        TablePrinter.print(new String[]{
                "symbol", "type", "strikePaisa", "oi", "volume", "spreadBps", "score"
        }, rows);
    }
}
