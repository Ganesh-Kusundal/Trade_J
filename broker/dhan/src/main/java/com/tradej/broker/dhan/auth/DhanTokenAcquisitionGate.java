package com.tradej.broker.dhan.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.broker.core.resilience.CircuitBreaker;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Single-flight, durable gate for Dhan token minting.
 *
 * <p>Dhan auth rate limits are stricter than normal REST limits and must survive
 * new manager instances and short-lived Gradle/CLI JVMs. The cooldown marker is
 * intentionally stored beside the token state file.
 */
public final class DhanTokenAcquisitionGate {
    private static final ConcurrentHashMap<String, ReentrantLock> CLIENT_LOCKS = new ConcurrentHashMap<>();
    private static final DhanRetryExecutor DEFAULT_EXECUTOR =
            new DhanRetryExecutor(DhanProtocolConstants.defaultRateLimiter(), new CircuitBreaker());

    private final DhanConnectionSettings settings;
    private final DhanRetryExecutor executor;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Path cooldownPath;

    public DhanTokenAcquisitionGate(DhanConnectionSettings settings) {
        this(settings, DEFAULT_EXECUTOR, Clock.systemDefaultZone(), new ObjectMapper());
    }

    DhanTokenAcquisitionGate(
            DhanConnectionSettings settings,
            DhanRetryExecutor executor,
            Clock clock,
            ObjectMapper objectMapper
    ) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.cooldownPath = cooldownPath(settings.tokenStateFile());
    }

    public <T> T acquire(String operation, Supplier<T> supplier) {
        ReentrantLock lock = CLIENT_LOCKS.computeIfAbsent(settings.clientId(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            assertNotCoolingDown();
            return executor.execute(ApiCategory.AUTH, operation, () -> {
                assertNotCoolingDown();
                try {
                    T value = supplier.get();
                    clearCooldown();
                    return value;
                } catch (DhanAuthRejectedException ex) {
                    if (ex.rateLimited()) {
                        recordCooldown(ex.getMessage());
                    }
                    throw ex;
                }
            });
        } finally {
            lock.unlock();
        }
    }

    public void assertNotCoolingDown() {
        CooldownState state = loadCooldown();
        if (state == null) {
            return;
        }
        long now = clock.millis();
        if (state.retryAfterEpochMs <= now) {
            clearCooldown();
            return;
        }
        throw activeCooldown(state, now);
    }

    public long remainingCooldownMs() {
        CooldownState state = loadCooldown();
        if (state == null) {
            return 0L;
        }
        long remaining = state.retryAfterEpochMs - clock.millis();
        if (remaining <= 0L) {
            clearCooldown();
            return 0L;
        }
        return remaining;
    }

    private void recordCooldown(String reason) {
        if (cooldownPath == null) {
            return;
        }
        CooldownState state = new CooldownState(
                settings.clientId(),
                clock.millis(),
                clock.millis() + DhanProtocolConstants.TOKEN_ACQUISITION_COOLDOWN_MS,
                reason == null ? "Dhan auth rate limited" : reason
        );
        try {
            Path parent = cooldownPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cooldownPath.toFile(), state);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist Dhan auth cooldown to " + cooldownPath, ex);
        }
    }

    private CooldownState loadCooldown() {
        if (cooldownPath == null || !Files.exists(cooldownPath)) {
            return null;
        }
        try {
            CooldownState state = objectMapper.readValue(Files.readString(cooldownPath), CooldownState.class);
            if (!settings.clientId().equals(state.clientId)) {
                return null;
            }
            return state;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load Dhan auth cooldown from " + cooldownPath, ex);
        }
    }

    private void clearCooldown() {
        if (cooldownPath == null) {
            return;
        }
        try {
            Files.deleteIfExists(cooldownPath);
        } catch (IOException ignored) {
        }
    }

    private DhanAuthRejectedException activeCooldown(CooldownState state, long now) {
        long waitSeconds = Math.max(1L, (state.retryAfterEpochMs - now + 999L) / 1_000L);
        return new DhanAuthRejectedException(
                "Dhan token generation cooldown active; retry after " + waitSeconds
                        + "s. Previous rejection: " + state.reason,
                true);
    }

    private static Path cooldownPath(Path tokenStateFile) {
        if (tokenStateFile == null) {
            return null;
        }
        return Path.of(tokenStateFile.toString() + ".auth-cooldown.json");
    }

    static final class CooldownState {
        public String clientId;
        public long recordedAtEpochMs;
        public long retryAfterEpochMs;
        public String reason;

        public CooldownState() {
        }

        CooldownState(String clientId, long recordedAtEpochMs, long retryAfterEpochMs, String reason) {
            this.clientId = clientId;
            this.recordedAtEpochMs = recordedAtEpochMs;
            this.retryAfterEpochMs = retryAfterEpochMs;
            this.reason = reason;
        }
    }
}
