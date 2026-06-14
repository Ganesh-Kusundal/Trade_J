package com.tradej.gateway.bridge;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.GammaExposureComputed;
import com.tradej.core.domain.event.GreeksComputed;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.ScanResultsPublished;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.StrategyMetricsSnapshot;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.gateway.protocol.GatewayTopic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the {@code (event class) → (GatewayTopic)}
 * mapping consumed by {@link GatewayEventBridge#buildSerializerMap()}.
 *
 * <p>The table is hand-maintained (it's the one place that needs to be
 * updated when a new event is bridged). The companion test
 * {@code BridgeTopicsTest} asserts that {@code GatewayEventBridge}
 * does not register a serializer for an event class that is missing
 * from this table, and vice versa — a forgotten entry fails the
 * test instead of silently dropping events.
 *
 * <p>Why not annotations? The {@code core} module is dependency-free
 * and cannot import a {@code gateway} annotation. A hand-maintained
 * table at the gateway boundary is the simplest correct contract.
 */
public final class BridgeTopics {

    private BridgeTopics() {}

    /**
     * The full event-class → topic map. {@link GatewayTopicRouter}
     * consumers should treat this as immutable.
     */
    public static final Map<Class<? extends DomainEvent>, GatewayTopic> MAP = buildMap();

    private static Map<Class<? extends DomainEvent>, GatewayTopic> buildMap() {
        Map<Class<? extends DomainEvent>, GatewayTopic> m = new LinkedHashMap<>();
        m.put(MarketTickEvent.class,        GatewayTopic.MARKET_TICK);
        m.put(DepthUpdateEvent.class,       GatewayTopic.MARKET_DEPTH);
        m.put(CandleDeveloping.class,       GatewayTopic.CANDLE_DEVELOPING);
        m.put(CandleClosed.class,           GatewayTopic.CANDLE_CLOSED);
        m.put(OrderAccepted.class,          GatewayTopic.ORDER_UPDATE);
        m.put(OrderRejected.class,          GatewayTopic.ORDER_UPDATE);
        m.put(OrderFilled.class,            GatewayTopic.ORDER_UPDATE);
        m.put(TradeOpened.class,            GatewayTopic.POSITION_UPDATE);
        m.put(TradeClosed.class,            GatewayTopic.POSITION_UPDATE);
        m.put(SignalGenerated.class,        GatewayTopic.STRATEGY_SIGNAL);
        m.put(ReplayTimeChangedEvent.class, GatewayTopic.REPLAY_CONTROL);
        m.put(PnlUpdatedEvent.class,        GatewayTopic.PNL_UPDATE);
        m.put(ScanResultsPublished.class,   GatewayTopic.SCAN_COMPLETED);
        m.put(OptionChainUpdated.class,     GatewayTopic.OI_UPDATE);
        m.put(GreeksComputed.class,         GatewayTopic.GREEKS_UPDATE);
        m.put(MaxPainComputed.class,        GatewayTopic.MAX_PAIN_UPDATE);
        m.put(GammaExposureComputed.class,  GatewayTopic.GAMMA_EXPOSURE_UPDATE);
        m.put(StrategyMetricsSnapshot.class, GatewayTopic.STRATEGY_METRICS);
        return Map.copyOf(m);
    }

    /** Defensive set view of the topic values for assertions. */
    public static final Set<GatewayTopic> TOPICS = Set.copyOf(MAP.values());
}
