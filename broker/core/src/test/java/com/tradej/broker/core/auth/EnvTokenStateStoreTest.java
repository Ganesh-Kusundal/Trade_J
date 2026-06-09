package com.tradej.broker.core.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class EnvTokenStateStoreTest {

    @Test
    void load_returnsNullWhenNoEnvVars() {
        EnvTokenStateStore store = new EnvTokenStateStore();
        // System.getenv() won't have TRADEJ_TOKEN_ACCESS in test environment
        TokenState state = store.load();
        assertNull(state, "Should return null when no env vars are set");
    }

    @Test
    void save_cachesInMemory() {
        EnvTokenStateStore store = new EnvTokenStateStore();
        TokenState state = new TokenState("access-123", "refresh-456",
                System.currentTimeMillis() + 3600_000L,
                System.currentTimeMillis(), TokenSource.STATIC);

        store.save(state);

        // After save, load should return the in-memory cached state
        TokenState loaded = store.load();
        assertNotNull(loaded, "Should return in-memory cached state after save");
        assertEquals("access-123", loaded.accessToken());
        assertEquals("refresh-456", loaded.refreshToken());
        assertEquals(TokenSource.STATIC, loaded.source());
    }

    @Test
    void saveNull_clearsCachedState() {
        EnvTokenStateStore store = new EnvTokenStateStore();
        TokenState state = new TokenState("access-123", null,
                System.currentTimeMillis() + 3600_000L,
                System.currentTimeMillis(), TokenSource.STATIC);

        store.save(state);
        assertNotNull(store.load());

        store.save(null);
        // After clearing, load falls back to env vars (which are empty in test)
        assertNull(store.load(), "Should return null after clearing cache and no env vars");
    }

    @Test
    void saveAndLoad_preservesAllFields() {
        EnvTokenStateStore store = new EnvTokenStateStore();
        long expiry = System.currentTimeMillis() + 7200_000L;
        long issued = System.currentTimeMillis();
        TokenState state = new TokenState("my-access", "my-refresh",
                expiry, issued, TokenSource.TOTP);

        store.save(state);
        TokenState loaded = store.load();

        assertNotNull(loaded);
        assertEquals("my-access", loaded.accessToken());
        assertEquals("my-refresh", loaded.refreshToken());
        assertEquals(expiry, loaded.expiryEpochMs());
        assertEquals(issued, loaded.issuedAtEpochMs());
        assertEquals(TokenSource.TOTP, loaded.source());
    }
}
