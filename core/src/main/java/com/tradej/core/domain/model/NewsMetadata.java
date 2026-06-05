package com.tradej.core.domain.model;

/**
 * Response metadata for paginated news results.
 */
public record NewsMetadata(
        int pageNumber,
        int pageSize,
        int totalRecords,
        int totalPages
) {
}