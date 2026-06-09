package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.OrderStatus;

import java.util.List;
import java.util.OptionalLong;

public final class UpstoxOrderQueryAdapter implements OrderQuery {

    private final UpstoxOrderRestClient restClient;
    private final UpstoxDomainMapper mapper;
    private final UpstoxInstrumentResolver instrumentResolver;

    public UpstoxOrderQueryAdapter(UpstoxOrderRestClient restClient, UpstoxDomainMapper mapper,
                                   UpstoxInstrumentResolver instrumentResolver) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
    }

    @Override
    public Order getOrder(String orderId) {
        var response = restClient.getOrderDetails(orderId);
        return resolveOrder(mapper.toOrder(response, null));
    }

    @Override
    public List<Order> getOrderBook() {
        var response = restClient.getOrderBook();
        return mapper.toOrderList(response).stream().map(this::resolveOrder).toList();
    }

    @Override
    public List<Trade> getTradeBook() {
        var response = restClient.getTrades();
        return mapper.toTradeList(response).stream().map(this::resolveTrade).toList();
    }

    @Override
    public OrderStatus getOrderStatus(String orderId) {
        Order order = getOrder(orderId);
        return order != null ? order.status() : OrderStatus.UNKNOWN;
    }

    @Override
    public OptionalLong getExecutedPricePaisa(String orderId) {
        return getTradeBook().stream()
                .filter(t -> t.orderId().equals(orderId))
                .mapToLong(t -> t.pricePaisa())
                .average()
                .stream()
                .mapToLong(Math::round)
                .findFirst();
    }

    @Override
    public OptionalLong getExchangeTimeMs(String orderId) {
        Order order = getOrder(orderId);
        if (order != null && order.exchangeTimeMs() > 0) {
            return OptionalLong.of(order.exchangeTimeMs());
        }
        return OptionalLong.empty();
    }

    /**
     * Re-resolve the order symbol from the instrument catalog to get the canonical symbol.
     * Mirrors DhanOrderQueryAdapter.resolveOrder().
     */
    private Order resolveOrder(Order order) {
        try {
            Instrument instrument = instrumentResolver.resolve(
                    new InstrumentKey(order.symbol(), order.exchangeSegment()));
            if (instrument != null) {
                return new Order(
                        order.orderId(), order.correlationId(),
                        instrument.canonicalSymbol(), instrument.exchangeSegment(),
                        order.side(), order.productType(), order.orderType(), order.status(),
                        order.quantity(), order.filledQuantity(),
                        order.pricePaisa(), order.triggerPricePaisa(),
                        order.exchangeTimeMs(), order.rejectionReason());
            }
        } catch (IllegalArgumentException ignored) {
            // Instrument not in catalog — keep wire-format symbol
        }
        return order;
    }

    /**
     * Re-resolve the trade symbol from the instrument catalog.
     * Mirrors DhanOrderQueryAdapter.resolveTrade().
     */
    private Trade resolveTrade(Trade trade) {
        try {
            Instrument instrument = instrumentResolver.resolve(
                    new InstrumentKey(trade.symbol(), trade.exchangeSegment()));
            if (instrument != null) {
                return new Trade(
                        trade.tradeId(), trade.orderId(),
                        instrument.canonicalSymbol(), instrument.exchangeSegment(),
                        trade.side(), trade.quantity(),
                        trade.pricePaisa(), trade.exchangeTimeMs());
            }
        } catch (IllegalArgumentException ignored) {
            // Instrument not in catalog — keep wire-format symbol
        }
        return trade;
    }
}
