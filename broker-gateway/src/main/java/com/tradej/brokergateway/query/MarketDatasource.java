package com.tradej.brokergateway.query;

import java.sql.Connection;

/**
 * A data source that can register its tables / views into a DuckDB connection.
 *
 * <p>Implementations load data from broker historical APIs, CSV files, or
 * parquet stores and expose them as SQL tables.
 */
@FunctionalInterface
public interface MarketDatasource {

    /**
     * Register tables and views into the given DuckDB connection.
     *
     * @param connection an active DuckDB JDBC connection
     * @param name       the logical name this datasource was registered under
     */
    void register(Connection connection, String name);
}
