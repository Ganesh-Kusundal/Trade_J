package com.tradej.app.service;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.NewsProvider;
import com.tradej.core.domain.model.NewsArticle;
import com.tradej.core.domain.model.NewsMetadata;
import com.tradej.core.domain.model.NewsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Application service for news retrieval operations.
 * Encapsulates broker news provider access and validation logic.
 */
@Service
public class NewsApplicationService {

    private final IBrokerConnection brokerConnection;

    public NewsApplicationService(IBrokerConnection brokerConnection) {
        this.brokerConnection = brokerConnection;
    }

    public NewsResponse getNews(String category, String instrumentKeys, int pageNumber, int pageSize) {
        NewsProvider newsProvider = brokerConnection.news();
        List<NewsArticle> articles = switch (category) {
            case "instrument_keys" -> {
                if (instrumentKeys == null || instrumentKeys.isBlank()) {
                    throw new IllegalArgumentException("instrument_keys parameter is required when category=instrument_keys");
                }
                validateInstrumentKeys(instrumentKeys);
                yield newsProvider.getNewsByInstrumentKeys(instrumentKeys, pageNumber, pageSize);
            }
            case "positions" -> newsProvider.getNewsForPositions(pageNumber, pageSize);
            case "holdings" -> newsProvider.getNewsForHoldings(pageNumber, pageSize);
            default -> throw new IllegalArgumentException(
                    "Invalid category: " + category + ". Allowed values: instrument_keys, positions, holdings");
        };

        NewsMetadata metadata = new NewsMetadata(pageNumber, pageSize, articles.size(), 1);
        return new NewsResponse(articles, metadata);
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
