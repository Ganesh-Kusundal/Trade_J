package com.tradej.app.api;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.core.domain.model.NewsArticle;
import com.tradej.core.domain.model.NewsMetadata;
import com.tradej.core.domain.model.NewsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for retrieving news articles from broker APIs.
 * Supports three categories: instrument_keys, positions, and holdings.
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final IBrokerConnection brokerConnection;

    public NewsController(IBrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    /**
     * Retrieves news articles based on the specified category.
     *
     * @param category       Category of news to fetch: instrument_keys, positions, or holdings
     * @param instrumentKeys Comma-separated instrument keys (required for instrument_keys category)
     * @param pageNumber     Page number (1-100), default 1
     * @param pageSize       Number of records per page (1-100), default 100
     * @return News response with articles and pagination metadata
     */
    @GetMapping
    ResponseEntity<NewsResponse> getNews(
            @RequestParam String category,
            @RequestParam(required = false) String instrumentKeys,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "100") int pageSize
    ) {
        NewsProvider newsProvider = brokerConnection.news();
        List<NewsArticle> articles;

        switch (category) {
            case "instrument_keys":
                if (instrumentKeys == null || instrumentKeys.isBlank()) {
                    throw new IllegalArgumentException("instrument_keys parameter is required when category=instrument_keys");
                }
                validateInstrumentKeys(instrumentKeys);
                articles = newsProvider.getNewsByInstrumentKeys(instrumentKeys, pageNumber, pageSize);
                break;
            case "positions":
                articles = newsProvider.getNewsForPositions(pageNumber, pageSize);
                break;
            case "holdings":
                articles = newsProvider.getNewsForHoldings(pageNumber, pageSize);
                break;
            default:
                throw new IllegalArgumentException("Invalid category: " + category + ". Allowed values: instrument_keys, positions, holdings");
        }

        NewsMetadata metadata = new NewsMetadata(pageNumber, pageSize, articles.size(), 1);
        return ResponseEntity.ok(new NewsResponse(articles, metadata));
    }

    private void validateInstrumentKeys(String instrumentKeys) {
        String[] keys = instrumentKeys.split(",");
        if (keys.length > 30) {
            throw new IllegalArgumentException("Maximum 30 instrument keys allowed per request, got: " + keys.length);
        }
        for (String key : keys) {
            if (key.trim().isEmpty()) {
                throw new IllegalArgumentException("Empty instrument key found in: " + instrumentKeys);
            }
        }
    }
}