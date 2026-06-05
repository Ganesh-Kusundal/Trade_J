package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.icici.mapper.BreezeDomainMapper;
import com.tradej.broker.icici.mapper.IciciExchangeSegmentMapper;
import com.tradej.broker.icici.rest.BreezeOrderRestClient;

import java.util.List;

/**
 * Resolves Breeze {@code exchange_code} for an order id by scanning supported exchanges.
 */
final class IciciOrderExchangeResolver {

    private final BreezeOrderRestClient restClient;
    private final BreezeDomainMapper mapper;

    IciciOrderExchangeResolver(BreezeOrderRestClient restClient, BreezeDomainMapper mapper) {
        this.restClient = restClient;
        this.mapper = mapper;
    }

    String resolveExchangeCode(String orderId) {
        for (String exchangeCode : IciciExchangeSegmentMapper.supportedOrderBookExchangeCodes()) {
            ObjectNode payload = mapper.toOrderDetailPayload(orderId, exchangeCode);
            JsonNode response = restClient.getOrderDetail(payload);
            if (response != null && !response.isMissingNode() && !response.isNull()) {
                String resolvedOrderId = response.path("order_id").asText("");
                if (!resolvedOrderId.isBlank() || response.path("Success").isArray()) {
                    return exchangeCode;
                }
            }
        }
        throw new IllegalArgumentException("Unable to resolve ICICI exchange for orderId=" + orderId);
    }

    List<JsonNode> fetchOrderListsAcrossExchanges() {
        return IciciExchangeSegmentMapper.supportedOrderBookExchangeCodes().stream()
                .map(exchangeCode -> {
                    ObjectNode payload = mapper.emptyPayload();
                    payload.put("exchange_code", exchangeCode);
                    return restClient.getOrderList(payload);
                })
                .filter(node -> node != null && node.isArray() && !node.isEmpty())
                .toList();
    }

    List<JsonNode> fetchTradeBooksAcrossExchanges() {
        return IciciExchangeSegmentMapper.supportedOrderBookExchangeCodes().stream()
                .map(exchangeCode -> {
                    ObjectNode payload = mapper.emptyPayload();
                    payload.put("exchange_code", exchangeCode);
                    return restClient.getTrades(payload);
                })
                .filter(node -> node != null && node.isArray() && !node.isEmpty())
                .toList();
    }
}
