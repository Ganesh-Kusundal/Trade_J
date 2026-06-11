package com.tradej.broker.icici;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.CoverOrderProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.MarketStatusProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;

import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.adapter.IciciBracketOrderAdapter;
import com.tradej.broker.icici.adapter.IciciCoverOrderAdapter;
import com.tradej.broker.icici.adapter.IciciGttOrderAdapter;
import com.tradej.broker.icici.adapter.IciciMarketStatusProvider;
import com.tradej.broker.icici.adapter.IciciSliceOrderAdapter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

public final class IciciBrokerConnection implements IBrokerConnection {


    private final MarketDataProvider marketDataProvider;
    private final FuturesProvider futuresProvider;
    private final OptionsProvider optionsProvider;
    private final OrderCommand orderCommand;
    private final OrderQuery orderQuery;
    private final PortfolioProvider portfolioProvider;
    private final MarginProvider marginProvider;
    private final BreezeInstrumentResolver instrumentResolver;
    private final WebSocketMultiplexer webSocketMultiplexer;
    private final MarketStatusProvider marketStatusProvider;
    private final BracketOrderProvider bracketOrderProvider;
    private final GttOrderProvider gttOrderProvider;
    private final SliceOrderCommand sliceOrderCommand;
    private final CoverOrderProvider coverOrderProvider;
    private final Map<Class<?>, Object> capabilityMap;


    public IciciBrokerConnection(
            MarketDataProvider marketDataProvider,
            FuturesProvider futuresProvider,
            OptionsProvider optionsProvider,
            OrderCommand orderCommand,
            OrderQuery orderQuery,
            PortfolioProvider portfolioProvider,
            MarginProvider marginProvider,
            BreezeInstrumentResolver instrumentResolver,
            WebSocketMultiplexer webSocketMultiplexer
    ) {
        this.marketDataProvider = Objects.requireNonNull(marketDataProvider);
        this.futuresProvider = Objects.requireNonNull(futuresProvider);
        this.optionsProvider = Objects.requireNonNull(optionsProvider);
        this.orderCommand = Objects.requireNonNull(orderCommand);
        this.orderQuery = Objects.requireNonNull(orderQuery);
        this.portfolioProvider = Objects.requireNonNull(portfolioProvider);
        this.marginProvider = Objects.requireNonNull(marginProvider);
        this.instrumentResolver = Objects.requireNonNull(instrumentResolver);
        this.webSocketMultiplexer = Objects.requireNonNull(webSocketMultiplexer);
        this.marketStatusProvider = new IciciMarketStatusProvider();
        this.bracketOrderProvider = new IciciBracketOrderAdapter();
        this.gttOrderProvider = new IciciGttOrderAdapter();
        this.sliceOrderCommand = new IciciSliceOrderAdapter();
        this.coverOrderProvider = new IciciCoverOrderAdapter();
        this.capabilityMap = buildCapabilityMap();
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
    public BracketOrderProvider bracketOrders() {
        return bracketOrderProvider;
    }

    @Override
    public GttOrderProvider gttOrders() {
        return gttOrderProvider;
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
    public SessionRiskProvider sessionRisk() {
        throw new UnsupportedOperationException("Session risk not supported by ICICI adapter");
    }

    @Override
    public ConditionalAlertProvider alerts() {
        throw new UnsupportedOperationException("Alerts not supported by ICICI adapter");
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
    public void connect() {
        webSocketMultiplexer.connect();
    }

    @Override
    public void disconnect() {
        webSocketMultiplexer.disconnect();
    }

    @Override
    public void loadInstrumentCatalog(Path catalogPath) {
        if (catalogPath == null) {
            instrumentResolver.loadFromRemote();
            return;
        }
        instrumentResolver.loadCatalog(catalogPath);
    }

    @Override
    public com.tradej.broker.api.spi.BrokerSource source() {
        return com.tradej.broker.api.spi.BrokerSource.ICICI;
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        if (capabilityClass.isInstance(this)) {
            return Optional.of(capabilityClass.cast(this));
        }
        Object impl = capabilityMap.get(capabilityClass);
        if (impl != null && capabilityClass.isInstance(impl)) {
            return Optional.of(capabilityClass.cast(impl));
        }
        // Fallback: scan all registered implementations (handles concrete-type lookups
        // and multi-interface implementations where the same instance satisfies
        // multiple capability interfaces)
        for (Object value : capabilityMap.values()) {
            if (capabilityClass.isInstance(value)) {
                return Optional.of(capabilityClass.cast(value));
            }
        }
        return Optional.empty();
    }

    private Map<Class<?>, Object> buildCapabilityMap() {
        Map<Class<?>, Object> map = new HashMap<>();
        putIfNotNull(map, MarketDataProvider.class, marketDataProvider);
        putIfNotNull(map, FuturesProvider.class, futuresProvider);
        putIfNotNull(map, OptionsProvider.class, optionsProvider);
        putIfNotNull(map, OrderCommand.class, orderCommand);
        putIfNotNull(map, OrderQuery.class, orderQuery);
        putIfNotNull(map, PortfolioProvider.class, portfolioProvider);
        putIfNotNull(map, MarginProvider.class, marginProvider);
        putIfNotNull(map, InstrumentResolver.class, instrumentResolver);
        putIfNotNull(map, WebSocketMultiplexer.class, webSocketMultiplexer);
        putIfNotNull(map, MarketStatusProvider.class, marketStatusProvider);
        putIfNotNull(map, BracketOrderProvider.class, bracketOrderProvider);
        putIfNotNull(map, GttOrderProvider.class, gttOrderProvider);
        putIfNotNull(map, SliceOrderCommand.class, sliceOrderCommand);
        putIfNotNull(map, CoverOrderProvider.class, coverOrderProvider);
        return map;
    }

    private static void putIfNotNull(Map<Class<?>, Object> map, Class<?> type, Object impl) {
        if (impl != null) {
            map.put(type, impl);
        }
    }
}
