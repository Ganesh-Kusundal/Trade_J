package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.broker.icici.instrument.BreezeInstrumentDefinition;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.OrderType;

import java.util.ArrayList;
import java.util.List;

public final class IciciOrderCommandAdapter implements OrderCommand {
    private final BreezeOrderRestClient restClient;
    private final BreezeDomainMapper mapper;
    private final BreezeInstrumentResolver instrumentResolver;
    private final BreezeConnectionSettings settings;

    public IciciOrderCommandAdapter(
            BreezeOrderRestClient restClient,
            BreezeDomainMapper mapper,
            BreezeInstrumentResolver instrumentResolver,
            BreezeConnectionSettings settings
    ) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
        this.settings = settings;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        ensureOrdersEnabled();
        if (request.orderType() == OrderType.MARKET || request.orderType() == OrderType.STOP_LOSS_MARKET) {
            throw new UnsupportedOperationException("ICICI Breeze API does not permit market orders");
        }
        BreezeInstrumentDefinition definition = instrumentResolver.requireBreezeDefinition(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));
        ObjectNode payload = mapper.toPlaceOrderPayload(request, definition);
        JsonNode response = restClient.placeOrder(payload);
        return mapper.toOrder(response, request);
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        ensureOrdersEnabled();
        ObjectNode payload = mapper.toModifyOrderPayload(request, "NSE");
        JsonNode response = restClient.modifyOrder(payload);
        return mapper.toOrder(response, null);
    }

    @Override
    public boolean cancelOrder(String orderId) {
        ensureOrdersEnabled();
        ObjectNode payload = mapper.toCancelOrderPayload(orderId, "NSE");
        restClient.cancelOrder(payload);
        return true;
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        ensureOrdersEnabled();
        return List.of();
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        ensureOrdersEnabled();
        throw new UnsupportedOperationException("ICICI square-off batch not implemented");
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        throw new UnsupportedOperationException("ICICI kill switch not supported");
    }

    private void ensureOrdersEnabled() {
        if (!settings.ordersEnabled()) {
            throw new UnsupportedOperationException(
                    "ICICI order placement is disabled until static IP is registered (trade.icici.orders-enabled=true)");
        }
    }
}
