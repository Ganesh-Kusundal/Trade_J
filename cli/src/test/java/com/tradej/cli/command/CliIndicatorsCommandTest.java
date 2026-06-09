package com.tradej.cli.command;

import com.tradej.cli.TradeCli;
import com.tradej.indicators.spi.IndicatorRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliIndicatorsCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void indicatorsCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("indicators"),
                "indicators command should be registered");
    }

    @Test
    void indicatorRegistry_discoversProviders() {
        IndicatorRegistry registry = IndicatorRegistry.discover();
        assertTrue(registry.size() >= 6, "should discover at least 6 built-in indicators");
        assertTrue(registry.names().contains("rsi"), "RSI should be registered");
        assertTrue(registry.names().contains("ema"), "EMA should be registered");
        assertTrue(registry.names().contains("sma"), "SMA should be registered");
    }

    @Test
    void indicatorsCommand_hasDescription() {
        var indicatorsCmd = cmd.getSubcommands().get("indicators");
        assertNotNull(indicatorsCmd);
        String[] desc = indicatorsCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("indicator"), "description should mention indicator");
    }
}
