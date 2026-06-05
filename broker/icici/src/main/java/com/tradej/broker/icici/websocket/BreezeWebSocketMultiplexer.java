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
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.FeedMode;
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
import java.util.concurrent.atomic.AtomicLong;

public final class BreezeWebSocketMultiplexer implements WebSocketMultiplexer {
    private static final Logger log = LoggerFactory.getLogger(BreezeWebSocketMultiplexer.class);

    private final BreezeTokenProvider tokenProvider;
    private final BreezeInstrumentResolver instrumentResolver;
    private final EventMetadataFactory metadataFactory;
    private final ReconnectListenerRegistry reconnectRegistry;
    private final AtomicLong sequenceCounter = new AtomicLong();

    private final Map<MarketSubscriptionRequest, FeedMode> subscriptions = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<MarketDataListener> marketListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<OrderUpdateListener> orderListeners = new CopyOnWriteArrayList<>();

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

    public BreezeWebSocketMultiplexer(
            BreezeTokenProvider tokenProvider,
            BreezeInstrumentResolver instrumentResolver,
            EventMetadataFactory metadataFactory,
            ReconnectListenerRegistry reconnectRegistry
    ) {
        this.tokenProvider = tokenProvider;
        this.instrumentResolver = instrumentResolver;
        this.metadataFactory = metadataFactory;
        this.reconnectRegistry = reconnectRegistry;
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
                    // Socket.io reconnect — let SubscriptionCoordinator handle via registry.
                    if (reconnectRegistry != null) {
                        reconnectRegistry.notifyReconnect();
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
        // Order notification parsing is broker-specific; REST order query remains authoritative.
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
