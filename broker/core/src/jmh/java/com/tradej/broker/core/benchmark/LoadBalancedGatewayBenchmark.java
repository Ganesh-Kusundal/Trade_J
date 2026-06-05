package com.tradej.broker.core.benchmark;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.OptionsCapable;
import com.tradej.broker.api.port.*;
import com.tradej.broker.core.routing.LoadBalancedBrokerGateway;
import com.tradej.core.domain.model.*;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH macro-benchmark for {@link LoadBalancedBrokerGateway} throughput under
 * simulated broker network latency.
 *
 * <p>Each fake broker node injects a 1&nbsp;ms sleep in {@code getLtpPaisa} to
 * simulate RTT without making the benchmark unbearably slow.  Three nodes are
 * round-robined by the load-balanced market-data provider.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 2)
@Fork(1)
@State(Scope.Benchmark)
public class LoadBalancedGatewayBenchmark {

    private static final long SIMULATED_LATENCY_MS = 1L;

    private LoadBalancedBrokerGateway gateway;
    private InstrumentKey key;

    @Setup
    public void setup() {
        List<IBrokerConnection> nodes = List.of(
                new FakeBrokerConnection("node-1"),
                new FakeBrokerConnection("node-2"),
                new FakeBrokerConnection("node-3")
        );
        gateway = new LoadBalancedBrokerGateway(nodes);
        key = InstrumentKey.of("NIFTY", ExchangeSegment.NSE_EQ);
    }

    @Benchmark
    @Threads(1)
    public long ltpLookup_1() {
        return gateway.marketData().getLtpPaisa(key);
    }

    @Benchmark
    @Threads(4)
    public long ltpLookup_4() {
        return gateway.marketData().getLtpPaisa(key);
    }

    @Benchmark
    @Threads(8)
    public long ltpLookup_8() {
        return gateway.marketData().getLtpPaisa(key);
    }

    @Benchmark
    @Threads(16)
    public long ltpLookup_16() {
        return gateway.marketData().getLtpPaisa(key);
    }

    /* ------------------------------------------------------------------ */
    /*  Fake implementations — Mockito is incompatible with JMH classloaders */
    /* ------------------------------------------------------------------ */

    private static final class FakeBrokerConnection implements IBrokerConnection, OptionsCapable {
        private final String label;
        private final MarketDataProvider marketData = new FakeLatencyMarketDataProvider();
        private final OrderCommand orders = new FakeOrderCommand();
        private final OrderQuery orderQuery = new FakeOrderQuery();
        private final PortfolioProvider portfolio = new FakePortfolioProvider();
        private final InstrumentResolver instruments = new FakeInstrumentResolver();
        private final WebSocketMultiplexer websocket = new FakeWebSocketMultiplexer();
        private final OptionsProvider options = new FakeOptionsProvider();

        FakeBrokerConnection(String label) { this.label = label; }

        @Override public MarketDataProvider marketData() { return marketData; }
        @Override public OrderCommand orders() { return orders; }
        @Override public OrderQuery orderQuery() { return orderQuery; }
        @Override public PortfolioProvider portfolio() { return portfolio; }
        @Override public InstrumentResolver instruments() { return instruments; }
        @Override public WebSocketMultiplexer websocket() { return websocket; }
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public void loadInstrumentCatalog(Path catalogPath) { }
        @Override public OptionsProvider options() { return options; }
        @Override public <T> Optional<T> getCapability(Class<T> capabilityClass) {
            if (capabilityClass == OptionsCapable.class) return Optional.of(capabilityClass.cast(this));
            return Optional.empty();
        }
    }

