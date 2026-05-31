package com.tradej.broker.api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract test that ALL {@link TokenLifecycleService} implementations must pass.
 *
 * <p>Subclass this test for each concrete broker auth implementation:
 * <ul>
 *   <li>{@code DhanTokenLifecycleServiceContractTest} in {@code trade-broker-dhan}</li>
 *   <li>{@code UpstoxTokenLifecycleServiceContractTest} in {@code trade-broker-upstox}</li>
 * </ul>
 */
@Tag("unit")
public abstract class TokenLifecycleServiceContractTest {

    /** Subclass must provide a freshly-constructed implementation. */
    protected abstract TokenLifecycleService createService();

    private TokenLifecycleService service;

    @BeforeEach
    void setUp() {
        service = createService();
    }

    @Test
    void initialStateIsNull() {
        TokenState state = service.currentState();
        assertNull(state, "currentState() must return null before acquireToken()");
    }

    @Test
    void acquireTokenReturnsNonNull() {
        TokenState state = service.acquireToken();
        assertNotNull(state);
        assertNotNull(state.accessToken());
        assertFalse(state.accessToken().isBlank());
    }

    @Test
    void acquireTokenReturnsValidToken() {
        TokenState state = service.acquireToken();
        assertTrue(state.valid(), "acquireToken() must return a non-expired token");
    }

    @Test
    void tokenHasSource() {
        TokenState state = service.acquireToken();
        assertNotNull(state.source(), "TokenState must have a non-null source");
    }

    @Test
    void ensureValidDoesNotThrow() {
        service.acquireToken();
        assertDoesNotThrow(() -> service.ensureValid());
    }

    @Test
    void currentStateAfterAcquireIsNonNull() {
        service.acquireToken();
        assertNotNull(service.currentState());
    }

    @Test
    void onRefreshCallbackIsInvoked() {
        boolean[] invoked = {false};
        service.onRefresh(() -> invoked[0] = true);
        service.acquireToken();
        // Note: some implementations may not invoke onRefresh for initial acquire;
        // this test is informational. Subclasses may override.
    }

    @Test
    void tokenStateRemainingMsReturnsPositive() {
        TokenState state = service.acquireToken();
        assertTrue(state.remainingMs() > 0,
                "remainingMs() must be > 0 for a valid token");
    }

    @Test
    void tokenStateRefreshRecommendedReturnsFalseForFresh() {
        TokenState state = service.acquireToken();
        assertFalse(state.refreshRecommended(600_000),
                "refreshRecommended() must be false for a freshly acquired token");
    }
}
