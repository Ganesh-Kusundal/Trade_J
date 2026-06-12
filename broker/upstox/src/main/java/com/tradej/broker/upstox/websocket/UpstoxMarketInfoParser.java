package com.tradej.broker.upstox.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.event.StreamHealthChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * Parses the {@code market_info} JSON text message that Upstox V3 sends as
 * the very first frame on the market-data WebSocket.
 * <p>
 * Per the official V3 docs, the wire format is:
 * <pre>{@code
 * {
 *   "type": "market_info",
 *   "currentTs": "1732775008661",
 *   "marketInfo": {
 *     "segmentStatus": {
 *       "NSE_COM": "NORMAL_OPEN",
 *       "NSE_FO": "NORMAL_OPEN",
 *       "BSE_EQ": "NORMAL_OPEN",
 *       ...
 *     }
 *   }
 * }
 * }</pre>
 * <p>
 * The second tick (a snapshot) and subsequent ticks ({@code live_feed}) are
 * protobuf-encoded and handled by the multiplexer's
 * {@code MarketFeedProto.FeedResponse} parser; this class only handles
 * {@code market_info}.
 */
public final class UpstoxMarketInfoParser {

    private static final Logger log = LoggerFactory.getLogger(UpstoxMarketInfoParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventMetadataFactory metadataFactory;

    public UpstoxMarketInfoParser(EventMetadataFactory metadataFactory) {
        this.metadataFactory = Objects.requireNonNull(metadataFactory, "metadataFactory");
    }

    /**
     * Parses a JSON text message and returns a {@link StreamHealthChanged}
     * event summarising segment status, or {@code null} if the message is
     * not a {@code market_info} frame.
     */
    public StreamHealthChanged parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(text);
            if (root.isMissingNode() || root.isNull()) {
                return null;
            }
            String type = root.has("type") ? root.get("type").asText() : "";
            if (!"market_info".equals(type)) {
                return null;
            }
            JsonNode marketInfo = root.get("marketInfo");
            if (marketInfo == null) {
                return null;
            }
            JsonNode segmentStatus = marketInfo.get("segmentStatus");
            String statusSummary = summarise(segmentStatus);
            log.info("Upstox V3 market_info received: {}", statusSummary);
            return new StreamHealthChanged(
                    metadataFactory.root(),
                    "upstox",
                    "MARKET_INFO:" + statusSummary,
                    0);
        } catch (Exception e) {
            // Malformed message — log and skip.
            log.debug("Failed to parse Upstox market_info JSON: {}", e.getMessage());
            return null;
        }
    }

    private static String summarise(JsonNode segmentStatus) {
        if (segmentStatus == null || !segmentStatus.isObject()) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, JsonNode> entry : segmentStatus.properties()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue().asText());
            // Cap the summary to avoid log spam.
            if (sb.length() > 256) {
                sb.append(",...");
                break;
            }
        }
        return sb.toString();
    }
}
