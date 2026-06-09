package com.tradej.cli.standalone;

import com.tradej.cli.config.CliConfig;

public final class BrokerSessionFactory {
    private BrokerSessionFactory() {
    }

    public static BrokerSession create(CliConfig.BrokerType brokerType, CliConfig.Profile profile) {
        return switch (brokerType) {
            case DHAN -> new DhanBrokerSession(profile);
            case UPSTOX -> new UpstoxBrokerSession(profile);
            case ICICI -> new IciciBrokerSession(profile);
        };
    }
}
