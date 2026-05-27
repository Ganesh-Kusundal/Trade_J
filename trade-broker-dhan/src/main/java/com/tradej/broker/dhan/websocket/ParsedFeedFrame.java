package com.tradej.broker.dhan.websocket;

import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.value.ExchangeSegment;

import java.util.List;
import java.util.Map;

public record ParsedFeedFrame(
        String type,
        ExchangeSegment exchangeSegment,
        String securityId,
        long ltpPaisa,
        long lastTradeQuantity,
        long timestampMs,
        List<DepthLevel> bids,
        List<DepthLevel> asks,
        Map<String, Long> metrics
) {
}
