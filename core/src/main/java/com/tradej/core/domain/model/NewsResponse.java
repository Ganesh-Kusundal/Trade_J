package com.tradej.core.domain.model;

import java.util.List;

/**
 * Complete news response including articles and pagination metadata.
 */
public record NewsResponse(
        List<NewsArticle> articles,
        NewsMetadata metadata
) {
}