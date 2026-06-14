package com.tradej.broker.upstox.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.core.auth.DefaultTokenLifecycleService;
import com.tradej.broker.core.auth.JsonTokenStateStore;
import com.tradej.broker.core.auth.TokenStateStore;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Upstox OAuth 2.0 token lifecycle manager.
 * <p>
 * Handles the full OAuth flow: PKCE generation, browser redirect, code exchange,
 * token refresh, and encrypted token persistence.
 */
public final class UpstoxTokenManager extends DefaultTokenLifecycleService implements UpstoxBearerTokenSource {

    private static final Logger log = LoggerFactory.getLogger(UpstoxTokenManager.class);

    private final UpstoxOAuthClient oauthClient;
    private final UpstoxConnectionSettings settings;

    public UpstoxTokenManager(
            UpstoxOAuthClient oauthClient,
            UpstoxConnectionSettings settings,
            TokenStateStore stateStore
    ) {
        super(stateStore, settings.refreshBufferMs());
        this.oauthClient = oauthClient;
        this.settings = settings;
    }

    /**
     * Convenience factory using JSON file token store.
     */
    public static UpstoxTokenManager create(
            UpstoxOAuthClient oauthClient,
            UpstoxConnectionSettings settings,
            Path tokenStatePath
    ) {
        return new UpstoxTokenManager(
                oauthClient,
                settings,
                new JsonTokenStateStore(tokenStatePath)
        );
    }

