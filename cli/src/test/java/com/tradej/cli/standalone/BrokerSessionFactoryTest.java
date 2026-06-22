package com.tradej.cli.standalone;

import com.tradej.cli.config.CliConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class BrokerSessionFactoryTest {

    @Test
    void dhanSessionRequiresConfiguredCredentials() {
        assertThrows(IllegalStateException.class,
                () -> BrokerSessionFactory.create(CliConfig.BrokerType.DHAN, CliConfig.Profile.LIVE));
    }

    @Test
    void upstoxSessionRequiresConfiguredCredentials() {
        assertThrows(IllegalStateException.class,
                () -> BrokerSessionFactory.create(CliConfig.BrokerType.UPSTOX, CliConfig.Profile.LIVE));
    }
}
