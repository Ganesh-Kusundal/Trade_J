package com.tradej.gateway.protocol;

/**
 * Gateway topic identifiers used for WebSocket subscription and routing.
 * Each topic corresponds to a specific event stream that WebSocket clients
 * can subscribe to via the gateway protocol.
 */
public enum GatewayTopic {
    MARKET_TICK(0, 1),
    MARKET_DEPTH(1, 2),
    CANDLE_DEVELOPING(2, 3),
    CANDLE_CLOSED(3, 4),
    ORDER_UPDATE(4, 5),
    POSITION_UPDATE(5, 6),
    STRATEGY_SIGNAL(6, 7),
    PNL_UPDATE(7, 8),
    REPLAY_CONTROL(8, 9),
    PIPELINE_HEALTH(9, 10),
    SCAN_COMPLETED(10, 1),
    DEPTH_IMBALANCE(11, 1),
    HEATMAP_CHUNK(12, 1),
    ICEBERG_ALERT(13, 1),
    ABSORPTION_ALERT(14, 1),
    SR_LEVELS_UPDATE(15, 1),
    ORDER_BOOK_SNAPSHOT(16, 1),
    MAX_PAIN_UPDATE(17, 1),
    GREEKS_UPDATE(18, 1),
    OI_UPDATE(19, 1),
    GAMMA_EXPOSURE_UPDATE(20, 1),
    STRATEGY_METRICS(21, 1);

    private final int wireId;
    private final int version;

    GatewayTopic(int wireId, int version) {
        this.wireId = wireId;
        this.version = version;
    }

    public int wireId() {
        return wireId;
    }

    public int version() {
        return version;
    }

    public static GatewayTopic fromWireId(int wireId) {
        for (GatewayTopic topic : values()) {
            if (topic.wireId == wireId) {
                return topic;
            }
        }
        throw new IllegalArgumentException("Unknown GatewayTopic wireId: " + wireId);
    }
}
