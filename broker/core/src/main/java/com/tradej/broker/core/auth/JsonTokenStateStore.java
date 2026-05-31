package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.api.auth.TokenSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON file-based token state store.
 * <p>
 * Stores token state as a JSON file at the configured path.
 * Intended for development and testing. Production deployments
 * should consider file permission hardening or encrypted storage.
 */
public final class JsonTokenStateStore implements TokenStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;

    public JsonTokenStateStore(Path filePath) {
        this.filePath = filePath;
    }

    @Override
    public TokenState load() {
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return MAPPER.readValue(filePath.toFile(), JsonTokenRecord.class).toTokenState();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void save(TokenState state) {
        if (state == null) {
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
            JsonTokenRecord record = JsonTokenRecord.from(state);
            Path tmp = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            MAPPER.writeValue(tmp.toFile(), record);
            Files.move(tmp, filePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save token state to " + filePath, e);
        }
    }

    @SuppressWarnings("unused") // Jackson serialization
    record JsonTokenRecord(
            String accessToken,
            String refreshToken,
            long expiryEpochMs,
            long issuedAtEpochMs,
            String source
    ) {
        static JsonTokenRecord from(TokenState state) {
            return new JsonTokenRecord(
                    state.accessToken(),
                    state.refreshToken(),
                    state.expiryEpochMs(),
                    state.issuedAtEpochMs(),
                    state.source().name()
            );
        }

        TokenState toTokenState() {
            return new TokenState(
                    accessToken,
                    refreshToken,
                    expiryEpochMs,
                    issuedAtEpochMs,
                    TokenSource.valueOf(source)
            );
        }
    }
}
