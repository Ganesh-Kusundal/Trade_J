package com.tradej.broker.dhan.reactive.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.reactive.auth.DhanTokenProvider;
import com.tradej.broker.dhan.reactive.config.DhanReactiveConnectionSettings;
import com.tradej.broker.dhan.reactive.mapper.DhanJsonResponse;
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
 * Reactive HTTP client for Dhan API.
 * 
 * GREEN Phase: Minimal implementation to make tests pass.
 */
public final class DhanReactiveHttpClient {
    
    private static final Logger log = LoggerFactory.getLogger(DhanReactiveHttpClient.class);
    
    private final WebClient webClient;
    private final DhanTokenProvider tokenProvider;
    private final DhanReactiveConnectionSettings settings;
    
    public DhanReactiveHttpClient(
            WebClient webClient,
            DhanTokenProvider tokenProvider,
            DhanReactiveConnectionSettings settings
    ) {
        this.webClient = webClient;
        this.tokenProvider = tokenProvider;
        this.settings = settings;
    }
    
    public Mono<DhanJsonResponse> postJson(String url, ObjectNode payload) {
        return executeRequestWithToken((token) -> 
            webClient.post()
                .uri(url)
                .header("access-token", token)
                .header("client-id", settings.clientId())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
        );
    }
    
    public Mono<DhanJsonResponse> getJson(String url) {
        return executeRequestWithToken((token) -> 
            webClient.get()
                .uri(url)
                .header("access-token", token)
                .header("client-id", settings.clientId())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
        );
    }
    
    public Mono<DhanJsonResponse> deleteJson(String url) {
        return executeRequestWithToken((token) -> 
            webClient.delete()
                .uri(url)
                .header("access-token", token)
                .header("client-id", settings.clientId())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(String.class)
        );
    }
    
    public Flux<DhanJsonResponse> getJsonStream(String url) {
        return executeRequestWithTokenStream((token) -> 
            webClient.get()
                .uri(url)
                .header("access-token", token)
                .header("client-id", settings.clientId())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(String.class)
                .map(DhanJsonResponse::new)
        );
    }
    
    public Mono<DhanJsonResponse> putJson(String url, ObjectNode payload) {
        return executeRequestWithToken((token) -> 
            webClient.put()
                .uri(url)
                .header("access-token", token)
                .header("client-id", settings.clientId())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
        );
    }
    
    private Mono<DhanJsonResponse> executeRequestWithToken(java.util.function.Function<String, Mono<String>> request) {
        return tokenProvider.ensureValidReactive()
            .flatMap(token -> request.apply(token)
                .map(DhanJsonResponse::new)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                    .filter(this::isRetryable)
                    .doBeforeRetry(signal -> 
                        log.warn("HTTP retry, attempt: {}", signal.totalRetries() + 1)
                    )
                )
            );
    }
    
    private <T> Flux<T> executeRequestWithTokenStream(java.util.function.Function<String, Flux<T>> request) {
        return tokenProvider.ensureValidReactive()
            .flatMapMany(token -> request.apply(token)
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                    .filter(this::isRetryable)
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
