package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliModulesCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void modulesCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("modules"),
                "modules command should be registered");
    }

    @Test
    void parseModules_parsesSettingsGradle() {
        List<CliModulesCommand.ModuleEntry> modules = CliModulesCommand.parseModules();
        assertFalse(modules.isEmpty(), "should parse at least one module from settings.gradle");
        assertTrue(modules.size() >= 30, "should find at least 30 modules, found: " + modules.size());

        List<String> names = modules.stream().map(CliModulesCommand.ModuleEntry::name).toList();
        assertTrue(names.contains("core"), "core module should be found");
        assertTrue(names.contains("cli"), "cli module should be found");
        assertTrue(names.contains("broker-gateway"), "broker-gateway module should be found");
    }

    @Test
    void categorize_assignsCorrectCategories() {
        assertEquals("broker", CliModulesCommand.categorize("broker/dhan"));
        assertEquals("trading", CliModulesCommand.categorize("trading/strategy"));
        assertEquals("data", CliModulesCommand.categorize("data/persistence"));
        assertEquals("runtime", CliModulesCommand.categorize("runtime/disruptor"));
        assertEquals("core", CliModulesCommand.categorize("core"));
        assertEquals("broker", CliModulesCommand.categorize("broker-gateway"));
    }
}
