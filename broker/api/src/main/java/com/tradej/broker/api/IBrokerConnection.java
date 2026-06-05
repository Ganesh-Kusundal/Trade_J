package com.tradej.broker.api;

import java.util.Optional;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.api.port.NewsProvider;

import java.nio.file.Path;

/**
 * Broker connection facade. Optional capabilities are resolved via {@link #getCapability(Class)}
 * with default port accessors delegating to capability lookup.
 */
public interface IBrokerConnection extends AutoCloseable {

    <T> Optional<T> getCapability(Class<T> capabilityClass);

    default MarketDataProvider marketData() {
        return requireCapability(MarketDataProvider.class);
    }

    default FuturesProvider futures() {
        return requireCapability(FuturesProvider.class);
    }

    default OptionsProvider options() {
        return requireCapability(OptionsProvider.class);
    }

    default OrderCommand orders() {
        return requireCapability(OrderCommand.class);
    }

    default OrderQuery orderQuery() {
        return requireCapability(OrderQuery.class);
    }

    default SliceOrderCommand sliceOrders() {
        return requireCapability(SliceOrderCommand.class);
    }

    default BracketOrderProvider bracketOrders() {
        return requireCapability(BracketOrderProvider.class);
    }

    default GttOrderProvider gttOrders() {
        return requireCapability(GttOrderProvider.class);
    }

    default PortfolioProvider portfolio() {
        return requireCapability(PortfolioProvider.class);
    }

    default MarginProvider margin() {
        return requireCapability(MarginProvider.class);
    }

    default SessionRiskProvider sessionRisk() {
        return requireCapability(SessionRiskProvider.class);
    }

    default ConditionalAlertProvider alerts() {
        return requireCapability(ConditionalAlertProvider.class);
    }

    default InstrumentResolver instruments() {
        return requireCapability(InstrumentResolver.class);
    }

    default WebSocketMultiplexer websocket() {
        return requireCapability(WebSocketMultiplexer.class);
    }

    default NewsProvider news() {
        return requireCapability(NewsProvider.class);
    }

    default <T> T requireCapability(Class<T> capabilityClass) {
        return getCapability(capabilityClass).orElseThrow(() ->
                new UnsupportedOperationException(
                        "Broker does not support capability: " + capabilityClass.getSimpleName()));
    }

    void connect();

    void disconnect();

    void loadInstrumentCatalog(Path catalogPath);

    @Override
    default void close() {
        disconnect();
    }
}
