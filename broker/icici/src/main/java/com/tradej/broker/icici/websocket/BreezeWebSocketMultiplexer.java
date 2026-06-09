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
    private final ConcurrentHashMap<MarketSubscriptionRequest, String> scriptCodeCache = new ConcurrentHashMap<>();
    private final com.tradej.broker.core.dedup.MarketTickDedupFilter tickDedupFilter =
            new com.tradej.broker.core.dedup.MarketTickDedupFilter();

    private volatile Socket quoteSocket;
    private volatile Socket orderSocket;
    private volatile boolean connected;
    private volatile BreezeWebSocketHealthMonitor healthMonitor;
    private volatile java.util.concurrent.ScheduledExecutorService sessionRefreshScheduler;

    // ── Reconnection & Circuit Breaker State ─────────────────────────
    private int reconnectAttempts;
    private long circuitOpenUntilMs;
    private static final int WS_RECONNECT_FAILURE_THRESHOLD = 10;
    private static final long WS_RECONNECT_CIRCUIT_OPEN_MS = 60_000L; // 1 minute
    private static final long WS_RECONNECT_BASE_DELAY_MS = 1_000L; // 1 second
    private static final long WS_RECONNECT_MAX_DELAY_MS = 30_000L; // 30 seconds
    private volatile java.util.concurrent.ScheduledExecutorService reconnectScheduler;

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
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "icici-ws-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void connect() {
        tokenProvider.ensureValid();
        var session = tokenProvider.session();
        quoteSocket = openSocket(BreezeApiEndpoints.LIVE_STREAM_URL, session.userId(), session.sessionKey(), true);
        orderSocket = openSocket(BreezeApiEndpoints.LIVE_FEEDS_URL, session.userId(), session.sessionKey(), false);
        connected = true;

        // Start health monitor for staleness detection
        if (healthMonitor == null) {
            healthMonitor = new BreezeWebSocketHealthMonitor(
                    idleMs -> log.warn("ICICI feed stale: {}ms idle", idleMs),
                    event -> { for (var l : marketListeners) { if (event instanceof com.tradej.core.domain.event.MarketTickEvent t) l.onEvent(t); } },
                    metadataFactory, "icici");
        }
        healthMonitor.resetTimestamps();
        healthMonitor.start();

        sessionRefreshScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "icici-session-refresh"); t.setDaemon(true); return t; });
        long msUntilRefresh = computeMsUntilPreMidnight();
        if (msUntilRefresh > 0) {
            sessionRefreshScheduler.schedule(this::refreshSession, msUntilRefresh, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        if (healthMonitor != null) {
            healthMonitor.emitDisconnected();
            healthMonitor.stop();
        }
        if (sessionRefreshScheduler != null) {
            sessionRefreshScheduler.shutdown();
            sessionRefreshScheduler = null;
        }
        if (reconnectScheduler != null) {
            reconnectScheduler.shutdown();
            try {
                if (!reconnectScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    reconnectScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                reconnectScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        closeSocket(quoteSocket);
        closeSocket(orderSocket);
        quoteSocket = null;
        orderSocket = null;
        subscriptions.clear();
        scriptCodeCache.clear();
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
        instruments.forEach(scriptCodeCache::remove);
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
            // Always resubscribe on every EVENT_CONNECT (initial + reconnect).
            // Socket.IO join is idempotent — re-emitting join for already-joined instruments is a no-op.
            // This fixes the critical bug where reconnects after the first connect silently lost
            // all subscriptions because firstConnect.compareAndSet prevented resubscribeAll().
            socket.on(Socket.EVENT_CONNECT, args -> {
                log.info("ICICI websocket connected: {}", url);
                tickDedupFilter.reset();
                com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordReconnect("icici");
                com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.setActiveSubscriptions("icici", subscriptions.size());
                resubscribeAll();
                if (healthMonitor != null) {
                    healthMonitor.emitConnected();
                }
                if (reconnectRegistry != null) {
                    wsExecutor.submit(() -> reconnectRegistry.notifyReconnect());
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

    private static final int EMIT_BATCH_SIZE = 200;
    private static final long EMIT_BATCH_DELAY_MS = 100;

    private void emitJoin(Collection<MarketSubscriptionRequest> instruments) {
        if (quoteSocket == null) {
            return;
        }
        List<String> tokens = scriptCodes(instruments);
        if (tokens.isEmpty()) {
            return;
        }
        // R9: Rate-limited batched emit to avoid Socket.IO message size limits
        for (int i = 0; i < tokens.size(); i += EMIT_BATCH_SIZE) {
            int end = Math.min(i + EMIT_BATCH_SIZE, tokens.size());
            List<String> batch = tokens.subList(i, end);
            quoteSocket.emit("join", new JSONArray(batch));
            if (end < tokens.size()) {
                try {
                    Thread.sleep(EMIT_BATCH_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private List<String> scriptCodes(Collection<MarketSubscriptionRequest> instruments) {
        List<String> tokens = new ArrayList<>();
        for (MarketSubscriptionRequest request : instruments) {
            try {
                String code = scriptCodeCache.computeIfAbsent(request, req ->
                        instrumentResolver.requireBreezeDefinition(
                                new InstrumentKey(req.symbol(), req.exchangeSegment())).scriptCode());
                tokens.add(code);
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
        if (healthMonitor != null) {
            healthMonitor.recordMarketEvent();
        }
        try {
            JSONObject tick = args[0] instanceof JSONObject json ? json : new JSONObject(String.valueOf(args[0]));
            long ltpPaisa = Math.round(tick.optDouble("last", tick.optDouble("ltp", 0.0)) * 100.0);
            long openPaisa = tick.has("open") ? Math.round(tick.optDouble("open", 0.0) * 100.0) : 0L;
            long highPaisa = tick.has("high") ? Math.round(tick.optDouble("high", 0.0) * 100.0) : 0L;
            long lowPaisa = tick.has("low") ? Math.round(tick.optDouble("low", 0.0) * 100.0) : 0L;
            long closePaisa = tick.has("close") ? Math.round(tick.optDouble("close", 0.0) * 100.0) : 0L;
            long volume = tick.optLong("ttq", tick.optLong("volume", 0L));
            long oi = tick.optLong("open_interest", tick.optLong("oi", 0L));
            String symbol = tick.optString("stock_code", tick.optString("stock_name", "UNKNOWN"));
            String exchangeCode = tick.optString("exchange_code", tick.optString("exchange", null));
            com.tradej.core.domain.value.ExchangeSegment segment = resolveSegment(exchangeCode, symbol);
            MarketTickEvent event = new MarketTickEvent(
                    metadataFactory.root(),
                    sequenceCounter.incrementAndGet(),
                    symbol,
                    segment,
                    com.tradej.core.domain.value.FeedMode.TICKER,
                    ltpPaisa,
                    tick.optLong("ltq", 0L),
                    volume,
                    System.currentTimeMillis(),
                    java.util.Optional.empty()
            , oi, 0L);
            if (tickDedupFilter.isDuplicate(symbol, segment.name(), event.sequenceId())) {
                com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordTickDropped("icici", "dedup");
                return;
            }
            com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordTickReceived("icici");
            for (MarketDataListener listener : marketListeners) {
                listener.onEvent(event);
            }
        } catch (Exception ex) {
            com.tradej.broker.core.metrics.BrokerFeedMetrics.INSTANCE.recordParseError("icici");
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

    private long computeMsUntilPreMidnight() {
        java.time.ZoneId ist = java.time.ZoneId.of("Asia/Kolkata");
        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(ist);
        java.time.ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ist);
        long fiveMinutesBeforeMs = nextMidnight.toInstant().toEpochMilli() - 5 * 60 * 1000L;
        long delay = fiveMinutesBeforeMs - now.toInstant().toEpochMilli();
        return Math.max(delay, 0L);
    }

    private void refreshSession() {
        try {
            log.info("Refreshing ICICI session before midnight expiry");
            tokenProvider.ensureValid();
            if (sessionRefreshScheduler != null && !sessionRefreshScheduler.isShutdown()) {
                long nextDelay = computeMsUntilPreMidnight();
                if (nextDelay > 0) {
                    sessionRefreshScheduler.schedule(this::refreshSession, nextDelay, java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception ex) {
            log.warn("ICICI session refresh failed: {}", ex.getMessage());
        }
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

    // ── Circuit Breaker & Reconnection Logic ─────────────────────────

    /**
     * Attempt reconnect with exponential backoff and circuit breaker.
     * Called when WebSocket disconnects unexpectedly.
     */
    void reconnectWithBackoff() {
        long now = System.currentTimeMillis();
        if (circuitOpenUntilMs > now) {
            log.debug("ICICI WebSocket reconnect circuit open until {}", circuitOpenUntilMs);
            return;
        }

        reconnectAttempts++;
        if (reconnectAttempts >= WS_RECONNECT_FAILURE_THRESHOLD) {
            circuitOpenUntilMs = now + WS_RECONNECT_CIRCUIT_OPEN_MS;
            log.warn("ICICI WebSocket reconnect circuit opened after {} consecutive failures",
                    reconnectAttempts);
            return;
        }

        long delayMs = computeBackoffDelay(reconnectAttempts);
        log.info("Scheduling ICICI WebSocket reconnect attempt {} in {}ms",
                reconnectAttempts, delayMs);

        reconnectScheduler.schedule(this::executeReconnect, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private long computeBackoffDelay(int attempt) {
        // Exponential backoff with jitter
        long baseDelay = Math.min(WS_RECONNECT_BASE_DELAY_MS * (1L << (attempt - 1)), WS_RECONNECT_MAX_DELAY_MS);
        long jitter = (long) (Math.random() * baseDelay * 0.1); // 10% jitter
        return baseDelay + jitter;
    }

    private void executeReconnect() {
        if (!connected && reconnectAttempts < WS_RECONNECT_FAILURE_THRESHOLD) {
            try {
                log.info("Executing ICICI WebSocket reconnect attempt {}", reconnectAttempts);
                tokenProvider.ensureValid();
                var session = tokenProvider.session();

                closeSocket(quoteSocket);
                closeSocket(orderSocket);

                quoteSocket = openSocket(BreezeApiEndpoints.LIVE_STREAM_URL, session.userId(), session.sessionKey(), true);
                orderSocket = openSocket(BreezeApiEndpoints.LIVE_FEEDS_URL, session.userId(), session.sessionKey(), false);
                connected = true;

                if (healthMonitor != null) {
                    healthMonitor.resetTimestamps();
                    healthMonitor.emitConnected();
                }

                if (reconnectRegistry != null) {
                    wsExecutor.submit(() -> reconnectRegistry.notifyReconnect());
                }

                // Reset reconnect counter on success
                reconnectAttempts = 0;
                circuitOpenUntilMs = 0;
                log.info("ICICI WebSocket reconnected successfully");
            } catch (Exception ex) {
                log.warn("ICICI WebSocket reconnect attempt {} failed: {}",
                        reconnectAttempts, ex.getMessage());
                connected = false;
                // Schedule another attempt
                reconnectWithBackoff();
            }
        }
    }

    /**
     * Reset reconnect circuit (called after successful manual connect).
     */
    private void resetReconnectCircuit() {
        reconnectAttempts = 0;
        circuitOpenUntilMs = 0;
    }
}
