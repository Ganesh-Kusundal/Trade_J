package com.tradej.broker.dhan.depth;

import com.tradej.core.domain.value.ExchangeSegment;

import java.util.Map;

/**
 * Wire-format exchange segment codes used by Dhan binary market/depth feeds.
 * Mirrors {@code io.github.sonicalgo.dhan.websocket.marketFeed.BinaryPacketParser}.
 */
public final class DhanExchangeSegmentCodes {
    private static final Map<Integer, ExchangeSegment> BY_CODE = Map.of(
            0, ExchangeSegment.IDX_I,
            1, ExchangeSegment.NSE_EQ,
            2, ExchangeSegment.NSE_FNO,
            3, ExchangeSegment.NSE_CURRENCY,
            4, ExchangeSegment.BSE_EQ,
            5, ExchangeSegment.MCX_COMM,
            7, ExchangeSegment.BSE_CURRENCY,
            8, ExchangeSegment.BSE_FNO
    );

    private DhanExchangeSegmentCodes() {
    }

    public static ExchangeSegment fromWireCode(int code) {
        ExchangeSegment segment = BY_CODE.get(code);
        if (segment == null) {
            throw new IllegalArgumentException("Unknown Dhan exchange segment code: " + code);
        }
        return segment;
    }
}
