package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.SliceOrderCommand;
import com.tradej.broker.upstox.instrument.UpstoxInstrumentResolver;
import com.tradej.broker.upstox.mapper.UpstoxDomainMapper;
import com.tradej.broker.upstox.rest.UpstoxOrderRestClient;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.SliceOrderRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter that implements {@link SliceOrderCommand} for the Upstox broker.
 * <p>
 * Splits large orders into configurable chunk sizes and places them via the
 * Upstox multi-order API (POST /v2/order/multi) for efficiency. Falls back to
 * individual order placement if the multi-order endpoint is unavailable.
 */
public final class UpstoxSliceOrderAdapter implements SliceOrderCommand {

    /** Default maximum quantity per individual order chunk (NSE standard). */
    private static final long DEFAULT_CHUNK_SIZE = 10_000L;

    private final UpstoxOrderRestClient restClient;
    private final UpstoxDomainMapper mapper;
    private final UpstoxInstrumentResolver instrumentResolver;
    private final long chunkSize;

    public UpstoxSliceOrderAdapter(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver
    ) {
        this(restClient, mapper, instrumentResolver, DEFAULT_CHUNK_SIZE);
    }

    public UpstoxSliceOrderAdapter(
            UpstoxOrderRestClient restClient,
            UpstoxDomainMapper mapper,
            UpstoxInstrumentResolver instrumentResolver,
            long chunkSize
    ) {
        this.restClient = restClient;
        this.mapper = mapper;
        this.instrumentResolver = instrumentResolver;
        this.chunkSize = chunkSize > 0 ? chunkSize : DEFAULT_CHUNK_SIZE;
    }

    @Override
    public List<Order> placeSliceOrder(SliceOrderRequest request) {
        // 1. Resolve instrument key
        String instrumentKey = instrumentResolver.requireInstrumentKey(
                new InstrumentKey(request.symbol(), request.exchangeSegment()));

        // 2. Split quantity into chunks
        List<Map<String, Object>> payloads = buildChunkPayloads(request, instrumentKey);

        // 3. Place via multi-order API
        try {
            var response = restClient.placeMultiOrder(payloads);
            var orders = mapper.toOrderList(response);
            if (!orders.isEmpty()) {
                return orders;
            }
        } catch (Exception ignored) {
            // Fall through to individual placement
        }

        // 4. Fallback: place each chunk individually
        return placeIndividually(request, instrumentKey, payloads.size());
    }

    /**
     * Builds a list of order payloads for each chunk.
     */
    private List<Map<String, Object>> buildChunkPayloads(SliceOrderRequest request, String instrumentKey) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        long remaining = request.quantity();

        while (remaining > 0) {
            long chunkQty = Math.min(remaining, chunkSize);
            OrderRequest chunkRequest = new OrderRequest(
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    chunkQty,
                    request.orderType(),
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    request.productType(),
                    request.validity(),
                    request.correlationId() != null ? request.correlationId() + "-slice-" + payloads.size() : null
            );
            payloads.add(mapper.toPlaceOrderPayload(chunkRequest, instrumentKey));
            remaining -= chunkQty;
        }

        return payloads;
    }

    /**
     * Fallback: places each chunk individually via the standard order endpoint.
     */
    private List<Order> placeIndividually(SliceOrderRequest request, String instrumentKey, int numChunks) {
        List<Order> orders = new ArrayList<>();
        long remaining = request.quantity();

        for (int i = 0; i < numChunks; i++) {
            long chunkQty = Math.min(remaining, chunkSize);
            OrderRequest chunkRequest = new OrderRequest(
                    request.symbol(),
                    request.exchangeSegment(),
                    request.side(),
                    chunkQty,
                    request.orderType(),
                    request.pricePaisa(),
                    request.triggerPricePaisa(),
                    request.productType(),
                    request.validity(),
                    request.correlationId() != null ? request.correlationId() + "-slice-" + i : null
            );
            Map<String, Object> payload = mapper.toPlaceOrderPayload(chunkRequest, instrumentKey);
            Instrument instrument = instrumentResolver.resolve(
                    new InstrumentKey(chunkRequest.symbol(), chunkRequest.exchangeSegment()));
            var response = restClient.placeOrder(payload);
            orders.add(mapper.toOrder(response, chunkRequest, instrument));
            remaining -= chunkQty;
        }

        return orders;
    }
}