    private static final class FakeLatencyMarketDataProvider implements MarketDataProvider {
        @Override public long getLtpPaisa(InstrumentKey instrumentKey) {
            try { Thread.sleep(SIMULATED_LATENCY_MS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return 18_000_00L;
        }
        @Override public Quote getQuote(InstrumentKey instrumentKey) { return null; }
        @Override public MarketDepth getDepth(InstrumentKey instrumentKey) { return null; }
        @Override public Quote getOhlcSnapshot(InstrumentKey instrumentKey) { return null; }
        @Override public List<Candle> getCandles(CandleHistoryRequest request) { return List.of(); }
        @Override public Map<InstrumentKey, Long> getLtpBatch(Collection<InstrumentKey> instrumentKeys) { return Map.of(); }
        @Override public Map<InstrumentKey, Quote> getQuoteBatch(Collection<InstrumentKey> instrumentKeys) { return Map.of(); }
        @Override public Map<InstrumentKey, Quote> getOhlcBatch(Collection<InstrumentKey> instrumentKeys) { return Map.of(); }
    }

    private static final class FakeOrderCommand implements OrderCommand {
        @Override public Order placeOrder(OrderRequest request) { return null; }
        @Override public Order modifyOrder(ModifyOrderRequest request) { return null; }
        @Override public boolean cancelOrder(String orderId) { return false; }
        @Override public List<String> cancelAllOpenOrders() { return List.of(); }
        @Override public List<String> cancelAndSquareOffIntradayPositions() { return List.of(); }
        @Override public boolean setKillSwitch(boolean enabled) { return false; }
    }

    private static final class FakeOrderQuery implements OrderQuery {
        @Override public Order getOrder(String orderId) { return null; }
        @Override public List<Order> getOrderBook() { return List.of(); }
        @Override public List<Trade> getTradeBook() { return List.of(); }
        @Override public OrderStatus getOrderStatus(String orderId) { return null; }
        @Override public OptionalLong getExecutedPricePaisa(String orderId) { return OptionalLong.empty(); }
        @Override public OptionalLong getExchangeTimeMs(String orderId) { return OptionalLong.empty(); }
    }

    private static final class FakePortfolioProvider implements PortfolioProvider {
        @Override public List<Position> getPositions() { return List.of(); }
        @Override public List<Holding> getHoldings() { return List.of(); }
        @Override public Balance getBalance() { return null; }
    }

    private static final class FakeInstrumentResolver implements InstrumentResolver {
        @Override public Instrument resolve(InstrumentKey key) { return null; }
        @Override public Instrument getBySymbol(InstrumentKey key) { return null; }
        @Override public List<Instrument> allInstruments() { return List.of(); }
        @Override public Instrument resolveBySecurityId(String securityId) { return null; }
        @Override public Instrument requireDefinition(InstrumentKey key) { return null; }
        @Override public Instrument resolvePayload(Object payload) { return null; }
        @Override public boolean isLoaded() { return false; }
        @Override public int catalogSize() { return 0; }
    }

    private static final class FakeWebSocketMultiplexer implements WebSocketMultiplexer {
        @Override public void connect() { }
        @Override public void disconnect() { }
        @Override public boolean isConnected() { return false; }
        @Override public void subscribe(Collection<com.tradej.broker.api.model.MarketSubscriptionRequest> instruments, com.tradej.core.domain.value.FeedMode feedMode) { }
        @Override public void unsubscribe(Collection<com.tradej.broker.api.model.MarketSubscriptionRequest> instruments, com.tradej.core.domain.value.FeedMode feedMode) { }
        @Override public void onMarketData(com.tradej.broker.api.port.MarketDataListener listener) { }
        @Override public void onOrderUpdate(com.tradej.broker.api.port.OrderUpdateListener listener) { }
        @Override public Map<com.tradej.broker.api.model.MarketSubscriptionRequest, Set<com.tradej.core.domain.value.FeedMode>> subscriptions() { return Map.of(); }
    }

    private static final class FakeOptionsProvider implements OptionsProvider {
        @Override public List<java.time.LocalDate> getExpiries(String underlying, ExchangeSegment exchangeSegment) { return List.of(); }
        @Override public List<Instrument> getOptionContracts(String underlying, ExchangeSegment exchangeSegment, java.time.LocalDate expiry) { return List.of(); }
        @Override public com.tradej.core.domain.model.OptionChainSnapshot getOptionChain(String underlying, ExchangeSegment exchangeSegment, java.time.LocalDate expiry) { return null; }
        @Override public com.tradej.core.domain.model.OptionQuote getGreeks(InstrumentKey instrumentKey) { return null; }
        @Override public com.tradej.core.domain.model.RollingOptionSeries getExpiredOptionHistory(com.tradej.core.domain.model.RollingOptionHistoryRequest request) { return null; }
        @Override public long selectStrikePaisa(String underlying, ExchangeSegment exchangeSegment, long spotPricePaisa, com.tradej.core.domain.value.OptionType optionType, com.tradej.core.domain.value.StrikeSelectionKind selectionKind, int depth) { return 0L; }
    }
}
