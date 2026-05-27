package com.tradej.broker.dhan.auth;

import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanTokenManagerUnitTest {
    private static final Instant NOW = Instant.parse("2026-05-26T12:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void reusesPersistedTokenWithoutProfileOrGenerationWhenFarFromExpiry() {
        RecordingAuthClient authClient = new RecordingAuthClient();
        Path tokenFile = tempDir.resolve("token-state.json");
        DhanTokenStateStore stateStore = new DhanTokenStateStore(tokenFile);
        stateStore.save(new DhanTokenState("persisted-token", NOW.plusSeconds(3600).toEpochMilli(), NOW.toEpochMilli(), "STORE"));

        DhanTokenManager manager = new DhanTokenManager(
                settings("bootstrap-token", tokenFile),
                authClient,
                new FixedTotpGenerator("123456"),
                stateStore,
                fixedClock()
        );

        assertEquals("persisted-token", manager.getAccessToken());
        assertEquals(0, authClient.profileCalls);
        assertEquals(0, authClient.generateCalls);
    }

    @Test
    void adoptsValidBootstrapTokenBeforeGenerating() {
        RecordingAuthClient authClient = new RecordingAuthClient();
        authClient.profileInfo = new DhanTokenInfo(true, NOW.plusSeconds(3600).toEpochMilli(), false);
        Path tokenFile = tempDir.resolve("bootstrap-state.json");

        DhanTokenManager manager = new DhanTokenManager(
                settings("bootstrap-token", tokenFile),
                authClient,
                new FixedTotpGenerator("123456"),
                new DhanTokenStateStore(tokenFile),
                fixedClock()
        );

        assertEquals("bootstrap-token", manager.getAccessToken());
        assertEquals(1, authClient.profileCalls);
        assertEquals(0, authClient.generateCalls);
    }

    @Test
    void confirmsNearExpiryTokenWithProfileAndSkipsGeneration() {
        RecordingAuthClient authClient = new RecordingAuthClient();
        authClient.profileInfo = new DhanTokenInfo(true, NOW.plusSeconds(1800).toEpochMilli(), false);
        Path tokenFile = tempDir.resolve("near-expiry-state.json");
        DhanTokenStateStore stateStore = new DhanTokenStateStore(tokenFile);
        stateStore.save(new DhanTokenState("near-expiry", NOW.plusSeconds(120).toEpochMilli(), NOW.toEpochMilli(), "STORE"));

        DhanTokenManager manager = new DhanTokenManager(
                settings("bootstrap-token", tokenFile),
                authClient,
                new FixedTotpGenerator("123456"),
                stateStore,
                fixedClock()
        );

        assertEquals("near-expiry", manager.getAccessToken());
        assertEquals(1, authClient.profileCalls);
        assertEquals(0, authClient.generateCalls);
    }

    @Test
    void generatesOnlyOnceWhenMultipleThreadsNeedRefresh() throws Exception {
        RecordingAuthClient authClient = new RecordingAuthClient();
        authClient.generatedState = new DhanTokenState("fresh-token", NOW.plusSeconds(3600).toEpochMilli(), NOW.toEpochMilli(), "TOTP_GENERATED");
        Path tokenFile = tempDir.resolve("generated-state.json");

        DhanTokenManager manager = new DhanTokenManager(
                settings(null, tokenFile),
                authClient,
                new FixedTotpGenerator("654321"),
                new DhanTokenStateStore(tokenFile),
                fixedClock()
        );

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return manager.getAccessToken();
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("fresh-token", future.get(5, TimeUnit.SECONDS));
            }
        }

        assertEquals(1, authClient.generateCalls);
        assertTrue(Files.exists(tokenFile), "Generated token should be persisted to the local token state file.");
    }

    private DhanConnectionSettings settings(String accessToken, Path tokenFile) {
        Path pinFile = tempDir.resolve("dhan-pin.txt");
        Path totpFile = tempDir.resolve("dhan-secret.txt");
        try {
            Files.writeString(pinFile, "960000");
            Files.writeString(totpFile, "TKGGTO2OG5UKSL7PNQG5PIRNKTKVH5XP");
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return DhanConnectionSettings.withDefaults(
                "1106251237",
                accessToken,
                DhanAuthMode.TOTP_GENERATED,
                pinFile,
                totpFile,
                tokenFile,
                10L
        );
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneId.of("Asia/Kolkata"));
    }

    private static final class RecordingAuthClient extends DhanAuthClient {
        private int profileCalls;
        private int generateCalls;
        private DhanTokenInfo profileInfo = new DhanTokenInfo(true, NOW.plusSeconds(1800).toEpochMilli(), false);
        private DhanTokenState generatedState = new DhanTokenState("generated-token", NOW.plusSeconds(1800).toEpochMilli(), NOW.toEpochMilli(), "TOTP_GENERATED");

        @Override
        public DhanTokenInfo fetchProfile(String accessToken, long refreshBufferMs) {
            profileCalls++;
            return profileInfo;
        }

        @Override
        public DhanTokenState generateViaTotp(String clientId, String pin, String totp) {
            generateCalls++;
            return generatedState;
        }
    }

    private static final class FixedTotpGenerator extends DhanTotpGenerator {
        private final String value;

        private FixedTotpGenerator(String value) {
            this.value = value;
        }

        @Override
        public String currentCode(String sharedSecret) {
            return value;
        }
    }
}
