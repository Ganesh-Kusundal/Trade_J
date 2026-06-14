package com.tradej.cli.standalone;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.dhan.config.DhanConfigPaths;
import com.tradej.broker.upstox.config.UpstoxConnectionSettings;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.cli.config.CliConfig;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UpstoxBrokerSession implements BrokerSession {
    private static final String CATALOG_CACHE_DIR = "runtime/cli-instruments-upstox";

    private final CliConfig.Profile profile;
    private final UpstoxConnectionSettings settings;
    private BrokerComposition composition;
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
    public BrokerComposition fullComposition() {
        if (composition == null) {
            BrokerProfile.UpstoxConfig upstoxConfig = new BrokerProfile.UpstoxConfig(
                    settings.clientId(),
                    settings.clientSecret(),
                    settings.redirectUri(),
                    settings.accessToken(),
                    settings.refreshToken(),
                    settings.analyticsToken(),
                    settings.extendedToken(),
                    settings.analyticsOnly(),
                    settings.isSandbox(),
                    settings.redirectServerPort(),
                    settings.refreshBufferMs(),
                    settings.tokenExpiryBufferMs()
            );
            BrokerProfile brokerProfile = new BrokerProfile(
                    BrokerProfile.BrokerType.UPSTOX, null, upstoxConfig, null);
            composition = BrokerComposition.create(brokerProfile);
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
        Path cache = DhanConfigPaths.resolve(CATALOG_CACHE_DIR);
        try {
            Files.createDirectories(cache);
            if (forceRefresh && Files.exists(cache)) {
                try (var stream = Files.list(cache)) {
                    stream.forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
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
        if (composition != null) {
            composition.brokerConnection().disconnect();
            composition = null;
        }
        catalogLoaded = false;
    }
}
