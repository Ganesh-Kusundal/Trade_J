package com.tradej.broker.api.port;

import com.tradej.core.domain.model.NewsArticle;

import java.util.List;

/**
 * Broker capability for retrieving news articles.
 * Implementations provide access to market news and announcements.
 */
public interface NewsProvider {

    /**
     * Retrieves news articles for the specified instrument keys.
     *
     * @param instrumentKeys Comma-separated list of instrument keys
     * @param pageNumber Page number (1-based)
     * @param pageSize Number of articles per page
     * @return List of news articles
     */
    List<NewsArticle> getNewsByInstrumentKeys(String instrumentKeys, int pageNumber, int pageSize);

    /**
     * Retrieves news articles relevant to current positions.
     *
     * @param pageNumber Page number (1-based)
     * @param pageSize Number of articles per page
     * @return List of news articles
     */
    List<NewsArticle> getNewsForPositions(int pageNumber, int pageSize);

    /**
     * Retrieves news articles relevant to current holdings.
     *
     * @param pageNumber Page number (1-based)
     * @param pageSize Number of articles per page
     * @return List of news articles
     */
    List<NewsArticle> getNewsForHoldings(int pageNumber, int pageSize);
}