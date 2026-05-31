package com.tradej.broker.upstox.websocket;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parser for Upstox Market Feed V3 binary protocol.
 * <p>
 * <b>IMPORTANT:</b> This is a best-effort implementation based on documented
 * Upstox market feed structure. The exact binary format must be verified
 * against real sandbox output. Binary fixtures captured from the sandbox
 * should be used to validate and fine-tune this parser.
 * <p>
 * Frame structure (expected):
 * <pre>{@code
 * [1 byte: frame_type][2 bytes: payload_length][N bytes: payload]
 * }</pre>
 * <p>
 * Frame types:
 * <ul>
 *   <li>1 = TICK (LTP + volume)</li>
 *   <li>2 = QUOTE (OHLC + LTP + volume)</li>
 *   <li>3 = DEPTH (bid/ask ladder, 5 levels)</li>
 *   <li>4 = FULL (quote + depth combined)</li>
 *   <li>5 = OI (open interest)</li>
 * </ul>
 */
public final class UpstoxBinaryParser {

    private UpstoxBinaryParser() {
    }

    // Frame type constants (int — used in switch on int frameType)
    public static final int FRAME_TICK       = 1;
    public static final int FRAME_QUOTE      = 2;
    public static final int FRAME_DEPTH      = 3;
    public static final int FRAME_FULL       = 4;
    public static final int FRAME_OI         = 5;
    public static final int FRAME_HEARTBEAT  = 100;
    public static final int FRAME_DISCONNECT = 200;
    public static final int FRAME_ERROR      = 255;

    /**
     * Parses a single binary frame from a ByteBuffer.
     *
     * @param buffer the buffer containing the frame data (position at start of frame)
     * @return the parsed frame, or {@code null} if the frame is a heartbeat/disconnect
     * @throws UpstoxParserException if the frame is malformed
     */
    public static ParsedFeedFrame parse(ByteBuffer buffer) throws UpstoxParserException {
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 3) {
            throw new UpstoxParserException("Frame too short: " + buffer.remaining() + " bytes");
        }
        int frameType = buffer.get() & 0xFF;
        int payloadLength = buffer.getShort() & 0xFFFF;
        if (buffer.remaining() < payloadLength) {
            throw new UpstoxParserException(
                    "Frame truncated: expected " + payloadLength + " bytes, got " + buffer.remaining());
        }

