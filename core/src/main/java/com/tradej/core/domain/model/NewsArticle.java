package com.tradej.core.domain.model;

import java.time.Instant;

/**
 * Represents a news article from a broker news feed.
 */
public record NewsArticle(
        String instrumentKey,
        String heading,
        String summary,
        String thumbnail,
        String articleLink,
        long publishedTimeMs
) {
    public Instant publishedAt() {
        return Instant.ofEpochMilli(publishedTimeMs);
    }
}