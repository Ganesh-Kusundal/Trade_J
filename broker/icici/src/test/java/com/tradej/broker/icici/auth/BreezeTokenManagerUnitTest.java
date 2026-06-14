package com.tradej.broker.icici.auth;

import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.spy;

/**
 * Unit test for {@link BreezeTokenManager} that explicitly guards against
 * the "5 minutes → multiple session mints" regression: with a valid
 * cached session, 100 {@code ensureValid()} calls must result in zero
 * writes to the state store (which is the proxy for "no
 * {@code BreezeSessionExchange.exchange} call").
 *
 * <p>We use a Mockito spy on the state store so we don't have to make
 * the production class non-final — Mockito handles final classes via
 * the inline-mock-maker.
 */
@Tag("unit")
class BreezeTokenManagerUnitTest {

    private static final Instant NOW = Instant.parse("2026-05-26T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void ensureValidNeverRegeneratesWhenCachedSessionIsValid() throws Exception {
        long expiresAt = BreezeSessionExchange.nextMidnightEpochMs(NOW.toEpochMilli());
        Path tokenState = tempDir.resolve("breeze-token-state.json");
        BreezeTokenStateStore realStore = new BreezeTokenStateStore(tokenState);
        realStore.save(new BreezeSession("user1", "key1", "cached-b64", NOW.toEpochMilli(), expiresAt));

        // Spy on a *fresh* state store (post-load) and watch writes from
        // this point on. The initial load() happened before the spy was
        // attached so we won't count that.
        BreezeTokenStateStore storeSpy = spy(realStore);
        AtomicInteger writesAfterSpy = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(inv -> {
            writesAfterSpy.incrementAndGet();
            return inv.callRealMethod();
        }).when(storeSpy).save(org.mockito.ArgumentMatchers.any());

        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                "app-key", "secret-key", null,
                IciciAuthMode.TOTP_GENERATED,
                tempDir.resolve("totp.txt"),
                tempDir.resolve("user.txt"),
                tempDir.resolve("pass.txt"),
                null,
                tokenState,
                false, 5, 8080, "/callback", true, 30
        );
        Files.writeString(tempDir.resolve("totp.txt"), "JBSWY3DPEHPK3PXP");

        BreezeTokenManager manager = new BreezeTokenManager(
                settings,
                new BreezeSessionExchange(),
                new BreezeTotpGenerator(),
                new BreezeBrowserSessionCapture(settings),
                storeSpy,
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );

        for (int i = 0; i < 100; i++) {
            manager.ensureValid();
            assertNotNull(manager.session());
        }

        assertEquals(0, writesAfterSpy.get(),
                "ensureValid() 100x with a valid cached session must not save a new session to the state store");
    }

    @Test
    void sessionAccessorIsAlsoNonRegenerating() throws Exception {
        long expiresAt = BreezeSessionExchange.nextMidnightEpochMs(NOW.toEpochMilli());
        Path tokenState = tempDir.resolve("breeze-token-state-2.json");
        BreezeTokenStateStore realStore = new BreezeTokenStateStore(tokenState);
        realStore.save(new BreezeSession("user1", "key1", "cached-b64", NOW.toEpochMilli(), expiresAt));

        BreezeTokenStateStore storeSpy = spy(realStore);
        AtomicInteger writesAfterSpy = new AtomicInteger(0);
        org.mockito.Mockito.doAnswer(inv -> {
            writesAfterSpy.incrementAndGet();
            return inv.callRealMethod();
        }).when(storeSpy).save(org.mockito.ArgumentMatchers.any());

        BreezeConnectionSettings settings = BreezeConnectionSettings.withDefaults(
                "app-key", "secret-key", null,
                IciciAuthMode.TOTP_GENERATED,
                tempDir.resolve("totp.txt"),
                tempDir.resolve("user.txt"),
                tempDir.resolve("pass.txt"),
                null,
                tokenState,
                false, 5, 8080, "/callback", true, 30
        );
        Files.writeString(tempDir.resolve("totp.txt"), "JBSWY3DPEHPK3PXP");

        BreezeTokenManager manager = new BreezeTokenManager(
                settings,
                new BreezeSessionExchange(),
                new BreezeTotpGenerator(),
                new BreezeBrowserSessionCapture(settings),
                storeSpy,
                Clock.fixed(NOW, ZoneId.of("UTC"))
        );

        for (int i = 0; i < 200; i++) {
            assertNotNull(manager.session());
        }

        assertEquals(0, writesAfterSpy.get(),
                "session() 200x with a valid cached session must not save a new session");
    }
}
