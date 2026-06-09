package com.tradej.app.integration;

import com.tradej.broker.dhan.auth.DhanTokenManager;
import com.tradej.broker.dhan.config.DhanAuthMode;
import com.tradej.broker.dhan.config.DhanApiEnvironment;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.Supplier;

final class LiveDhanTestSupport {
    enum Profile {
        LIVE,
        SANDBOX
    }

    private static final String CLIENT_ID_PROPERTY = "dhan.clientId";
    private static final String ACCESS_TOKEN_PROPERTY = "dhan.accessToken";
    private static final String AUTH_MODE_PROPERTY = "dhan.authMode";
    private static final String PIN_FILE_PROPERTY = "dhan.pinFile";
    private static final String TOTP_SECRET_FILE_PROPERTY = "dhan.totpSecretFile";
    private static final String TOKEN_STATE_FILE_PROPERTY = "dhan.tokenStateFile";
    private static final String REFRESH_BUFFER_PROPERTY = "dhan.refreshBufferMinutes";
    private static final Properties LIVE_PROPERTIES = loadProperties("config/dhan-local.properties");
    private static final Properties SANDBOX_PROPERTIES = loadProperties("config/dhan-sandbox.properties");

    private LiveDhanTestSupport() {
    }

    static DhanConnectionSettings connectionSettingsOrSkip() {
        return liveConnectionSettingsOrSkip();
    }

    static DhanConnectionSettings liveConnectionSettingsOrSkip() {
        DhanConnectionSettings settings = liveConnectionSettingsWithoutPreflightOrSkip();
        String fundLimitUrl = settings.restBaseUrl() + "/fundlimit";
        Assumptions.assumeTrue(
                preflightAuth(settings.clientId(), resolveLiveAccessToken(settings), fundLimitUrl),
                "Live Dhan credentials are missing, expired, or not visible to the Gradle test worker. "
                        + "For TOTP_GENERATED, ensure config/dhan-pin.txt and config/dhan-totp-secret.txt are present. "
                        + "If you are supplying env vars from the shell, rerun with --no-daemon.");
        return settings;
    }

