package com.tradej.broker.icici.auth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class BreezeTokenStateStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;

    public BreezeTokenStateStore(Path filePath) {
        this.filePath = filePath;
    }

    public Optional<BreezeSession> load() {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(filePath.toFile(), BreezeSession.class));
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    public void save(BreezeSession session) {
        if (session == null) {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }
            return;
        }
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), session);
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save ICICI token state to " + filePath, ex);
        }
    }
}
