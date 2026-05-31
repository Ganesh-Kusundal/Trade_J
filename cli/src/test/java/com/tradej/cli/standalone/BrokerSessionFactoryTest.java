package com.tradej.cli.standalone;

import com.tradej.cli.config.CliConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("unit")
class BrokerSessionFactoryTest {

    @Test
    void createsDhanSession() {
        BrokerSession session = BrokerSessionFactory.create(CliConfig.BrokerType.DHAN, CliConfig.Profile.LIVE);
        assertInstanceOf(DhanBrokerSession.class, session);
        assertEquals(CliConfig.BrokerType.DHAN, session.brokerType());
    }

    @Test
    void createsUpstoxSessionType() {
        BrokerSession session = BrokerSessionFactory.create(CliConfig.BrokerType.UPSTOX, CliConfig.Profile.LIVE);
        assertInstanceOf(UpstoxBrokerSession.class, session);
        assertEquals(CliConfig.BrokerType.UPSTOX, session.brokerType());
    }
}
