package com.tradej.scanner.universe;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class IndexConstituentsLoader {
    private IndexConstituentsLoader() {
    }

    public static List<String> load(Path file) {
        if (file == null) {
            return List.of();
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Index constituents file does not exist: " + file);
        }
        try {
            List<String> symbols = new ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                symbols.add(trimmed.toUpperCase());
            }
            return List.copyOf(symbols);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read index constituents from " + file, e);
        }
    }
}
