package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.NewsProvider;
import com.tradej.broker.upstox.rest.UpstoxNewsRestClient;
import com.tradej.core.domain.model.NewsArticle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class UpstoxNewsProvider implements NewsProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final UpstoxNewsRestClient newsRestClient;

    public UpstoxNewsProvider(UpstoxNewsRestClient newsRestClient) {
        this.newsRestClient = newsRestClient;
    }

    @Override
    public List<NewsArticle> getNewsByInstrumentKeys(String instrumentKeys, int pageNumber, int pageSize) {
        JsonNode response = newsRestClient.getNewsByInstrumentKeys(instrumentKeys, pageNumber, pageSize);
        return parseResponse(response);
    }

    @Override
    public List<NewsArticle> getNewsForPositions(int pageNumber, int pageSize) {
        JsonNode response = newsRestClient.getNewsForPositions(pageNumber, pageSize);
        return parseResponse(response);
    }

    @Override
    public List<NewsArticle> getNewsForHoldings(int pageNumber, int pageSize) {
        JsonNode response = newsRestClient.getNewsForHoldings(pageNumber, pageSize);
        return parseResponse(response);
    }

    private List<NewsArticle> parseResponse(JsonNode response) {
        List<NewsArticle> articles = new ArrayList<>();

        if (response == null || response.isMissingNode()) {
            return articles;
        }

        JsonNode dataNode = response.path("data");
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            return articles;
        }

        dataNode.fields().forEachRemaining(entry -> {
            String instrumentKey = entry.getKey();
            JsonNode newsArray = entry.getValue();

            if (newsArray.isArray()) {
                for (JsonNode newsItem : newsArray) {
                    NewsArticle article = parseNewsArticle(instrumentKey, newsItem);
                    if (article != null) {
                        articles.add(article);
                    }
                }
            }
        });

        return articles;
    }

    private NewsArticle parseNewsArticle(String instrumentKey, JsonNode node) {
        try {
            String heading = node.path("heading").asText("");
            String summary = node.path("summary").asText("");
            String thumbnail = node.path("thumbnail").asText("");
            String articleLink = node.path("article_link").asText("");
            long publishedTimeMs = node.path("published_time").asLong(0L);

            if (publishedTimeMs == 0) {
                // Try alternative field name
                publishedTimeMs = node.path("publishedTime").asLong(0L);
            }

            return new NewsArticle(
                    instrumentKey,
                    heading,
                    summary,
                    thumbnail,
                    articleLink,
                    publishedTimeMs
            );
        } catch (Exception e) {
            // Log and skip malformed entries
            return null;
        }
    }
}