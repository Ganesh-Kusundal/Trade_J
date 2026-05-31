package com.tradej.broker.dhan.websocket;

import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DhanBinaryParser {
    private static final int FEED_TICKER = 2;
    private static final int FEED_DEPTH = 3;
    private static final int FEED_QUOTE = 4;
    private static final int FEED_OI = 5;
    private static final int FEED_FULL = 8;
    private static final int FEED_DISCONNECT = 50;

    private DhanBinaryParser() {
    }

    public static ParsedFeedFrame parse(byte[] payload) {
        if (payload == null || payload.length < 2) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        int feedCode = Byte.toUnsignedInt(buffer.get(0));
        return switch (feedCode) {
            case FEED_TICKER -> parseTicker(buffer);
            case FEED_DEPTH -> parseDepth(buffer);
            case FEED_QUOTE -> parseQuote(buffer);
            case FEED_OI -> parseOi(buffer);
            case FEED_FULL -> parseFull(buffer);
            case FEED_DISCONNECT -> parseDisconnect(buffer);
            default -> null;
        };
    }

    private static ParsedFeedFrame parseTicker(ByteBuffer buffer) {
        buffer.position(3);
        ExchangeSegment segment = fromNumericSegment(Short.toUnsignedInt(buffer.getShort()));
        String securityId = Integer.toUnsignedString(buffer.getInt());
        long ltpPaisa = Math.round(buffer.getFloat() * 100.0d);
        long epochSeconds = Integer.toUnsignedLong(buffer.getInt());
        return new ParsedFeedFrame(
                "tick",
                segment,
                securityId,
                ltpPaisa,
                0L,
                Instant.ofEpochSecond(epochSeconds).toEpochMilli(),
                List.of(),
                List.of(),
                Map.of()
        );
    }

    private static ParsedFeedFrame parseQuote(ByteBuffer buffer) {
        buffer.position(3);
        ExchangeSegment segment = fromNumericSegment(Short.toUnsignedInt(buffer.getShort()));
        String securityId = Integer.toUnsignedString(buffer.getInt());
        long ltpPaisa = Math.round(buffer.getFloat() * 100.0d);
        long ltq = Short.toUnsignedLong(buffer.getShort());
        long epochSeconds = Integer.toUnsignedLong(buffer.getInt());
        long avgPrice = Math.round(buffer.getFloat() * 100.0d);
        long volume = Integer.toUnsignedLong(buffer.getInt());
        long totalSell = Integer.toUnsignedLong(buffer.getInt());
        long totalBuy = Integer.toUnsignedLong(buffer.getInt());
        long open = Math.round(buffer.getFloat() * 100.0d);
        long close = Math.round(buffer.getFloat() * 100.0d);
        long high = Math.round(buffer.getFloat() * 100.0d);
        long low = Math.round(buffer.getFloat() * 100.0d);
        return new ParsedFeedFrame(
                "quote",
                segment,
                securityId,
                ltpPaisa,
                ltq,
                Instant.ofEpochSecond(epochSeconds).toEpochMilli(),
                List.of(),
                List.of(),
                Map.of(
                        "avgPricePaisa", avgPrice,
                        "volume", volume,
                        "totalSellQuantity", totalSell,
                        "totalBuyQuantity", totalBuy,
                        "openPaisa", open,
                        "closePaisa", close,
                        "highPaisa", high,
                        "lowPaisa", low
                )
        );
    }

    private static ParsedFeedFrame parseDepth(ByteBuffer buffer) {
        buffer.position(3);
        ExchangeSegment segment = fromNumericSegment(Short.toUnsignedInt(buffer.getShort()));
        String securityId = Integer.toUnsignedString(buffer.getInt());
        long ltpPaisa = Math.round(buffer.getFloat() * 100.0d);
        List<DepthLevel> bids = new ArrayList<>();
        List<DepthLevel> asks = new ArrayList<>();
        unpackDepth(buffer, 5, bids, asks);
        return new ParsedFeedFrame("depth", segment, securityId, ltpPaisa, 0L, 0L, bids, asks, Map.of());
    }

    private static ParsedFeedFrame parseFull(ByteBuffer buffer) {
        buffer.position(3);
        ExchangeSegment segment = fromNumericSegment(Short.toUnsignedInt(buffer.getShort()));
        String securityId = Integer.toUnsignedString(buffer.getInt());
        long ltpPaisa = Math.round(buffer.getFloat() * 100.0d);
        long ltq = Short.toUnsignedLong(buffer.getShort());
        long epochSeconds = Integer.toUnsignedLong(buffer.getInt());
        long avgPrice = Math.round(buffer.getFloat() * 100.0d);
        long volume = Integer.toUnsignedLong(buffer.getInt());
        long totalSell = Integer.toUnsignedLong(buffer.getInt());
        long totalBuy = Integer.toUnsignedLong(buffer.getInt());
        long oi = Integer.toUnsignedLong(buffer.getInt());
        long oiHigh = Integer.toUnsignedLong(buffer.getInt());
        long oiLow = Integer.toUnsignedLong(buffer.getInt());
        long open = Math.round(buffer.getFloat() * 100.0d);
        long close = Math.round(buffer.getFloat() * 100.0d);
        long high = Math.round(buffer.getFloat() * 100.0d);
        long low = Math.round(buffer.getFloat() * 100.0d);
        List<DepthLevel> bids = new ArrayList<>();
        List<DepthLevel> asks = new ArrayList<>();
        unpackDepth(buffer, 5, bids, asks);
        return new ParsedFeedFrame(
                "full",
                segment,
                securityId,
                ltpPaisa,
                ltq,
                Instant.ofEpochSecond(epochSeconds).toEpochMilli(),
                bids,
                asks,
                Map.ofEntries(
                        Map.entry("avgPricePaisa", avgPrice),
                        Map.entry("volume", volume),
                        Map.entry("totalSellQuantity", totalSell),
                        Map.entry("totalBuyQuantity", totalBuy),
                        Map.entry("openInterest", oi),
                        Map.entry("openInterestDayHigh", oiHigh),
                        Map.entry("openInterestDayLow", oiLow),
                        Map.entry("openPaisa", open),
                        Map.entry("closePaisa", close),
                        Map.entry("highPaisa", high),
                        Map.entry("lowPaisa", low)
                )
        );
    }

    private static ParsedFeedFrame parseOi(ByteBuffer buffer) {
        buffer.position(3);
        ExchangeSegment segment = fromNumericSegment(Short.toUnsignedInt(buffer.getShort()));
        String securityId = Integer.toUnsignedString(buffer.getInt());
        long oi = Integer.toUnsignedLong(buffer.getInt());
        return new ParsedFeedFrame("oi", segment, securityId, 0L, 0L, 0L, List.of(), List.of(), Map.of("openInterest", oi));
    }

    private static ParsedFeedFrame parseDisconnect(ByteBuffer buffer) {
        buffer.position(Math.min(buffer.limit() - 2, 8));
        int code = Short.toUnsignedInt(buffer.getShort());
        return new ParsedFeedFrame("disconnect", ExchangeSegment.UNKNOWN, "", 0L, 0L, 0L, List.of(), List.of(), Map.of("code", (long) code));
    }

    private static void unpackDepth(ByteBuffer buffer, int levels, List<DepthLevel> bids, List<DepthLevel> asks) {
        for (int index = 0; index < levels; index++) {
            long bidQuantity = Integer.toUnsignedLong(buffer.getInt());
            long askQuantity = Integer.toUnsignedLong(buffer.getInt());
            int bidOrders = Short.toUnsignedInt(buffer.getShort());
            int askOrders = Short.toUnsignedInt(buffer.getShort());
            long bidPricePaisa = Math.round(buffer.getFloat() * 100.0d);
            long askPricePaisa = Math.round(buffer.getFloat() * 100.0d);
            bids.add(new DepthLevel(bidPricePaisa, bidQuantity, bidOrders));
            asks.add(new DepthLevel(askPricePaisa, askQuantity, askOrders));
        }
    }

    private static ExchangeSegment fromNumericSegment(int numericSegment) {
        return switch (numericSegment) {
            case 1 -> ExchangeSegment.IDX_I;
            case 2 -> ExchangeSegment.NSE_EQ;
            case 3 -> ExchangeSegment.NSE_FNO;
            case 4 -> ExchangeSegment.NSE_CURRENCY;
            case 5 -> ExchangeSegment.BSE_EQ;
            case 6 -> ExchangeSegment.MCX_COMM;
            case 7 -> ExchangeSegment.BSE_FNO;
            case 8 -> ExchangeSegment.BSE_CURRENCY;
            default -> ExchangeSegment.UNKNOWN;
        };
    }
}
