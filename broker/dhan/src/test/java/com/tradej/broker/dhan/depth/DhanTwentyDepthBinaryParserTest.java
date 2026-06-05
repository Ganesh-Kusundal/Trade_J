package com.tradej.broker.dhan.depth;

import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("unit")
class DhanTwentyDepthBinaryParserTest {

    @Test
    void parsesBidAndAskSidePackets() {
        byte[] payload = concat(
                sidePacket(DhanTwentyDepthBinaryParser.BID_FEED_CODE, 1, 11536, 100.25d, 10L, 2),
                sidePacket(DhanTwentyDepthBinaryParser.ASK_FEED_CODE, 1, 11536, 100.50d, 12L, 3)
        );

        List<DhanTwentyDepthBinaryParser.DepthSidePacket> packets = DhanTwentyDepthBinaryParser.parse(payload);

        assertEquals(2, packets.size());
        assertEquals(DhanTwentyDepthBinaryParser.BID_FEED_CODE, packets.get(0).feedCode());
        assertEquals(ExchangeSegment.NSE_EQ, packets.get(0).segment());
        assertEquals("11536", packets.get(0).securityId());
        assertFalse(packets.get(0).levels().isEmpty());
        assertEquals(10025L, packets.get(0).levels().getFirst().pricePaisa());
        assertEquals(DhanTwentyDepthBinaryParser.ASK_FEED_CODE, packets.get(1).feedCode());
        assertEquals(10050L, packets.get(1).levels().getFirst().pricePaisa());
    }

    private static byte[] sidePacket(
            int feedCode,
            int segmentCode,
            int securityId,
            double price,
            long quantity,
            int orders
    ) {
        ByteBuffer buffer = ByteBuffer.allocate(DhanTwentyDepthBinaryParser.SIDE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) (DhanTwentyDepthBinaryParser.SIDE_BYTES - 2));
        buffer.put((byte) feedCode);
        buffer.put((byte) segmentCode);
        buffer.putInt(securityId);
        buffer.putInt(0); // sequence
        buffer.putDouble(price);
        buffer.putInt(Math.toIntExact(quantity));
        buffer.putInt(orders);
        for (int i = 1; i < DhanTwentyDepthBinaryParser.LEVEL_COUNT; i++) {
            buffer.putDouble(0.0d);
            buffer.putInt(0);
            buffer.putInt(0);
        }
        return buffer.array();
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] merged = new byte[left.length + right.length];
        System.arraycopy(left, 0, merged, 0, left.length);
        System.arraycopy(right, 0, merged, left.length, right.length);
        return merged;
    }
}
