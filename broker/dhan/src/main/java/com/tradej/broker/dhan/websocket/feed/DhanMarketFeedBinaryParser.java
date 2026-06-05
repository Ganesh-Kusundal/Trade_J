package com.tradej.broker.dhan.websocket.feed;

import com.tradej.broker.dhan.constants.DhanProtocolConstants;
import com.tradej.broker.dhan.depth.DhanExchangeSegmentCodes;
import com.tradej.core.domain.value.ExchangeSegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parses Dhan live market feed binary packets ({@code wss://api-feed.dhan.co}).
 */
public final class DhanMarketFeedBinaryParser {
    private DhanMarketFeedBinaryParser() {
    }

    public static void parse(byte[] bytes, Consumer<DhanMarketFeedPacket> consumer, Consumer<Throwable> errorHandler) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int packetType = buffer.get() & 0xFF;
            switch (packetType) {
                case DhanProtocolConstants.FEED_RESPONSE_INDEX -> consumer.accept(parseIndex(buffer));
                case DhanProtocolConstants.FEED_RESPONSE_TICKER -> consumer.accept(parseTicker(buffer));
                case DhanProtocolConstants.FEED_RESPONSE_QUOTE -> consumer.accept(parseQuote(buffer));
                case DhanProtocolConstants.FEED_RESPONSE_FULL -> consumer.accept(parseFull(buffer));
                case DhanProtocolConstants.FEED_RESPONSE_DISCONNECT -> errorHandler.accept(
                        new IllegalStateException("Server requested market feed disconnect (code "
                                + readDisconnectReason(buffer) + ")"));
                default -> errorHandler.accept(new IllegalStateException("Unknown market feed packet type: " + packetType));
            }
        } catch (RuntimeException ex) {
            errorHandler.accept(ex);
        }
    }

    private static int readDisconnectReason(ByteBuffer buffer) {
        if (buffer.remaining() >= 9) {
            buffer.position(buffer.position() + 7);
            return buffer.getShort() & 0xFFFF;
        }
        if (buffer.remaining() >= 2) {
            buffer.position(buffer.position() + buffer.remaining() - 2);
            return buffer.getShort() & 0xFFFF;
        }
        return -1;
    }

    private static Header parseHeader(ByteBuffer buffer) {
        buffer.getShort();
        int segmentCode = buffer.get() & 0xFF;
        ExchangeSegment segment = DhanExchangeSegmentCodes.fromWireCode(segmentCode);
        String securityId = Integer.toString(buffer.getInt());
        return new Header(segment, securityId);
    }

    private static DhanMarketFeedPacket.Index parseIndex(ByteBuffer buffer) {
        Header header = parseHeader(buffer);
        return new DhanMarketFeedPacket.Index(
                header.segment(),
                header.securityId(),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat())
        );
    }

    private static DhanMarketFeedPacket.Ticker parseTicker(ByteBuffer buffer) {
        Header header = parseHeader(buffer);
        return new DhanMarketFeedPacket.Ticker(
                header.segment(),
                header.securityId(),
                formatPrice(buffer.getFloat()),
                buffer.getInt() & 0xFFFFFFFFL
        );
    }

    private static DhanMarketFeedPacket.Quote parseQuote(ByteBuffer buffer) {
        Header header = parseHeader(buffer);
        return new DhanMarketFeedPacket.Quote(
                header.segment(),
                header.securityId(),
                formatPrice(buffer.getFloat()),
                buffer.getShort() & 0xFFFF,
                buffer.getInt() & 0xFFFFFFFFL,
                formatPrice(buffer.getFloat()),
                buffer.getInt() & 0xFFFFFFFFL,
                buffer.getInt() & 0xFFFFFFFFL,
                buffer.getInt() & 0xFFFFFFFFL,
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat()),
                formatPrice(buffer.getFloat())
        );
    }

    private static DhanMarketFeedPacket.Full parseFull(ByteBuffer buffer) {
        Header header = parseHeader(buffer);
        String ltp = formatPrice(buffer.getFloat());
        int ltq = buffer.getShort() & 0xFFFF;
        long ltt = buffer.getInt() & 0xFFFFFFFFL;
        String avgPrice = formatPrice(buffer.getFloat());
        long volume = buffer.getInt() & 0xFFFFFFFFL;
        long totalSell = buffer.getInt() & 0xFFFFFFFFL;
        long totalBuy = buffer.getInt() & 0xFFFFFFFFL;
        long openInterest = buffer.getInt() & 0xFFFFFFFFL;
        long oiDayHigh = buffer.getInt() & 0xFFFFFFFFL;
        long oiDayLow = buffer.getInt() & 0xFFFFFFFFL;
        String open = formatPrice(buffer.getFloat());
        String close = formatPrice(buffer.getFloat());
        String high = formatPrice(buffer.getFloat());
        String low = formatPrice(buffer.getFloat());
        List<DhanMarketFeedPacket.DepthLevel> bids = new ArrayList<>(5);
        List<DhanMarketFeedPacket.DepthLevel> asks = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            int bidQty = buffer.getInt();
            int askQty = buffer.getInt();
            int bidOrders = buffer.getShort() & 0xFFFF;
            int askOrders = buffer.getShort() & 0xFFFF;
            String bidPrice = formatPrice(buffer.getFloat());
            String askPrice = formatPrice(buffer.getFloat());
            bids.add(new DhanMarketFeedPacket.DepthLevel(bidPrice, bidQty, bidOrders));
            asks.add(new DhanMarketFeedPacket.DepthLevel(askPrice, askQty, askOrders));
        }
        return new DhanMarketFeedPacket.Full(
                header.segment(), header.securityId(), ltp, ltq, ltt, avgPrice, volume,
                totalBuy, totalSell, openInterest, oiDayHigh, oiDayLow,
                open, close, high, low, List.copyOf(bids), List.copyOf(asks)
        );
    }

    private static String formatPrice(float value) {
        return String.format("%.2f", value);
    }

    private record Header(ExchangeSegment segment, String securityId) {
    }
}
