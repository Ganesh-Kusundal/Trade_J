package com.tradej.app.api;

import com.tradej.app.service.NewsApplicationService;
import com.tradej.core.domain.model.NewsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for retrieving news articles from broker APIs.
 * Delegates to {@link NewsApplicationService}.
 */
@RestController
@RequestMapping("/api/v1/news")
public class NewsController {

    private final NewsApplicationService newsService;

    public NewsController(NewsApplicationService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    ResponseEntity<NewsResponse> getNews(
            @RequestParam String category,
            @RequestParam(required = false) String instrumentKeys,
            @RequestParam(defaultValue = "1") int pageNumber,
            @RequestParam(defaultValue = "100") int pageSize
    ) {
        return ResponseEntity.ok(newsService.getNews(category, instrumentKeys, pageNumber, pageSize));
    }
}
