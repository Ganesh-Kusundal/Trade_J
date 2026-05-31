package com.tradej.app.integration;

import com.tradej.broker.icici.auth.BreezeSessionExchange;
import com.tradej.broker.icici.auth.BreezeTokenManager;
import com.tradej.broker.icici.auth.BreezeTotpGenerator;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.config.IciciAuthMode;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class LiveIciciTestSupport {
    private static final Properties LOCAL_PROPERTIES = loadProperties("config/icici-local.properties");

    private LiveIciciTestSupport() {
    }

    static BreezeConnectionSettings connectionSettingsOrSkip() {
        Assumptions.assumeTrue(isEnabled(), enabledMessage());
        String appKey = value("ICICI_API_KEY", "icici.appKey");
        String secretKey = value("ICICI_API_SECRET", "icici.secretKey");
        Assumptions.assumeTrue(isPresent(appKey) && isPresent(secretKey),
                "Configure ICICI credentials in config/icici-local.properties or environment variables.");
        IciciAuthMode authMode = authMode();
        if (authMode == IciciAuthMode.TOTP_GENERATED || authMode == IciciAuthMode.BROWSER_AUTOMATED) {
            Path totpFile = pathValue("ICICI_TOTP_SECRET_FILE", "icici.totpSecretFile", "config/icici-totp-secret.txt");
            Assumptions.assumeTrue(Files.exists(totpFile), "Missing ICICI TOTP secret file: " + totpFile);
        }
        if (authMode == IciciAuthMode.BROWSER_AUTOMATED) {
            Path usernameFile = pathValue("ICICI_USERNAME_FILE", "icici.usernameFile", "config/icici-username.txt");
            Path passwordFile = pathValue("ICICI_PASSWORD_FILE", "icici.passwordFile", "config/icici-password.txt");
            Assumptions.assumeTrue(Files.exists(usernameFile), "Missing ICICI username file: " + usernameFile);
            Assumptions.assumeTrue(Files.exists(passwordFile), "Missing ICICI password file: " + passwordFile);
        } else if (authMode == IciciAuthMode.API_SESSION) {
            Path apiSessionFile = pathValue("ICICI_API_SESSION_FILE", "icici.apiSessionFile", "config/icici-api-session.txt");
            try {
                Assumptions.assumeTrue(Files.exists(apiSessionFile) && !Files.readString(apiSessionFile).isBlank(),
                        "Missing ICICI API session file: " + apiSessionFile);
            } catch (IOException ex) {
                Assumptions.assumeTrue(false, "Failed to read ICICI API session file: " + apiSessionFile);
            }
        }
        return BreezeConnectionSettings.withDefaults(
                appKey,
                secretKey,
                value("ICICI_SESSION_TOKEN", "icici.sessionToken"),
                authMode,
                pathValue("ICICI_TOTP_SECRET_FILE", "icici.totpSecretFile", "config/icici-totp-secret.txt"),
                pathValue("ICICI_USERNAME_FILE", "icici.usernameFile", "config/icici-username.txt"),
                pathValue("ICICI_PASSWORD_FILE", "icici.passwordFile", "config/icici-password.txt"),
                pathValue("ICICI_API_SESSION_FILE", "icici.apiSessionFile", "config/icici-api-session.txt"),
                pathValue("ICICI_TOKEN_STATE_FILE", "icici.tokenStateFile", "runtime/icici-token-state.json"),
                Boolean.parseBoolean(value("ICICI_ORDER_TEST_ENABLED", "icici.ordersEnabled", "false")),
                Long.parseLong(value("ICICI_REFRESH_BUFFER_MINUTES", "icici.refreshBufferMinutes", "10")),
                Integer.parseInt(value("ICICI_LOGIN_REDIRECT_PORT", "icici.loginRedirectPort", "9080")),
                value("ICICI_LOGIN_REDIRECT_PATH", "icici.loginRedirectPath", "/api"),
                Boolean.parseBoolean(value("ICICI_BROWSER_HEADLESS", "icici.browserHeadless", "true")),
                Long.parseLong(value("ICICI_BROWSER_LOGIN_TIMEOUT_SECONDS", "icici.browserLoginTimeoutSeconds", "120"))
        );
    }

    static String totpSessionInput(BreezeConnectionSettings settings) throws IOException {
        if (settings.authMode() == IciciAuthMode.API_SESSION
                || settings.authMode() == IciciAuthMode.BROWSER_AUTOMATED) {
            if (Files.exists(settings.apiSessionFile())) {
                String cached = Files.readString(settings.apiSessionFile()).trim();
                if (!cached.isBlank()) {
                    return cached;
                }
            }
        }
        if (settings.authMode() == IciciAuthMode.TOTP_GENERATED) {
            String secret = Files.readString(settings.totpSecretFile()).trim();
            return new BreezeTotpGenerator().currentCode(secret);
        }
        return settings.staticSessionToken();
    }

    static void preflightSessionOrSkip(BreezeConnectionSettings settings) {
        try {
            if (settings.authMode() == IciciAuthMode.BROWSER_AUTOMATED) {
                BreezeTokenManager tokenManager = new BreezeTokenManager(settings);
                tokenManager.ensureValid();
                Assumptions.assumeTrue(tokenManager.session().base64SessionToken() != null,
                        "ICICI browser session exchange failed");
                return;
            }
            BreezeSessionExchange exchange = new BreezeSessionExchange();
            var session = exchange.exchange(settings.appKey(), totpSessionInput(settings));
            Assumptions.assumeTrue(session.base64SessionToken() != null && !session.base64SessionToken().isBlank(),
                    "ICICI session exchange failed");
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "ICICI preflight failed: " + ex.getMessage());
        }
    }

    private static boolean isEnabled() {
        return isSessionDrillEnabled()
                || "true".equalsIgnoreCase(System.getenv("ICICI_TEST_ENABLED"))
                || "true".equalsIgnoreCase(System.getProperty("ICICI_TEST_ENABLED"));
    }

    static boolean isSessionDrillEnabled() {
        return "true".equalsIgnoreCase(System.getenv("ICICI_SESSION_DRILL"))
                || "true".equalsIgnoreCase(System.getProperty("ICICI_SESSION_DRILL"));
    }

    private static String enabledMessage() {
        return "Set ICICI_TEST_ENABLED=true or ICICI_SESSION_DRILL=true to run live ICICI tests.";
    }

    private static IciciAuthMode authMode() {
        String raw = value("ICICI_AUTH_MODE", "icici.authMode", "BROWSER_AUTOMATED");
        return IciciAuthMode.valueOf(raw.trim().toUpperCase());
    }

    private static String value(String envKey, String propertyKey) {
        return value(envKey, propertyKey, null);
    }

    private static String value(String envKey, String propertyKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (isPresent(env)) {
            return env;
        }
        String property = System.getProperty(propertyKey);
        if (isPresent(property)) {
            return property;
        }
        if (LOCAL_PROPERTIES != null) {
            String configured = LOCAL_PROPERTIES.getProperty(propertyKey);
            if (isPresent(configured)) {
                return configured;
            }
        }
        return defaultValue;
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static Path pathValue(String envKey, String propertyKey, String defaultValue) {
        return propertiesPath(value(envKey, propertyKey, defaultValue));
    }

    private static Properties loadProperties(String path) {
        Properties properties = new Properties();
        Path file = propertiesPath(path);
        if (!Files.exists(file)) {
            return properties;
        }
        try (var input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            return properties;
        }
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
        return Path.of(relativePath).toAbsolutePath();
    }
}
