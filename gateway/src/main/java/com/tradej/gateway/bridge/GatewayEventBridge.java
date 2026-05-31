package com.tradej.gateway.bridge;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DepthUpdateEvent;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.OrderRejected;
import com.tradej.core.domain.event.PnlUpdatedEvent;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.TickReceived;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.port.EventBus;
import com.tradej.gateway.protocol.GatewayTopic;
import com.tradej.gateway.router.GatewayTopicRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges domain events from the event bus to the gateway WebSocket topic router.
 * Each domain event type is mapped to a {@link GatewayTopic} and serialized to JSON.
 */
public final class GatewayEventBridge {

    private static final Logger log = LoggerFactory.getLogger(GatewayEventBridge.class);

    private final GatewayTopicRouter router;
    private final ObjectMapper objectMapper;

    public GatewayEventBridge(GatewayTopicRouter router, ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
    }

    /**
     * Register this bridge as a subscriber to all domain events on the event bus.
     */
    public void register(EventBus eventBus) {
        eventBus.subscribe(DomainEvent.class, this::onDomainEvent);
    }

    void onDomainEvent(DomainEvent event) {
        try {
            Objects.requireNonNull(event);
            switch (event) {
                case MarketTickEvent tick ->
                        router.publish(GatewayTopic.MARKET_TICK, writeJson(marketTickPayload(tick)));
                case TickReceived tick ->
                        router.publish(GatewayTopic.MARKET_TICK, writeJson(tickPayload(tick)));
                case DepthUpdateEvent depth ->
                        router.publish(GatewayTopic.MARKET_DEPTH, writeJson(depthPayload(depth)));
                case CandleDeveloping dev ->
                        router.publish(GatewayTopic.CANDLE_DEVELOPING, writeJson(candlePayload(dev.candle())));
                case CandleClosed closed ->
                        router.publish(GatewayTopic.CANDLE_CLOSED, writeJson(candlePayload(closed.candle())));
                case OrderAccepted accepted ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(accepted)));
                case OrderRejected rejected ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(rejected)));
                case OrderFilled filled ->
                        router.publish(GatewayTopic.ORDER_UPDATE, writeJson(orderPayload(filled)));
                case TradeOpened opened ->
                        router.publish(GatewayTopic.POSITION_UPDATE, writeJson(positionPayload(
                                opened.symbol(), opened.size(), opened.entryPricePaisa(), "OPEN")));
                case TradeClosed closed ->
                        router.publish(GatewayTopic.POSITION_UPDATE, writeJson(positionPayload(
                                closed.symbol(), 0L, 0L, "CLOSED")));
                case SignalGenerated signal ->
                        router.publish(GatewayTopic.STRATEGY_SIGNAL, writeJson(signalPayload(signal)));
                case ReplayTimeChangedEvent replay ->
                        router.publish(GatewayTopic.REPLAY_CONTROL, writeJson(replayPayload(replay)));
                case PnlUpdatedEvent pnl ->
                        router.publish(GatewayTopic.PNL_UPDATE, writeJson(pnlPayload(pnl)));
                default -> { }
            }
        } catch (Exception e) {
            log.warn("Gateway bridge failed for {}: {}", event.getClass().getSimpleName(), e.getMessage());
        }
    }

    private byte[] writeJson(Map<String, Object> payload) throws com.fasterxml.jackson.core.JsonProcessingException {
        return objectMapper.writeValueAsBytes(payload);
    }

    // ── Payload builders ──

    private static Map<String, Object> marketTickPayload(MarketTickEvent tick) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", tick.symbol());
        map.put("ltpPaisa", tick.ltpPaisa());
        map.put("lastTradeQuantity", tick.lastTradeQuantity());
        map.put("cumulativeVolume", tick.cumulativeVolume());
        map.put("exchangeTimestampMs", tick.exchangeTimestampEpochMs());
        map.put("segment", tick.segment().name());
        map.put("feedMode", tick.feedMode().name());
        map.put("sequence", tick.sequenceId());
        return map;
    }

    private static Map<String, Object> tickPayload(TickReceived tick) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", tick.symbol());
        map.put("ltpPaisa", tick.ltpPaisa());
        map.put("lastTradeQuantity", tick.lastTradeQuantity());
        map.put("cumulativeVolume", tick.cumulativeVolume());
        map.put("exchangeTimestampMs", tick.exchangeTimestampMs());
        map.put("sequence", tick.sequenceId());
        return map;
    }

    private static Map<String, Object> depthPayload(DepthUpdateEvent depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", depth.symbol());
        map.put("segment", depth.segment().name());
        map.put("levels", depth.levels());
        map.put("exchangeTimestampMs", depth.exchangeTimestampMs());
        map.put("bids", depth.bids().stream().map(GatewayEventBridge::depthLevelToMap).toList());
        map.put("asks", depth.asks().stream().map(GatewayEventBridge::depthLevelToMap).toList());
        map.put("sequence", depth.sequenceId());
        return map;
    }

    private static Map<String, Object> depthLevelToMap(DepthLevel level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pricePaisa", level.pricePaisa());
        m.put("quantity", level.quantity());
        m.put("orders", level.orderCount());
        return m;
    }

    private static Map<String, Object> candlePayload(Candle candle) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", candle.symbol());
        map.put("interval", candle.interval());
        map.put("startTimeMs", candle.startTimeMs());
        map.put("endTimeMs", candle.endTimeMs());
        map.put("openPaisa", candle.openPaisa());
        map.put("highPaisa", candle.highPaisa());
        map.put("lowPaisa", candle.lowPaisa());
        map.put("closePaisa", candle.closePaisa());
        map.put("volume", candle.volume());
        return map;
    }

    private static Map<String, Object> orderPayload(DomainEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();
        String type = event.getClass().getSimpleName();
        map.put("type", type);
        if (event instanceof OrderAccepted a) {
            map.put("orderId", a.order().orderId());
            map.put("symbol", a.order().symbol());
            map.put("status", a.order().status().name());
            map.put("quantity", a.order().quantity());
            map.put("filledQuantity", a.order().filledQuantity());
            map.put("pricePaisa", a.order().pricePaisa());
            map.put("side", a.order().side().name());
        } else if (event instanceof OrderRejected r) {
            map.put("orderId", r.order().orderId());
            map.put("symbol", r.order().symbol());
            map.put("status", r.order().status().name());
            map.put("reason", r.reason());
        } else if (event instanceof OrderFilled f) {
            map.put("orderId", f.order().orderId());
            map.put("symbol", f.order().symbol());
            map.put("status", f.order().status().name());
            map.put("filledQuantity", f.order().filledQuantity());
            // TODO: serialize full fills list; currently only reports first fill
            // to preserve backward compatibility with the original single-fill schema.
            map.put("fillCount", f.fills().size());
            if (!f.fills().isEmpty()) {
                var firstFill = f.fills().getFirst();
                map.put("pricePaisa", firstFill.pricePaisa());
                map.put("tradeId", firstFill.tradeId());
            }
        }
        return map;
    }

    private static Map<String, Object> positionPayload(String symbol, long size, long entryPricePaisa, String action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", symbol);
        map.put("size", size);
        map.put("entryPricePaisa", entryPricePaisa);
        map.put("action", action);
        return map;
    }

    private static Map<String, Object> signalPayload(SignalGenerated signal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("signalId", signal.signalId());
        map.put("symbol", signal.symbol());
        map.put("side", signal.side().name());
        map.put("setup", signal.setup());
        return map;
    }

    private static Map<String, Object> replayPayload(ReplayTimeChangedEvent replay) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "REPLAY_TIME_CHANGED");
        map.put("currentTimeMs", replay.currentTimeMs());
        map.put("replaySpeedNanos", replay.replaySpeedNanos());
        return map;
    }

    private static Map<String, Object> pnlPayload(PnlUpdatedEvent pnl) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("realizedPnlPaisa", pnl.realizedPnlPaisa());
        map.put("unrealizedPnlPaisa", pnl.unrealizedPnlPaisa());
        map.put("netExposurePaisa", pnl.netExposurePaisa());
        return map;
    }
}
