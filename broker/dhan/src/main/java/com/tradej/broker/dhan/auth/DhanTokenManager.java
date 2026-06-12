package com.tradej.broker.dhan.auth;

import com.tradej.broker.api.auth.BrokerTokenSource;
import com.tradej.broker.api.auth.TokenAcquisitionThrottle;
import com.tradej.broker.api.auth.TokenLifecycleService;
import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class DhanTokenManager implements DhanTokenProvider, TokenLifecycleService, BrokerTokenSource {
    private static final Logger log = LoggerFactory.getLogger(DhanTokenManager.class);
    /** Base cooldown between token mints. 5 minutes. */
    private static final long TOKEN_ACQUISITION_COOLDOWN_MS = 5L * 60_000L;
    /** Clock skew tolerance in milliseconds (30 seconds) */
    private static final long CLOCK_SKEW_TOLERANCE_MS = 30_000L;

    private final DhanConnectionSettings settings;
    private final DhanAuthClient authClient;
    private final DhanTotpGenerator totpGenerator;
    private final DhanTokenStateStore stateStore;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final TokenAcquisitionThrottle throttle = new TokenAcquisitionThrottle();
    private final AtomicLong lastAcquisitionAttemptMs = new AtomicLong(0L);
    private final AtomicLong tokenGeneration = new AtomicLong(0);
    private final BrokerTokenSource.CallbackRegistry callbackRegistry = new BrokerTokenSource.CallbackRegistry();

    private volatile DhanTokenState currentState;

    public DhanTokenManager(DhanConnectionSettings settings) {
        this(
                settings,
                new DhanAuthClient(),
                new DhanTotpGenerator(),
                new DhanTokenStateStore(settings.tokenStateFile()),
                Clock.systemDefaultZone()
        );
    }

    DhanTokenManager(
            DhanConnectionSettings settings,
            DhanAuthClient authClient,
            DhanTotpGenerator totpGenerator,
            DhanTokenStateStore stateStore,
            Clock clock
    ) {
        this.settings = settings;
        this.authClient = authClient;
        this.totpGenerator = totpGenerator;
        this.stateStore = stateStore;
        this.clock = clock;
        this.currentState = stateStore.load().orElse(null);
    }

    @Override
    public String getAccessToken() {
        if (settings.authMode() == DhanAuthMode.STATIC) {
            return requireBootstrapToken();
        }
        ensureValid();
        DhanTokenState state = currentState;
        if (state == null || state.accessToken() == null || state.accessToken().isBlank()) {
            throw new IllegalStateException("Dhan token manager did not resolve an access token");
        }
        return state.accessToken();
    }

    @Override
    public DhanTokenInfo getTokenInfo() {
        if (settings.authMode() == DhanAuthMode.STATIC) {
            return authClient.fetchProfile(requireBootstrapToken(), settings.refreshBufferMillis());
        }
        ensureValid();
        DhanTokenState state = currentState;
        long now = clock.millis();
        return new DhanTokenInfo(
                state != null && state.expiryEpochMs() > now,
                state == null ? 0L : state.expiryEpochMs(),
                state == null || state.expiryEpochMs() <= now + settings.refreshBufferMillis()
        );
    }

    @Override
    public void ensureValid() {
        if (settings.authMode() == DhanAuthMode.STATIC) {
            return;
        }
        long now = clock.millis();
        if (isReusable(currentState, now)) {
            return;
        }
        refreshLock.lock();
        try {
            long lockedNow = clock.millis();
            if (isReusable(currentState, lockedNow)) {
                return;
            }
            currentState = resolveValidState(lockedNow);
            if (currentState == null) {
                throw new IllegalStateException("Unable to resolve a valid Dhan access token");
            }
            tokenGeneration.incrementAndGet();
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public long tokenGenerationId() {
        return tokenGeneration.get();
    }

    @Override
    public void invalidate() {
        refreshLock.lock();
        try {
            DhanTokenState previous = currentState;
            currentState = null;
            stateStore.save(null);
            tokenGeneration.incrementAndGet();
            log.info("Dhan token invalidated — next ensureValid() will generate a fresh token");
            if (previous != null) {
                callbackRegistry.fireInvalidate();
            }
        } finally {
            refreshLock.unlock();
        }
    }

    // ── BrokerTokenSource SPI Implementation ────────────────────────

    @Override
    public String bearerToken() {
        return getAccessToken();
    }

    @Override
    public long expiryEpochMs() {
        DhanTokenState state = currentState;
        return state == null ? -1L : state.expiryEpochMs();
    }

    @Override
    public void onInvalidate(Runnable callback) {
        callbackRegistry.onInvalidate(callback);
        log.debug("onInvalidate callback registered ({} total)", callbackRegistry.invalidateListenerCount());
    }

    /**
     * Atomic "ensure valid and return the access token" — closes the
     * time-of-check / time-of-use window in callers that previously did
     * {@code ensureValid(); getAccessToken();} on separate calls.
     */
    public String ensureValidAndGet() {
        if (settings.authMode() == DhanAuthMode.STATIC) {
            return requireBootstrapToken();
        }
        ensureValid();
        DhanTokenState state = currentState;
        if (state == null || state.accessToken() == null || state.accessToken().isBlank()) {
            throw new IllegalStateException("Dhan token manager did not resolve an access token");
        }
        return state.accessToken();
    }

    /**
     * Returns the raw {@link DhanTokenState} snapshot, or {@code null} if
     * the cache is empty. Used by the background revalidator to read the
     * current access token without forcing a mint.
     */
    public DhanTokenState currentSnapshot() {
        return currentState;
    }

    /**
     * Updates only the {@code expiryEpochMs} of the cached token, preserving
     * the access token. Used by the scheduled revalidation task to keep the
     * cached expiry in sync with the broker's authoritative view without
     * triggering a full re-mint.
     *
     * @param brokerExpiryEpochMs the new expiry as reported by the broker
     * @return {@code true} if the cached expiry was updated
     */
    public boolean updateCachedExpiry(long brokerExpiryEpochMs) {
        DhanTokenState snapshot = this.currentState;
        if (snapshot == null || snapshot.accessToken() == null || snapshot.accessToken().isBlank()) {
            return false;
        }
        if (brokerExpiryEpochMs == snapshot.expiryEpochMs()) {
            return true;
        }
        refreshLock.lock();
        try {
            snapshot = this.currentState;
            if (snapshot == null) {
                return false;
            }
            DhanTokenState updated = new DhanTokenState(
                    snapshot.accessToken(),
                    brokerExpiryEpochMs,
                    snapshot.issuedAtEpochMs(),
                    snapshot.source()
            );
            this.currentState = updated;
            stateStore.save(updated);
            log.info("Dhan token cached expiry updated to {} (source={})",
                    Instant.ofEpochMilli(brokerExpiryEpochMs), updated.source());
            callbackRegistry.fireRefresh();
            return true;
        } finally {
            refreshLock.unlock();
        }
    }

    /**
     * Constructs a {@link DhanTokenRevalidator} bound to this manager.
     * The revalidator uses this manager's own {@link DhanAuthClient},
     * so the lifecycle is shared.
     */
    public DhanTokenRevalidator newRevalidator() {
        return new DhanTokenRevalidator(this, authClient, settings);
    }

    // ── TokenLifecycleService SPI Implementation ──────────────────────

    @Override
    public TokenState acquireToken() {
        long now = clock.millis();
        DhanTokenState state = resolveValidState(now);
        if (state == null) {
            throw new IllegalStateException("Unable to acquire a valid Dhan access token");
        }
        return toTokenState(state);
    }

    @Override
    public CompletableFuture<TokenState> acquireTokenAsync() {
        return CompletableFuture.supplyAsync(this::acquireToken);
    }

    @Override
    public TokenState currentState() {
        DhanTokenState state = currentState;
        return state != null ? toTokenState(state) : null;
    }

    @Override
    public void revoke() {
        invalidate();
    }

    @Override
    public void onExpiry(Runnable callback) {
        // Dhan tokens are short-lived and auto-refreshed via ensureValid()
        // Pre-emptive expiry callbacks are not applicable
        log.debug("onExpiry callback registered (no-op for Dhan auto-refresh model)");
    }

    @Override
    public void onRefresh(Runnable callback) {
        // Dhan token manager auto-refreshes via ensureValid()
        // Callback notification is supported via BrokerTokenSource registry.
        callbackRegistry.onRefresh(callback);
        log.debug("onRefresh callback registered ({} total)", callbackRegistry.refreshListenerCount());
    }

    private TokenState toTokenState(DhanTokenState dhanState) {
        if (dhanState == null) {
            return null;
        }
        return new TokenState(
                dhanState.accessToken(),
                null, // Dhan does not use refresh tokens
                dhanState.expiryEpochMs(),
                dhanState.issuedAtEpochMs(),
                TokenSource.STATIC
        );
    }

    public boolean invalidate(long failedGenerationId) {
        if (!tokenGeneration.compareAndSet(failedGenerationId, failedGenerationId + 1)) {
            log.debug("Dhan invalidate({}) skipped — another thread already regenerated (current gen={})",
                    failedGenerationId, tokenGeneration.get());
            return false;
        }
        refreshLock.lock();
        try {
            if (tokenGeneration.get() != failedGenerationId + 1) {
                return false;
            }
            currentState = null;
            stateStore.save(null);
            log.info("Dhan token invalidated via CAS (gen={}) — next ensureValid() will generate a fresh token",
                    failedGenerationId);
        } finally {
            refreshLock.unlock();
        }
        return true;
    }

    private DhanTokenState resolveValidState(long now) {
        if (currentState != null) {
            DhanTokenState confirmed = confirmExistingState(currentState, now);
            if (confirmed != null) {
                return persist(confirmed);
            }
        }

        DhanTokenState adoptedBootstrap = adoptBootstrapToken(now);
        if (adoptedBootstrap != null) {
            return persist(adoptedBootstrap);
        }

        DhanTokenState generated = generateFreshToken(now);
        return persist(generated);
    }

    private DhanTokenState confirmExistingState(DhanTokenState state, long now) {
        if (state == null || state.accessToken() == null || state.accessToken().isBlank()) {
            return null;
        }
        if (isReusable(state, now)) {
            return state;
        }
        if (state.expiryEpochMs() > now) {
            try {
                DhanTokenInfo info = authClient.fetchProfile(state.accessToken(), settings.refreshBufferMillis());
                if (info.valid() && !info.refreshRecommended()) {
                    return new DhanTokenState(state.accessToken(), info.expiryEpochMs(), state.issuedAtEpochMs(), state.source());
                }
            } catch (DhanAuthenticationException | DhanHttpException | DhanAuthRejectedException ignored) {
                return null;
            }
        }
        return null;
    }

    private DhanTokenState adoptBootstrapToken(long now) {
        if (!settings.hasConfiguredAccessToken()) {
            return null;
        }
        String bootstrapToken = requireBootstrapToken();
        if (currentState != null && bootstrapToken.equals(currentState.accessToken())) {
            return null;
        }
        try {
            DhanTokenInfo info = authClient.fetchProfile(bootstrapToken, settings.refreshBufferMillis());
            if (!info.valid()) {
                return null;
            }
            return new DhanTokenState(bootstrapToken, info.expiryEpochMs(), now, "BOOTSTRAP");
        } catch (DhanAuthenticationException | DhanHttpException | DhanAuthRejectedException ignored) {
            return null;
        }
    }

    private DhanTokenState generateFreshToken(long now) {
        // Use the shared throttle — 5 min base cooldown, doubles on
        // each consecutive failure, capped at 30 min. Stops the
        // "5 minutes → 3 mints" failure mode where the revalidator
        // repeatedly invalidates and ensureValid() repeatedly mints.
        TokenAcquisitionThrottle.AcquireResult r = throttle.tryAcquire("dhan-totp");
        if (!r.allowed()) {
            throttle.recordFailure();
            throw new DhanAuthRejectedException(
                    "Dhan token generation cooldown active ("
                            + r.currentCooldownMs() / 1000L + "s, "
                            + "consecutive failures=" + throttle.consecutiveFailures() + "); "
                            + "retry in " + (r.retryAfterMs() / 1000L) + "s",
                    true);
        }
        lastAcquisitionAttemptMs.set(now);
        log.warn(
                "Dhan token MINT #{} — source={}, throttle-failures={}, cooldown-remaining={}ms",
                tokenGeneration.get() + 1,
                settings.authMode(),
                throttle.consecutiveFailures(),
                0L);
        return switch (settings.authMode()) {
            case TOTP_GENERATED -> authClient.generateViaTotp(
                    settings.clientId(),
                    readSecret(settings.pinFile(), "pin"),
                    totpGenerator.currentCode(readSecret(settings.totpSecretFile(), "totp secret"))
            );
            case WEB_RENEWABLE -> {
                if (currentState == null || currentState.accessToken() == null || currentState.accessToken().isBlank()) {
                    throw new IllegalStateException("Cannot renew Dhan token without an active current token");
                }
                yield authClient.renewToken(settings.clientId(), currentState.accessToken());
            }
            case STATIC -> new DhanTokenState(requireBootstrapToken(), Long.MAX_VALUE, now, "STATIC");
        };
    }

    private DhanTokenState persist(DhanTokenState state) {
        stateStore.save(state);
        log.info(
                "Dhan access token state updated (source={}, expiresAt={}, stateFile={})",
                state.source(),
                Instant.ofEpochMilli(state.expiryEpochMs()),
                settings.tokenStateFile()
        );
        callbackRegistry.fireRefresh();
        return state;
    }

    private boolean isReusable(DhanTokenState state, long now) {
        return state != null
                && state.accessToken() != null
                && !state.accessToken().isBlank()
                && state.expiryEpochMs() > now + settings.refreshBufferMillis() + CLOCK_SKEW_TOLERANCE_MS;
    }

    private String requireBootstrapToken() {
        if (!settings.hasConfiguredAccessToken()) {
            throw new IllegalStateException("Dhan access token is not configured");
        }
        return settings.accessToken().trim();
    }

    private String readSecret(Path path, String label) {
        if (path == null) {
            throw new IllegalStateException("Dhan " + label + " file is not configured");
        }
        try {
            if (!Files.exists(path)) {
                throw new IllegalStateException("Dhan " + label + " file not found at " + path);
            }
            String value = Files.readString(path).trim();
            if (value.isBlank()) {
                throw new IllegalStateException("Dhan " + label + " file is empty at " + path);
            }
            return value;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read Dhan " + label + " file from " + path, ex);
        }
    }
}
