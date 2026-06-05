package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.NewsCapable;
import com.tradej.broker.api.capability.OptionsCapable;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Aggregates multiple {@link IBrokerConnection} nodes behind a single connection facade.
 */
public final class LoadBalancedBrokerGateway implements IBrokerConnection, OptionsCapable {

    private final CopyOnWriteArrayList<IBrokerConnection> connections;
    private final AtomicInteger primaryIndex = new AtomicInteger();
    private final LoadBalancedMarketDataProvider marketData;
    private final FailoverOrderCommand orders;
    private final FailoverWebSocketMultiplexer websocket;

    public LoadBalancedBrokerGateway(List<IBrokerConnection> connections) {
        this(connections, null);
    }

    public LoadBalancedBrokerGateway(List<IBrokerConnection> connections, ReconnectListenerRegistry reconnectRegistry) {
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("At least one broker connection is required");
        }
        this.connections = new CopyOnWriteArrayList<>(connections);
        List<MarketDataProvider> marketDataProviders = connections.stream()
                .map(IBrokerConnection::marketData)
                .toList();
        this.marketData = new LoadBalancedMarketDataProvider(marketDataProviders);
        this.orders = new FailoverOrderCommand(connections, this::rotatePrimary);
        this.websocket = new FailoverWebSocketMultiplexer(connections, reconnectRegistry);
    }

    public void addConnection(IBrokerConnection connection) {
        connections.addIfAbsent(connection);
    }

    public void removeConnection(IBrokerConnection connection) {
        connections.remove(connection);
    }

    public void rotatePrimary() {
        primaryIndex.incrementAndGet();
    }

    public int connectionCount() {
        return connections.size();
    }

    private IBrokerConnection primary() {
        return connections.get(Math.floorMod(primaryIndex.get(), connections.size()));
    }

    @Override
    public MarketDataProvider marketData() {
        return marketData;
    }

    @Override
    public FuturesProvider futures() {
        return primary().futures();
    }

    @Override
    public OptionsProvider options() {
        for (IBrokerConnection connection : connections) {
            Optional<OptionsCapable> capability = connection.getCapability(OptionsCapable.class);
            if (capability.isPresent()) {
                return connection.options();
            }
        }
        return primary().options();
    }

    @Override
    public OrderCommand orders() {
        return orders;
    }

    @Override
    public OrderQuery orderQuery() {
        return primary().orderQuery();
    }

    @Override
    public SliceOrderCommand sliceOrders() {
        return primary().sliceOrders();
    }

    @Override
    public BracketOrderProvider bracketOrders() {
        return primary().bracketOrders();
    }

    @Override
    public GttOrderProvider gttOrders() {
        return primary().gttOrders();
    }

    @Override
    public PortfolioProvider portfolio() {
        return primary().portfolio();
    }

    @Override
    public MarginProvider margin() {
        return primary().margin();
    }

    @Override
    public SessionRiskProvider sessionRisk() {
        return primary().sessionRisk();
    }

    @Override
    public ConditionalAlertProvider alerts() {
        return primary().alerts();
    }

    @Override
    public NewsProvider news() {
        for (IBrokerConnection connection : connections) {
            Optional<NewsCapable> capability = connection.getCapability(NewsCapable.class);
            if (capability.isPresent()) {
                return connection.news();
            }
        }
        return primary().news(); // Will throw if none support it
    }

    @Override
    public InstrumentResolver instruments() {
        return primary().instruments();
    }

    @Override
    public WebSocketMultiplexer websocket() {
        return websocket;
    }

    @Override
    public void connect() {
        for (IBrokerConnection connection : connections) {
            connection.connect();
        }
    }

    @Override
    public void disconnect() {
        for (IBrokerConnection connection : connections) {
            connection.disconnect();
        }
    }

    @Override
    public void loadInstrumentCatalog(Path catalogPath) {
        for (IBrokerConnection connection : connections) {
            connection.loadInstrumentCatalog(catalogPath);
        }
    }

    @Override
    public <T> Optional<T> getCapability(Class<T> capabilityClass) {
        if (capabilityClass == null) {
            return Optional.empty();
        }
        if (OptionsCapable.class.equals(capabilityClass)) {
            return Optional.of(capabilityClass.cast(this));
        }
        for (IBrokerConnection connection : connections) {
            Optional<T> capability = connection.getCapability(capabilityClass);
            if (capability.isPresent()) {
                return capability;
            }
        }
        return Optional.empty();
    }

    public List<IBrokerConnection> connections() {
        return List.copyOf(connections);
    }
}
