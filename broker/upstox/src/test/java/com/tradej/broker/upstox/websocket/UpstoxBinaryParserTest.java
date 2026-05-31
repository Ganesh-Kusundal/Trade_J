package com.tradej.broker.upstox.websocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxBinaryParserTest {

    @Test
    void parsesTickFrame() {
        // header(3) + token(4) + ltp(4) + qty(4) + vol(4) + ts(8) = 27
        ByteBuffer buf = ByteBuffer.allocate(27).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.putShort((short) 24);
        buf.putInt(12345);
        buf.putInt(85050);
        buf.putInt(100);
        buf.putInt(500000);
        buf.putLong(1717000000000L);
        buf.flip();

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buf);
        assertNotNull(frame);
        assertEquals(12345L, frame.instrumentToken());
        assertEquals(85050L * 100, frame.ltpPaisa());
        assertEquals(100L, frame.lastTradeQuantity());
        assertEquals(500000L, frame.volume());
        assertEquals(1717000000000L, frame.exchangeTimestampMs());
    }

    @Test
    void parsesDepthFrame() {
        // header(3) + token(4) + levelCount(1) + 5*bids(5*10=50) + 5*asks(5*10=50) + ts(8) = 116
        ByteBuffer buf = ByteBuffer.allocate(116).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 3);
        buf.putShort((short) 113);
        buf.putInt(12345);
        buf.put((byte) 5);
        for (int i = 5; i >= 1; i--) {
            buf.putInt(i * 100);
            buf.putInt(i * 1000);
            buf.putShort((short) i);
        }
        for (int i = 1; i <= 5; i++) {
            buf.putInt(i * 105 + 100);
            buf.putInt(i * 800);
            buf.putShort((short) i);
        }
        buf.putLong(1717000000000L);
        buf.flip();

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buf);
        assertNotNull(frame);
        assertTrue(frame.hasDepth());
        assertEquals(5, frame.bids().length);
        assertEquals(5, frame.asks().length);
        assertEquals(500L * 100, frame.bids()[0].pricePaisa());
    }

    @Test
    void returnsNullForHeartbeat() {
        ByteBuffer buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 100);
        buf.putShort((short) 0);
        buf.flip();
        assertNull(UpstoxBinaryParser.parse(buf));
    }

    @Test
    void throwsOnTruncatedFrame() {
        // Buffer with header (3 bytes) that claims a large payload but has no data
        ByteBuffer buf = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.putShort((short) 100);
        buf.putInt(42);
        buf.flip();
        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buf));
    }

    @Test
    void throwsOnVeryShortFrame() {
        ByteBuffer buf = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 1);
        buf.put((byte) 5);
        buf.flip();
        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buf));
    }

    @Test
    void throwsOnUnknownFrameType() {
        ByteBuffer buf = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 99);
        buf.putShort((short) 0);
        buf.flip();
        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buf));
    }
}
