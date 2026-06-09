package com.tradej.broker.dhan.websocket.feed;

import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;

/**
 * Parsed payloads from the Dhan live market feed binary protocol.
 *
 * @see <a href="https://dhanhq.co/docs/v2/live-market-feed/">DhanHQ Live Market Feed</a>
 */
public sealed interface DhanMarketFeedPacket permits
        DhanMarketFeedPacket.Ticker,
        DhanMarketFeedPacket.Quote,
        DhanMarketFeedPacket.Full,
        DhanMarketFeedPacket.Index,
        DhanMarketFeedPacket.Oi,
        DhanMarketFeedPacket.Heartbeat,
        DhanMarketFeedPacket.MarketStatus,
        DhanMarketFeedPacket.PrevClose {

    ExchangeSegment exchangeSegment();

    String securityId();

    record Ticker(
            ExchangeSegment exchangeSegment,
            String securityId,
            String ltp,
            long ltt
    ) implements DhanMarketFeedPacket {
    }

    record Quote(
            ExchangeSegment exchangeSegment,
            String securityId,
            String ltp,
            int ltq,
            long ltt,
            String avgPrice,
            long volume,
            long totalBuyQuantity,
            long totalSellQuantity,
            String openPrice,
            String closePrice,
            String highPrice,
            String lowPrice
    ) implements DhanMarketFeedPacket {
    }

    record DepthLevel(String price, int quantity, int orders) {
    }

    record Full(
            ExchangeSegment exchangeSegment,
            String securityId,
            String ltp,
            int ltq,
            long ltt,
            String avgPrice,
            long volume,
            long totalBuyQuantity,
            long totalSellQuantity,
            long openInterest,
            long oiDayHigh,
            long oiDayLow,
            String openPrice,
            String closePrice,
            String highPrice,
            String lowPrice,
            List<DepthLevel> bids,
            List<DepthLevel> asks
    ) implements DhanMarketFeedPacket {
    }

    record Index(
            ExchangeSegment exchangeSegment,
            String securityId,
            String indexValue,
            String openValue,
            String highValue,
            String lowValue,
            String closeValue,
            String changePercent
    ) implements DhanMarketFeedPacket {
    }

    record Oi(
            ExchangeSegment exchangeSegment,
            String securityId,
            long openInterest
    ) implements DhanMarketFeedPacket {
    }

    record Heartbeat() implements DhanMarketFeedPacket {
        @Override public ExchangeSegment exchangeSegment() { return null; }
        @Override public String securityId() { return ""; }
    }

    record MarketStatus(
            ExchangeSegment exchangeSegment,
            String securityId,
            int status
    ) implements DhanMarketFeedPacket {
    }

    record PrevClose(
            ExchangeSegment exchangeSegment,
            String securityId,
            String prevClose,
            String prevOpenInterest
    ) implements DhanMarketFeedPacket {
    }
}
