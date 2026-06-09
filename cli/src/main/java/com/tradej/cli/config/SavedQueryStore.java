package com.tradej.cli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Persistent saved query store.
 *
 * <p>Queries are stored in {@code ~/.tradej/queries.properties} and allow
 * users to save frequently-used SQL queries for quick recall.
 *
 * <p>Example:
 * <pre>
 *   tradej query save top-oi "SELECT symbol, open_interest FROM option_chain ORDER BY open_interest DESC LIMIT 10"
 *   tradej query load top-oi
 *   tradej query list
 *   tradej query remove top-oi
 * </pre>
 */
public final class SavedQueryStore {

    private static final Logger log = LoggerFactory.getLogger(SavedQueryStore.class);
    private static final Path QUERY_FILE = Paths.get(
            System.getProperty("user.home", "."), ".tradej", "queries.properties");

    private final Map<String, String> queries;

    private SavedQueryStore(Map<String, String> queries) {
        this.queries = queries;
    }

    /**
     * Load saved queries from disk.
     */
    public static SavedQueryStore load() {
        Map<String, String> map = new LinkedHashMap<>();
        if (Files.exists(QUERY_FILE)) {
            try (var reader = Files.newBufferedReader(QUERY_FILE)) {
                Properties props = new Properties();
                props.load(reader);
                for (String key : props.stringPropertyNames()) {
                    map.put(key, props.getProperty(key));
                }
            } catch (IOException e) {
                log.warn("Failed to load queries from {}: {}", QUERY_FILE, e.getMessage());
            }
        }
        return new SavedQueryStore(map);
    }

    public void save(String name, String sql) {
        queries.put(name, sql);
        persist();
    }

    public boolean remove(String name) {
        boolean removed = queries.remove(name) != null;
        if (removed) persist();
        return removed;
    }

    public String get(String name) {
        return queries.get(name);
    }

    public boolean has(String name) {
        return queries.containsKey(name);
    }

    public Map<String, String> all() {
        return Map.copyOf(queries);
    }

    private void persist() {
        try {
            Files.createDirectories(QUERY_FILE.getParent());
            Properties props = new Properties();
            props.putAll(queries);
            try (var writer = Files.newBufferedWriter(QUERY_FILE)) {
                props.store(writer, "Trade-J saved queries");
            }
        } catch (IOException e) {
            log.warn("Failed to save queries to {}: {}", QUERY_FILE, e.getMessage());
        }
    }
}
