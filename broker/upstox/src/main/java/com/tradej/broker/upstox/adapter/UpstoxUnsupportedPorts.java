package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.BracketOrderProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.GttOrderProvider;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.broker.api.port.SliceOrderCommand;

public final class UpstoxUnsupportedPorts {
    public static final OrderCommand ORDERS = new UpstoxUnsupportedOrderCommand();
    public static final OrderQuery ORDER_QUERY = new UpstoxUnsupportedOrderQuery();
    public static final PortfolioProvider PORTFOLIO = new UpstoxUnsupportedPortfolioProvider();
    public static final MarginProvider MARGIN = new UpstoxUnsupportedMarginProvider();
    public static final SliceOrderCommand SLICE_ORDERS = new UpstoxUnsupportedSliceOrders();
    public static final BracketOrderProvider BRACKET_ORDERS = new UpstoxUnsupportedBracketOrders();
    public static final GttOrderProvider GTT_ORDERS = new UpstoxUnsupportedGttOrders();
    public static final SessionRiskProvider SESSION_RISK = new UpstoxUnsupportedSessionRisk();
    public static final ConditionalAlertProvider ALERTS = new UpstoxUnsupportedAlerts();

    private UpstoxUnsupportedPorts() {
    }
}
