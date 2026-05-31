package com.tradej.cli.config;

import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CliConfigBrokerTest {

    @Test
    void brokerTypeDefaultsToDhan() {
        assertEquals(CliConfig.BrokerType.DHAN, CliConfig.BrokerType.parse(null));
        assertEquals(CliConfig.BrokerType.UPSTOX, CliConfig.BrokerType.parse("upstox"));
    }

    @Test
    void upstoxLiveAnalyticsSettingsFromProperties(@TempDir Path repoRoot) throws IOException {
        Path configDir = repoRoot.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("upstox-live.properties"), """
                upstox.live.clientId=live-client
                upstox.live.clientSecret=live-secret
                upstox.live.analyticsToken=analytics-token
                """);

        withRepoRoot(repoRoot, () -> {
            UpstoxConnectionSettings settings = CliConfig.upstoxConnectionSettings(CliConfig.Profile.LIVE);
            assertTrue(settings.analyticsOnly());
            assertEquals("analytics-token", settings.analyticsToken());
            assertFalse(settings.isSandbox());
        });
    }

    @Test
    void upstoxSandboxRequiresAccessToken(@TempDir Path repoRoot) throws IOException {
        Path configDir = repoRoot.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("upstox-sandbox.properties"), """
                upstox.sandbox.clientId=sandbox-client
                upstox.sandbox.clientSecret=sandbox-secret
                """);

        withRepoRoot(repoRoot, () -> assertThrows(
                IllegalStateException.class,
                () -> CliConfig.upstoxConnectionSettings(CliConfig.Profile.SANDBOX)));
    }

    private static void withRepoRoot(Path repoRoot, Runnable action) {
        String previousWorkspace = System.getProperty("tradej.workspace.root");
        System.setProperty("tradej.workspace.root", repoRoot.toString());
        try {
            action.run();
        } finally {
            if (previousWorkspace == null) {
                System.clearProperty("tradej.workspace.root");
            } else {
                System.setProperty("tradej.workspace.root", previousWorkspace);
            }
        }
    }
}
