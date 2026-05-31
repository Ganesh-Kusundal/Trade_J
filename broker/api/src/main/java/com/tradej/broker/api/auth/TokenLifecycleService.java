package com.tradej.broker.api.auth;

import java.util.concurrent.CompletableFuture;

/**
 * Contract for broker token lifecycle management.
 * <p>
 * Implementations handle broker-specific authentication flows:
 * <ul>
 *   <li><b>STATIC:</b> Single access token with no refresh (e.g. Dhan with pre-generated token)</li>
 *   <li><b>TOTP:</b> Token generated via PIN + TOTP (Dhan)</li>
 *   <li><b>OAUTH:</b> Authorization Code Grant with PKCE + refresh token rotation (Upstox)</li>
 * </ul>
 * <p>
 * The OMS and adapter infrastructure depend ONLY on this interface.
 * Broker-specific auth complexity is encapsulated in the implementation.
 */
public interface TokenLifecycleService {

    /**
     * Acquires a fresh access token. Blocks until the token is available.
     * On first call this may involve a full authentication flow (TOTP or OAuth).
     */
    TokenState acquireToken();

    /**
     * Asynchronously acquires a fresh access token for non-blocking init.
     */
    CompletableFuture<TokenState> acquireTokenAsync();

    /**
     * Returns the current token state without triggering any refresh.
     * Never blocks.
     */
    TokenState currentState();

    /**
     * Ensures the current token is valid, refreshing if necessary.
     * This is the primary method called before every broker API operation.
     * Implementations must be thread-safe with double-check locking.
     */
    void ensureValid();

    /**
     * Revokes the current token. After this, {@link #acquireToken()} must be called again.
     */
    void revoke();

    /**
     * Registers a callback to be invoked when the token is about to expire.
     * Useful for pre-emptive refresh scheduling.
     */
    void onExpiry(Runnable callback);

    /**
     * Registers a callback to be invoked after a successful token refresh.
     * Used by {@code ClientHolder} to rebuild HTTP clients with the new token.
     */
    void onRefresh(Runnable callback);
}
