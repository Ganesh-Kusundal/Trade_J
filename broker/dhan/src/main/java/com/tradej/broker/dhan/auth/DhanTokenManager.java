package com.tradej.broker.dhan.auth;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class DhanTokenManager implements DhanTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(DhanTokenManager.class);
    private static final long TOKEN_ACQUISITION_COOLDOWN_MS = 130_000L;

    private final DhanConnectionSettings settings;
    private final DhanAuthClient authClient;
    private final DhanTotpGenerator totpGenerator;
    private final DhanTokenStateStore stateStore;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicLong lastAcquisitionAttemptMs = new AtomicLong(0L);

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
        } finally {
            refreshLock.unlock();
        }
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
        long lastAttempt = lastAcquisitionAttemptMs.get();
        if (lastAttempt > 0 && now - lastAttempt < TOKEN_ACQUISITION_COOLDOWN_MS) {
            throw new DhanAuthRejectedException(
                    "Dhan token generation cooldown active; retry after "
                            + ((TOKEN_ACQUISITION_COOLDOWN_MS - (now - lastAttempt)) / 1000) + "s",
                    true);
        }
        lastAcquisitionAttemptMs.set(now);
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
        return state;
    }

    private boolean isReusable(DhanTokenState state, long now) {
        return state != null
                && state.accessToken() != null
                && !state.accessToken().isBlank()
                && state.expiryEpochMs() > now + settings.refreshBufferMillis();
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
