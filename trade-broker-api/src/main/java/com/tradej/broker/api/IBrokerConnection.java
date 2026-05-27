package com.tradej.broker.api;

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

import java.nio.file.Path;

public interface IBrokerConnection extends AutoCloseable {
    MarketDataProvider marketData();

    FuturesProvider futures();

    OptionsProvider options();

    OrderCommand orders();

    OrderQuery orderQuery();

    SliceOrderCommand sliceOrders();

    BracketOrderProvider bracketOrders();

    GttOrderProvider gttOrders();

    PortfolioProvider portfolio();

    MarginProvider margin();

    SessionRiskProvider sessionRisk();

    ConditionalAlertProvider alerts();

    InstrumentResolver instruments();

    WebSocketMultiplexer websocket();

    void connect();

    void disconnect();

    void loadInstrumentCatalog(Path catalogPath);

    @Override
    default void close() {
        disconnect();
    }
}
