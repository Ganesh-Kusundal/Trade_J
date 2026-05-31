package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.cli.config.CliConfig;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UpstoxBrokerSession implements BrokerSession {
    private static final String CATALOG_CACHE_DIR = "runtime/cli-instruments-upstox";

    private final CliConfig.Profile profile;
    private final UpstoxConnectionSettings settings;
    private IBrokerConnection connection;
    private boolean catalogLoaded;
    private Path lastCatalogPath;

    public UpstoxBrokerSession(CliConfig.Profile profile) {
        this.profile = profile;
        this.settings = CliConfig.upstoxConnectionSettings(profile);
    }

    @Override
    public CliConfig.BrokerType brokerType() {
        return CliConfig.BrokerType.UPSTOX;
    }

    @Override
    public CliConfig.Profile profile() {
        return profile;
    }

    public UpstoxConnectionSettings settings() {
        return settings;
    }

    @Override
    public IBrokerConnection connection() {
        if (connection == null) {
            connection = UpstoxCliConnectionFactory.create(settings);
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
        Path cache = DhanConfigPaths.resolve(CATALOG_CACHE_DIR);
        try {
            Files.createDirectories(cache);
            if (forceRefresh && Files.exists(cache)) {
                try (var stream = Files.list(cache)) {
                    stream.forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // best-effort cache wipe before re-download
                        }
                    });
                }
            }
            connection().loadInstrumentCatalog(cache);
            lastCatalogPath = cache;
            catalogLoaded = true;
            if (catalogSize() == 0) {
                throw new IllegalStateException("Instrument catalog loaded zero instruments from " + lastCatalogPath);
            }
            return lastCatalogPath;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to refresh Upstox instrument catalog: " + ex.getMessage(), ex);
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
