package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenLifecycleService;
import com.tradej.broker.api.auth.TokenState;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * </ul>
 * <p>
 * Subclasses override {@link #doAcquire()} and {@link #doRefresh(String)}
 * to implement broker-specific token generation.
 */
public abstract class DefaultTokenLifecycleService implements TokenLifecycleService {

    private static final long FAILED_REFRESH_COOLDOWN_MS = 30_000L;

    protected final ReentrantLock lock = new ReentrantLock();
    private final TokenStateStore stateStore;
    private final long refreshBufferMs;
    private final List<Runnable> expiryCallbacks = new CopyOnWriteArrayList<>();
    private final List<Runnable> refreshCallbacks = new CopyOnWriteArrayList<>();
 private final AtomicLong lastFailedRefreshMs = new AtomicLong(0L);
 private final AtomicLong tokenGeneration = new AtomicLong(0L);

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
        expiryCallbacks.add(callback);
    }

    @Override
    public void onRefresh(Runnable callback) {
        refreshCallbacks.add(callback);
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
        refreshCallbacks.forEach(Runnable::run);
    }
}
