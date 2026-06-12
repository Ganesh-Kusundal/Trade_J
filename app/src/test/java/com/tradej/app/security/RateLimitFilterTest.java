package com.tradej.app.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the sliding-window rate limiter on order endpoints.
 */
@Tag("unit")
class RateLimitFilterTest {

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    @DisplayName("shouldNotFilter returns true for non-order paths")
    void shouldNotFilterNonOrderPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/ltp");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter returns false for order paths")
    void shouldFilterOrderPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("shouldNotFilter returns false for order sub-paths")
    void shouldFilterOrderSubPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders/place");
        assertFalse(filter.shouldNotFilter(request));
    }

    @Test
    @DisplayName("First 10 requests pass through successfully")
    void firstTenRequestsPass() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
            request.setRemoteAddr("192.168.1.100");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilterInternal(request, response, chain);

            assertEquals(200, response.getStatus(),
                    "Request " + (i + 1) + " should pass through");
        }
    }

    @Test
    @DisplayName("11th request in same window returns 429")
    void eleventhRequestReturns429() throws Exception {
        // Exhaust the 10-request limit
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
            request.setRemoteAddr("10.0.0.50");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilterInternal(request, response, chain);
        }

        // 11th request should be rate limited
        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/v1/orders");
        blockedRequest.setRemoteAddr("10.0.0.50");
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        MockFilterChain blockedChain = new MockFilterChain();

        filter.doFilterInternal(blockedRequest, blockedResponse, blockedChain);

        assertEquals(429, blockedResponse.getStatus(),
                "11th request should be rate limited with 429");
        assertTrue(blockedResponse.getContentAsString().contains("RATE_LIMITED"),
                "Response body should contain RATE_LIMITED code");
    }

    @Test
    @DisplayName("Different client IPs have independent rate limits")
    void independentLimitsPerIp() throws Exception {
        // Exhaust limit for IP-A
        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
            request.setRemoteAddr("10.0.0.1");
            filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        }

        // IP-A should be blocked
        MockHttpServletRequest blockedA = new MockHttpServletRequest("POST", "/api/v1/orders");
        blockedA.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse responseA = new MockHttpServletResponse();
        filter.doFilterInternal(blockedA, responseA, new MockFilterChain());
        assertEquals(429, responseA.getStatus(), "IP-A should be rate limited");

        // IP-B should still pass
        MockHttpServletRequest passB = new MockHttpServletRequest("POST", "/api/v1/orders");
        passB.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilterInternal(passB, responseB, new MockFilterChain());
        assertEquals(200, responseB.getStatus(), "IP-B should not be rate limited");
    }

    @Test
    @DisplayName("Non-order endpoints are not rate limited even after many calls")
    void nonOrderEndpointsNotLimited() throws Exception {
        for (int i = 0; i < 100; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/market/ltp");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            // shouldNotFilter returns true, so the filter delegates directly to chain
        }
        // No exception thrown — 100 requests to non-order endpoint pass freely
    }
}
