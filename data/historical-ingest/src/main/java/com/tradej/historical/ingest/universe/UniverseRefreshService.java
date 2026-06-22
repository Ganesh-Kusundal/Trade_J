package com.tradej.historical.ingest.universe;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.instrument.StandardInstrumentIdentityService;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class UniverseRefreshService {

    private static final Logger log = LoggerFactory.getLogger(UniverseRefreshService.class);

    private final Nifty500UniverseFetcher fetcher;
    private final UniverseSnapshotWriter writer;
    private final Path rootPath;

    public UniverseRefreshService(
            Nifty500UniverseFetcher fetcher,
            UniverseSnapshotWriter writer,
            Path rootPath
    ) {
        this.fetcher = fetcher;
        this.writer = writer;
        this.rootPath = HistoricalEquityPaths.root(rootPath);
    }

    public UniverseRefreshResult refresh(InstrumentResolver instrumentResolver) throws Exception {
        List<Nifty500Constituent> fetched = fetcher.fetch();
        List<String> unresolved = new ArrayList<>();
        List<Nifty500Constituent> resolved = new ArrayList<>();
        for (Nifty500Constituent constituent : fetched) {
            InstrumentKey key = StandardInstrumentIdentityService.INSTANCE.equity(constituent.symbol());
            String symbol = key.symbol();
            if (instrumentResolver.resolve(key) == null) {
                unresolved.add(symbol);
                continue;
            }
            resolved.add(new Nifty500Constituent(
                    symbol,
                    constituent.companyName(),
                    constituent.industry(),
                    constituent.macroSector(),
                    constituent.isin(),
                    constituent.asOfDate()
            ));
        }
        if (resolved.isEmpty()) {
            throw new IllegalStateException("No Nifty 500 symbols resolved in Upstox NSE_EQ catalog");
        }
        writer.write(rootPath, resolved);
        log.info("Universe refresh complete: {} resolved, {} unresolved", resolved.size(), unresolved.size());
        return new UniverseRefreshResult(resolved.size(), unresolved.size(), unresolved, rootPath);
    }

    public record UniverseRefreshResult(
            int resolvedCount,
            int unresolvedCount,
            List<String> unresolvedSymbols,
            Path rootPath
    ) {
    }
}
