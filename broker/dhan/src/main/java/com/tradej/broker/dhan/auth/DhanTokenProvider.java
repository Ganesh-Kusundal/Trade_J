package com.tradej.broker.dhan.auth;

import com.tradej.broker.api.auth.BrokerTokenSource;

/**
 * @deprecated Use {@link BrokerTokenSource} from {@code broker-api} for new
 *             code. {@code DhanTokenManager} now implements
 *             {@link BrokerTokenSource} directly; this interface is preserved
 *             only for binary compatibility with downstream callers that
 *             have not yet migrated.
 */
@Deprecated
public interface DhanTokenProvider extends BrokerTokenSource {
    String getAccessToken();

    DhanTokenInfo getTokenInfo();

    /**
     * Atomic "ensure valid and return the access token" — closes the
     * time-of-check / time-of-use window in callers that previously did
     * {@code ensureValid(); getAccessToken();} on separate calls.
     *
     * <p>Default delegates to the two-step pattern; concrete managers
     * should override to provide a lock-protected single read.
     */
    default String ensureValidAndGet() {
        ensureValid();
        return getAccessToken();
    }

    /**
     * Monotonic counter incremented each time a new token is generated.
     */
    default long tokenGenerationId() { return 0; }

    /**
     * Compare-and-swap invalidation: only clears the token if the current
     * {@link #tokenGenerationId()} matches {@code failedGenerationId}.
     *
     * @return {@code true} if the token was actually invalidated
     */
    default boolean invalidate(long failedGenerationId) { invalidate(); return true; }

    /**
     * Default delegates to {@link #getAccessToken()} so test stubs and
     * static providers don't need to implement the new {@link BrokerTokenSource}
     * method separately.
     */
    @Override
    default String bearerToken() {
        return getAccessToken();
    }
}
