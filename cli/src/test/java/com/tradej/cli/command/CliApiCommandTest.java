package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliApiCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void apiCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("api"),
                "api command should be registered");
    }

    @Test
    void apiCommand_hasFilterOption() {
        var apiCmd = cmd.getSubcommands().get("api");
        assertNotNull(apiCmd);
        assertNotNull(apiCmd.getCommandSpec().optionsMap().get("--filter"));
        assertNotNull(apiCmd.getCommandSpec().optionsMap().get("--controller"));
    }

    @Test
    void scanControllers_findsControllers() {
        List<CliApiCommand.ApiEndpoint> endpoints = CliApiCommand.scanControllers();
        assertFalse(endpoints.isEmpty(), "should find at least one controller");
        assertTrue(endpoints.size() >= 20, "should find at least 20 endpoints, found: " + endpoints.size());

        List<String> controllers = endpoints.stream()
                .map(CliApiCommand.ApiEndpoint::controller).distinct().toList();
        assertTrue(controllers.contains("OrderController"), "OrderController should be found");
        assertTrue(controllers.contains("MarketDataController"), "MarketDataController should be found");
    }

    @Test
    void categorize_assignsCorrectCategories() {
        assertEquals("orders", CliApiCommand.categorize("OrderController"));
        assertEquals("market-data", CliApiCommand.categorize("MarketDataController"));
        assertEquals("analytics", CliApiCommand.categorize("AnalyticsController"));
        assertEquals("options", CliApiCommand.categorize("OptionsAnalyticsController"));
        assertEquals("scanner", CliApiCommand.categorize("ScanController"));
        assertEquals("admin", CliApiCommand.categorize("AdminController"));
        assertEquals("pipeline", CliApiCommand.categorize("PipelineController"));
        assertEquals("replay", CliApiCommand.categorize("ReplayStudioController"));
    }

    @Test
    void endpoints_haveHttpMethods() {
        List<CliApiCommand.ApiEndpoint> endpoints = CliApiCommand.scanControllers();
        for (var e : endpoints) {
            assertTrue(List.of("GET", "POST", "PUT", "DELETE", "PATCH").contains(e.httpMethod()),
                    "HTTP method should be valid: " + e.httpMethod());
            assertFalse(e.path().isEmpty(), "path should not be empty");
        }
    }
}
