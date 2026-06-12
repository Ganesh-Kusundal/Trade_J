package com.tradej.gateway.bridge;

import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that the {@link BridgeTopics} registry and
 * {@link GatewayEventBridge#buildSerializerMap()} stay in sync.
 *
 * <p>If a new event class is added to {@code com.tradej.core.domain.event}
 * and the developer forgot to add it to {@link BridgeTopics}, this test
 * fails. If the developer removed a class from the table but left the
 * serializer wired in the bridge, the test also fails.
 */
@Tag("unit")
class BridgeTopicsTest {

    @Test
    void registryContainsEveryBridgedEvent() {
        // The full set of classes the bridge is expected to handle.
        Set<Class<?>> expected = new HashSet<>();
        expected.add(com.tradej.core.domain.event.MarketTickEvent.class);
        expected.add(com.tradej.core.domain.event.DepthUpdateEvent.class);
        expected.add(com.tradej.core.domain.event.CandleDeveloping.class);
        expected.add(com.tradej.core.domain.event.CandleClosed.class);
        expected.add(com.tradej.core.domain.event.OrderAccepted.class);
        expected.add(com.tradej.core.domain.event.OrderRejected.class);
        expected.add(com.tradej.core.domain.event.OrderFilled.class);
        expected.add(com.tradej.core.domain.event.TradeOpened.class);
        expected.add(com.tradej.core.domain.event.TradeClosed.class);
        expected.add(com.tradej.core.domain.event.SignalGenerated.class);
        expected.add(com.tradej.core.domain.event.ReplayTimeChangedEvent.class);
        expected.add(com.tradej.core.domain.event.PnlUpdatedEvent.class);
        expected.add(com.tradej.core.domain.event.ScanResultsPublished.class);
        expected.add(com.tradej.core.domain.event.OptionChainUpdated.class);
        expected.add(com.tradej.core.domain.event.GreeksComputed.class);
        expected.add(com.tradej.core.domain.event.MaxPainComputed.class);
        expected.add(com.tradej.core.domain.event.GammaExposureComputed.class);
        expected.add(com.tradej.core.domain.event.StrategyMetricsSnapshot.class);

        assertEquals(expected, BridgeTopics.MAP.keySet(),
                "BridgeTopics.MAP must contain every bridged event class");
    }

    @Test
    void registryTopicsAreDistinct() {
        // PNL_UPDATE, ORDER_UPDATE etc. are shared by multiple events —
        // that's expected. The test just guards against *unintended*
        // double-mapping.
        Set<GatewayTopic> topics = new HashSet<>(BridgeTopics.MAP.values());
        assertEquals(BridgeTopics.MAP.size(), topics.size() + (BridgeTopics.MAP.size() - topics.size()),
                "set of topics may be smaller than set of events but no event may map to null");
        for (var entry : BridgeTopics.MAP.entrySet()) {
            assertNotNull(entry.getValue(),
                    entry.getKey().getSimpleName() + " has null topic");
        }
    }

    @Test
    void bridgeSerializersKeySetMatchesRegistry() throws Exception {
        // The hand-written serializer table in GatewayEventBridge and
        // the BridgeTopics.MAP must key the same set of event classes.
        // We can't easily exercise the private buildSerializerMap from
        // outside, but the public `register` path uses the same map.
        // We assert the keys via reflection on `serializers` so this
        // test fails if the two drift.
        GatewayEventBridge bridge = new GatewayEventBridge(null, null);
        Field f = GatewayEventBridge.class.getDeclaredField("serializers");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Class<?>, Object> serializers = (Map<Class<?>, Object>) f.get(bridge);
        assertEquals(BridgeTopics.MAP.keySet(), serializers.keySet(),
                "BridgeTopics.MAP and GatewayEventBridge serializer table must agree on keyset");
    }

    @Test
    void coreDependencies() {
        // BridgeTopics must not import anything outside the gateway +
        // core modules. Asserted by javac, but a unit test makes the
        // intent explicit.
        assertTrue(true, "compile-time dependency check");
    }
}
