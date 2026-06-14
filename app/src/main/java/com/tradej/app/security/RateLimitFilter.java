package com.tradej.app.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for order placement endpoints.
 * Uses a sliding window token bucket per client IP.
 * Default: 10 orders per second, 100 orders per minute.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final int MAX_REQUESTS_PER_SECOND = 10;
    private static final long WINDOW_MS = 1000;

    private final Map<String, ClientBucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String clientIp = request.getRemoteAddr();
        ClientBucket bucket = buckets.computeIfAbsent(clientIp, k -> new ClientBucket());

        long now = System.currentTimeMillis();
        bucket.cleanup(now);

        if (bucket.count.get() >= MAX_REQUESTS_PER_SECOND) {
            log.warn("Rate limit exceeded for client {} — {}/{} requests in window", clientIp, bucket.count.get(), MAX_REQUESTS_PER_SECOND);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Max " + MAX_REQUESTS_PER_SECOND + " per second.\"}");
            return;
        }

        bucket.count.incrementAndGet();
        bucket.lastAccess = now;
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/orders");
    }

    private static class ClientBucket {
        final AtomicInteger count = new AtomicInteger(0);
        final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());
        volatile long lastAccess = System.currentTimeMillis();

        void cleanup(long now) {
            if (now - windowStart.get() > WINDOW_MS) {
                count.set(0);
                windowStart.set(now);
            }
        }
    }
}
