package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public class DhanTokenStateStore {
    private final Path path;
    private final ObjectMapper objectMapper;

    public DhanTokenStateStore(Path path) {
        this(path, new ObjectMapper());
    }

    public DhanTokenStateStore(Path path, ObjectMapper objectMapper) {
        this.path = path;
        this.objectMapper = objectMapper;
    }

    public Optional<DhanTokenState> load() {
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readString(path), DhanTokenState.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load Dhan token state from " + path, ex);
        }
    }

    public void save(DhanTokenState state) {
        if (path == null) {
            return;
        }
        if (state == null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tempFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(state));
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist Dhan token state to " + path, ex);
        }
    }
}
