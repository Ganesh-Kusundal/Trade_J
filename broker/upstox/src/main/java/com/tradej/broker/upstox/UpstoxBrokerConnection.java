package com.tradej.broker.upstox;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.NewsCapable;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentLoader;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Objects;

/**
 * Upstox broker connection facade — capabilities resolved via {@link #getCapability(Class)}.
 */
public final class UpstoxBrokerConnection implements IBrokerConnection {

    private final MarketDataProvider marketDataProvider;
    private final OrderCommand orderCommand;
    private final OrderQuery orderQuery;
    private final PortfolioProvider portfolioProvider;
    private final MarginProvider marginProvider;
    private final InstrumentResolver instrumentResolver;
    private final WebSocketMultiplexer webSocketMultiplexer;
    private final FuturesProvider futuresProvider;
    private final OptionsProvider optionsProvider;
    private final NewsProvider newsProvider;
    private final UpstoxInstrumentLoader instrumentLoader;
    private final UpstoxInstrumentResolver upstoxInstrumentResolver;

    public UpstoxBrokerConnection(
            MarketDataProvider marketDataProvider,
            OrderCommand orderCommand,
            OrderQuery orderQuery,
            PortfolioProvider portfolioProvider,
            MarginProvider marginProvider,
            UpstoxInstrumentResolver instrumentResolver,
            WebSocketMultiplexer webSocketMultiplexer,
            FuturesProvider futuresProvider,
            OptionsProvider optionsProvider,
            NewsProvider newsProvider,
            UpstoxInstrumentLoader instrumentLoader
    ) {
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider);
        this.orderCommand = Objects.requireNonNull(orderCommand);
        this.orderQuery = Objects.requireNonNull(orderQuery);
        this.portfolioProvider = Objects.requireNonNull(portfolioProvider);
        this.marginProvider = Objects.requireNonNull(marginProvider);
        this.instrumentResolver = Objects.requireNonNull(instrumentResolver);
        this.webSocketMultiplexer = Objects.requireNonNull(webSocketMultiplexer);
        this.futuresProvider = futuresProvider;
        this.optionsProvider = optionsProvider;
        this.newsProvider = Objects.requireNonNull(newsProvider);
        this.instrumentLoader = Objects.requireNonNull(instrumentLoader);
        this.upstoxInstrumentResolver = Objects.requireNonNull(instrumentResolver);
    }

    @Override
    public void connect() {
        webSocketMultiplexer.connect();
    }

    @Override
    public void disconnect() {
        webSocketMultiplexer.disconnect();
    }

    @Override
    public void loadInstrumentCatalog(Path catalogPath) {
        try {
            if (Files.isRegularFile(catalogPath)) {
                instrumentLoader.loadFromPath(catalogPath, upstoxInstrumentResolver);
                return;
            }
            Path cacheDir = catalogPath;
            if (!Files.exists(cacheDir)) {
                Files.createDirectories(cacheDir);
            }
            Path cached = cacheDir.resolve("complete.json.gz");
            boolean hasStaleCache = Files.exists(cached);
            try {
                instrumentLoader.downloadAndLoad(cacheDir, upstoxInstrumentResolver);
            } catch (IOException downloadEx) {
                if (hasStaleCache) {
                    instrumentLoader.loadFromPath(cached, upstoxInstrumentResolver);
                    return;
                }
                throw downloadEx;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load Upstox instrument catalog from " + catalogPath, ex);
        }
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        if (capabilityClass.isInstance(marketDataProvider)) {
            return Optional.of(capabilityClass.cast(marketDataProvider));
        }
        if (capabilityClass.isInstance(orderCommand)) {
            return Optional.of(capabilityClass.cast(orderCommand));
        }
        if (capabilityClass.isInstance(orderQuery)) {
            return Optional.of(capabilityClass.cast(orderQuery));
        }
        if (capabilityClass.isInstance(portfolioProvider)) {
            return Optional.of(capabilityClass.cast(portfolioProvider));
        }
        if (capabilityClass.isInstance(marginProvider)) {
            return Optional.of(capabilityClass.cast(marginProvider));
        }
        if (futuresProvider != null && capabilityClass.isInstance(futuresProvider)) {
            return Optional.of(capabilityClass.cast(futuresProvider));
        }
        if (optionsProvider != null && capabilityClass.isInstance(optionsProvider)) {
            return Optional.of(capabilityClass.cast(optionsProvider));
        }
        if (capabilityClass.isInstance(instrumentResolver)) {
            return Optional.of(capabilityClass.cast(instrumentResolver));
        }
        if (capabilityClass.isInstance(webSocketMultiplexer)) {
            return Optional.of(capabilityClass.cast(webSocketMultiplexer));
        }
        if (capabilityClass.isInstance(newsProvider)) {
            return Optional.of(capabilityClass.cast(newsProvider));
        }
        if (NewsCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(new NewsCapable() {}));
        }
        return Optional.empty();
    }
}
