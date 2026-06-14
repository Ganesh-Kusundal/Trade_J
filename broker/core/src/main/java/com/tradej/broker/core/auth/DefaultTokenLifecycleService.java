package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.BrokerTokenSource;
import com.tradej.broker.api.auth.TokenAcquisitionThrottle;
import com.tradej.broker.api.auth.TokenLifecycleService;
import com.tradej.broker.api.auth.TokenState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Default thread-safe token lifecycle manager.
 * <p>
 * Handles the common token lifecycle patterns shared across brokers:
 * <ul>
 *   <li>Double-check locking with {@link ReentrantLock}</li>
 *   <li>On-disk persistence via {@link TokenStateStore}</li>
 *   <li>Refresh callback notification for client rotation</li>
 *   <li>Refresh scheduling via {@link #onExpiry} / {@link #onRefresh}</li>
 *   <li>Acquisition throttle (shared {@link TokenAcquisitionThrottle}) so
 *       a misbehaving revalidator / network blip can't churn the
 *       session token 5 times in 5 minutes</li>
 * </ul>
 * <p>
 * Subclasses override {@link #doAcquire()} and {@link #doRefresh(String)}
 * to implement broker-specific token generation.
 */
public abstract class DefaultTokenLifecycleService implements TokenLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenLifecycleService.class);
    private static final long FAILED_REFRESH_COOLDOWN_MS = 30_000L;

    protected final ReentrantLock lock = new ReentrantLock();
    private final TokenStateStore stateStore;
    private final long refreshBufferMs;
    // LOW-3: use the shared, dedup-by-reference registry so duplicate
    // onRefresh() registrations don't multiply the fan-out on each
    // rotation. Exceptions thrown by listeners are isolated so a single
    // bad listener cannot break the chain.
    private final BrokerTokenSource.CallbackRegistry callbackRegistry = new BrokerTokenSource.CallbackRegistry();
    private final AtomicLong lastFailedRefreshMs = new AtomicLong(0L);
    private final AtomicLong tokenGeneration = new AtomicLong(0L);
    /**
     * Shared throttle — single source of truth for cooldown + backoff
     * between token mints. Default: 5 min base, doubles on each
     * consecutive failure, capped at 30 min.
     */
    private final TokenAcquisitionThrottle throttle = new TokenAcquisitionThrottle();

    protected volatile TokenState currentState;

    protected DefaultTokenLifecycleService(TokenStateStore stateStore, long refreshBufferMs) {
        this.stateStore = stateStore;
        this.refreshBufferMs = refreshBufferMs;
        this.currentState = stateStore.load();
    }

    @Override
    public TokenState acquireToken() {
        lock.lock();
        try {
            TokenState state = doAcquire();
            currentState = state;
            stateStore.save(state);
            notifyRefresh();
            return state;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public java.util.concurrent.CompletableFuture<TokenState> acquireTokenAsync() {
        return java.util.concurrent.CompletableFuture.supplyAsync(this::acquireToken);
    }

    @Override
    public TokenState currentState() {
        return currentState;
    }

    @Override
    public void ensureValid() {
        TokenState state = currentState;
        if (state == null) {
            acquireToken();
            return;
        }
        // Double-check: if still valid, skip the lock
        if (state.valid() && !state.refreshRecommended(refreshBufferMs)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastFailedRefreshMs.get() < FAILED_REFRESH_COOLDOWN_MS) {
            return;
        }
        lock.lock();
        try {
            TokenState previous = currentState;
            state = currentState;
            if (state == null) {
                currentState = doAcquire();
                stateStore.save(currentState);
                notifyRefresh();
                lastFailedRefreshMs.set(0L);
                return;
            }
            if (state.valid() && !state.refreshRecommended(refreshBufferMs)) {
                return;
            }
            try {
                TokenState refreshed = doRefresh(state.refreshToken());
                // HIGH-2: a refreshed TokenState that loses the refresh token
                // would break the next refresh cycle. Brokers like Upstox
                // may legitimately omit the refresh_token in a response, so
                // preserve the old one to keep the rotation chain alive.
                if (refreshed != null
                        && (refreshed.refreshToken() == null || refreshed.refreshToken().isBlank())
                        && previous != null
                        && previous.refreshToken() != null
                        && !previous.refreshToken().isBlank()) {
                    refreshed = new TokenState(
                            refreshed.accessToken(),
                            previous.refreshToken(),
                            refreshed.expiryEpochMs(),
                            refreshed.issuedAtEpochMs(),
                            refreshed.source()
                    );
                }
                currentState = refreshed;
                stateStore.save(refreshed);
                notifyRefresh();
                lastFailedRefreshMs.set(0L);
            } catch (RuntimeException ex) {
                lastFailedRefreshMs.set(System.currentTimeMillis());
                throw ex;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void revoke() {
        lock.lock();
        try {
            doRevoke(currentState);
            currentState = null;
            stateStore.save(null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onExpiry(Runnable callback) {
        // The registry is the single sink for both expiry and refresh
        // listeners — it deduplicates by reference identity and isolates
        // listener exceptions. We log at debug so operators can still
        // confirm the wiring.
        callbackRegistry.onRefresh(callback);
        if (log.isDebugEnabled()) {
            log.debug("onExpiry callback registered (treated as refresh listener; {} total)",
                    callbackRegistry.refreshListenerCount());
        }
    }

    @Override
    public void onRefresh(Runnable callback) {
        callbackRegistry.onRefresh(callback);
        if (log.isDebugEnabled()) {
            log.debug("onRefresh callback registered ({} total)",
                    callbackRegistry.refreshListenerCount());
        }
    }

    /** Broker-specific token acquisition. Called under the write lock. */
    protected abstract TokenState doAcquire();

    /**
     * Broker-specific token refresh.
     *
     * @param refreshToken the refresh token to use (may be null for STATIC/TOTP)
     * @return the new token state
     */
    protected abstract TokenState doRefresh(String refreshToken);

    /** Broker-specific token revocation. Default is no-op. */
    protected void doRevoke(TokenState state) {
        // override if the broker supports token revocation
    }

    /**
     * Replaces the current token state atomically (used by webhook injection).
     * Called under the write lock by subclasses.
     */
    protected void replaceState(TokenState newState) {
        this.currentState = newState;
        stateStore.save(newState);
        notifyRefresh();
    }


    private void notifyRefresh() {
        callbackRegistry.fireRefresh();
    }
}
