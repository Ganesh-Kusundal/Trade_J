package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists {@link DhanTokenState} to a JSON file with strict schema
 * validation. Unknown fields cause load failure (LOW-1 fix: catches
 * schema drift the next time the state file is read, instead of silently
 * dropping new fields).
 */
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
            DhanTokenState state = objectMapper.readValue(
                    Files.readString(path),
                    DhanTokenState.class
            );
            if (state == null) {
                return Optional.empty();
            }
            // Defensive validation: reject obviously corrupt state.
            if (state.accessToken() == null || state.accessToken().isBlank()) {
                throw new IllegalStateException(
                        "Dhan token state file " + path + " is corrupt: accessToken is blank. " +
                                "Delete the file and re-mint.");
            }
            return Optional.of(state);
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException ex) {
            // Schema drift: state file has a field that DhanTokenState doesn't know.
            // Fail loudly so an operator can see it, rather than silently dropping data.
            throw new IllegalStateException(
                    "Dhan token state file " + path + " has an unrecognized field '" +
                            ex.getPropertyName() + "'. " +
                            "This usually means DhanTokenState was extended but the persisted " +
                            "file uses the old schema. Delete the file to force a re-mint.", ex);
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), state);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist Dhan token state to " + path, ex);
        }
    }
}
