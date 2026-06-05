package com.tradej.broker.dhan.depth;

import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.PriceMath;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Dhan 20-level depth binary packets from {@code wss://depth-api-feed.dhan.co/twentydepth}.
 *
 * @see <a href="https://dhanhq.co/docs/v2/full-market-depth/">Dhan Full Market Depth</a>
 */
public final class DhanTwentyDepthBinaryParser {
    public static final int HEADER_BYTES = 12;
    public static final int LEVEL_BYTES = 16;
    public static final int LEVEL_COUNT = 20;
    public static final int SIDE_BYTES = HEADER_BYTES + (LEVEL_BYTES * LEVEL_COUNT);
    public static final int BID_FEED_CODE = 41;
    public static final int ASK_FEED_CODE = 51;

    public record DepthSidePacket(
            int feedCode,
            ExchangeSegment segment,
            String securityId,
            List<DepthLevel> levels
    ) {
    }

    private DhanTwentyDepthBinaryParser() {
    }

    public static List<DepthSidePacket> parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return List.of();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        List<DepthSidePacket> packets = new ArrayList<>();
        while (buffer.remaining() >= HEADER_BYTES) {
            int packetStart = buffer.position();
            int messageLength = Short.toUnsignedInt(buffer.getShort());
            if (messageLength < HEADER_BYTES || buffer.remaining() < messageLength - 2) {
                break;
            }
            buffer.position(packetStart);
            int sideBytes = Math.min(messageLength + 2, buffer.remaining());
            if (sideBytes < SIDE_BYTES) {
                break;
            }
            packets.add(parseSidePacket(buffer));
            if (buffer.position() < packetStart + sideBytes) {
                buffer.position(packetStart + sideBytes);
            }
        }
        return List.copyOf(packets);
    }

    private static DepthSidePacket parseSidePacket(ByteBuffer buffer) {
        buffer.getShort(); // message length
        int feedCode = Byte.toUnsignedInt(buffer.get());
        ExchangeSegment segment = DhanExchangeSegmentCodes.fromWireCode(Byte.toUnsignedInt(buffer.get()));
        String securityId = Integer.toString(buffer.getInt());
        buffer.getInt(); // sequence — ignored
        List<DepthLevel> levels = new ArrayList<>(LEVEL_COUNT);
        for (int i = 0; i < LEVEL_COUNT; i++) {
            double price = buffer.getDouble();
            long quantity = Integer.toUnsignedLong(buffer.getInt());
            int orders = buffer.getInt();
            if (price <= 0.0d || quantity <= 0L) {
                continue;
            }
            levels.add(new DepthLevel(PriceMath.toPaisa(Double.toString(price)), quantity, orders));
        }
        return new DepthSidePacket(feedCode, segment, securityId, List.copyOf(levels));
    }
}
