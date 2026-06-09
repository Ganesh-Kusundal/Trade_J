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
 * Persistent command alias store.
 *
 * <p>Aliases are stored in {@code ~/.tradej/aliases.properties} and allow
 * users to create shortcuts for frequently used commands.
 *
 * <p>Example:
 * <pre>
 *   tradej alias add q quote
 *   tradej alias add oc chain
 *   tradej alias add pos portfolio positions
 *   tradej alias list
 *   tradej alias remove q
 * </pre>
 *
 * <p>After adding {@code q=quote}, typing {@code q RELIANCE} in the REPL
 * expands to {@code quote RELIANCE}.
 */
public final class AliasStore {

    private static final Logger log = LoggerFactory.getLogger(AliasStore.class);
    private static final Path ALIAS_FILE = Paths.get(
            System.getProperty("user.home", "."), ".tradej", "aliases.properties");

    private final Map<String, String> aliases;

    private AliasStore(Map<String, String> aliases) {
        this.aliases = aliases;
    }

    /**
     * Load aliases from disk.
     */
    public static AliasStore load() {
        Map<String, String> map = new LinkedHashMap<>();
        if (Files.exists(ALIAS_FILE)) {
            try (var reader = Files.newBufferedReader(ALIAS_FILE)) {
                Properties props = new Properties();
                props.load(reader);
                for (String key : props.stringPropertyNames()) {
                    map.put(key, props.getProperty(key));
                }
            } catch (IOException e) {
                log.warn("Failed to load aliases from {}: {}", ALIAS_FILE, e.getMessage());
            }
        }
        return new AliasStore(map);
    }

    /**
     * Add or update an alias.
     */
    public void add(String alias, String command) {
        aliases.put(alias, command);
        save();
    }

    /**
     * Remove an alias.
     */
    public boolean remove(String alias) {
        boolean removed = aliases.remove(alias) != null;
        if (removed) save();
        return removed;
    }

    /**
     * Get the command for an alias, or null if not found.
     */
    public String get(String alias) {
        return aliases.get(alias);
    }

    /**
     * Check if an alias exists.
     */
    public boolean has(String alias) {
        return aliases.containsKey(alias);
    }

    /**
     * Return all aliases.
     */
    public Map<String, String> all() {
        return Map.copyOf(aliases);
    }

    /**
     * Expand a command line by replacing the first token with its alias expansion.
     *
     * @param line the raw command line (e.g. "q RELIANCE NSE_EQ")
     * @return expanded line (e.g. "quote RELIANCE NSE_EQ"), or the original if no alias matches
     */
    public String expand(String line) {
        if (line == null || line.isBlank()) return line;
        String[] parts = line.trim().split("\\s+", 2);
        String firstToken = parts[0];
        String expansion = aliases.get(firstToken);
        if (expansion != null) {
            return parts.length > 1 ? expansion + " " + parts[1] : expansion;
        }
        return line;
    }

    private void save() {
        try {
            Files.createDirectories(ALIAS_FILE.getParent());
            Properties props = new Properties();
            props.putAll(aliases);
            try (var writer = Files.newBufferedWriter(ALIAS_FILE)) {
                props.store(writer, "Trade-J command aliases");
            }
        } catch (IOException e) {
            log.warn("Failed to save aliases to {}: {}", ALIAS_FILE, e.getMessage());
        }
    }
}
