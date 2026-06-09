package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.composition.FullComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.cli.config.CliConfig;

import java.nio.file.Files;
import java.nio.file.Path;

public final class IciciBrokerSession implements BrokerSession {
    private static final String CATALOG_CACHE_DIR = "runtime/cli-instruments-icici";

    private final CliConfig.Profile profile;
    private final BrokerProfile.IciciConfig iciciConfig;
    private FullComposition composition;
    private boolean catalogLoaded;
    private Path lastCatalogPath;

    public IciciBrokerSession(CliConfig.Profile profile) {
        this.profile = profile;
        this.iciciConfig = CliConfig.iciciConfig();
    }

    @Override
    public CliConfig.BrokerType brokerType() {
        return CliConfig.BrokerType.ICICI;
    }

    @Override
    public CliConfig.Profile profile() {
        return profile;
    }

    @Override
    public FullComposition fullComposition() {
        if (composition == null) {
            BrokerProfile brokerProfile = new BrokerProfile(
                    BrokerProfile.BrokerType.ICICI, null, null, iciciConfig);
            composition = FullComposition.brokerOnly(brokerProfile);
        }
        return composition;
    }

    @Override
    public Path lastCatalogPath() {
        return lastCatalogPath;
    }

    @Override
    public int catalogSize() {
        return connection().instruments().catalogSize();
    }

    @Override
    public void ensureCatalogLoaded() {
        if (!catalogLoaded) {
            refreshInstrumentCatalog(false);
        }
    }

    @Override
    public Path refreshInstrumentCatalog(boolean forceRefresh) {
        try {
            connection().loadInstrumentCatalog(null);
            catalogLoaded = true;
            lastCatalogPath = null;
            return null;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh ICICI instrument catalog: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        if (composition != null) {
            composition.brokerConnection().disconnect();
            composition = null;
        }
        catalogLoaded = false;
    }
}
