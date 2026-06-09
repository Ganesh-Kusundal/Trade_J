package com.tradej.cli.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Persistent command macro store.
 *
 * <p>Macros allow recording and replaying sequences of commands.
 * Stored in {@code ~/.tradej/macros.properties} with command sequences
 * separated by {@code |}.
 *
 * <p>Example:
 * <pre>
 *   tradej macro add morning "quote RELIANCE" "chain NIFTY --expiry nearest" "balance"
 *   tradej macro run morning
 *   tradej macro list
 *   tradej macro remove morning
 * </pre>
 */
public final class MacroStore {

    private static final Logger log = LoggerFactory.getLogger(MacroStore.class);
    private static final Path MACRO_FILE = Paths.get(
            System.getProperty("user.home", "."), ".tradej", "macros.properties");
    private static final String SEPARATOR = "|||";

    private final Map<String, List<String>> macros;

    private MacroStore(Map<String, List<String>> macros) {
        this.macros = macros;
    }

    public static MacroStore load() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        if (Files.exists(MACRO_FILE)) {
            try (var reader = Files.newBufferedReader(MACRO_FILE)) {
                Properties props = new Properties();
                props.load(reader);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key);
                    List<String> commands = List.of(value.split("\\|\\|\\|"));
                    map.put(key, commands);
                }
            } catch (IOException e) {
                log.warn("Failed to load macros from {}: {}", MACRO_FILE, e.getMessage());
            }
        }
        return new MacroStore(map);
    }

    public void add(String name, List<String> commands) {
        macros.put(name, new ArrayList<>(commands));
        persist();
    }

    public boolean remove(String name) {
        boolean removed = macros.remove(name) != null;
        if (removed) persist();
        return removed;
    }

    public List<String> get(String name) {
        List<String> cmds = macros.get(name);
        return cmds != null ? List.copyOf(cmds) : null;
    }

    public boolean has(String name) {
        return macros.containsKey(name);
    }

    public Map<String, List<String>> all() {
        return Map.copyOf(macros);
    }

    private void persist() {
        try {
            Files.createDirectories(MACRO_FILE.getParent());
            Properties props = new Properties();
            for (var entry : macros.entrySet()) {
                props.setProperty(entry.getKey(), String.join(SEPARATOR, entry.getValue()));
            }
            try (var writer = Files.newBufferedWriter(MACRO_FILE)) {
                props.store(writer, "Trade-J command macros");
            }
        } catch (IOException e) {
            log.warn("Failed to save macros to {}: {}", MACRO_FILE, e.getMessage());
        }
    }
}
