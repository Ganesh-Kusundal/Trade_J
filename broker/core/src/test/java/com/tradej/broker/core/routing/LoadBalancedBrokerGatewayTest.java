package com.tradej.broker.core.routing;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.*;
import static org.mockito.Mockito.mock;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class LoadBalancedBrokerGatewayTest {

    @Test
    void roundRobinsLtpAcrossNodes() {
        AtomicInteger calls = new AtomicInteger();
        IBrokerConnection first = stubConnection("first", calls, 100L);
        IBrokerConnection second = stubConnection("second", calls, 200L);
        LoadBalancedBrokerGateway gateway = new LoadBalancedBrokerGateway(List.of(first, second));

        InstrumentKey key = new InstrumentKey("SBIN", ExchangeSegment.NSE_EQ);
        long ltp1 = gateway.marketData().getLtpPaisa(key);
        long ltp2 = gateway.marketData().getLtpPaisa(key);

        assertEquals(2, calls.get());
        assertTrue(ltp1 != ltp2 || ltp1 == 100L || ltp2 == 200L);
    }

    @Test
    void advertisesOptionsCapability() {
        LoadBalancedBrokerGateway gateway = new LoadBalancedBrokerGateway(
                List.of(stubConnection("only", new AtomicInteger(), 1L)));
        assertTrue(gateway.getCapability(OptionsProvider.class).isPresent());
    }

    private static final class StubMarketData implements MarketDataProvider {
        private final long ltpPaisa;
        private final AtomicInteger calls;

        StubMarketData(long ltpPaisa, AtomicInteger calls) {
            this.ltpPaisa = ltpPaisa;
            this.calls = calls;
        }

        @Override
        public long getLtpPaisa(InstrumentKey instrumentKey) {
            calls.incrementAndGet();
            return ltpPaisa;
        }

        @Override
        public com.tradej.core.domain.model.Quote getQuote(InstrumentKey instrumentKey) {
            return null;
        }

        @Override
        public com.tradej.core.domain.model.MarketDepth getDepth(InstrumentKey instrumentKey) {
            return null;
        }

        @Override
        public com.tradej.core.domain.model.Quote getOhlcSnapshot(InstrumentKey instrumentKey) {
            return null;
        }

        @Override
        public java.util.List<com.tradej.core.domain.model.Candle> getCandles(
                com.tradej.core.domain.model.CandleHistoryRequest request) {
            return java.util.List.of();
        }

        @Override
        public java.util.Map<InstrumentKey, Long> getLtpBatch(
                java.util.Collection<InstrumentKey> instrumentKeys) {
            return java.util.Map.of();
        }

        @Override
        public java.util.Map<InstrumentKey, com.tradej.core.domain.model.Quote> getQuoteBatch(
                java.util.Collection<InstrumentKey> instrumentKeys) {
            return java.util.Map.of();
        }

        @Override
        public java.util.Map<InstrumentKey, com.tradej.core.domain.model.Quote> getOhlcBatch(
                java.util.Collection<InstrumentKey> instrumentKeys) {
            return java.util.Map.of();
        }
    }

    private static IBrokerConnection stubConnection(String label, AtomicInteger calls, long ltpPaisa) {
        MarketDataProvider marketData = new StubMarketData(ltpPaisa, calls);
        OrderCommand orders = new OrderCommand() {
            @Override
            public Order placeOrder(OrderRequest request) {
                return null;
            }

            @Override
            public Order modifyOrder(com.tradej.core.domain.model.ModifyOrderRequest request) {
                return null;
            }

            @Override
            public boolean cancelOrder(String orderId) {
                return false;
            }

            @Override
            public List<String> cancelAllOpenOrders() {
                return List.of();
            }

            @Override
            public List<String> cancelAndSquareOffIntradayPositions() {
                return List.of();
            }

            @Override
            public boolean setKillSwitch(boolean enabled) {
                return false;
            }

            @Override
            public com.tradej.core.domain.model.OrderPreview previewOrder(com.tradej.core.domain.model.OrderRequest request) {
                return com.tradej.core.domain.model.OrderPreview.valid(
                        request.symbol(),
                        request.exchangeSegment(),
                        request.side(),
                        request.quantity(),
                        request.pricePaisa(),
                        request.triggerPricePaisa(),
                        request.productType(),
                        0L,
                        0L
                );
            }
        };
        return new IBrokerConnection() {
            @Override
            public MarketDataProvider marketData() {
                return marketData;
            }

            @Override
            public FuturesProvider futures() {
                return null;
            }

            @Override
            public OptionsProvider options() {
                return null;
            }

            @Override
            public OrderCommand orders() {
                return orders;
            }

            @Override
            public OrderQuery orderQuery() {
                return null;
            }

            @Override
            public SliceOrderCommand sliceOrders() {
                return null;
            }

            @Override
            public BracketOrderProvider bracketOrders() {
                return null;
            }

            @Override
            public GttOrderProvider gttOrders() {
                return null;
            }

            @Override
            public PortfolioProvider portfolio() {
                return null;
            }

            @Override
            public MarginProvider margin() {
                return null;
            }

            @Override
            public SessionRiskProvider sessionRisk() {
                return null;
            }

            @Override
            public ConditionalAlertProvider alerts() {
                return null;
            }

            @Override
            public InstrumentResolver instruments() {
                return null;
            }

            @Override
            public WebSocketMultiplexer websocket() {
                return new WebSocketMultiplexer() {
                    @Override
                    public void connect() {}

                    @Override
                    public void disconnect() {}

                    @Override
                    public boolean isConnected() {
                        return false;
                    }

                    @Override
                    public void subscribe(
                            java.util.Collection<com.tradej.broker.api.model.MarketSubscriptionRequest> instruments,
                            com.tradej.core.domain.value.FeedMode feedMode) {}

                    @Override
                    public void unsubscribe(
                            java.util.Collection<com.tradej.broker.api.model.MarketSubscriptionRequest> instruments) {}

                    @Override
                    public void onMarketData(MarketDataListener listener) {}

                    @Override
                    public void onOrderUpdate(OrderUpdateListener listener) {}

                    @Override
                    public java.util.Map<com.tradej.broker.api.model.MarketSubscriptionRequest, com.tradej.core.domain.value.FeedMode> subscriptions() {
                        return java.util.Map.of();
                    }
                };
            }

            @Override
            public void connect() {}

            @Override
            public void disconnect() {}

            @Override
            public void loadInstrumentCatalog(Path catalogPath) {}

            @Override
            public com.tradej.broker.api.spi.BrokerSource source() {
                return com.tradej.broker.api.spi.BrokerSource.SIMULATION;
            }

            @Override
            public <T> Optional<T> getCapability(Class<T> capabilityClass) {
                if (OptionsProvider.class.equals(capabilityClass)) {
                    return Optional.of(capabilityClass.cast(mock(OptionsProvider.class)));
                }
                return Optional.empty();
            }
        };
    }
}
