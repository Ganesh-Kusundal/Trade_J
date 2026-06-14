package com.tradej.broker.upstox.auth;

import com.tradej.broker.api.auth.BrokerTokenSource;

/**
 * Supplies a Bearer token for Upstox REST and WebSocket authorize calls.
 *
 * @deprecated Use {@link BrokerTokenSource} from {@code broker-api} for new
 *             code. {@code UpstoxTokenManager} (and the static/analytics
 *             holders) now implement {@link BrokerTokenSource} directly;
 *             this interface is preserved only for binary compatibility.
 */
@Deprecated
public interface UpstoxBearerTokenSource extends BrokerTokenSource {

    @Override
    String bearerToken();

    @Override
    void ensureValid();

    @Override
    default void invalidate() {
        // Default: no-op for static/analytics holders. Token managers
        // (UpstoxTokenManager) override this with real behavior.
    }

    @Override
    default void onRefresh(Runnable callback) {
        // Default: no-op. Implementations with a registry override this.
        if (callback == null) return;
    }

    @Override
    default void onInvalidate(Runnable callback) {
        // Default: no-op. Implementations with a registry override this.
        if (callback == null) return;
    }

    /** Token expiry epoch millis, or {@code -1} if unknown. */
    @Override
    default long expiryEpochMs() {
        return -1L;
    }

    default boolean analyticsOnly() {
        return false;
    }
}
