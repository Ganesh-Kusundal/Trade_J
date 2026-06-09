package com.tradej.broker.icici.auth;

import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public final class BreezeTokenManager implements BreezeTokenProvider {
    private static final Logger log = LoggerFactory.getLogger(BreezeTokenManager.class);
    private static final long SESSION_ACQUISITION_COOLDOWN_MS = 30_000L;
    private final BreezeConnectionSettings settings;
    private final BreezeSessionExchange sessionExchange;
    private final BreezeTotpGenerator totpGenerator;
    private final BreezeBrowserSessionCapture browserSessionCapture;
    private final BreezeTokenStateStore stateStore;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private final AtomicLong sessionGeneration = new AtomicLong(0);
    private final AtomicLong lastAcquisitionAttemptMs = new AtomicLong(0L);

    private volatile BreezeSession currentSession;

    public BreezeTokenManager(BreezeConnectionSettings settings) {
        this(
                settings,
                new BreezeSessionExchange(),
                new BreezeTotpGenerator(),
                new BreezeBrowserSessionCapture(settings),
                new BreezeTokenStateStore(settings.tokenStateFile()),
                Clock.systemDefaultZone()
        );
    }

    BreezeTokenManager(
            BreezeConnectionSettings settings,
            BreezeSessionExchange sessionExchange,
            BreezeTotpGenerator totpGenerator,
            BreezeBrowserSessionCapture browserSessionCapture,
            BreezeTokenStateStore stateStore,
            Clock clock
    ) {
        this.settings = settings;
        this.sessionExchange = sessionExchange;
        this.totpGenerator = totpGenerator;
        this.browserSessionCapture = browserSessionCapture;
        this.stateStore = stateStore;
        this.clock = clock;
        this.currentSession = stateStore.load().orElse(null);
    }

    @Override
    public void ensureValid() {
        // Check reuse BEFORE any mode-specific logic so STATIC sessions
        // are also subject to expiry validation.
        long now = clock.millis();
        if (isReusable(currentSession, now)) {
            return;
        }
        if (settings.authMode() == IciciAuthMode.STATIC) {
            if (settings.staticSessionToken() != null && !settings.staticSessionToken().isBlank()) {
                currentSession = BreezeSession.fromEncodedToken(
                        settings.staticSessionToken(),
                        now,
                        BreezeSessionExchange.nextMidnightEpochMs(now)
                );
                sessionGeneration.incrementAndGet();
            }
            return;
        }
        if (isReusable(currentSession, now)) {
            return;
        }
        refreshLock.lock();
        try {
            long lockedNow = clock.millis();
            if (isReusable(currentSession, lockedNow)) {
                return;
            }
            currentSession = resolveSession(lockedNow);
            sessionGeneration.incrementAndGet();
            stateStore.save(currentSession);
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public BreezeSession session() {
        ensureValid();
        if (currentSession == null) {
            throw new IllegalStateException("ICICI session is not available");
        }
        return currentSession;
    }

    @Override
    public String appKey() {
        return settings.appKey();
    }

    @Override
    public String secretKey() {
        return settings.secretKey();
    }

    @Override
    public long sessionGenerationId() {
        return sessionGeneration.get();
    }

    @Override
    public void invalidate() {
        refreshLock.lock();
        try {
            currentSession = null;
            stateStore.save(null);
            log.info("ICICI session invalidated — next ensureValid() will generate a fresh session");
        } finally {
            refreshLock.unlock();
        }
    }

    @Override
    public boolean invalidate(long failedGenerationId) {
        if (!sessionGeneration.compareAndSet(failedGenerationId, failedGenerationId + 1)) {
            log.debug("ICICI invalidate({}) skipped — another thread already regenerated (current gen={})",
                    failedGenerationId, sessionGeneration.get());
            return false;
        }
        refreshLock.lock();
        try {
            if (sessionGeneration.get() != failedGenerationId + 1) {
                return false;
            }
            currentSession = null;
            stateStore.save(null);
            log.info("ICICI session invalidated via CAS (gen={}) — next ensureValid() will generate a fresh session",
                    failedGenerationId);
        } finally {
            refreshLock.unlock();
        }
        return true;
    }

    private BreezeSession resolveSession(long now) {
        long lastAttempt = lastAcquisitionAttemptMs.get();
        if (lastAttempt > 0 && now - lastAttempt < SESSION_ACQUISITION_COOLDOWN_MS) {
            long retryInSec = (SESSION_ACQUISITION_COOLDOWN_MS - (now - lastAttempt)) / 1000;
            throw new IllegalStateException(
                    "ICICI session acquisition cooldown active; retry after " + retryInSec + "s");
        }
        lastAcquisitionAttemptMs.set(now);
        String sessionInput = resolveSessionInput();
        BreezeSession session = sessionExchange.exchange(settings.appKey(), sessionInput);
        if (session.expiresAtEpochMs() <= now) {
            return new BreezeSession(
                    session.userId(),
                    session.sessionKey(),
                    session.base64SessionToken(),
                    now,
                    BreezeSessionExchange.nextMidnightEpochMs(now)
            );
        }
        return session;
    }

    private String resolveSessionInput() {
        return switch (settings.authMode()) {
            case BROWSER_AUTOMATED -> browserSessionCapture.captureApiSession();
            case TOTP_GENERATED -> totpGenerator.currentCode(readSecretFile(settings.totpSecretFile(), "TOTP secret"));
            case API_SESSION -> readSecretFile(settings.apiSessionFile(), "API session");
            case STATIC -> {
                if (settings.staticSessionToken() == null || settings.staticSessionToken().isBlank()) {
                    throw new IllegalStateException("ICICI static session token is not configured");
                }
                yield settings.staticSessionToken();
            }
        };
    }

    private static String readSecretFile(Path path, String label) {
        try {
            if (path == null || !Files.exists(path)) {
                throw new IllegalStateException("ICICI " + label + " file missing: " + path);
            }
            String value = Files.readString(path).trim();
            if (value.isBlank()) {
                throw new IllegalStateException("ICICI " + label + " file is blank: " + path);
            }
            return value;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read ICICI " + label + " from " + path, ex);
        }
    }

    private boolean isReusable(BreezeSession session, long now) {
        if (session == null) {
            return false;
        }
        long bufferMs = settings.refreshBufferMinutes() * 60_000L;
        return session.expiresAtEpochMs() > now + bufferMs;
    }
}
