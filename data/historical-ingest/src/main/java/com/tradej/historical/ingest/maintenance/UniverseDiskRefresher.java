package com.tradej.historical.ingest.maintenance;

import com.tradej.historical.ingest.universe.HistoricalEquityPaths;
import com.tradej.historical.ingest.universe.Nifty500Constituent;
import com.tradej.historical.ingest.universe.UniverseSnapshotWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rebuilds universe parquet snapshots from on-disk hive symbol directories when
 * online Nifty 500 refresh is unavailable.
 */
public final class UniverseDiskRefresher {

    private static final Logger log = LoggerFactory.getLogger(UniverseDiskRefresher.class);
    private static final String INTERVAL_FOLDER = "interval=1m";

    private final UniverseSnapshotWriter writer = new UniverseSnapshotWriter();
    private final Path rootPath;

    public UniverseDiskRefresher(Path rootPath) {
        this.rootPath = HistoricalEquityPaths.root(rootPath);
    }

    public int refreshFromBars() throws SQLException, IOException {
        Path barsDir = HistoricalEquityPaths.barsDir(rootPath, INTERVAL_FOLDER);
        if (!Files.isDirectory(barsDir)) {
            throw new IllegalStateException("No equity bars directory at " + barsDir);
        }
        LocalDate asOfDate = LocalDate.now();
        List<Nifty500Constituent> constituents = new ArrayList<>();
        try (Stream<Path> symbolDirs = Files.list(barsDir)) {
            symbolDirs
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("symbol="))
                    .map(name -> name.substring("symbol=".length()))
                    .sorted(Comparator.naturalOrder())
                    .forEach(symbol -> constituents.add(new Nifty500Constituent(
                            symbol,
                            symbol,
                            "",
                            "",
                            "",
                            asOfDate
                    )));
        }
        if (constituents.isEmpty()) {
            throw new IllegalStateException("No symbol directories found under " + barsDir);
        }
        writer.write(rootPath, constituents);
        log.info("Refreshed universe from disk: {} symbols at {}", constituents.size(), rootPath);
        return constituents.size();
    }
}
