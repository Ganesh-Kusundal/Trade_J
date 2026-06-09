package com.tradej.broker.dhan.websocket;

import com.tradej.broker.api.model.MarketSubscriptionRequest;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.broker.dhan.depth.DhanTwentyDepthWebSocketClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class DhanWebSocketSubscriptionManagerTest {

    @Mock private DhanInstrumentResolver resolver;
    @Mock private DhanTwentyDepthWebSocketClient depthClient;

    private DhanWebSocketSubscriptionManager manager;
    private MarketSubscriptionRequest request1;
    private MarketSubscriptionRequest request2;
    private DhanInstrumentDefinition def1;
    private DhanInstrumentDefinition def2;

    @BeforeEach
    void setUp() {
        manager = new DhanWebSocketSubscriptionManager(resolver);

        request1 = new MarketSubscriptionRequest("RELIANCE", ExchangeSegment.NSE_EQ);
        request2 = new MarketSubscriptionRequest("TCS", ExchangeSegment.NSE_EQ);
        def1 = new DhanInstrumentDefinition(
                "RELIANCE", "RELIANCE", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "3045", "EQ", "RELIANCE", null, null, null, 1L, 5L, "3045"
        );
        def2 = new DhanInstrumentDefinition(
                "TCS", "TCS", Exchange.NSE, ExchangeSegment.NSE_EQ,
                "11536", "EQ", "TCS", null, null, null, 1L, 5L, "11536"
        );
    }

    // ── Initial state ─────────────────────────────────────────────────

    @Nested
    class InitialStateTests {

        @Test
        void subscriptionsAreEmptyAfterConstruction() {
            assertTrue(manager.isEmpty());
            assertEquals(0, manager.size());
            assertTrue(manager.snapshot().isEmpty());
        }

        @Test
        void depthClientIsNullAfterConstruction() {
            assertNull(manager.getDepthClient());
        }

        @Test
        void depthIsNotConnectedWhenClientIsNull() {
            assertFalse(manager.isDepthConnected());
        }

        @Test
        void noDepthSubscriptionsInitially() {
            assertFalse(manager.hasDepthSubscriptions());
        }
    }

    // ── Depth client lifecycle ────────────────────────────────────────

    @Nested
    class DepthClientLifecycleTests {

        @Test
        void setDepthClientStoresReference() {
            manager.setDepthClient(depthClient);
            assertSame(depthClient, manager.getDepthClient());
        }

        @Test
        void setDepthClientReplacesPreviousReference() {
            var client2 = mock(DhanTwentyDepthWebSocketClient.class);
            manager.setDepthClient(depthClient);
            manager.setDepthClient(client2);

            assertSame(client2, manager.getDepthClient());
            assertNotSame(depthClient, manager.getDepthClient());
        }

        @Test
        void isDepthConnectedReturnsFalseWhenClientNotConnected() {
            when(depthClient.isConnected()).thenReturn(false);
            manager.setDepthClient(depthClient);

            assertFalse(manager.isDepthConnected());
        }

        @Test
        void isDepthConnectedReturnsTrueWhenClientConnected() {
            when(depthClient.isConnected()).thenReturn(true);
            manager.setDepthClient(depthClient);

            assertTrue(manager.isDepthConnected());
        }

        @Test
        void isDepthConnectedReturnsFalseAfterClearingClient() {
            manager.setDepthClient(depthClient);

            // Setting null clears the reference
            manager.setDepthClient(null);
            assertNull(manager.getDepthClient());
            assertFalse(manager.isDepthConnected());
        }
    }

    // ── Subscription mutations ────────────────────────────────────────

    @Nested
    class SubscriptionMutationTests {

        @Test
        void addStoresSubscription() {
            manager.add(request1, FeedMode.QUOTE);

            assertEquals(1, manager.size());
            assertEquals(FeedMode.QUOTE, manager.get(request1));
        }

        @Test
        void addOverwritesExistingSubscription() {
            manager.add(request1, FeedMode.TICKER);
            manager.add(request1, FeedMode.FULL);

            assertEquals(1, manager.size());
            assertEquals(FeedMode.FULL, manager.get(request1));
        }

        @Test
        void addAllStoresMultipleSubscriptions() {
            manager.addAll(List.of(request1, request2), FeedMode.QUOTE);

            assertEquals(2, manager.size());
            assertEquals(FeedMode.QUOTE, manager.get(request1));
            assertEquals(FeedMode.QUOTE, manager.get(request2));
        }

        @Test
        void removeAllRemovesSpecifiedSubscriptions() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.QUOTE);
            assertEquals(2, manager.size());

            manager.removeAll(List.of(request1));

            assertEquals(1, manager.size());
            assertNull(manager.get(request1));
            assertEquals(FeedMode.QUOTE, manager.get(request2));
        }

        @Test
        void removeAllWithEmptyListDoesNothing() {
            manager.add(request1, FeedMode.QUOTE);

            manager.removeAll(List.of());

            assertEquals(1, manager.size());
        }

        @Test
        void removeAllWithUnknownRequestsDoesNothing() {
            manager.add(request1, FeedMode.QUOTE);

            manager.removeAll(List.of(request2));

            assertEquals(1, manager.size());
            assertNotNull(manager.get(request1));
        }

        @Test
        void clearRemovesAllSubscriptions() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.TICKER);
            assertEquals(2, manager.size());

            manager.clear();

            assertTrue(manager.isEmpty());
            assertEquals(0, manager.size());
        }

        @Test
        void clearOnEmptyManagerIsSafe() {
            manager.clear();
            assertTrue(manager.isEmpty());
        }
    }

    // ── State queries ─────────────────────────────────────────────────

    @Nested
    class StateQueryTests {

        @Test
        void sizeReflectsSubscriptionCount() {
            assertEquals(0, manager.size());
            manager.add(request1, FeedMode.QUOTE);
            assertEquals(1, manager.size());
            manager.add(request2, FeedMode.TICKER);
            assertEquals(2, manager.size());
            manager.clear();
            assertEquals(0, manager.size());
        }

        @Test
        void isEmptyReflectsSubscriptionState() {
            assertTrue(manager.isEmpty());
            manager.add(request1, FeedMode.QUOTE);
            assertFalse(manager.isEmpty());
            manager.clear();
            assertTrue(manager.isEmpty());
        }

        @Test
        void hasDepthSubscriptionsIsFalseForNonDepthModes() {
            manager.add(request1, FeedMode.TICKER);
            manager.add(request2, FeedMode.QUOTE);

            assertFalse(manager.hasDepthSubscriptions());
        }

        @Test
        void hasDepthSubscriptionsIsTrueWhenDepthSubscribed() {
            manager.add(request1, FeedMode.DEPTH_20);

            assertTrue(manager.hasDepthSubscriptions());
        }

        @Test
        void hasDepthSubscriptionsIsFalseAfterDepthCleared() {
            manager.add(request1, FeedMode.DEPTH_20);
            manager.clear();

            assertFalse(manager.hasDepthSubscriptions());
        }
    }

    // ── Snapshot ──────────────────────────────────────────────────────

    @Nested
    class SnapshotTests {

        @Test
        void snapshotReturnsAllSubscriptions() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.TICKER);

            Map<MarketSubscriptionRequest, FeedMode> snap = manager.snapshot();

            assertEquals(2, snap.size());
            assertEquals(FeedMode.QUOTE, snap.get(request1));
            assertEquals(FeedMode.TICKER, snap.get(request2));
        }

        @Test
        void snapshotReturnsImmutableMap() {
            manager.add(request1, FeedMode.QUOTE);

            Map<MarketSubscriptionRequest, FeedMode> snap = manager.snapshot();

            assertThrows(UnsupportedOperationException.class, () -> snap.put(request2, FeedMode.TICKER));
        }

        @Test
        void snapshotIsIndependentOfSubsequentMutations() {
            manager.add(request1, FeedMode.QUOTE);
            Map<MarketSubscriptionRequest, FeedMode> snap1 = manager.snapshot();

            manager.add(request2, FeedMode.TICKER);
            Map<MarketSubscriptionRequest, FeedMode> snap2 = manager.snapshot();

            assertEquals(1, snap1.size());
            assertEquals(2, snap2.size());
        }
    }

    // ── Grouping ──────────────────────────────────────────────────────

    @Nested
    class GroupingTests {

        @Test
        void groupedByModeReturnsEmptyForNoSubscriptions() {
            Map<FeedMode, List<MarketSubscriptionRequest>> grouped = manager.groupedByMode();

            assertTrue(grouped.isEmpty());
        }

        @Test
        void groupedByModeGroupsByFeedMode() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.TICKER);

            Map<FeedMode, List<MarketSubscriptionRequest>> grouped = manager.groupedByMode();

            assertEquals(2, grouped.size());
            assertEquals(List.of(request1), grouped.get(FeedMode.QUOTE));
            assertEquals(List.of(request2), grouped.get(FeedMode.TICKER));
        }

        @Test
        void groupedByModeCollectsMultipleRequestsForSameMode() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.QUOTE);

            Map<FeedMode, List<MarketSubscriptionRequest>> grouped = manager.groupedByMode();

            assertEquals(1, grouped.size());
            assertEquals(2, grouped.get(FeedMode.QUOTE).size());
            assertTrue(grouped.get(FeedMode.QUOTE).containsAll(List.of(request1, request2)));
        }
    }

    // ── Feed-key conversion ───────────────────────────────────────────

    @Nested
    class FeedKeyConversionTests {

        @Test
        void toFeedKeyResolvesDefinitionAndCreatesKey() {
            when(resolver.requireDhanDefinition(request1.symbol(), request1.exchangeSegment()))
                    .thenReturn(def1);

            DhanMarketFeedWebSocketClient.SubscriptionKey key = manager.toFeedKey(request1);

            assertEquals(def1.exchangeSegment(), key.exchangeSegment());
            assertEquals(def1.securityId(), key.securityId());
        }

        @Test
        void toFeedKeysConvertsMultipleRequests() {
            when(resolver.requireDhanDefinition(request1.symbol(), request1.exchangeSegment()))
                    .thenReturn(def1);
            when(resolver.requireDhanDefinition(request2.symbol(), request2.exchangeSegment()))
                    .thenReturn(def2);

            List<DhanMarketFeedWebSocketClient.SubscriptionKey> keys = manager.toFeedKeys(List.of(request1, request2));

            assertEquals(2, keys.size());
            assertEquals(def1.securityId(), keys.get(0).securityId());
            assertEquals(def2.securityId(), keys.get(1).securityId());
        }

        @Test
        void toFeedKeysReturnsEmptyForEmptyInput() {
            List<DhanMarketFeedWebSocketClient.SubscriptionKey> keys = manager.toFeedKeys(List.of());

            assertTrue(keys.isEmpty());
        }

        @Test
        void toFeedKeyPropagatesResolverError() {
            when(resolver.requireDhanDefinition(request1.symbol(), request1.exchangeSegment()))
                    .thenThrow(new IllegalArgumentException("Unknown symbol"));

            assertThrows(IllegalArgumentException.class, () -> manager.toFeedKey(request1));
        }
    }

    // ── Partitioning ──────────────────────────────────────────────────

    @Nested
    class PartitioningTests {

        @Test
        void partitionByDepthSeparatesDepthAndMarket() {
            manager.add(request1, FeedMode.DEPTH_20);
            manager.add(request2, FeedMode.QUOTE);

            List<MarketSubscriptionRequest> depthOut = new java.util.ArrayList<>();
            List<MarketSubscriptionRequest> marketOut = new java.util.ArrayList<>();
            manager.partitionByDepth(List.of(request1, request2), depthOut, marketOut);

            assertEquals(List.of(request1), depthOut);
            assertEquals(List.of(request2), marketOut);
        }

        @Test
        void partitionByDepthHandlesAllDepth() {
            manager.add(request1, FeedMode.DEPTH_20);
            manager.add(request2, FeedMode.DEPTH_20);

            List<MarketSubscriptionRequest> depthOut = new java.util.ArrayList<>();
            List<MarketSubscriptionRequest> marketOut = new java.util.ArrayList<>();
            manager.partitionByDepth(List.of(request1, request2), depthOut, marketOut);

            assertEquals(2, depthOut.size());
            assertTrue(marketOut.isEmpty());
        }

        @Test
        void partitionByDepthHandlesAllMarket() {
            manager.add(request1, FeedMode.QUOTE);
            manager.add(request2, FeedMode.TICKER);

            List<MarketSubscriptionRequest> depthOut = new java.util.ArrayList<>();
            List<MarketSubscriptionRequest> marketOut = new java.util.ArrayList<>();
            manager.partitionByDepth(List.of(request1, request2), depthOut, marketOut);

            assertTrue(depthOut.isEmpty());
            assertEquals(2, marketOut.size());
        }

        @Test
        void partitionByDepthHandlesEmptyInput() {
            List<MarketSubscriptionRequest> depthOut = new java.util.ArrayList<>();
            List<MarketSubscriptionRequest> marketOut = new java.util.ArrayList<>();
            manager.partitionByDepth(List.of(), depthOut, marketOut);

            assertTrue(depthOut.isEmpty());
            assertTrue(marketOut.isEmpty());
        }

        @Test
        void partitionByDepthHandlesUnsubscribedRequests() {
            // Request not in subscription map — should go to marketOut
            List<MarketSubscriptionRequest> depthOut = new java.util.ArrayList<>();
            List<MarketSubscriptionRequest> marketOut = new java.util.ArrayList<>();
            manager.partitionByDepth(List.of(request1), depthOut, marketOut);

            assertTrue(depthOut.isEmpty());
            assertEquals(List.of(request1), marketOut);
        }
    }
}
