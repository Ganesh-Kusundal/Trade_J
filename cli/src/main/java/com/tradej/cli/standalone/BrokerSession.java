package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.cli.config.CliConfig;
import com.tradej.brokergateway.wiring.BrokerComposition;

import java.nio.file.Path;

public interface BrokerSession extends AutoCloseable {
    CliConfig.BrokerType brokerType();

    CliConfig.Profile profile();

    BrokerComposition fullComposition();

    default IBrokerConnection connection() {
        return fullComposition().brokerConnection();
    }

    Path lastCatalogPath();

    int catalogSize();

    void ensureCatalogLoaded();

    Path refreshInstrumentCatalog(boolean forceRefresh);

    @Override
    void close();
}
