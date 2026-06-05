package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class DefaultTokenLifecycleServiceTest {

    @Test
    void ensureValidHonorsFailedRefreshCooldown() {
        AtomicInteger refreshAttempts = new AtomicInteger();
        long now = System.currentTimeMillis();
        TokenState expired = new TokenState(
                "access",
                "refresh",
                now - 1_000L,
                now - 3_600_000L,
                TokenSource.OAUTH
        );
        AtomicReference<TokenState> stored = new AtomicReference<>(expired);
        TokenStateStore store = new TokenStateStore() {
            @Override
            public TokenState load() {
                return stored.get();
            }

            @Override
            public void save(TokenState state) {
                stored.set(state);
            }
        };

        TestTokenService service = new TestTokenService(store, refreshAttempts);

        assertThrows(IllegalStateException.class, service::ensureValid);
        assertEquals(1, refreshAttempts.get());

        assertDoesNotThrow(service::ensureValid);
        assertEquals(1, refreshAttempts.get(), "Second call within cooldown must not retry refresh");
    }

    private static final class TestTokenService extends DefaultTokenLifecycleService {
        private final AtomicInteger refreshAttempts;

        TestTokenService(TokenStateStore store, AtomicInteger refreshAttempts) {
            super(store, 60_000L);
            this.refreshAttempts = refreshAttempts;
        }

        @Override
        protected TokenState doAcquire() {
            throw new UnsupportedOperationException("acquire not expected in refresh cooldown test");
        }

        @Override
        protected TokenState doRefresh(String refreshToken) {
            refreshAttempts.incrementAndGet();
            throw new IllegalStateException("refresh failed");
        }
    }
}