        // Parse based on frame type
        return switch (frameType) {
            case FRAME_TICK -> parseTick(buffer);
            case FRAME_QUOTE -> parseQuote(buffer);
            case FRAME_DEPTH -> parseDepth(buffer);
            case FRAME_FULL -> parseFull(buffer);
            case FRAME_OI -> parseOi(buffer);
            case FRAME_HEARTBEAT, FRAME_DISCONNECT -> null;
            default -> throw new UpstoxParserException("Unknown frame type: " + frameType);
        };
    }

    private static ParsedFeedFrame parseTick(ByteBuffer buffer) {
        long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;
        long ltpPaisa = toPaisa(buffer.getInt());
        long lastTradeQty = buffer.getInt() & 0xFFFFFFFFL;
        long volume = buffer.getInt() & 0xFFFFFFFFL;
        long timestamp = buffer.getLong();
        return new ParsedFeedFrame(instrumentToken, ltpPaisa, lastTradeQty, 0L, volume,
                timestamp, null, null, FRAME_TICK);
    }

    private static ParsedFeedFrame parseQuote(ByteBuffer buffer) {
        long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;
        long ltpPaisa = toPaisa(buffer.getInt());
        long lastTradeQty = buffer.getInt() & 0xFFFFFFFFL;
        long openPaisa = toPaisa(buffer.getInt());
        long highPaisa = toPaisa(buffer.getInt());
        long lowPaisa = toPaisa(buffer.getInt());
        long closePaisa = toPaisa(buffer.getInt());
        long volume = buffer.getInt() & 0xFFFFFFFFL;
        long oi = buffer.getInt() & 0xFFFFFFFFL;
        long timestamp = buffer.getLong();
        return new ParsedFeedFrame(instrumentToken, ltpPaisa, lastTradeQty, 0L, volume,
                timestamp, null, null, FRAME_QUOTE);
    }

    private static ParsedFeedFrame parseDepth(ByteBuffer buffer) {
        long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;
        int levelCount = buffer.get() & 0xFF;
        ParsedFeedFrame.DepthLevel[] bids = new ParsedFeedFrame.DepthLevel[levelCount];
        ParsedFeedFrame.DepthLevel[] asks = new ParsedFeedFrame.DepthLevel[levelCount];
        // Bids
        for (int i = 0; i < levelCount; i++) {
            long price = toPaisa(buffer.getInt());
            long qty = buffer.getInt() & 0xFFFFFFFFL;
            int orders = buffer.getShort() & 0xFFFF;
            bids[i] = new ParsedFeedFrame.DepthLevel(price, qty, orders);
        }
        // Asks
        for (int i = 0; i < levelCount; i++) {
            long price = toPaisa(buffer.getInt());
            long qty = buffer.getInt() & 0xFFFFFFFFL;
            int orders = buffer.getShort() & 0xFFFF;
            asks[i] = new ParsedFeedFrame.DepthLevel(price, qty, orders);
        }
        long timestamp = buffer.getLong();
        return new ParsedFeedFrame(instrumentToken, 0L, 0L, 0L, 0L,
                timestamp, bids, asks, FRAME_DEPTH);
    }

    private static ParsedFeedFrame parseFull(ByteBuffer buffer) {
        long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;
        long ltpPaisa = toPaisa(buffer.getInt());
        long lastTradeQty = buffer.getInt() & 0xFFFFFFFFL;
        long openPaisa = toPaisa(buffer.getInt());
        long highPaisa = toPaisa(buffer.getInt());
        long lowPaisa = toPaisa(buffer.getInt());
        long closePaisa = toPaisa(buffer.getInt());
        long volume = buffer.getInt() & 0xFFFFFFFFL;
        long oi = buffer.getInt() & 0xFFFFFFFFL;
        int depthLevels = buffer.get() & 0xFF;
        ParsedFeedFrame.DepthLevel[] bids = new ParsedFeedFrame.DepthLevel[depthLevels];
        ParsedFeedFrame.DepthLevel[] asks = new ParsedFeedFrame.DepthLevel[depthLevels];
        for (int i = 0; i < depthLevels; i++) {
            long price = toPaisa(buffer.getInt());
            long qty = buffer.getInt() & 0xFFFFFFFFL;
            int orders = buffer.getShort() & 0xFFFF;
            bids[i] = new ParsedFeedFrame.DepthLevel(price, qty, orders);
        }
        for (int i = 0; i < depthLevels; i++) {
            long price = toPaisa(buffer.getInt());
            long qty = buffer.getInt() & 0xFFFFFFFFL;
            int orders = buffer.getShort() & 0xFFFF;
            asks[i] = new ParsedFeedFrame.DepthLevel(price, qty, orders);
        }
        long timestamp = buffer.getLong();
        return new ParsedFeedFrame(instrumentToken, ltpPaisa, lastTradeQty, 0L, volume,
                timestamp, bids, asks, FRAME_FULL);
    }

    private static ParsedFeedFrame parseOi(ByteBuffer buffer) {
        long instrumentToken = buffer.getInt() & 0xFFFFFFFFL;
        long oi = buffer.getInt() & 0xFFFFFFFFL;
        long timestamp = buffer.getLong();
        return new ParsedFeedFrame(instrumentToken, 0L, 0L, oi, 0L,
                timestamp, null, null, FRAME_OI);
    }

    /**
     * Converts a 4-byte integer price value to paisa.
     * The Upstox feed sends prices as integers with 2 decimal places.
     */
    private static long toPaisa(int price) {
        return (long) price * 100L;
    }

    /**
     * Exception for malformed binary frames.
     */
    public static class UpstoxParserException extends RuntimeException {
        public UpstoxParserException(String message) {
            super(message);
        }
    }
}
