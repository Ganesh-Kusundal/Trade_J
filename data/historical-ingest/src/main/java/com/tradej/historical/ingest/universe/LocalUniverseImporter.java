package com.tradej.historical.ingest.universe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class LocalUniverseImporter {

    private static final Logger log = LoggerFactory.getLogger(LocalUniverseImporter.class);

    private final UniverseSnapshotWriter writer = new UniverseSnapshotWriter();

    public int importUniverse(Path root, Path csvPath, Path industryParquetPath) throws SQLException {
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("Universe CSV not found: " + csvPath);
        }
        if (!Files.exists(industryParquetPath)) {
            throw new IllegalArgumentException("Industry parquet not found: " + industryParquetPath);
        }

        List<Nifty500Constituent> constituents = loadConstituents(csvPath, industryParquetPath);
        writer.write(root, constituents);
        log.info("Imported {} Nifty 500 constituents from {} and {}", constituents.size(), csvPath, industryParquetPath);
        return constituents.size();
    }

    public List<String> loadSymbols(Path csvPath) throws SQLException {
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("Universe CSV not found: " + csvPath);
        }
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create or replace temporary view nifty500_csv as
                    select upper(trim("Symbol")) as symbol
                    from read_csv_auto('%s', header=true)
                    where trim("Symbol") <> ''
                    """.formatted(escapePath(csvPath)));
            List<String> symbols = new ArrayList<>();
            try (ResultSet rs = conn.createStatement().executeQuery("""
                    select symbol from nifty500_csv order by symbol asc
                    """)) {
                while (rs.next()) {
                    symbols.add(rs.getString("symbol"));
                }
            }
            return symbols;
        }
    }

    private List<Nifty500Constituent> loadConstituents(Path csvPath, Path industryParquetPath) throws SQLException {
        LocalDate asOfDate = LocalDate.now();
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:")) {
            conn.createStatement().execute("""
                    create or replace temporary view nifty500_csv as
                    select
                        upper(trim("Symbol")) as symbol,
                        trim("Company Name") as company_name,
                        trim("ISIN Code") as isin
                    from read_csv_auto('%s', header=true)
                    where trim("Symbol") <> ''
                    """.formatted(escapePath(csvPath)));
            conn.createStatement().execute("""
                    create or replace temporary view nifty500_industry_src as
                    select
                        upper(trim(symbol)) as symbol,
                        trim(industry) as industry
                    from read_parquet('%s')
                    where trim(symbol) <> ''
                    """.formatted(escapePath(industryParquetPath)));

            List<Nifty500Constituent> constituents = new ArrayList<>();
            try (ResultSet rs = conn.createStatement().executeQuery("""
                    select
                        c.symbol,
                        c.company_name,
                        coalesce(i.industry, '') as industry,
                        c.isin
                    from nifty500_csv c
                    left join nifty500_industry_src i using (symbol)
                    order by c.symbol asc
                    """)) {
                while (rs.next()) {
                    constituents.add(new Nifty500Constituent(
                            rs.getString("symbol"),
                            rs.getString("company_name"),
                            rs.getString("industry"),
                            null,
                            rs.getString("isin"),
                            asOfDate
                    ));
                }
            }
            return constituents;
        }
    }

    private static String escapePath(Path path) {
        return path.toAbsolutePath().toString().replace("'", "''");
    }
}
