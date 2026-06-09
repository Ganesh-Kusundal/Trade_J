package com.tradej.broker.icici;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.AdvancedOrderCapable;
import com.tradej.broker.api.capability.AlertCapable;
import com.tradej.broker.api.capability.FuturesCapable;
import com.tradej.broker.api.capability.MarginCapable;
import com.tradej.broker.api.capability.OptionsCapable;
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
import com.tradej.broker.core.capability.CapabilityMap;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.adapter.IciciBracketOrderAdapter;
import com.tradej.broker.icici.adapter.IciciCoverOrderAdapter;
import com.tradej.broker.icici.adapter.IciciGttOrderAdapter;
import com.tradej.broker.icici.adapter.IciciMarketStatusProvider;
import com.tradej.broker.icici.adapter.IciciSliceOrderAdapter;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Objects;

public final class IciciBrokerConnection implements IBrokerConnection {
    private static final OptionsCapable OPTIONS_CAPABLE = new OptionsCapable() { };
    private static final FuturesCapable FUTURES_CAPABLE = new FuturesCapable() { };
    private static final MarginCapable MARGIN_CAPABLE = new MarginCapable() { };
    private static final AlertCapable ALERT_CAPABLE = new AlertCapable() { };
    private static final AdvancedOrderCapable ADVANCED_ORDER_CAPABLE = new AdvancedOrderCapable() { };

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
    private final CapabilityMap capabilityMap;

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
        this.capabilityMap = CapabilityMap.builder()
                .register(MarketDataProvider.class, marketDataProvider)
                .register(FuturesProvider.class, futuresProvider)
                .register(OptionsProvider.class, optionsProvider)
                .register(OrderCommand.class, orderCommand)
                .register(OrderQuery.class, orderQuery)
                .register(PortfolioProvider.class, portfolioProvider)
                .register(MarginProvider.class, marginProvider)
                .register(InstrumentResolver.class, instrumentResolver)
                .register(WebSocketMultiplexer.class, webSocketMultiplexer)
                .register(MarketStatusProvider.class, marketStatusProvider)
                .register(BracketOrderProvider.class, bracketOrderProvider)
                .register(GttOrderProvider.class, gttOrderProvider)
                .register(SliceOrderCommand.class, sliceOrderCommand)
                .register(CoverOrderProvider.class, coverOrderProvider)
                .register(OptionsCapable.class, OPTIONS_CAPABLE)
                .register(FuturesCapable.class, FUTURES_CAPABLE)
                .register(MarginCapable.class, MARGIN_CAPABLE)
                .register(AlertCapable.class, ALERT_CAPABLE)
                .register(AdvancedOrderCapable.class, ADVANCED_ORDER_CAPABLE)
                .build();
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
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        return capabilityMap.get(capabilityClass);
    }
}
