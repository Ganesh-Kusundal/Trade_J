package com.tradej.broker.dhan.rate;

import com.tradej.broker.dhan.constants.DhanProtocolConstants;

import java.util.EnumMap;
import java.util.Map;

public final class MultiBucketRateLimiter {
    private final Map<ApiCategory, TokenBucketRateLimiter> buckets = new EnumMap<>(ApiCategory.class);

    public MultiBucketRateLimiter() {
        buckets.put(ApiCategory.ORDER, new TokenBucketRateLimiter(
                DhanProtocolConstants.RATE_LIMIT_ORDER_RATE,
                DhanProtocolConstants.RATE_LIMIT_ORDER_CAPACITY
        ));
        buckets.put(ApiCategory.DATA, new TokenBucketRateLimiter(
                DhanProtocolConstants.RATE_LIMIT_DATA_RATE,
                DhanProtocolConstants.RATE_LIMIT_DATA_CAPACITY
        ));
        buckets.put(ApiCategory.QUOTE, new TokenBucketRateLimiter(
                DhanProtocolConstants.RATE_LIMIT_QUOTE_RATE,
                DhanProtocolConstants.RATE_LIMIT_QUOTE_CAPACITY
        ));
        buckets.put(ApiCategory.OPTION_CHAIN, new TokenBucketRateLimiter(
                DhanProtocolConstants.RATE_LIMIT_OPTION_CHAIN_RATE,
                DhanProtocolConstants.RATE_LIMIT_OPTION_CHAIN_CAPACITY
        ));
        buckets.put(ApiCategory.NON_TRADING, new TokenBucketRateLimiter(
                DhanProtocolConstants.RATE_LIMIT_NON_TRADING_RATE,
                DhanProtocolConstants.RATE_LIMIT_NON_TRADING_CAPACITY
        ));
    }

    public void acquire(ApiCategory category) {
        if (category == null) {
            throw new IllegalArgumentException("API category must not be null — rate limiting cannot be bypassed");
        }
        TokenBucketRateLimiter limiter = buckets.get(category);
        if (limiter != null) {
            limiter.acquire();
        }
    }
}
