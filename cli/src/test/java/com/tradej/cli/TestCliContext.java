package com.tradej.cli;

import com.tradej.cli.config.CliConfig;

public class TestCliContext {

    public static CliContext create() {
        return new CliContext("http://localhost:8080", CliConfig.Profile.SANDBOX, CliConfig.BrokerType.DHAN, false, true);
    }

    public static CliContext createJson() {
        return new CliContext("http://localhost:8080", CliConfig.Profile.SANDBOX, CliConfig.BrokerType.DHAN, true, true);
    }
}
