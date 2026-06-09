package com.tradej.broker.upstox;

import com.tradej.broker.api.IBrokerConnection;

import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarketStatusProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;

import com.tradej.broker.upstox.adapter.UpstoxCoverOrderAdapter;
import com.tradej.broker.upstox.adapter.UpstoxDataServicesProvider;
import com.tradej.broker.upstox.adapter.UpstoxMarketStatusProvider;
import com.tradej.broker.upstox.adapter.UpstoxProfileProvider;
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
    private final ConditionalAlertProvider conditionalAlertProvider;
    private final SliceOrderCommand sliceOrderCommand;
    private final UpstoxDataServicesProvider dataServicesProvider;
    private final UpstoxProfileProvider profileProvider;
    private final UpstoxInstrumentLoader instrumentLoader;
    private final UpstoxInstrumentResolver upstoxInstrumentResolver;
    private final MarketStatusProvider marketStatusProvider;
    private final CoverOrderProvider coverOrderProvider;
    


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
            ConditionalAlertProvider conditionalAlertProvider,
            SliceOrderCommand sliceOrderCommand,
            UpstoxDataServicesProvider dataServicesProvider,
            UpstoxProfileProvider profileProvider,
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
        this.conditionalAlertProvider = Objects.requireNonNull(conditionalAlertProvider);
        this.sliceOrderCommand = sliceOrderCommand;
        this.dataServicesProvider = dataServicesProvider;
        this.profileProvider = profileProvider;
        this.instrumentLoader = Objects.requireNonNull(instrumentLoader);
        this.upstoxInstrumentResolver = Objects.requireNonNull(instrumentResolver);
        this.marketStatusProvider = new UpstoxMarketStatusProvider();
        this.coverOrderProvider = new UpstoxCoverOrderAdapter();

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
    public MarketDataProvider marketData() {
        return marketDataProvider;
    }

    @Override
    public FuturesProvider futures() {
        return futuresProvider;
    }

    @Override
    public OptionsProvider options() {
        return optionsProvider;
    }

    @Override
    public OrderCommand orders() {
        return orderCommand;
    }

    @Override
    public OrderQuery orderQuery() {
        return orderQuery;
    }

    @Override
    public SliceOrderCommand sliceOrders() {
        return sliceOrderCommand;
    }

    @Override
    public PortfolioProvider portfolio() {
        return portfolioProvider;
    }

    @Override
    public MarginProvider margin() {
        return marginProvider;
    }

    @Override
    public ConditionalAlertProvider alerts() {
        return conditionalAlertProvider;
    }

    @Override
    public GttOrderProvider gttOrders() {
        if (conditionalAlertProvider instanceof GttOrderProvider gtt) {
            return gtt;
        }
        throw new UnsupportedOperationException("GTT orders not supported by Upstox adapter");
    }

    @Override
    public InstrumentResolver instruments() {
        return instrumentResolver;
    }

    @Override
    public WebSocketMultiplexer websocket() {
        return webSocketMultiplexer;
    }

    @Override
    public NewsProvider news() {
        return newsProvider;
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        if (capabilityClass.isInstance(this)) {
            return Optional.of(capabilityClass.cast(this));
        }
        if (marketDataProvider != null && capabilityClass.isInstance(marketDataProvider)) return Optional.of(capabilityClass.cast(marketDataProvider));
        if (orderCommand != null && capabilityClass.isInstance(orderCommand)) return Optional.of(capabilityClass.cast(orderCommand));
        if (orderQuery != null && capabilityClass.isInstance(orderQuery)) return Optional.of(capabilityClass.cast(orderQuery));
        if (portfolioProvider != null && capabilityClass.isInstance(portfolioProvider)) return Optional.of(capabilityClass.cast(portfolioProvider));
        if (marginProvider != null && capabilityClass.isInstance(marginProvider)) return Optional.of(capabilityClass.cast(marginProvider));
        if (instrumentResolver != null && capabilityClass.isInstance(instrumentResolver)) return Optional.of(capabilityClass.cast(instrumentResolver));
        if (webSocketMultiplexer != null && capabilityClass.isInstance(webSocketMultiplexer)) return Optional.of(capabilityClass.cast(webSocketMultiplexer));
        if (marketStatusProvider != null && capabilityClass.isInstance(marketStatusProvider)) return Optional.of(capabilityClass.cast(marketStatusProvider));
        if (coverOrderProvider != null && capabilityClass.isInstance(coverOrderProvider)) return Optional.of(capabilityClass.cast(coverOrderProvider));
        if (newsProvider != null && capabilityClass.isInstance(newsProvider)) return Optional.of(capabilityClass.cast(newsProvider));
        
        if (futuresProvider != null && capabilityClass.isInstance(futuresProvider)) return Optional.of(capabilityClass.cast(futuresProvider));
        if (optionsProvider != null && capabilityClass.isInstance(optionsProvider)) return Optional.of(capabilityClass.cast(optionsProvider));
        if (sliceOrderCommand != null && capabilityClass.isInstance(sliceOrderCommand)) return Optional.of(capabilityClass.cast(sliceOrderCommand));
        if (dataServicesProvider != null && capabilityClass.isInstance(dataServicesProvider)) return Optional.of(capabilityClass.cast(dataServicesProvider));
        if (profileProvider != null && capabilityClass.isInstance(profileProvider)) return Optional.of(capabilityClass.cast(profileProvider));
        if (conditionalAlertProvider != null && capabilityClass.isInstance(conditionalAlertProvider)) return Optional.of(capabilityClass.cast(conditionalAlertProvider));
        
        return Optional.empty();
    }
}
