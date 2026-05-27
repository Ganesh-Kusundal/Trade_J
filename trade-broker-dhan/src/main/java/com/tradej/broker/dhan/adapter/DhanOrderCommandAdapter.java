package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanSdkConverters;
import com.tradej.broker.dhan.mapper.DhanSdkMapper;
import com.tradej.broker.dhan.mapper.DhanSdkResponse;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanResilienceExecutor;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import io.github.sonicalgo.dhan.usecase.ModifyOrderParamsBuilder;
import io.github.sonicalgo.dhan.usecase.PlaceOrderParamsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DhanOrderCommandAdapter extends DhanBaseRestAdapter implements OrderCommand {
    private final IdempotencyCachePort idempotencyCache;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;

    public DhanOrderCommandAdapter(
            DhanClientHolder clientHolder,
            DhanInstrumentResolver instrumentResolver,
            DhanResilienceExecutor resilienceExecutor,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient,
            IdempotencyCachePort idempotencyCache
    ) {
        super(clientHolder, instrumentResolver, resilienceExecutor);
        this.settings = settings;
        this.restOrderClient = restOrderClient;
        this.idempotencyCache = idempotencyCache;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            Optional<Order> cached = idempotencyCache.get(request.correlationId());
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        DhanInstrumentDefinition instrument = resolveInstrument(request);
        if (settings.isSandbox()) {
            Order order = restOrderClient.placeOrder(request, instrument);
            if (request.correlationId() != null && !request.correlationId().isBlank()) {
                idempotencyCache.put(request.correlationId(), order);
            }
            return order;
        }
        PlaceOrderParamsBuilder builder = new PlaceOrderParamsBuilder()
                .transactionType(DhanSdkConverters.transactionType(request.side()))
                .exchangeSegment(DhanSdkConverters.segment(instrument.exchangeSegment()))
                .productType(DhanSdkConverters.productType(request.productType()))
                .orderType(DhanSdkConverters.orderType(request.orderType()))
                .validity(DhanSdkConverters.validity(request.validity()))
                .securityId(instrument.securityId())
                .quantity((int) request.quantity())
                .price(com.tradej.core.domain.value.PriceMath.fromPaisa(request.pricePaisa()).doubleValue())
                .correlationId(request.correlationId());
        if (request.triggerPricePaisa() > 0L) {
            builder.triggerPrice(com.tradej.core.domain.value.PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
        }
        DhanSdkResponse<?> response = new DhanSdkResponse<>(execute(ApiCategory.ORDER, "place-order", () -> clientHolder.client().placeOrder(builder.build())));
        Order order = DhanSdkMapper.toOrder(response, instrument.toInstrument());
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            idempotencyCache.put(request.correlationId(), order);
        }
        return order;
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        if (settings.isSandbox()) {
            return restOrderClient.modifyOrder(request, null);
        }
        ModifyOrderParamsBuilder builder = new ModifyOrderParamsBuilder();
        if (request.quantity() != null) {
            builder.quantity(request.quantity().intValue());
        }
        if (request.pricePaisa() != null) {
            builder.price(com.tradej.core.domain.value.PriceMath.fromPaisa(request.pricePaisa()).doubleValue());
        }
        if (request.triggerPricePaisa() != null) {
            builder.triggerPrice(com.tradej.core.domain.value.PriceMath.fromPaisa(request.triggerPricePaisa()).doubleValue());
        }
        if (request.orderType() != null) {
            builder.orderType(DhanSdkConverters.orderType(request.orderType()));
        }
        if (request.validity() != null) {
            builder.validity(DhanSdkConverters.validity(request.validity()));
        }
        run(ApiCategory.ORDER, "modify-order", () -> clientHolder.client().modifyOrder(request.orderId(), builder.build()));
        DhanSdkResponse<?> updated = new DhanSdkResponse<>(execute(ApiCategory.ORDER, "fetch-modified-order", () -> clientHolder.client().getOrderById(request.orderId())));
        DhanInstrumentDefinition definition = resolvePayload(updated);
        return DhanSdkMapper.toOrder(updated, definition.toInstrument());
    }

    @Override
    public boolean cancelOrder(String orderId) {
        if (settings.isSandbox()) {
            return restOrderClient.cancelOrder(orderId);
        }
        return execute(ApiCategory.ORDER, "cancel-order", () -> clientHolder.client().cancelOrder(orderId) != null);
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        if (settings.isSandbox()) {
            return restOrderClient.cancelAllOpenOrders();
        }
        List<String> cancelledIds = new ArrayList<>();
        execute(ApiCategory.ORDER, "list-open-orders", () -> clientHolder.client().getOrders()).forEach(order -> {
            DhanSdkResponse<?> response = new DhanSdkResponse<>(order);
            DhanInstrumentDefinition definition = resolvePayload(response);
            Order mapped = DhanSdkMapper.toOrder(response, definition.toInstrument());
            if (mapped.status().isActive()) {
                if (execute(ApiCategory.ORDER, "cancel-open-order", () -> clientHolder.client().cancelOrder(mapped.orderId()) != null)) {
                    cancelledIds.add(mapped.orderId());
                }
            }
        });
        return cancelledIds;
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        if (settings.isSandbox()) {
            return restOrderClient.cancelAndSquareOffIntradayPositions();
        }
        List<String> cancelled = cancelAllOpenOrders();
        List<Position> positions = execute(ApiCategory.NON_TRADING, "list-positions-for-squareoff",
                () -> clientHolder.client().getPositions().stream()
                        .map(raw -> {
                            DhanSdkResponse<?> response = new DhanSdkResponse<>(raw);
                            DhanInstrumentDefinition definition = resolvePayload(response);
                            return DhanSdkMapper.toPosition(response, definition.toInstrument());
                        }).toList());
        for (Position position : positions) {
            if (position.quantity() == 0L) {
                continue;
            }
            Side exitSide = position.quantity() > 0 ? Side.SELL : Side.BUY;
            long quantity = Math.abs(position.quantity());
            OrderRequest exit = new OrderRequest(
                    position.symbol(),
                    position.exchangeSegment(),
                    exitSide,
                    quantity,
                    OrderType.MARKET,
                    0L,
                    0L,
                    ProductType.INTRADAY,
                    Validity.DAY,
                    "squareoff-" + position.symbol() + "-" + System.currentTimeMillis()
            );
            Order placed = placeOrder(exit);
            cancelled.add(placed.orderId());
        }
        return cancelled;
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        return execute(ApiCategory.ORDER, "kill-switch", () -> clientHolder.client().setKillSwitch(DhanSdkConverters.killSwitch(enabled)) != null);
    }

    private DhanInstrumentDefinition resolveInstrument(OrderRequest request) {
        DhanInstrumentDefinition instrument = resolveDef(request.symbol(), request.exchangeSegment());
        if (request.exchangeSegment() == instrument.exchangeSegment()) {
            return instrument;
        }
        throw new IllegalStateException("Dhan instrument resolution: invalid venue metadata for " + request.symbol());
    }
}
