package com.tradej.broker.upstox.websocket;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxFeedAuthorizerTest {

    @Test
    void isExpiredReturnsTrueWhenNowExceedsExpiry() {
        var feed = new UpstoxFeedAuthorizer.AuthorizedFeed("ws://example.com", 1_000L);
        assertTrue(feed.isExpired(1_000L), "should be expired when now equals expiry");
        assertTrue(feed.isExpired(1_001L), "should be expired when now exceeds expiry");
    }

    @Test
    void isExpiredReturnsFalseWhenNowIsBeforeExpiry() {
        var feed = new UpstoxFeedAuthorizer.AuthorizedFeed("ws://example.com", 2_000L);
        assertFalse(feed.isExpired(1_999L), "should not be expired before expiry");
    }

    @Test
    void isExpiredReturnsFalseWhenExpiryIsZero() {
        var feed = new UpstoxFeedAuthorizer.AuthorizedFeed("ws://example.com", 0L);
        assertFalse(feed.isExpired(Long.MAX_VALUE), "no expiry set should never be expired");
    }

    @Test
    void isExpiredReturnsFalseWhenExpiryIsNegative() {
        var feed = new UpstoxFeedAuthorizer.AuthorizedFeed("ws://example.com", -1L);
        assertFalse(feed.isExpired(Long.MAX_VALUE), "negative expiry should never be expired");
    }
}
