package com.tradej.broker.icici.auth;

import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.locks.ReentrantLock;

public final class BreezeTokenManager implements BreezeTokenProvider {
    private final BreezeConnectionSettings settings;
    private final BreezeSessionExchange sessionExchange;
    private final BreezeTotpGenerator totpGenerator;
    private final BreezeBrowserSessionCapture browserSessionCapture;
    private final BreezeTokenStateStore stateStore;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();

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
        if (settings.authMode() == IciciAuthMode.STATIC) {
            if (currentSession == null && settings.staticSessionToken() != null && !settings.staticSessionToken().isBlank()) {
                long now = clock.millis();
                currentSession = BreezeSession.fromEncodedToken(
                        settings.staticSessionToken(),
                        now,
                        BreezeSessionExchange.nextMidnightEpochMs(now)
                );
            }
            return;
        }
        long now = clock.millis();
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

    private BreezeSession resolveSession(long now) {
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
