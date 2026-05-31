package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.cli.config.CliConfig;

import java.nio.file.Path;

public interface BrokerSession extends AutoCloseable {
    CliConfig.BrokerType brokerType();

    CliConfig.Profile profile();

    IBrokerConnection connection();

    Path lastCatalogPath();

    int catalogSize();

    void ensureCatalogLoaded();

    Path refreshInstrumentCatalog(boolean forceRefresh);

    @Override
    void close();
}
