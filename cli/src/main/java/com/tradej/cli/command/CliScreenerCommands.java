package com.tradej.cli.command;

import com.tradej.cli.CliContext;
import com.tradej.cli.output.OutputFormatter;
import com.tradej.cli.output.TablePrinter;
import com.tradej.cli.scan.ScanProfileJsonLoader;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.ScanProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Screener commands — runs scan profiles directly using the broker connection,
 * no running app required.
 */
public final class CliScreenerCommands extends CliCommandSupport {

    public CliScreenerCommands(CliContext context, OutputFormatter out) {
        super(context, out);
    }

    /** `tradej screener run --profile momentum` */
    public void run(String profileId) throws Exception {
        session().ensureCatalogLoaded();
        var broker = session().connection();
        ScanProfile profile = ScanProfileJsonLoader.load(profileId);
        ScanEngine engine = new ScanEngine(new ScanDependencies(
                broker.instruments(),
                broker.marketData(),
                broker.options(),
                broker.futures()
        ));
        ScanResult result = engine.run(profile);
        if (context().json()) {
            out().print(Map.of(
                    "runId", result.run().runId(),
                    "profileId", result.run().profileId(),
                    "status", result.run().status().name(),
                    "hitCount", result.hits().size(),
                    "hits", result.hits()
            ));
            return;
        }
        out().println("Screener " + result.run().runId()
                + " profile=" + profileId
                + " status=" + result.run().status()
                + " hits=" + result.hits().size());
        printHits(result);
    }

    /** `tradej screener results --profile momentum --last 5` */
    public void results(String profileId, int last) throws Exception {
        out().println("Screener results for profile: " + profileId);
        out().println("Use `tradej scan list --profile " + profileId + " --last " + last
                + " --attach http://localhost:8080` for historical results from a running app.");
        out().println("Or run `tradej screener run --profile " + profileId + "` for a fresh scan.");
    }

    private void printHits(ScanResult result) {
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
}
