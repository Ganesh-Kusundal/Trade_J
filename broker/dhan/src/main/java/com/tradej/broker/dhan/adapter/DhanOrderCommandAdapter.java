package com.tradej.broker.dhan.adapter;

import com.tradej.broker.api.port.IdempotencyCachePort;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.orders.DhanRestOrderClient;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.broker.dhan.validator.DhanOrderValidator;
import com.tradej.core.domain.model.ModifyOrderRequest;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderPreview;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class DhanOrderCommandAdapter implements OrderCommand {
    private static final int MAX_MODIFICATIONS_PER_ORDER = 25;

    private final DhanAdapterContext context;
    private final DhanInstrumentResolver resolver;
    private final IdempotencyCachePort idempotencyCache;
    private final DhanConnectionSettings settings;
    private final DhanRestOrderClient restOrderClient;
    private final DhanOrderValidator validator;
    private final ConcurrentHashMap<String, Object> correlationLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> modificationCounts = new ConcurrentHashMap<>();

    public DhanOrderCommandAdapter(
            DhanAdapterContext context,
            DhanConnectionSettings settings,
            DhanRestOrderClient restOrderClient,
            IdempotencyCachePort idempotencyCache,
            DhanOrderValidator validator
    ) {
        this.context = context;
        this.resolver = context.resolver();
        this.settings = settings;
        this.restOrderClient = restOrderClient;
        this.idempotencyCache = idempotencyCache;
        this.validator = validator;
    }

    @Override
    public Order placeOrder(OrderRequest request) {
        validator.validateOrThrow(request);

        if (!resolver.isLoaded()) {
            throw new IllegalStateException("Instrument catalog not loaded. Call loadInstrumentCatalog() before placing orders.");
        }

        String correlationId = request.correlationId();
        if (correlationId == null || correlationId.isBlank()) {
            return doPlaceOrder(request);
        }

        Object lock = correlationLocks.computeIfAbsent(correlationId, k -> new Object());
        synchronized (lock) {
            try {
                Optional<Order> cached = idempotencyCache.get(correlationId);
                if (cached.isPresent()) {
                    return cached.get();
                }
                Order order = doPlaceOrder(request);
                idempotencyCache.put(correlationId, order);
                return order;
            } finally {
                correlationLocks.remove(correlationId);
            }
        }
    }

    private Order doPlaceOrder(OrderRequest request) {
        DhanInstrumentDefinition instrument = resolveInstrument(request);
        if (settings.isSandbox()) {
            return restOrderClient.placeOrder(request, instrument);
        }
        return context.execute(ApiCategory.ORDER, "place-order", () -> {
            var response = DhanJsonMapper.wrap(
                    restOrderClient.placeOrderViaApi(request, instrument, settings)
            );
            return DhanJsonMapper.toOrder(response, instrument.toInstrument());
        });
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return context.execute(ApiCategory.ORDER, "cancel-order", () -> restOrderClient.cancelOrderViaApi(orderId, settings));
    }

    @Override
    public Order modifyOrder(ModifyOrderRequest request) {
        String orderId = request.orderId();
        AtomicInteger counter = modificationCounts.computeIfAbsent(orderId, k -> new AtomicInteger(0));
        int count = counter.incrementAndGet();
        if (count > MAX_MODIFICATIONS_PER_ORDER) {
            counter.decrementAndGet();
            throw new IllegalStateException(
                    "Dhan limits modifications to " + MAX_MODIFICATIONS_PER_ORDER
                            + " per order. Order " + orderId + " has been modified " + (count - 1) + " times.");
        }
        try {
            return context.execute(ApiCategory.ORDER, "modify-order", () -> {
                var response = DhanJsonMapper.wrap(
                        restOrderClient.modifyOrderViaApi(request, null, settings)
                );
                DhanInstrumentDefinition definition = context.resolvePayload(response.raw());
                return DhanJsonMapper.toOrder(response, definition.toInstrument());
            });
        } catch (RuntimeException ex) {
            counter.decrementAndGet();
            throw ex;
        }
    }

    @Override
    public List<String> cancelAllOpenOrders() {
        List<String> cancelled = new ArrayList<>();
        List<Order> openOrders = restOrderClient.getOrders();
        for (Order order : openOrders) {
            if (order.status().isActive()) {
                DhanInstrumentDefinition definition = context.resolveDef(order.symbol(), order.exchangeSegment());
                if (restOrderClient.cancelOrderViaApi(order.orderId(), settings)) {
                    cancelled.add(order.orderId());
                }
            }
        }
        return cancelled;
    }

    @Override
    public List<String> cancelAndSquareOffIntradayPositions() {
        List<String> cancelled = cancelAllOpenOrders();
        List<Position> positions = context.execute(ApiCategory.NON_TRADING, "list-positions-for-squareoff",
                () -> restOrderClient.fetchPositionsViaApi(settings)
        );
        for (Position position : positions) {
            if (position.quantity() == 0L) {
                continue;
            }
            Side exitSide = position.quantity() > 0 ? Side.SELL : Side.BUY;
            long quantity = Math.abs(position.quantity());
            OrderRequest exit = new OrderRequest(
                    position.symbol(), position.exchangeSegment(), exitSide, quantity,
                    OrderType.MARKET, 0L, 0L, ProductType.INTRADAY, Validity.DAY,
                    "squareoff-" + position.symbol() + "-" + System.currentTimeMillis()
            );
            Order placed = placeOrder(exit);
            cancelled.add(placed.orderId());
        }
        return cancelled;
    }

    @Override
    public boolean setKillSwitch(boolean enabled) {
        return context.execute(ApiCategory.ORDER, "kill-switch", () -> restOrderClient.setKillSwitchViaApi(enabled, settings));
    }

    @Override
    public OrderPreview previewOrder(OrderRequest request) {
        return validator.previewOrder(request);
    }

    private DhanInstrumentDefinition resolveInstrument(OrderRequest request) {
        DhanInstrumentDefinition instrument = context.resolveDef(request.symbol(), request.exchangeSegment());
        if (request.exchangeSegment() == instrument.exchangeSegment()) {
            return instrument;
        }
        throw new IllegalArgumentException("Dhan instrument resolution: invalid venue metadata for " + request.symbol());
    }
}
