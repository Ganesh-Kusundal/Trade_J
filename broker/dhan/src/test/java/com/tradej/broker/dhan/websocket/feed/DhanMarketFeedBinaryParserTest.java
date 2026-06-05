package com.tradej.broker.dhan.websocket.feed;

import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.core.domain.value.ExchangeSegment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class DhanMarketFeedBinaryParserTest {

    @Test
    void parsesTickerPacket() {
        byte[] payload = tickerPacket(ExchangeSegment.NSE_EQ, 1, 11536, 3500.25f, 1_700_000_000L);
        List<DhanMarketFeedPacket> packets = parse(payload);

        assertEquals(1, packets.size());
        DhanMarketFeedPacket.Ticker ticker = assertInstanceOf(DhanMarketFeedPacket.Ticker.class, packets.getFirst());
        assertEquals(ExchangeSegment.NSE_EQ, ticker.exchangeSegment());
        assertEquals("11536", ticker.securityId());
        assertEquals("3500.25", ticker.ltp());
        assertEquals(1_700_000_000L, ticker.ltt());
    }

    @Test
    void parsesIndexPacket() {
        byte[] payload = indexPacket(ExchangeSegment.IDX_I, 0, 13, 24500.50f);
        List<DhanMarketFeedPacket> packets = parse(payload);

        assertEquals(1, packets.size());
        DhanMarketFeedPacket.Index index = assertInstanceOf(DhanMarketFeedPacket.Index.class, packets.getFirst());
        assertEquals(ExchangeSegment.IDX_I, index.exchangeSegment());
        assertEquals("13", index.securityId());
        assertEquals("24500.50", index.indexValue());
    }

    @Test
    void disconnectPacketSurfacesError() {
        byte[] payload = disconnectPacket();
        AtomicReference<Throwable> error = new AtomicReference<>();
        DhanMarketFeedBinaryParser.parse(payload, ignored -> {
        }, error::set);
        assertTrue(error.get() instanceof IllegalStateException);
        assertTrue(error.get().getMessage().contains("disconnect"));
    }

    private static List<DhanMarketFeedPacket> parse(byte[] payload) {
        List<DhanMarketFeedPacket> packets = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        DhanMarketFeedBinaryParser.parse(payload, packets::add, error::set);
        if (error.get() != null) {
            throw new AssertionError(error.get());
        }
        return packets;
    }

    private static byte[] tickerPacket(
            ExchangeSegment segment,
            int segmentCode,
            int securityId,
            float ltp,
            long ltt
    ) {
        return packet(
                DhanProtocolConstants.FEED_RESPONSE_TICKER,
                segmentCode,
                securityId,
                buffer -> {
                    buffer.putFloat(ltp);
                    buffer.putInt((int) ltt);
                }
        );
    }

    private static byte[] indexPacket(
            ExchangeSegment segment,
            int segmentCode,
            int securityId,
            float indexValue
    ) {
        return packet(
                DhanProtocolConstants.FEED_RESPONSE_INDEX,
                segmentCode,
                securityId,
                buffer -> {
                    buffer.putFloat(indexValue);
                    buffer.putFloat(indexValue - 10f);
                    buffer.putFloat(indexValue + 10f);
                    buffer.putFloat(indexValue - 20f);
                    buffer.putFloat(indexValue - 5f);
                    buffer.putFloat(0.25f);
                }
        );
    }

    private static byte[] disconnectPacket() {
        ByteBuffer buffer = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) DhanProtocolConstants.FEED_RESPONSE_DISCONNECT);
        buffer.putShort((short) 0);
        buffer.put((byte) 0);
        buffer.putInt(0);
        buffer.putShort((short) 806);
        return buffer.array();
    }

    private static byte[] packet(int packetType, int segmentCode, int securityId, java.util.function.Consumer<ByteBuffer> bodyWriter) {
        ByteBuffer buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) packetType);
        buffer.putShort((short) 0);
        buffer.put((byte) segmentCode);
        buffer.putInt(securityId);
        bodyWriter.accept(buffer);
        buffer.flip();
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }
}
