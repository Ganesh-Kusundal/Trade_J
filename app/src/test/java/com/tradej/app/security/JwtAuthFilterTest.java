package com.tradej.app.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class JwtAuthFilterTest {

    private static final String TEST_SECRET =
            "test-secret-with-at-least-32-characters-please-thanks";

    private JwtAuthFilter filter;
    private JwtTokenService service;

    @BeforeEach
    void setUp() throws Exception {
        Constructor<JwtTokenService> ctor =
                JwtTokenService.class.getDeclaredConstructor(String.class, long.class, boolean.class);
        ctor.setAccessible(true);
        service = ctor.newInstance(TEST_SECRET, 3600L, false);
        filter = new JwtAuthFilter(service);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("No Authorization header does not authenticate the request")
    void noAuthHeaderLeavesContextEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(200, res.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Without a Bearer token the security context must remain empty");
    }

    @Test
    @DisplayName("Valid Bearer token populates the security context")
    void validBearerTokenPopulatesContext() throws Exception {
        String token = service.generateToken("admin");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/orders");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "Valid Bearer token must populate the security context");
        assertEquals("admin", auth.getName());
    }

    @Test
    @DisplayName("Expired Bearer token does not authenticate")
    void expiredTokenDoesNotAuthenticate() throws Exception {
        Constructor<JwtTokenService> ctor =
                JwtTokenService.class.getDeclaredConstructor(String.class, long.class, boolean.class);
        ctor.setAccessible(true);
        JwtTokenService shortLived = ctor.newInstance(TEST_SECRET, -10L, false);
        String token = shortLived.generateToken("admin");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/orders");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Expired tokens must not populate the security context");
    }

    @Test
    @DisplayName("Tampered Bearer token does not authenticate")
    void tamperedTokenDoesNotAuthenticate() throws Exception {
        String token = service.generateToken("admin");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/orders");
        req.addHeader("Authorization", "Bearer " + tampered);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "Tampered tokens must not populate the security context");
    }
}
