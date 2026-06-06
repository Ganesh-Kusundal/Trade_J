package com.tradej.broker.icici.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.api.port.MarketDataListener;
import com.tradej.broker.api.port.OrderUpdateListener;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.constants.BreezeApiEndpoints;
import com.tradej.broker.icici.instrument.BreezeInstrumentResolver;
import com.tradej.broker.core.reconnect.ReconnectListenerRegistry;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderCancelled;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderModified;
import com.tradej.core.domain.event.OrderPartiallyFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.OrderUpdateEvent;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import io.socket.client.IO;
import io.socket.client.Socket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class BreezeWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final Logger log = LoggerFactory.getLogger(BreezeWebSocketMultiplexer.class);

    private final BreezeTokenProvider tokenProvider;
    private final BreezeInstrumentResolver instrumentResolver;
    private final EventMetadataFactory metadataFactory;
    private final ReconnectListenerRegistry reconnectRegistry;
    private final ExecutorService wsExecutor;
    private final AtomicLong sequenceCounter = new AtomicLong();

    private final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderListeners = new CopyOnWriteArrayList<>();
    private final Map<String, OrderStatus> latestOrderStatuses = new ConcurrentHashMap<>();

    private volatile Socket quoteSocket;
    private volatile Socket orderSocket;
    private volatile boolean connected;

    public BreezeWebSocketMultiplexer(
            BreezeTokenProvider tokenProvider,
            BreezeInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory
    ) {
        this(tokenProvider, instrumentResolver, metadataFactory, null);
    }

    /**
     * Convenience constructor that binds the internal reconnect executor.
     * For test injection use the 4-arg constructor.
     */
    public BreezeWebSocketMultiplexer(
            BreezeTokenProvider tokenProvider,
            BreezeInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry
    ) {
        this(tokenProvider, instrumentResolver, metadataFactory,
                reconnectRegistry,
                Executors.newSingleThreadExecutor(r -> new Thread(r, "breeze-ws-reconnect")));
    }

    // Visible for testing — allows injector of a mock/scoped executor.
    BreezeWebSocketMultiplexer(
            BreezeTokenProvider tokenProvider,
            BreezeInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry,
            ExecutorService wsExecutor
    ) {
        this.tokenProvider = tokenProvider;
        this.instrumentResolver = instrumentResolver;
        this.metadataFactory = metadataFactory;
        this.reconnectRegistry = reconnectRegistry;
        this.wsExecutor = wsExecutor;
    }

    @Override
    public void connect() {
        tokenProvider.ensureValid();
        var session = tokenProvider.session();
        quoteSocket = openSocket(BreezeApiEndpoints.LIVE_STREAM_URL, session.userId(), session.sessionKey(), true);
        orderSocket = openSocket(BreezeApiEndpoints.LIVE_FEEDS_URL, session.userId(), session.sessionKey(), false);
        connected = true;
        // Initial resubscribe is handled by the EVENT_CONNECT handler in openSocket().
        // Reconnect re-subscription is handled by ReconnectListenerRegistry → SubscriptionCoordinator.
    }

    @Override
    public void disconnect() {
        connected = false;
        closeSocket(quoteSocket);
        closeSocket(orderSocket);
        quoteSocket = null;
        orderSocket = null;
        // MED-1: prevent unbounded memory growth over trading sessions.
        // Subscriptions are repopulated on reconnect via resubscribeAll() / SubscriptionCoordinator.
        subscriptions.clear();
        shutdownExecutor(wsExecutor);
    }

    /**
     * Releases all resources held by this multiplexer.
     * Idempotent and safe to call multiple times.
     */
    public void close() {
        disconnect();
    }

    private static void shutdownExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isConnected() {
        return connected && quoteSocket != null && quoteSocket.connected();
    }

    @Override
    public void subscribe(Collection<MarketSubscriptionRequest> instruments, FeedMode feedMode) {
        for (MarketSubscriptionRequest request : instruments) {
            subscriptions.put(request, feedMode);
        }
        if (connected) {
            emitJoin(instruments);
        }
    }

    @Override
    public void unsubscribe(Collection<MarketSubscriptionRequest> instruments) {
        instruments.forEach(subscriptions::remove);
        if (connected && quoteSocket != null) {
            List<String> tokens = scriptCodes(instruments);
            if (!tokens.isEmpty()) {
                quoteSocket.emit("leave", new JSONArray(tokens));
            }
        }
    }

    @Override
    public void onMarketData(MarketDataListener listener) {
        marketListeners.add(listener);
    }

    @Override
    public void onOrderUpdate(OrderUpdateListener listener) {
        orderListeners.add(listener);
    }

    @Override
    public Map<MarketSubscriptionRequest, FeedMode> subscriptions() {
        return Collections.unmodifiableMap(subscriptions);
    }

    private Socket openSocket(String url, String userId, String sessionKey, boolean marketFeed) {
        try {
            IO.Options options = IO.Options.builder()
                    .setTransports(new String[]{"websocket"})
                    .setAuth(Map.of("user", userId, "token", sessionKey))
                    .build();
            options.extraHeaders = Map.of(
                    BreezeApiEndpoints.HEADER_USER_AGENT,
                    List.of(BreezeApiEndpoints.USER_AGENT)
            );
            Socket socket = IO.socket(URI.create(url), options);
            if (marketFeed) {
                socket.on("stock", args -> handleQuote(args));
                socket.on("if", args -> handleQuote(args));
            } else {
                socket.on("order", args -> handleOrder(args));
            }
            // Track first connect to distinguish initial connect from socket.io reconnects.
            // Socket.io fires EVENT_CONNECT on both initial connect and reconnect.
            java.util.concurrent.atomic.AtomicBoolean firstConnect = new java.util.concurrent.atomic.AtomicBoolean(true);
            socket.on(Socket.EVENT_CONNECT, args -> {
                log.info("ICICI websocket connected: {}", url);
                if (firstConnect.compareAndSet(true, false)) {
                    // First connect — resubscribe directly (coordinator has no desired state yet).
                    resubscribeAll();
                } else {
                    // Socket.io reconnect — delegate to dedicated executor so it never
                    // competes with strategy threads or ForkJoinPool.common.
                    if (reconnectRegistry != null) {
                        wsExecutor.submit(() -> reconnectRegistry.notifyReconnect());
                    }
                }
            });
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> log.warn("ICICI websocket connect error {}: {}", url, args));
            socket.connect();
            return socket;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to connect ICICI websocket " + url, ex);
        }
    }

    private void resubscribeAll() {
        if (!subscriptions.isEmpty()) {
            emitJoin(subscriptions.keySet());
        }
    }

    private void emitJoin(Collection<MarketSubscriptionRequest> instruments) {
        if (quoteSocket == null) {
            return;
        }
        List<String> tokens = scriptCodes(instruments);
        if (!tokens.isEmpty()) {
            quoteSocket.emit("join", new JSONArray(tokens));
        }
    }

    private List<String> scriptCodes(Collection<MarketSubscriptionRequest> instruments) {
        List<String> tokens = new ArrayList<>();
        for (MarketSubscriptionRequest request : instruments) {
            try {
                tokens.add(instrumentResolver.requireBreezeDefinition(
                        new InstrumentKey(request.symbol(), request.exchangeSegment())).scriptCode());
            } catch (Exception ex) {
                log.warn("Skipping ICICI subscription for {}: {}", request, ex.getMessage());
            }
        }
        return tokens;
    }

    private void handleQuote(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }
        try {
            JSONObject tick = args[0] instanceof JSONObject json ? json : new JSONObject(String.valueOf(args[0]));
            long ltpPaisa = Math.round(tick.optDouble("last", tick.optDouble("ltp", 0.0)) * 100.0);
            String symbol = tick.optString("stock_code", tick.optString("stock_name", "UNKNOWN"));
            MarketTickEvent event = new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    symbol,
                    com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                    com.tradej.core.domain.value.FeedMode.TICKER,
                    ltpPaisa,
                    tick.optLong("ltq", 0L),
                    tick.optLong("ttq", 0L),
                    System.currentTimeMillis(),
                    java.util.Optional.empty()
            , 0L, 0L);
            for (MarketDataListener listener : marketListeners) {
                listener.onEvent(event);
            }
        } catch (Exception ex) {
            log.debug("Failed to parse ICICI quote tick: {}", ex.getMessage());
        }
    }

    private void handleOrder(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }
        try {
            JSONObject json = args[0] instanceof JSONObject j ? j : new JSONObject(String.valueOf(args[0]));
            OrderUpdateEvent event = toOrderEvent(json);
            if (event == null) {
                return;
            }
            if (isDuplicateOrderEvent(event)) {
                return;
            }
            for (OrderUpdateListener listener : orderListeners) {
                listener.onEvent(event);
            }
        } catch (Exception ex) {
            log.debug("Failed to parse ICICI order event: {}", ex.getMessage());
        }
    }

    private boolean isDuplicateOrderEvent(OrderUpdateEvent event) {
        String orderId = extractOrderId(event);
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        OrderStatus newStatus = extractStatus(event);
        if (newStatus == null) {
            return false;
        }
        OrderStatus previousStatus = latestOrderStatuses.put(orderId, newStatus);
        if (previousStatus == null) {
            return false;
        }
        return previousStatus == newStatus;
    }

    private static String extractOrderId(OrderUpdateEvent event) {
        return switch (event) {
            case OrderAccepted e -> e.order().orderId();
            case OrderFilled e -> e.order().orderId();
            case OrderPartiallyFilled e -> e.order().orderId();
            case OrderRejected e -> e.order().orderId();
            case OrderCancelled e -> e.order().orderId();
            case OrderModified e -> e.order().orderId();
            default -> null;
        };
    }

    private static OrderStatus extractStatus(OrderUpdateEvent event) {
        return switch (event) {
            case OrderAccepted ignored -> OrderStatus.OPEN;
            case OrderFilled ignored -> OrderStatus.TRADED;
            case OrderPartiallyFilled ignored -> OrderStatus.PART_TRADED;
            case OrderRejected ignored -> OrderStatus.REJECTED;
            case OrderCancelled ignored -> OrderStatus.CANCELLED;
            case OrderModified ignored -> OrderStatus.OPEN;
            default -> null;
        };
    }

    private OrderUpdateEvent toOrderEvent(JSONObject json) throws org.json.JSONException {
        String orderId = json.optString("order_id", json.optString("orderId", ""));
        String symbol   = json.optString("stock_code", json.optString("symbol", ""));
        String status   = json.optString("status", json.optString("order_status", "")).toLowerCase();
        String action   = json.optString("action", "").toLowerCase();
        String exchangeCode = json.optString("exchange_code", json.optString("exchangeSegment", ""));
        String exchangeOrderId = json.optString("exchange_order_id", json.optString("exchangeOrderId", ""));
        String rejectionReason = json.optString("reason", json.optString("rejection_reason", ""));

        long qty    = parseLongOrZero(json, "quantity");
        long filled = parseLongOrZero(json, "filled_quantity", "executed_quantity", "tradedQty");
        double priceRaw = parseDoubleOrZero(json, "price", "order_price");
        long pricePaisa = Math.round(priceRaw * 100.0);
        long triggerPaisa = Math.round(parseDoubleOrZero(json, "trigger_price") * 100.0);
        long exchangeTimeMs = json.has("exchange_time")
                ? json.getLong("exchange_time")
                : (json.has("exchangeTimestamp") ? json.getLong("exchangeTimestamp") : System.currentTimeMillis());

        // Derive local correlation-id from exchange-order-id when available.
        String correlationId = !exchangeOrderId.isBlank() ? exchangeOrderId : null;

        com.tradej.core.domain.value.ExchangeSegment segment =
                resolveSegment(exchangeCode, symbol);
        Side side = "sell".equals(action) ? Side.SELL : Side.BUY;
        OrderStatus orderStatus = parseStatus(status);

        Order order = new Order(
                orderId,
                correlationId,
                symbol,
                segment,
                side,
                ProductType.CNC,  // WebSocket does not expose product type; default to CNC.
                OrderType.LIMIT,        // WebSocket does not expose order type; default to LIMIT.
                orderStatus,
                qty,
                filled,
                pricePaisa,
                triggerPaisa,
                exchangeTimeMs,
                rejectionReason
        );

        var em = metadataFactory.root();

        return switch (status) {
            case "executed", "complete", "filled", "trade" ->
                    new OrderFilled(em, order, List.of());
            case "partially executed", "partial_fill", "partial", "part_traded" ->
                    new OrderPartiallyFilled(em, order, List.of());
            case "rejected", "reject" ->
                    new OrderRejected(em, order, rejectionReason);
            case "cancelled", "canceled", "cancel" ->
                    new OrderCancelled(em, order, rejectionReason);
            case "modified", "amended" ->
                    new OrderModified(em, order);
            default -> {
                // Ordered / open / pending — emit as accepted.
                if (orderStatus.isActive()) {
                    yield new OrderAccepted(em, order);
                }
                // Skip events with no actionable status.
                yield null;
            }
        };
    }

    private static long parseLongOrZero(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                try { return json.getLong(key); } catch (Exception ignored) {}
            }
        }
        return 0L;
    }

    private static double parseDoubleOrZero(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key)) {
                try { return json.getDouble(key); } catch (Exception ignored) {}
            }
        }
        return 0.0;
    }

    private com.tradej.core.domain.value.ExchangeSegment resolveSegment(String exchangeCode, String symbol) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return com.tradej.core.domain.value.ExchangeSegment.NSE_EQ;
        }
        return switch (exchangeCode.toUpperCase()) {
            case "NSE", "NSE_CM"     -> com.tradej.core.domain.value.ExchangeSegment.NSE_EQ;
            case "BSE", "BSE_CM"     -> com.tradej.core.domain.value.ExchangeSegment.BSE_EQ;
            case "NSE_FO"           -> com.tradej.core.domain.value.ExchangeSegment.NSE_FNO;
            case "BSE_FO"           -> com.tradej.core.domain.value.ExchangeSegment.BSE_FNO;
            case "NSE_RX", "CDS"     -> com.tradej.core.domain.value.ExchangeSegment.NSE_CURRENCY;
            case "MCX"              -> com.tradej.core.domain.value.ExchangeSegment.MCX_COMM;
            default                  -> com.tradej.core.domain.value.ExchangeSegment.NSE_EQ;
        };
    }

    private static OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return OrderStatus.UNKNOWN;
        }
        return switch (status) {
            case "executed", "complete", "filled", "trade"    -> OrderStatus.TRADED;
            case "cancelled", "canceled", "cancel"               -> OrderStatus.CANCELLED;
            case "rejected", "reject"                           -> OrderStatus.REJECTED;
            case "partially executed", "partial_fill", "partial", "part_traded" -> OrderStatus.PART_TRADED;
            case "ordered", "open", "pending"                   -> OrderStatus.OPEN;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.disconnect();
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }
}
