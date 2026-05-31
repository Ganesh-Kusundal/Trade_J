package com.tradej.app.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Servlet filter that applies rate limiting per endpoint with burst allowance.
 * <p>
 * Design:
 * <ul>
 *   <li>Token-bucket per endpoint with configurable capacity and refill rate.</li>
 *   <li>Burst allowance: UI endpoints get a higher initial token pool that refills
 *       quickly so the frontend can initialise without false positives.</li>
 *   <li>Returns {@code 429 Too Many Requests} with {@code Retry-After} and
 *       {@code X-RateLimit-*} headers when throttled.</li>
 *   <li>Exposes counters via {@link #getMetrics()} for observability.</li>
 *   <li>Non-blocking: uses {@link System#nanoTime()} per-endpoint checks with
 *       no locks held across the request lifecycle.</li>
 * </ul>
 */
@Component
@Order(1)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Default rate limit: 30 requests/second per path (burst up to 60). */
    static final int DEFAULT_CAPACITY = 60;
    static final double DEFAULT_REFILL_PER_SEC = 30.0;

    /** UI symbols endpoint: 10 requests/second (burst up to 20) — frontend fetches once. */
    private static final int SYMBOLS_CAPACITY = 20;
    private static final double SYMBOLS_REFILL_PER_SEC = 10.0;

    /** Analytics SQL: 2 requests/second (burst up to 5). */
    private static final int ANALYTICS_SQL_CAPACITY = 5;
    private static final double ANALYTICS_SQL_REFILL_PER_SEC = 2.0;

    /** Admin/summary endpoint: 5 requests/second (burst up to 10) — dashboard polls every 3s. */
    private static final int ADMIN_CAPACITY = 10;
    private static final double ADMIN_REFILL_PER_SEC = 5.0;

    /** Per-endpoint rate limiter state. */
    private static final class Bucket {
        final int capacity;
        final double refillPerSec;
        private double tokens;
        private long lastRefillNs;
        final LongAdder rejectCount = new LongAdder();
        final LongAdder requestCount = new LongAdder();

        Bucket(int capacity, double refillPerSec) {
            this.capacity = capacity;
            this.refillPerSec = refillPerSec;
            this.tokens = capacity;
            this.lastRefillNs = System.nanoTime();
        }

        /** Returns true if the request is allowed, false if rate-limited. */
        synchronized boolean tryConsume() {
            long now = System.nanoTime();
            double elapsedSec = (now - lastRefillNs) / 1_000_000_000.0;
            if (elapsedSec > 0) {
                double refill = elapsedSec * refillPerSec;
                tokens = Math.min(capacity, tokens + refill);
                lastRefillNs = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                requestCount.increment();
                return true;
            }
            rejectCount.increment();
            return false;
        }

        /** Seconds until at least one token is available. */
        synchronized double secondsUntilReady() {
            if (tokens >= 1.0) return 0;
            return (1.0 - tokens) / refillPerSec;
        }

        /** Read current token count rounded to tenths, under synchronization. */
        synchronized double tokensRoundedToTenths() {
            return Math.round(tokens * 10.0) / 10.0;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // ── Exposed metrics ──

    /** Total requests seen since startup. */
    private final AtomicLong totalRequests = new AtomicLong();

    /** Total rejected (429) responses since startup. */
    private final AtomicLong totalRejected = new AtomicLong();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        totalRequests.incrementAndGet();

        String path = normalizePath(httpRequest.getRequestURI());

        // Skip rate limiting for actuator, static resources
        if (path.startsWith("/actuator") || path.startsWith("/console") || path.startsWith("/dashboard")) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = getBucketForPath(path);
        if (!bucket.tryConsume()) {
            totalRejected.incrementAndGet();
            double retryAfterSec = Math.ceil(bucket.secondsUntilReady());

            httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResponse.setHeader("Retry-After", String.valueOf((int) Math.ceil(retryAfterSec)));
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(bucket.capacity));
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            httpResponse.setHeader("X-RateLimit-Reset", String.valueOf(System.currentTimeMillis() / 1000 + (long) Math.ceil(retryAfterSec)));
            httpResponse.setContentType("application/json");

            String body = String.format(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":%d,\"path\":\"%s\"}",
                    (int) Math.ceil(retryAfterSec), path
            );
            var writer = httpResponse.getWriter();
            writer.write(body);
            writer.flush();

            log.warn("Rate limited request to {} (retry-after={}s, totalRejected={})",
                    path, (int) Math.ceil(retryAfterSec), totalRejected.get());
            return;
        }

        chain.doFilter(request, response);
    }

    /** Get current metrics for observability / monitoring. */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new java.util.LinkedHashMap<>();
        metrics.put("totalRequests", totalRequests.get());
        metrics.put("totalRejected", totalRejected.get());
        metrics.put("activeClients", estimateActiveClients());

        Map<String, Object> endpointMetrics = new java.util.LinkedHashMap<>();
        for (var entry : buckets.entrySet()) {
            Bucket b = entry.getValue();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("requests", b.requestCount.sum());
            m.put("rejected", b.rejectCount.sum());
            m.put("capacity", b.capacity);
            m.put("tokensRemaining", b.tokensRoundedToTenths());
            endpointMetrics.put(entry.getKey(), m);
        }
        metrics.put("endpoints", endpointMetrics);
        return metrics;
    }

    // ── Private helpers ──

    private String normalizePath(String uri) {
        // Strip query string and trailing slash
        int qIdx = uri.indexOf('?');
        String path = qIdx >= 0 ? uri.substring(0, qIdx) : uri;
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private Bucket getBucketForPath(String path) {
        return buckets.computeIfAbsent(path, this::createBucket);
    }

    private Bucket createBucket(String path) {
        if (path.endsWith("/api/v1/analytics/sql")) {
            return new Bucket(ANALYTICS_SQL_CAPACITY, ANALYTICS_SQL_REFILL_PER_SEC);
        }
        if (path.contains("/symbols")) {
            return new Bucket(SYMBOLS_CAPACITY, SYMBOLS_REFILL_PER_SEC);
        }
        if (path.contains("/admin/")) {
            return new Bucket(ADMIN_CAPACITY, ADMIN_REFILL_PER_SEC);
        }
        return new Bucket(DEFAULT_CAPACITY, DEFAULT_REFILL_PER_SEC);
    }

    /** Estimate active clients based on unique paths that have seen traffic. */
    private int estimateActiveClients() {
        return buckets.size();
    }
}
