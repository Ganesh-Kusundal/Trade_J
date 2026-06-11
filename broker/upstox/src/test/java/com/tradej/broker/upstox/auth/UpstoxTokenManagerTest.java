package com.tradej.broker.upstox.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class UpstoxTokenManagerTest {

    @Mock
    UpstoxOAuthClient oauthClient;

    @Mock
    UpstoxConnectionSettings settings;

    @Test
    void extendedTokenHolderParsesJwtExpiry() {
        // JWT with exp = 1811628000 (2027-03-28 12:00:00 UTC)
        String extendedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";

        UpstoxBearerTokenSource holder =
                UpstoxTokenManager.createExtendedTokenHolder(extendedToken);

        assertEquals(extendedToken, holder.bearerToken());
        assertTrue(holder.analyticsOnly());
        assertEquals(1_811_628_000_000L, holder.expiryEpochMs());
    }

    @Test
    void extendedTokenHolderThrowsWhenExpired() {
        // JWT with exp in the past
        String expiredToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjEwMDAwMDAwMDB9" // exp = 1000000000 (2001)
                + ".signature";

        UpstoxBearerTokenSource holder =
                UpstoxTokenManager.createExtendedTokenHolder(expiredToken);

        assertThrows(IllegalStateException.class, holder::ensureValid);
    }

    @Test
    void extendedTokenHolderWithNoExpClaim() {
        String tokenWithoutExp = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                + ".signature";

        UpstoxBearerTokenSource holder =
                UpstoxTokenManager.createExtendedTokenHolder(tokenWithoutExp);

        assertEquals(-1L, holder.expiryEpochMs());
        // Should not throw when no expiry
        holder.ensureValid();
    }

    @Test
    void bootstrapUsesProfileExpiryWhenAvailable() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.accessToken()).thenReturn("test-access-token");
        when(settings.refreshToken()).thenReturn("test-refresh-token");
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);
        when(oauthClient.fetchProfile(anyString())).thenReturn(
                Instant.now().plusSeconds(3600).toEpochMilli()
        );

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        // Trigger bootstrap by calling doAcquire (via bearerToken)
        String token = manager.bearerToken();

        assertEquals("test-access-token", token);
        verify(oauthClient).fetchProfile("test-access-token");
        TokenState state = manager.currentState();
        assertNotNull(state);
        assertEquals(TokenSource.STATIC, state.source());
        assertTrue(state.expiryEpochMs() > System.currentTimeMillis());
    }

    @Test
    void bootstrapFallsBackToJwtWhenProfileFails() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.refreshToken()).thenReturn("test-refresh-token");
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);
        // fetchProfile returns -1 (not available) for any token
        when(oauthClient.fetchProfile(anyString())).thenReturn(-1L);

        // JWT with exp claim
        String jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJleHAiOjE4MTE2MjgwMDB9"
                + ".signature";

        when(settings.accessToken()).thenReturn(jwtToken);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        String token = manager.bearerToken();

        assertEquals(jwtToken, token);
        verify(oauthClient).fetchProfile(jwtToken);
        TokenState state = manager.currentState();
        assertNotNull(state);
        assertEquals(TokenSource.STATIC, state.source());
        assertEquals(1_811_628_000_000L, state.expiryEpochMs());
    }

    @Test
    void bootstrapFallsBackToDailyExpiryWhenBothFail() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.accessToken()).thenReturn("plain-token-no-jwt");
        when(settings.refreshToken()).thenReturn("test-refresh-token");
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);
        // fetchProfile returns -1 (not available)
        when(oauthClient.fetchProfile(anyString())).thenReturn(-1L);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        String token = manager.bearerToken();

        assertEquals("plain-token-no-jwt", token);
        TokenState state = manager.currentState();
        assertNotNull(state);
        assertEquals(TokenSource.STATIC, state.source());
        // Should fall back to daily 3:30 AM IST expiry
        long expectedExpiry = UpstoxTokenExpiry.nextExpiryEpochMs();
        assertEquals(expectedExpiry, state.expiryEpochMs());
    }

    @Test
    void upgradeFromWebhookReplacesToken() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.accessToken()).thenReturn("old-token");
        when(settings.refreshToken()).thenReturn("old-refresh");
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);
        when(oauthClient.fetchProfile(anyString())).thenReturn(-1L);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        // Bootstrap with old token
        assertEquals("old-token", manager.bearerToken());

        // Upgrade via webhook — expiry must be later than 3:30 AM IST fallback (~22h)
        long futureExpiry = System.currentTimeMillis() + 86_400_000L;
        manager.upgradeFromWebhook("new-webhook-token", futureExpiry);

        assertEquals("new-webhook-token", manager.bearerToken());
        TokenState state = manager.currentState();
        assertNotNull(state);
        assertEquals(TokenSource.OAUTH, state.source());
        assertEquals(futureExpiry, state.expiryEpochMs());
    }

    @Test
    void upgradeFromWebhookSkipsStaleToken() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.accessToken()).thenReturn("current-token");
        when(settings.refreshToken()).thenReturn("current-refresh");
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);
        long farFuture = System.currentTimeMillis() + 72_000_000L;
        when(oauthClient.fetchProfile(anyString())).thenReturn(farFuture);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        // Bootstrap with current token that expires far in the future
        assertEquals("current-token", manager.bearerToken());

        // Try to upgrade with a token that expires sooner — should be skipped
        long nearExpiry = System.currentTimeMillis() + 36_000_000L;
        manager.upgradeFromWebhook("stale-webhook-token", nearExpiry);

        // Original token should remain
        assertEquals("current-token", manager.bearerToken());
    }

    @Test
    void upgradeFromWebhookRejectsBlankToken() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.refreshBufferMs()).thenReturn(1_800_000L);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        assertThrows(IllegalArgumentException.class,
                () -> manager.upgradeFromWebhook("", System.currentTimeMillis() + 3600_000L));
        assertThrows(IllegalArgumentException.class,
                () -> manager.upgradeFromWebhook("token", 0L));
    }

    @Test
    void performInteractiveOAuthReturnsTokenState() throws Exception {
        Path tempFile = Files.createTempFile("token-state", ".json");
        tempFile.toFile().deleteOnExit();

        when(settings.clientId()).thenReturn("client-id");
        when(settings.clientSecret()).thenReturn("client-secret");
        when(settings.isSandbox()).thenReturn(true);
        when(settings.refreshBufferMs()).thenReturn(1_800_000L);

        UpstoxPkceUtil.PkcePair pkcePair = UpstoxPkceUtil.generate();
        UpstoxRedirectServer redirectServer = mock(UpstoxRedirectServer.class);
        when(redirectServer.redirectUri()).thenReturn("http://localhost:18080/callback");
        when(redirectServer.waitForAuthorization(anyLong())).thenReturn("auth-code-123");

        UpstoxOAuthClient.TokenResponse tokenResp = new UpstoxOAuthClient.TokenResponse(
                "new-access-token",
                "new-refresh-token",
                86400L,
                System.currentTimeMillis()
        );
        when(oauthClient.exchangeCode(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(tokenResp);

        UpstoxTokenManager manager = new UpstoxTokenManager(
                oauthClient, settings,
                new com.tradej.broker.core.auth.JsonTokenStateStore(tempFile)
        );

        TokenState state = manager.performInteractiveOAuth(pkcePair, redirectServer);

        assertEquals(TokenSource.OAUTH, state.source());
        assertEquals("new-access-token", state.accessToken());
        assertEquals("new-refresh-token", state.refreshToken());
        verify(redirectServer).waitForAuthorization(300_000);
        
        // clientId is called twice in the method (once for URL building, once for token exchange)
        verify(settings, atLeastOnce()).clientId();
        verify(settings, atLeastOnce()).clientSecret();
        verify(settings).isSandbox();
        verify(settings).refreshBufferMs();
        // redirectServerPort is NOT called in performInteractiveOAuth - it's only used for server startup
    }
}