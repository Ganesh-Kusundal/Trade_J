package com.tradej.historical.ingest.universe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class UniverseSnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(UniverseSnapshotWriter.class);

    public void write(Path root, List<Nifty500Constituent> constituents) throws SQLException {
        Path universeDir = HistoricalEquityPaths.universeDir(root);
        try {
            Files.createDirectories(universeDir);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Unable to create universe directory " + universeDir, ex);
        }

        Path symbolsPath = HistoricalEquityPaths.symbolsParquet(root).toAbsolutePath();
        Path industryPath = HistoricalEquityPaths.industryParquet(root).toAbsolutePath();

        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create temporary table nifty500_symbols (
                        symbol varchar,
                        company_name varchar,
                        isin varchar,
                        as_of_date date
                    )
                    """);
            conn.createStatement().execute("""
                    create temporary table nifty500_industry (
                        symbol varchar,
                        industry varchar,
                        macro_sector varchar,
                        as_of_date date
                    )
                    """);

            try (PreparedStatement ps = conn.prepareStatement("""
                    insert into nifty500_symbols values (?, ?, ?, ?)
                    """)) {
                for (Nifty500Constituent constituent : constituents) {
                    ps.setString(1, constituent.symbol());
                    ps.setString(2, constituent.companyName());
                    ps.setString(3, constituent.isin());
                    ps.setObject(4, constituent.asOfDate());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            try (PreparedStatement ps = conn.prepareStatement("""
                    insert into nifty500_industry values (?, ?, ?, ?)
                    """)) {
                for (Nifty500Constituent constituent : constituents) {
                    ps.setString(1, constituent.symbol());
                    ps.setString(2, constituent.industry());
                    ps.setString(3, constituent.macroSector());
                    ps.setObject(4, constituent.asOfDate());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.createStatement().execute(
                    "copy nifty500_symbols to '" + escapePath(symbolsPath) + "' (format parquet, overwrite_or_ignore true)");
            conn.createStatement().execute(
                    "copy nifty500_industry to '" + escapePath(industryPath) + "' (format parquet, overwrite_or_ignore true)");
        }

        log.info("Wrote {} Nifty 500 constituents to {} and {}", constituents.size(), symbolsPath, industryPath);
    }

    private static String escapePath(Path path) {
        return path.toString().replace("'", "''");
    }
}
