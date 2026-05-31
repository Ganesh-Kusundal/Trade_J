package com.tradej.app.integration;

import com.tradej.broker.upstox.auth.UpstoxOAuthClient;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class LiveUpstoxTestSupport {
    private static final Properties SANDBOX_PROPERTIES = loadProperties("config/upstox-sandbox.properties");
    private static final Properties LIVE_PROPERTIES = loadProperties("config/upstox-live.properties");

    private LiveUpstoxTestSupport() {
    }

    static UpstoxConnectionSettings sandboxConnectionSettingsOrSkip() {
        String clientId = sandboxValue("UPSTOX_SANDBOX_CLIENT_ID", "upstox.sandbox.clientId");
        String clientSecret = sandboxValue("UPSTOX_SANDBOX_CLIENT_SECRET", "upstox.sandbox.clientSecret");
        String redirectUri = sandboxValue(
                "UPSTOX_SANDBOX_REDIRECT_URI", "upstox.sandbox.redirectUri", "http://127.0.0.1:18080/callback");
        String accessToken = sandboxValue("UPSTOX_SANDBOX_ACCESS_TOKEN", "upstox.sandbox.accessToken");
        Assumptions.assumeTrue(isPresent(clientId) && isPresent(clientSecret) && isPresent(accessToken),
                "Configure config/upstox-sandbox.properties or UPSTOX_SANDBOX_* env vars.");
        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                clientId,
                clientSecret,
                redirectUri,
                accessToken,
                null,
                null,
                false,
                true,
                18080,
                1_800_000L,
                600_000L
        );
        Assumptions.assumeTrue(preflightAccessToken(settings, true),
                "Upstox sandbox token is missing, expired, or unreachable.");
        return settings;
    }

    static UpstoxConnectionSettings plusExpiredConnectionSettingsOrSkip() {
        String clientId = liveValue("UPSTOX_CLIENT_ID", "upstox.live.clientId");
        String clientSecret = liveValue("UPSTOX_CLIENT_SECRET", "upstox.live.clientSecret");
        String redirectUri = liveValue(
                "UPSTOX_REDIRECT_URI", "upstox.live.redirectUri", "http://127.0.0.1:18080/callback");
        String accessToken = liveValue("UPSTOX_ACCESS_TOKEN", "upstox.live.accessToken");
        Assumptions.assumeTrue(isPresent(clientId) && isPresent(clientSecret) && isPresent(accessToken),
                "Configure config/upstox-live.properties with upstox.live.accessToken (Plus algo token) or UPSTOX_ACCESS_TOKEN.");
        return new UpstoxConnectionSettings(
                clientId,
                clientSecret,
                redirectUri,
                accessToken,
                null,
                null,
                false,
                false,
                18080,
                1_800_000L,
                600_000L
        );
    }

    static UpstoxConnectionSettings analyticsConnectionSettingsOrSkip() {
        String clientId = liveValue("UPSTOX_CLIENT_ID", "upstox.live.clientId");
        String clientSecret = liveValue("UPSTOX_CLIENT_SECRET", "upstox.live.clientSecret");
        String redirectUri = liveValue(
                "UPSTOX_REDIRECT_URI", "upstox.live.redirectUri", "http://127.0.0.1:18080/callback");
        String analyticsToken = liveValue("UPSTOX_ANALYTICS_TOKEN", "upstox.live.analyticsToken");
        Assumptions.assumeTrue(isPresent(clientId) && isPresent(clientSecret) && isPresent(analyticsToken),
                "Configure config/upstox-live.properties with upstox.live.analyticsToken or UPSTOX_ANALYTICS_TOKEN.");
        UpstoxConnectionSettings settings = new UpstoxConnectionSettings(
                clientId,
                clientSecret,
                redirectUri,
                null,
                null,
                analyticsToken,
                true,
                false,
                18080,
                1_800_000L,
                600_000L
        );
        Assumptions.assumeTrue(preflightAnalyticsToken(settings),
                "Upstox analytics token is missing, expired, or unreachable on live API.");
        return settings;
    }

    static boolean integrationEnabled() {
        String flag = System.getenv("UPSTOX_TEST_ENABLED");
        if (flag != null) {
            return "true".equalsIgnoreCase(flag);
        }
        return Files.exists(propertiesPath("config/upstox-sandbox.properties"))
                || Files.exists(propertiesPath("config/upstox-live.properties"));
    }

    static boolean analyticsIntegrationEnabled() {
        String env = System.getenv("UPSTOX_ANALYTICS_TOKEN");
        if (isPresent(env)) {
            return true;
        }
        return isPresent(LIVE_PROPERTIES.getProperty("upstox.live.analyticsToken"));
    }

    static void assumeIntegrationEnabled() {
        Assumptions.assumeTrue(integrationEnabled(),
                "Set UPSTOX_TEST_ENABLED=true or provide upstox credential files.");
    }

    private static boolean preflightAccessToken(UpstoxConnectionSettings settings, boolean sandbox) {
        UpstoxOAuthClient client = new UpstoxOAuthClient(
                HttpClient.newHttpClient(),
                sandbox ? "https://sandbox-api.upstox.com/v2" : "https://api.upstox.com/v2"
        );
        return client.fetchProfile(settings.accessToken()) > System.currentTimeMillis();
    }

    private static boolean preflightAnalyticsToken(UpstoxConnectionSettings settings) {
        UpstoxOAuthClient client = new UpstoxOAuthClient(
                HttpClient.newHttpClient(),
                "https://api.upstox.com/v2"
        );
        return client.validateReadOnlyToken(settings.analyticsToken());
    }

    private static String sandboxValue(String envName, String propertyName) {
        return sandboxValue(envName, propertyName, null);
    }

    private static String sandboxValue(String envName, String propertyName, String defaultValue) {
        String env = System.getenv(envName);
        if (isPresent(env)) {
            return env;
        }
        String property = SANDBOX_PROPERTIES.getProperty(propertyName);
        if (isPresent(property)) {
            return property;
        }
        return defaultValue;
    }

    private static String liveValue(String envName, String propertyName) {
        return liveValue(envName, propertyName, null);
    }

    private static String liveValue(String envName, String propertyName, String defaultValue) {
        String env = System.getenv(envName);
        if (isPresent(env)) {
            return env;
        }
        String property = LIVE_PROPERTIES.getProperty(propertyName);
        if (isPresent(property)) {
            return property;
        }
        return defaultValue;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static Properties loadProperties(String relativePath) {
        Properties properties = new Properties();
        Path path = propertiesPath(relativePath);
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read " + path, ex);
        }
        return properties;
    }

    static Path propertiesPath(String relativePath) {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path direct = cwd.resolve(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve(relativePath))) {
            return parent.resolve(relativePath);
        }
        return direct;
    }
}
