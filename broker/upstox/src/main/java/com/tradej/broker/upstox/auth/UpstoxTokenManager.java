package com.tradej.broker.upstox.auth;

import com.tradej.broker.api.auth.TokenSource;
import com.tradej.broker.api.auth.TokenState;
import com.tradej.broker.core.auth.DefaultTokenLifecycleService;
import com.tradej.broker.core.auth.JsonTokenStateStore;
import com.tradej.broker.core.auth.TokenStateStore;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;

import java.nio.file.Path;

/**
 * Upstox OAuth 2.0 token lifecycle manager.
 * <p>
 * Handles the full OAuth flow: PKCE generation, browser redirect, code exchange,
 * token refresh, and encrypted token persistence.
 */
public final class UpstoxTokenManager extends DefaultTokenLifecycleService implements UpstoxBearerTokenSource {

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
        if (settings.accessToken() != null && !settings.accessToken().isBlank()) {
            return bootstrapFromConfiguredToken(settings.accessToken(), settings.refreshToken());
        }
        throw new UnsupportedOperationException(
                "No Upstox access token available. Paste upstox.accessToken in config or run performInteractiveOAuth().");
    }

    private TokenState bootstrapFromConfiguredToken(String accessToken, String refreshToken) {
        long expiry = oauthClient.fetchProfile(accessToken);
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
}
