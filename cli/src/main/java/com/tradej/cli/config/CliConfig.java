package com.tradej.cli.config;

import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.brokergateway.config.BrokerProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class CliConfig {
    public enum Profile {
        LIVE,
        SANDBOX
    }

    public enum BrokerType {
        DHAN,
        UPSTOX,
        ICICI;

        public static BrokerType parse(String value) {
            if (value == null || value.isBlank()) {
                return DHAN;
            }
            return valueOf(value.trim().toUpperCase());
        }
    }

    private CliConfig() {
    }

    public static BrokerType brokerType() {
        String env = System.getenv("TRADEJ_BROKER");
        if (env != null && !env.isBlank()) {
            return BrokerType.parse(env);
        }
        String prop = System.getProperty("tradej.broker");
        if (prop != null && !prop.isBlank()) {
            return BrokerType.parse(prop);
        }
        return BrokerType.DHAN;
    }

    public static String attachUrl() {
        String env = System.getenv("TRADEJ_ATTACH_URL");
        if (env != null && !env.isBlank()) {
            return trimTrailingSlash(env);
        }
        String prop = System.getProperty("tradej.attach.url");
        if (prop != null && !prop.isBlank()) {
            return trimTrailingSlash(prop);
        }
        return "http://127.0.0.1:8080";
    }

    public static DhanConnectionSettings dhanConnectionSettings(Profile profile) {
        Properties properties = loadProperties(profile);
        String clientId = firstNonBlank(
                System.getenv(profile == Profile.LIVE ? "DHAN_CLIENT_ID" : "DHAN_SANDBOX_CLIENT_ID"),
                properties.getProperty(profile == Profile.LIVE ? "dhan.clientId" : "dhan.sandbox.clientId")
        );
        String accessToken = firstNonBlank(
                System.getenv(profile == Profile.LIVE ? "DHAN_ACCESS_TOKEN" : "DHAN_SANDBOX_ACCESS_TOKEN"),
                properties.getProperty(profile == Profile.LIVE ? "dhan.accessToken" : "dhan.sandbox.accessToken")
        );
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Missing Dhan client id for profile " + profile
                    + ". Configure config/dhan-local.properties or config/dhan-sandbox.properties.");
        }
        DhanAuthMode authMode = DhanAuthMode.valueOf(
                firstNonBlank(
                        System.getenv("DHAN_AUTH_MODE"),
                        properties.getProperty("dhan.authMode"),
                        DhanAuthMode.STATIC.name()
                ).trim().toUpperCase()
        );
        Path pinFile = DhanConfigPaths.resolve(firstNonBlank(
                System.getenv("DHAN_PIN_FILE"),
                properties.getProperty("dhan.pinFile"),
                "config/dhan-pin.txt"
        ));
        Path totpFile = DhanConfigPaths.resolve(firstNonBlank(
                System.getenv("DHAN_TOTP_SECRET_FILE"),
                properties.getProperty("dhan.totpSecretFile"),
                "config/dhan-totp-secret.txt"
        ));
        Path tokenState = DhanConfigPaths.resolve(firstNonBlank(
                System.getenv(profile == Profile.LIVE ? "DHAN_TOKEN_STATE_FILE" : "DHAN_SANDBOX_TOKEN_STATE_FILE"),
                properties.getProperty(profile == Profile.LIVE ? "dhan.tokenStateFile" : "dhan.sandbox.tokenStateFile"),
                profile == Profile.LIVE ? "runtime/dhan-token-state.json" : "runtime/dhan-sandbox-token-state.json"
        ));
        String restBaseUrl = firstNonBlank(
                System.getenv(profile == Profile.LIVE ? "DHAN_REST_BASE_URL" : "DHAN_SANDBOX_REST_BASE_URL"),
                properties.getProperty(profile == Profile.LIVE ? "dhan.restBaseUrl" : "dhan.sandbox.restBaseUrl")
        );
        if (profile == Profile.SANDBOX) {
            return DhanConnectionSettings.withDefaults(
                    clientId,
                    accessToken,
                    DhanApiEnvironment.SANDBOX,
                    restBaseUrl,
                    DhanAuthMode.STATIC,
                    pinFile,
                    totpFile,
                    tokenState,
                    10L
            );
        }
        return DhanConnectionSettings.withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.LIVE,
                restBaseUrl,
                authMode,
                pinFile,
                totpFile,
                tokenState,
                10L
        );
    }

    public static String resolveAccessToken(Profile profile, DhanConnectionSettings settings) {
        if (settings.authMode() != DhanAuthMode.TOTP_GENERATED) {
            if (settings.accessToken() == null || settings.accessToken().isBlank()) {
                throw new IllegalStateException("Missing access token for profile " + profile);
            }
            return settings.accessToken();
        }
        DhanTokenManager tokenManager = new DhanTokenManager(settings);
        return tokenManager.getAccessToken();
    }

    public static UpstoxConnectionSettings upstoxConnectionSettings(Profile profile) {
        Properties properties = loadUpstoxProperties(profile);
        if (profile == Profile.SANDBOX) {
            String clientId = firstNonBlank(
                    System.getenv("UPSTOX_SANDBOX_CLIENT_ID"),
                    properties.getProperty("upstox.sandbox.clientId")
            );
            String clientSecret = firstNonBlank(
                    System.getenv("UPSTOX_SANDBOX_CLIENT_SECRET"),
                    properties.getProperty("upstox.sandbox.clientSecret")
            );
            String redirectUri = firstNonBlank(
                    System.getenv("UPSTOX_SANDBOX_REDIRECT_URI"),
                    properties.getProperty("upstox.sandbox.redirectUri"),
                    "http://127.0.0.1:18080/callback"
            );
            String accessToken = firstNonBlank(
                    System.getenv("UPSTOX_SANDBOX_ACCESS_TOKEN"),
                    properties.getProperty("upstox.sandbox.accessToken")
            );
            if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
                throw new IllegalStateException("Missing Upstox sandbox credentials. Configure config/upstox-sandbox.properties.");
            }
            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Missing Upstox sandbox access token.");
            }
            return new UpstoxConnectionSettings(
                    clientId, clientSecret, redirectUri, accessToken, null, null, null,
                    false, true, 18080, 1_800_000L, 600_000L
            );
        }

        String clientId = firstNonBlank(
                System.getenv("UPSTOX_CLIENT_ID"),
                properties.getProperty("upstox.live.clientId")
        );
        String clientSecret = firstNonBlank(
                System.getenv("UPSTOX_CLIENT_SECRET"),
                properties.getProperty("upstox.live.clientSecret")
        );
        String redirectUri = firstNonBlank(
                System.getenv("UPSTOX_REDIRECT_URI"),
                properties.getProperty("upstox.live.redirectUri"),
                "http://127.0.0.1:18080/callback"
        );
        String analyticsToken = firstNonBlank(
                System.getenv("UPSTOX_ANALYTICS_TOKEN"),
                properties.getProperty("upstox.live.analyticsToken")
        );
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Missing Upstox live credentials. Configure config/upstox-live.properties.");
        }
        if (analyticsToken != null && !analyticsToken.isBlank()) {
            return new UpstoxConnectionSettings(
                    clientId, clientSecret, redirectUri, null, null, analyticsToken, null,
                    true, false, 18080, 1_800_000L, 600_000L
            );
        }
        String accessToken = firstNonBlank(
                System.getenv("UPSTOX_ACCESS_TOKEN"),
                properties.getProperty("upstox.live.accessToken")
        );
        String refreshToken = firstNonBlank(
                System.getenv("UPSTOX_REFRESH_TOKEN"),
                properties.getProperty("upstox.live.refreshToken")
        );
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "Missing Upstox live token. Set upstox.live.analyticsToken or upstox.live.accessToken.");
        }
        return new UpstoxConnectionSettings(
                clientId, clientSecret, redirectUri, accessToken, refreshToken, null, null,
                false, false, 18080, 1_800_000L, 600_000L
        );
    }

    /** @deprecated use {@link #dhanConnectionSettings(Profile)} */
    @Deprecated
    public static DhanConnectionSettings connectionSettings(Profile profile) {
        return dhanConnectionSettings(profile);
    }

    public static BrokerProfile.IciciConfig iciciConfig() {
        Path propsPath = DhanConfigPaths.resolve("config/icici-local.properties");
        Properties props = new Properties();
        if (Files.exists(propsPath)) {
            try (InputStream in = Files.newInputStream(propsPath)) {
                props.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read " + propsPath, ex);
            }
        }
        String appKey = firstNonBlank(System.getenv("ICICI_APP_KEY"), props.getProperty("icici.appKey"));
        String secretKey = firstNonBlank(System.getenv("ICICI_SECRET_KEY"), props.getProperty("icici.secretKey"));
        String authModeStr = firstNonBlank(System.getenv("ICICI_AUTH_MODE"), props.getProperty("icici.authMode", "BROWSER_AUTOMATED"));
        com.tradej.broker.icici.config.IciciAuthMode authMode =
                com.tradej.broker.icici.config.IciciAuthMode.valueOf(authModeStr.toUpperCase());
        return new BrokerProfile.IciciConfig(
                appKey, secretKey, null, authMode,
                DhanConfigPaths.resolve(props.getProperty("icici.totpSecretFile", "config/icici-totp-secret.txt")),
                DhanConfigPaths.resolve("config/icici-username.txt"),
                DhanConfigPaths.resolve("config/icici-password.txt"),
                DhanConfigPaths.resolve(props.getProperty("icici.apiSessionFile", "config/icici-api-session.txt")),
                DhanConfigPaths.resolve(props.getProperty("icici.tokenStateFile", "runtime/icici-token-state.json")),
                Boolean.parseBoolean(props.getProperty("icici.ordersEnabled", "false")),
                Long.parseLong(props.getProperty("icici.refreshBufferMinutes", "10")),
                Integer.parseInt(props.getProperty("icici.loginRedirectPort", "9080")),
                props.getProperty("icici.loginRedirectPath", "/api"),
                Boolean.parseBoolean(props.getProperty("icici.browserHeadless", "false")),
                Long.parseLong(props.getProperty("icici.browserLoginTimeoutSeconds", "180"))
        );
    }

    private static Properties loadUpstoxProperties(Profile profile) {
        String file = profile == Profile.LIVE ? "config/upstox-live.properties" : "config/upstox-sandbox.properties";
        Path path = DhanConfigPaths.resolve(file);
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read " + path, ex);
            }
        }
        return properties;
    }

    private static Properties loadProperties(Profile profile) {
        String file = profile == Profile.LIVE ? "config/dhan-local.properties" : "config/dhan-sandbox.properties";
        Path path = DhanConfigPaths.resolve(file);
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                properties.load(in);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read " + path, ex);
            }
        }
        return properties;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