    /**
     * Performs the full interactive OAuth flow:
     * 1. Generates PKCE challenge
     * 2. Starts redirect server
     * 3. Builds authorization URL
     * 4. Opens browser (or prints URL)
     * 5. Waits for callback
     * 6. Exchanges code for tokens
     *
     * @param pkcePair    pre-generated PKCE pair
     * @param redirectServer local HTTP server for the OAuth callback
     * @return the new access token
     */
    public TokenState performInteractiveOAuth(
            UpstoxPkceUtil.PkcePair pkcePair,
            UpstoxRedirectServer redirectServer
    ) {
        String baseAuthUrl = settings.isSandbox()
                ? "https://sandbox-api.upstox.com/v2/login/authorization/dialog"
                : "https://api.upstox.com/v2/login/authorization/dialog";
        String authUrl = baseAuthUrl
                + "?client_id=" + settings.clientId()
                + "&redirect_uri=" + java.net.URLEncoder.encode(
                        redirectServer.redirectUri(), java.nio.charset.StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&code_challenge_method=S256"
                + "&code_challenge=" + pkcePair.codeChallenge();
        System.out.println("Open the following URL in your browser to authorize:");
        System.out.println(authUrl);
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(authUrl));
        } catch (Exception ignored) {
            System.out.println("(Could not open browser automatically. Copy the URL manually.)");
        }
        String code = redirectServer.waitForAuthorization(300_000);
        UpstoxOAuthClient.TokenResponse tokenResp = oauthClient.exchangeCode(
                code, settings.clientId(), settings.clientSecret(),
                redirectServer.redirectUri(), pkcePair.codeVerifier()
        );
        return new TokenState(
                tokenResp.accessToken(),
                tokenResp.refreshToken(),
                System.currentTimeMillis() + tokenResp.expiresInSeconds() * 1000L,
                tokenResp.issuedAtMs(),
                TokenSource.OAUTH
        );
    }

    @Override
    protected TokenState doAcquire() {
        if (currentState() != null && currentState().valid()) {
            return currentState();
        }
        boolean hasRefresh = settings.refreshToken() != null && !settings.refreshToken().isBlank();
        if (settings.accessToken() != null && !settings.accessToken().isBlank()) {
            if (hasRefresh) {
                // OAuth-style bootstrap: the refresh token is present, so future
                // ensureValid() calls may legitimately rotate the access token.
                return bootstrapFromConfiguredToken(settings.accessToken(), settings.refreshToken());
            }
            // Pure static: no refresh capability. Mark as STATIC so doRefresh
            // is never called and the 3:30 AM IST fallback is not used.
            long jwtExpiry = UpstoxJwtExpiry.parseExpiryEpochMs(settings.accessToken());
            return new TokenState(
                    settings.accessToken(),
                    null,
                    jwtExpiry > 0 ? jwtExpiry : UpstoxTokenExpiry.nextExpiryEpochMs(),
                    System.currentTimeMillis(),
                    TokenSource.STATIC
            );
        }
        throw new UnsupportedOperationException(
                "No Upstox access token available. Paste upstox.accessToken in config or run performInteractiveOAuth().");
    }

    private TokenState bootstrapFromConfiguredToken(String accessToken, String refreshToken) {
        long expiry = oauthClient.fetchProfile(accessToken);
        // Fallback 1: Try to parse JWT exp claim if profile endpoint doesn't return token_expiry
        if (expiry <= 0) {
            expiry = UpstoxJwtExpiry.parseExpiryEpochMs(accessToken);
        }
        // Fallback 2: Use daily 3:30 AM IST expiry calculation
        if (expiry <= 0) {
            expiry = UpstoxTokenExpiry.nextExpiryEpochMs();
        }
        return new TokenState(
                accessToken,
                refreshToken,
                expiry,
                System.currentTimeMillis(),
                TokenSource.STATIC
        );
    }

    @Override
    public String bearerToken() {
        ensureValid();
        TokenState state = currentState();
        if (state == null || state.accessToken() == null || state.accessToken().isBlank()) {
            throw new IllegalStateException("No Upstox access token available");
        }
        return state.accessToken();
    }

    @Override
    public long expiryEpochMs() {
        TokenState state = currentState();
        return state != null ? state.expiryEpochMs() : -1L;
    }

    @Override
    protected TokenState doRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("Cannot refresh token: no refresh token available");
        }
        UpstoxOAuthClient.TokenResponse tokenResp = oauthClient.refreshToken(
                refreshToken, settings.clientId(), settings.clientSecret()
        );
        return new TokenState(
                tokenResp.accessToken(),
                tokenResp.refreshToken(),
                System.currentTimeMillis() + tokenResp.expiresInSeconds() * 1000L,
                tokenResp.issuedAtMs(),
                TokenSource.OAUTH
        );
    }

    /**
     * Upgrades the token from a webhook-delivered token (Flow 2).
     * <p>
     * Only replaces the current state if:
     * <ul>
     *   <li>There is no current state, OR</li>
     *   <li>The incoming token expires later than the current one, OR</li>
     *   <li>The current token is already expired</li>
     * </ul>
     * This prevents a stale/earlier webhook payload from overwriting a newer token.
     *
     * @param accessToken the new access token from the notifier webhook
     * @param expiresAtMs epoch millis when the token expires
     */
    public void upgradeFromWebhook(String accessToken, long expiresAtMs) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken must not be blank");
        }
        if (expiresAtMs <= 0) {
            throw new IllegalArgumentException("expiresAtMs must be positive; use UpstoxTokenExpiry.nextExpiryEpochMs() as fallback");
        }

        lock.lock();
        try {
            TokenState current = currentState;
            boolean shouldReplace = current == null
                    || current.expiryEpochMs() <= System.currentTimeMillis()
                    || expiresAtMs > current.expiryEpochMs();

            if (!shouldReplace) {
                log.debug("upgradeFromWebhook skipped — incoming token expires at {} which is <= current {}",
                        expiresAtMs, current.expiryEpochMs());
                return;
            }

            // Preserve the previous refresh_token. Upstox webhook payloads do
            // not include a refresh_token, but the old one is still valid
            // until used; the next doRefresh() will rotate to a fresh pair.
            String preservedRefresh = current != null ? current.refreshToken() : null;
            TokenState newState = new TokenState(
                    accessToken,
                    preservedRefresh,
                    expiresAtMs,
                    System.currentTimeMillis(),
                    TokenSource.OAUTH
            );
            replaceState(newState);
            log.info("Upstox token upgraded via webhook — expires at {}", expiresAtMs);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a token holder for Upstox Extended Token (1-year validity, read-only).
     * Extended tokens are generated from Developer Apps → Analytics tab and
     * are valid for 1 year or until user revokes access.
     *
     * @param extendedToken the extended token from Upstox Developer Apps
     * @return a token source that doesn't attempt refresh
     */
    public static UpstoxBearerTokenSource createExtendedTokenHolder(String extendedToken) {
        return new UpstoxExtendedTokenHolder(extendedToken);
    }

    /**
     * Token holder for Upstox Extended Token (long-lived read-only access).
     * Valid for 1 year, no refresh capability.
     */
    public static final class UpstoxExtendedTokenHolder implements UpstoxBearerTokenSource {
        private final String token;
        private final long expiryEpochMs;

        public UpstoxExtendedTokenHolder(String extendedToken) {
            this.token = extendedToken;
            this.expiryEpochMs = UpstoxJwtExpiry.parseExpiryEpochMs(extendedToken);
        }

        @Override
        public String bearerToken() {
            return token;
        }

        @Override
        public void ensureValid() {
            if (expiryEpochMs > 0 && System.currentTimeMillis() >= expiryEpochMs) {
                throw new IllegalStateException(
                        "Upstox extended token expired at " + java.time.Instant.ofEpochMilli(expiryEpochMs)
                                + " — regenerate from Developer Apps → Analytics tab");
            }
        }

        @Override
        public long expiryEpochMs() {
            return expiryEpochMs;
        }

        @Override
        public boolean analyticsOnly() {
            return true; // Extended tokens are read-only
        }
    }
}
