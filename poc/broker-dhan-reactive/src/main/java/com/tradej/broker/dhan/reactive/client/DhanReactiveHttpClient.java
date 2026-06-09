package com.tradej.broker.dhan.reactive.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.auth.DhanTokenProvider;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.reactive.resilience.MultiBucketRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Reactive HTTP client for Dhan API with rate limiting.
 * 
 * Features:
 * - Token-based rate limiting per API category
 * - Automatic retry with exponential backoff
 * - Reactive Mono/Flux patterns throughout
 */
public final class DhanReactiveHttpClient {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactiveHttpClient.class);
    
    private final WebClient webClient;
    private final DhanTokenProvider tokenProvider;
    private final DhanReactiveConnectionSettings settings;
    private final MultiBucketRateLimiter rateLimiter;
    
    public DhanReactiveHttpClient(
            WebClient webClient,
            DhanTokenProvider tokenProvider,
            DhanReactiveConnectionSettings settings,
            MultiBucketRateLimiter rateLimiter
    ) {
        this.webClient = webClient;
        this.tokenProvider = tokenProvider;
        this.settings = settings;
        this.rateLimiter = rateLimiter;
    }
    
    public Mono<DhanJsonResponse> postJson(String url, ObjectNode payload) {
        return postJson(url, payload, "DATA");
    }
    
    public Mono<DhanJsonResponse> postJson(String url, ObjectNode payload, String rateLimitCategory) {
        return Mono.fromCallable(() -> {
                rateLimiter.acquire(rateLimitCategory);
                return null;
            })
            .flatMap(ignored -> executeRequestWithToken((token) -> 
                webClient.post()
                    .uri(url)
                    .header("access-token", token)
                    .header("client-id", settings.clientId())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
            ));
    }
    
    public Mono<DhanJsonResponse> getJson(String url) {
        return getJson(url, "DATA");
    }
    
    public Mono<DhanJsonResponse> getJson(String url, String rateLimitCategory) {
        return Mono.fromCallable(() -> {
                rateLimiter.acquire(rateLimitCategory);
                return null;
            })
            .flatMap(ignored -> executeRequestWithToken((token) -> 
                webClient.get()
                    .uri(url)
                    .header("access-token", token)
                    .header("client-id", settings.clientId())
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
            ));
    }
    
    public Mono<DhanJsonResponse> deleteJson(String url) {
        return deleteJson(url, "NON_TRADING");
    }
    
    public Mono<DhanJsonResponse> deleteJson(String url, String rateLimitCategory) {
        return Mono.fromCallable(() -> {
                rateLimiter.acquire(rateLimitCategory);
                return null;
            })
            .flatMap(ignored -> executeRequestWithToken((token) -> 
                webClient.delete()
                    .uri(url)
                    .header("access-token", token)
                    .header("client-id", settings.clientId())
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
            ));
    }
    
    public Flux<DhanJsonResponse> getJsonStream(String url) {
        return getJsonStream(url, "DATA");
    }
    
    public Flux<DhanJsonResponse> getJsonStream(String url, String rateLimitCategory) {
        return Mono.fromCallable(() -> {
                rateLimiter.acquire(rateLimitCategory);
                return null;
            })
            .flatMapMany(ignored -> executeRequestWithTokenStream((token) -> 
                webClient.get()
                    .uri(url)
                    .header("access-token", token)
                    .header("client-id", settings.clientId())
                    .header("Accept", "application/json")
                    .retrieve()
                    .bodyToFlux(String.class)
                    .map(DhanJsonResponse::new)
            ));
    }
    
    public Mono<DhanJsonResponse> putJson(String url, ObjectNode payload) {
        return putJson(url, payload, "ORDER");
    }
    
    public Mono<DhanJsonResponse> putJson(String url, ObjectNode payload, String rateLimitCategory) {
        return Mono.fromCallable(() -> {
                rateLimiter.acquire(rateLimitCategory);
                return null;
            })
            .flatMap(ignored -> executeRequestWithToken((token) -> 
                webClient.put()
                    .uri(url)
                    .header("access-token", token)
                    .header("client-id", settings.clientId())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
            ));
    }
    
    private Mono<DhanJsonResponse> executeRequestWithToken(java.util.function.Function<String, Mono<String>> request) {
        return tokenProvider.ensureValidReactive()
            .flatMap(token -> request.apply(token)
                .map(DhanJsonResponse::new)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(10))
                    .filter(this::isRetryable)
                    .jitter(0.5)
                    .doBeforeRetry(signal -> 
                        log.warn("HTTP retry, attempt: {}", signal.totalRetries() + 1)
                    )
                )
            );
    }
    
    private <T> Flux<T> executeRequestWithTokenStream(java.util.function.Function<String, Flux<T>> request) {
        return tokenProvider.ensureValidReactive()
            .flatMapMany(token -> request.apply(token)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                    .maxBackoff(Duration.ofSeconds(10))
                    .filter(this::isRetryable)
                    .jitter(0.5)
                    .doBeforeRetry(signal -> 
                        log.warn("HTTP stream retry, attempt: {}", signal.totalRetries() + 1)
                    )
                )
            );
    }
    
    private WebClient.RequestBodySpec buildRequestWithHeaders(WebClient.RequestBodyUriSpec requestSpec, String url) {
        return requestSpec
            .uri(url)
            .header("access-token", "PLACEHOLDER") // Will be set in executeRequest
            .header("client-id", settings.clientId())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
    }
    
    private boolean isRetryable(Throwable ex) {
        return ex instanceof WebClientResponseException &&
               ((WebClientResponseException) ex).getStatusCode().is5xxServerError();
    }
}
