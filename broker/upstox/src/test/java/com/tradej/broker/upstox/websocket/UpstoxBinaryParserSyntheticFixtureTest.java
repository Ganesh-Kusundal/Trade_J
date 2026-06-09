package com.tradej.broker.upstox.websocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Synthetic fixture tests for UpstoxBinaryParser.
 *
 * <p>Builds binary frames matching the documented Upstox V3 market feed structure
 * and verifies the parser decodes all fields correctly. These synthetic fixtures
 * serve as regression baselines until real broker sandbox captures are available.
 *
 * <p>Frame format: [1 byte: frame_type][2 bytes: payload_length][N bytes: payload]
 */
@Tag("unit")
class UpstoxBinaryParserSyntheticFixtureTest {

    // ── TICK Frame (type=1) ──────────────────────────────────────────

    @Test
    void parseTick_decodesAllFields() throws Exception {
        // toPaisa(raw) = raw * 100. So raw=1275 → ltpPaisa=127500 (₹1,275.00)
        ByteBuffer buffer = buildTickFrame(12345, 1275, 100, 50000, 1717800000000L);

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);

        assertNotNull(frame);
        assertEquals(UpstoxBinaryParser.FRAME_TICK, frame.frameType());
        assertEquals(12345L, frame.instrumentToken());
        assertEquals(127500L, frame.ltpPaisa());
        assertEquals(100L, frame.lastTradeQuantity());
        assertEquals(50000L, frame.volume());
        assertEquals(1717800000000L, frame.exchangeTimestampMs());
    }

    @Test
    void parseTick_zeroValues() throws Exception {
        ByteBuffer buffer = buildTickFrame(0, 0, 0, 0, 0L);
        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);
        assertNotNull(frame);
        assertEquals(0L, frame.ltpPaisa());
    }

    // ── QUOTE Frame (type=2) ─────────────────────────────────────────

    @Test
    void parseQuote_decodesOHLC() throws Exception {
        // raw=2500 → ltpPaisa=250000 (₹2,500.00)
        ByteBuffer buffer = buildQuoteFrame(99999,
                2500, 50, 2480, 2520, 2470, 2490, 100000, 500000, 1717800000000L);

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);

        assertNotNull(frame);
        assertEquals(UpstoxBinaryParser.FRAME_QUOTE, frame.frameType());
        assertEquals(99999L, frame.instrumentToken());
        assertEquals(250000L, frame.ltpPaisa());
    }

    // ── DEPTH Frame (type=3) ─────────────────────────────────────────

    @Test
    void parseDepth_decodesBidAskLadder() throws Exception {
        int levels = 3;
        // raw int values: raw * 100 = paisa
        long[] bidPrices = {1000, 999, 998};
        long[] bidQtys = {500, 1000, 1500};
        int[] bidOrders = {5, 10, 15};
        long[] askPrices = {1001, 1002, 1003};
        long[] askQtys = {600, 1100, 1600};
        int[] askOrders = {6, 11, 16};

        ByteBuffer buffer = buildDepthFrame(55555, levels,
                bidPrices, bidQtys, bidOrders, askPrices, askQtys, askOrders,
                1717800000000L);

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);

        assertNotNull(frame);
        assertEquals(UpstoxBinaryParser.FRAME_DEPTH, frame.frameType());
        assertEquals(55555L, frame.instrumentToken());
        assertNotNull(frame.bids());
        assertNotNull(frame.asks());
        assertEquals(3, frame.bids().length);
        assertEquals(3, frame.asks().length);

        assertEquals(100000L, frame.bids()[0].pricePaisa());  // raw 1000 * 100
        assertEquals(500L, frame.bids()[0].quantity());
        assertEquals(5, frame.bids()[0].orderCount());

        assertEquals(100100L, frame.asks()[0].pricePaisa());  // raw 1001 * 100
        assertEquals(600L, frame.asks()[0].quantity());
        assertEquals(6, frame.asks()[0].orderCount());
    }

    // ── OI Frame (type=5) ────────────────────────────────────────────

    @Test
    void parseOi_decodesOpenInterest() throws Exception {
        ByteBuffer buffer = buildOiFrame(77777, 1234567, 1717800000000L);

        ParsedFeedFrame frame = UpstoxBinaryParser.parse(buffer);

        assertNotNull(frame);
        assertEquals(UpstoxBinaryParser.FRAME_OI, frame.frameType());
        assertEquals(77777L, frame.instrumentToken());
        assertEquals(1234567L, frame.openInterest());
    }

    // ── HEARTBEAT / DISCONNECT ───────────────────────────────────────

    @Test
    void parseHeartbeat_returnsNull() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 100); // FRAME_HEARTBEAT
        buffer.putShort((short) 0); // no payload
        buffer.flip();

        assertNull(UpstoxBinaryParser.parse(buffer));
    }

    @Test
    void parseDisconnect_returnsNull() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 200); // FRAME_DISCONNECT
        buffer.putShort((short) 0);
        buffer.flip();

        assertNull(UpstoxBinaryParser.parse(buffer));
    }

    // ── Error handling ───────────────────────────────────────────────

    @Test
    void parseTooShort_throws() {
        ByteBuffer buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1);
        buffer.put((byte) 0);
        buffer.flip();

        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buffer));
    }

    @Test
    void parseTruncatedFrame_throws() {
        // Header says 100 bytes payload but only 4 bytes follow
        ByteBuffer buffer = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 1); // TICK
        buffer.putShort((short) 100); // claims 100 bytes payload
        buffer.putInt(0); // only 4 bytes of actual data
        buffer.flip();

        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buffer));
    }

    @Test
    void parseUnknownFrameType_throws() {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) 99); // unknown type
        buffer.putShort((short) 1);
        buffer.put((byte) 0);
        buffer.flip();

        assertThrows(UpstoxBinaryParser.UpstoxParserException.class,
                () -> UpstoxBinaryParser.parse(buffer));
    }

    // ── Frame builders ───────────────────────────────────────────────

    private ByteBuffer buildTickFrame(long instrumentToken, int ltpRaw, int ltq, int volume, long timestamp) {
        int payloadSize = 4 + 4 + 4 + 4 + 8; // token + ltp + ltq + vol + ts
        ByteBuffer buffer = ByteBuffer.allocate(3 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) UpstoxBinaryParser.FRAME_TICK);
        buffer.putShort((short) payloadSize);
        buffer.putInt((int) instrumentToken);
        buffer.putInt(ltpRaw);
        buffer.putInt(ltq);
        buffer.putInt(volume);
        buffer.putLong(timestamp);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer buildQuoteFrame(long instrumentToken, int ltpRaw, int ltq,
                                        int openRaw, int highRaw, int lowRaw, int closeRaw,
                                        int volume, int oi, long timestamp) {
        int payloadSize = 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 4 + 8;
        ByteBuffer buffer = ByteBuffer.allocate(3 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) UpstoxBinaryParser.FRAME_QUOTE);
        buffer.putShort((short) payloadSize);
        buffer.putInt((int) instrumentToken);
        buffer.putInt(ltpRaw);
        buffer.putInt(ltq);
        buffer.putInt(openRaw);
        buffer.putInt(highRaw);
        buffer.putInt(lowRaw);
        buffer.putInt(closeRaw);
        buffer.putInt(volume);
        buffer.putInt(oi);
        buffer.putLong(timestamp);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer buildDepthFrame(long instrumentToken, int levels,
                                        long[] bidPrices, long[] bidQtys, int[] bidOrders,
                                        long[] askPrices, long[] askQtys, int[] askOrders,
                                        long timestamp) {
        int levelSize = 4 + 4 + 2; // price + qty + orders
        int payloadSize = 4 + 1 + (levels * levelSize * 2) + 8;
        ByteBuffer buffer = ByteBuffer.allocate(3 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) UpstoxBinaryParser.FRAME_DEPTH);
        buffer.putShort((short) payloadSize);
        buffer.putInt((int) instrumentToken);
        buffer.put((byte) levels);
        for (int i = 0; i < levels; i++) {
            buffer.putInt((int) bidPrices[i]);
            buffer.putInt((int) bidQtys[i]);
            buffer.putShort((short) bidOrders[i]);
        }
        for (int i = 0; i < levels; i++) {
            buffer.putInt((int) askPrices[i]);
            buffer.putInt((int) askQtys[i]);
            buffer.putShort((short) askOrders[i]);
        }
        buffer.putLong(timestamp);
        buffer.flip();
        return buffer;
    }

    private ByteBuffer buildOiFrame(long instrumentToken, int oi, long timestamp) {
        int payloadSize = 4 + 4 + 8;
        ByteBuffer buffer = ByteBuffer.allocate(3 + payloadSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte) UpstoxBinaryParser.FRAME_OI);
        buffer.putShort((short) payloadSize);
        buffer.putInt((int) instrumentToken);
        buffer.putInt(oi);
        buffer.putLong(timestamp);
        buffer.flip();
        return buffer;
    }
}
