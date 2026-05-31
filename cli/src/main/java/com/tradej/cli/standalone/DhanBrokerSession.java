package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.cli.config.CliConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DhanBrokerSession implements BrokerSession {
    private static final String CATALOG_CACHE_DIR = "runtime/cli-instruments";

    private final CliConfig.Profile profile;
    private final DhanConnectionSettings settings;
    private IBrokerConnection connection;
    private boolean catalogLoaded;
    private Path lastCatalogPath;

    public DhanBrokerSession(CliConfig.Profile profile) {
        this.profile = profile;
        this.settings = CliConfig.dhanConnectionSettings(profile);
    }

    @Override
    public CliConfig.BrokerType brokerType() {
        return CliConfig.BrokerType.DHAN;
    }

    @Override
    public CliConfig.Profile profile() {
        return profile;
    }

    public DhanConnectionSettings settings() {
        return settings;
    }

    @Override
    public IBrokerConnection connection() {
        if (connection == null) {
            connection = DhanBrokerConnection.create(settings, new NoOpIdempotencyCache());
        }
        return connection;
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
            Path cache = DhanConfigPaths.resolve(CATALOG_CACHE_DIR);
            Files.createDirectories(cache);
            IBrokerConnection active = connection();
            if (active instanceof DhanBrokerConnection dhan) {
                lastCatalogPath = dhan.loadDailyInstrumentCatalog(cache, forceRefresh);
            } else {
                Path catalog = DhanConfigPaths.resolve("runtime/instruments.csv");
                active.loadInstrumentCatalog(catalog);
                lastCatalogPath = catalog;
            }
            catalogLoaded = true;
            if (catalogSize() == 0) {
                throw new IllegalStateException("Instrument catalog loaded zero instruments from " + lastCatalogPath);
            }
            return lastCatalogPath;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to refresh instrument catalog: " + ex.getMessage(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh instrument catalog: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
        catalogLoaded = false;
    }
}
