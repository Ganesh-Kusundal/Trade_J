package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenState;

/**
 * Abstract persistence for broker token state.
 * <p>
 * Implementations handle encryption and file I/O for token state storage.
 * Each broker adapter provides its own subclass with the appropriate
 * file path and encryption strategy.
 */
public interface TokenStateStore {

    /**
     * Loads the persisted token state from storage.
     *
     * @return the token state, or {@code null} if no state is persisted
     */
    TokenState load();

    /**
     * Persists the given token state to storage.
     *
     * @param state the token state to persist, or {@code null} to clear storage
     */
    void save(TokenState state);
}
