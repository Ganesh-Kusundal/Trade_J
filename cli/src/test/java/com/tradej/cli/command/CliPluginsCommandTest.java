package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliPluginsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void pluginsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("plugins"),
                "plugins command should be registered");
    }

    @Test
    void discoverPlugins_findsSpiRegistrations() {
        List<CliPluginsCommand.PluginEntry> plugins = CliPluginsCommand.discoverPlugins();
        assertFalse(plugins.isEmpty(), "should discover at least one SPI registration");
        assertTrue(plugins.size() >= 5, "should find broker + indicator + transformation providers, found: " + plugins.size());

        List<String> spis = plugins.stream().map(CliPluginsCommand.PluginEntry::spiInterface).distinct().toList();
        assertTrue(spis.contains("BrokerProvider"), "BrokerProvider SPI should be discovered");
        assertTrue(spis.contains("IndicatorProvider"), "IndicatorProvider SPI should be discovered");
    }

    @Test
    void pluginEntries_haveModuleInfo() {
        List<CliPluginsCommand.PluginEntry> plugins = CliPluginsCommand.discoverPlugins();
        for (CliPluginsCommand.PluginEntry p : plugins) {
            assertNotNull(p.spiInterface(), "SPI interface should not be null");
            assertNotNull(p.providerClass(), "provider class should not be null");
            assertNotNull(p.module(), "module should not be null");
            assertFalse(p.providerClass().isBlank(), "provider class should not be blank");
        }
    }
}
