package com.tradej.cli.command;

import com.tradej.broker.api.spi.BrokerProvider;
import com.tradej.cli.TradeCli;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CliBrokersCommandTest {

    private final CommandLine cmd = new CommandLine(new TradeCli());

    @Test
    void brokersCommand_isRegistered() {
        assertNotNull(cmd.getSubcommands().get("brokers"),
                "brokers command should be registered");
    }

    @Test
    void brokersCommand_hasDescription() {
        var brokersCmd = cmd.getSubcommands().get("brokers");
        assertNotNull(brokersCmd);
        String[] desc = brokersCmd.getCommandSpec().usageMessage().description();
        assertTrue(desc.length > 0);
        assertTrue(desc[0].contains("broker"), "description should mention broker");
    }

    @Test
    void serviceLoader_discoversBrokerProviders() {
        List<BrokerProvider> providers = new ArrayList<>();
        ServiceLoader.load(BrokerProvider.class).forEach(providers::add);
        assertFalse(providers.isEmpty(), "ServiceLoader should discover at least one broker provider");
        assertTrue(providers.size() >= 3, "should discover Dhan, Upstox, ICICI, and Simulation providers");
    }

    @Test
    void brokerProviders_haveDisplayNames() {
        for (BrokerProvider provider : ServiceLoader.load(BrokerProvider.class)) {
            assertNotNull(provider.displayName(), provider.source() + " should have a display name");
            assertFalse(provider.displayName().isBlank());
            assertNotNull(provider.descriptor(), provider.source() + " should have a descriptor");
        }
    }
}