    static DhanConnectionSettings liveConnectionSettingsWithoutPreflightOrSkip() {
        String clientId = valueForProfile(Profile.LIVE, "DHAN_CLIENT_ID", CLIENT_ID_PROPERTY, null);
        String accessToken = valueForProfile(Profile.LIVE, "DHAN_ACCESS_TOKEN", ACCESS_TOKEN_PROPERTY, null);
        DhanAuthMode authMode = authMode();
        Assumptions.assumeTrue(isPresent(clientId) && (isPresent(accessToken) || authMode != DhanAuthMode.STATIC),
                "Set DHAN credentials via config/dhan-local.properties, environment variables, or -Pdhan.clientId/-Pdhan.accessToken to run live Dhan integration tests.");
        if (authMode == DhanAuthMode.TOTP_GENERATED) {
            Path pinFile = DhanConfigPaths.resolve(value("DHAN_PIN_FILE", PIN_FILE_PROPERTY, "config/dhan-pin.txt"));
            Path totpFile = DhanConfigPaths.resolve(value("DHAN_TOTP_SECRET_FILE", TOTP_SECRET_FILE_PROPERTY, "config/dhan-totp-secret.txt"));
            Assumptions.assumeTrue(Files.exists(pinFile) && Files.exists(totpFile),
                    "TOTP_GENERATED requires pin and TOTP secret files at " + pinFile + " and " + totpFile);
        }
        return DhanConnectionSettings.withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.LIVE,
                valueForProfile(Profile.LIVE, "DHAN_REST_BASE_URL", "dhan.restBaseUrl", null),
                authMode,
                DhanConfigPaths.resolve(value("DHAN_PIN_FILE", PIN_FILE_PROPERTY, "config/dhan-pin.txt")),
                DhanConfigPaths.resolve(value("DHAN_TOTP_SECRET_FILE", TOTP_SECRET_FILE_PROPERTY, "config/dhan-totp-secret.txt")),
                DhanConfigPaths.resolve(value("DHAN_TOKEN_STATE_FILE", TOKEN_STATE_FILE_PROPERTY, "runtime/dhan-token-state.json")),
                longValue(REFRESH_BUFFER_PROPERTY, 10L)
        );
    }

    /**
     * Resolves a live access token, reusing a JVM-wide session and respecting Dhan's TOTP mint cooldown.
     *
     * <p>Set {@code DHAN_FORCE_TOKEN_REFRESH=true} to allow a forced regeneration when preflight fails.
     */
    static String resolveLiveAccessToken(DhanConnectionSettings settings) {
        return LiveDhanAuthSession.resolve(settings, forceTokenRefresh());
    }

    static boolean forceTokenRefresh() {
        String value = System.getenv("DHAN_FORCE_TOKEN_REFRESH");
        return value != null && value.equalsIgnoreCase("true");
    }

    static DhanConnectionSettings sandboxConnectionSettingsOrSkip() {
        String clientId = valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_CLIENT_ID", "dhan.sandbox.clientId", "2505162156");
        String accessToken = valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_ACCESS_TOKEN", "dhan.sandbox.accessToken", null);
        Assumptions.assumeTrue(isPresent(clientId) && isPresent(accessToken),
                "Set sandbox credentials in config/dhan-sandbox.properties or via DHAN_SANDBOX_CLIENT_ID/DHAN_SANDBOX_ACCESS_TOKEN.");
        String restBaseUrl = valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_REST_BASE_URL", "dhan.sandbox.restBaseUrl", "https://sandbox.dhan.co/v2");
        Assumptions.assumeTrue(preflightAuth(clientId, accessToken, restBaseUrl + "/fundlimit"),
                "Sandbox Dhan credentials are missing or expired.");
        return DhanConnectionSettings.withDefaults(
                clientId,
                accessToken,
                DhanApiEnvironment.SANDBOX,
                restBaseUrl,
                DhanAuthMode.STATIC,
                DhanConfigPaths.resolve(valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_PIN_FILE", PIN_FILE_PROPERTY, "config/dhan-pin.txt")),
                DhanConfigPaths.resolve(valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_TOTP_SECRET_FILE", TOTP_SECRET_FILE_PROPERTY, "config/dhan-totp-secret.txt")),
                DhanConfigPaths.resolve(valueForProfile(Profile.SANDBOX, "DHAN_SANDBOX_TOKEN_STATE_FILE", TOKEN_STATE_FILE_PROPERTY, "runtime/dhan-sandbox-token-state.json")),
                longValueForProfile(Profile.SANDBOX, REFRESH_BUFFER_PROPERTY, 10L)
        );
    }

    static String value(String envName, String propertyName) {
        return value(envName, propertyName, null);
    }

    static String value(String envName, String propertyName, String fallback) {
        return valueForProfile(Profile.LIVE, envName, propertyName, fallback);
    }

    static String valueForProfile(Profile profile, String envName, String propertyName, String fallback) {
        String property = System.getProperty(propertyName);
        if (isPresent(property)) {
            return property;
        }
        String local = propertiesFor(profile).getProperty(propertyName);
        if (isPresent(local)) {
            return local;
        }
        String envValue = envName == null ? null : System.getenv(envName);
        return isPresent(envValue) ? envValue : fallback;
    }

    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    static <T> T assumeSandboxSupported(Supplier<T> call, String capability) {
        try {
            return call.get();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "Sandbox does not support " + capability + ": " + ex.getMessage());
            throw new AssertionError("unreachable");
        }
    }

    static void assumeSandboxSupported(Runnable call, String capability) {
        assumeSandboxSupported(() -> {
            call.run();
            return null;
        }, capability);
    }

    private static final List<String> PENDING_SANDBOX_ORDER_IDS = Collections.synchronizedList(new ArrayList<>());

    static void trackSandboxOrderForCleanup(String orderId) {
        if (isPresent(orderId)) {
            PENDING_SANDBOX_ORDER_IDS.add(orderId);
        }
    }

    static void cancelTrackedSandboxOrders(com.tradej.broker.dhan.DhanBrokerConnection brokerConnection) {
        if (brokerConnection == null) {
            PENDING_SANDBOX_ORDER_IDS.clear();
            return;
        }
        for (String orderId : List.copyOf(PENDING_SANDBOX_ORDER_IDS)) {
            try {
                brokerConnection.orders().cancelOrder(orderId);
            } catch (Exception ignored) {
                // Sandbox may already have cancelled or filled the order.
            }
        }
        PENDING_SANDBOX_ORDER_IDS.clear();
    }

    private static DhanAuthMode authMode() {
        String value = value("DHAN_AUTH_MODE", AUTH_MODE_PROPERTY, DhanAuthMode.STATIC.name());
        return DhanAuthMode.valueOf(value.trim().toUpperCase());
    }

    private static long longValue(String propertyName, long fallback) {
        String value = value(null, propertyName, Long.toString(fallback));
        return Long.parseLong(value);
    }

    private static long longValueForProfile(Profile profile, String propertyName, long fallback) {
        String value = valueForProfile(profile, null, propertyName, Long.toString(fallback));
        return Long.parseLong(value);
    }

    static boolean preflightAuth(String clientId, String accessToken, String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("client-id", clientId)
                .header("access-token", accessToken)
                .GET()
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("dhanClientId");
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static Properties loadProperties(String relativePath) {
        Properties properties = new Properties();
        Path path = propertiesPath(relativePath);
        if (!Files.exists(path)) {
            return properties;
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load local Dhan config from " + path, ex);
        }
    }

    private static Properties propertiesFor(Profile profile) {
        return profile == Profile.SANDBOX ? SANDBOX_PROPERTIES : LIVE_PROPERTIES;
    }

    static Path propertiesPathForTest(String relativePath) {
        return propertiesPath(relativePath);
    }

    private static Path propertiesPath(String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        Path projectRoot = findProjectRoot();
        return projectRoot != null
                ? projectRoot.resolve(relativePath)
                : Path.of(relativePath).toAbsolutePath();
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("settings.gradle.kts"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
