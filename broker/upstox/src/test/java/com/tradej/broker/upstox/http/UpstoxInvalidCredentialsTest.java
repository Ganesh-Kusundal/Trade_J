package com.tradej.broker.upstox.http;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxInvalidCredentialsTest {

    @Test
    void http401IsAuthFailure() {
        UpstoxApiException ex = new UpstoxApiException(401, null, "Unauthorized");
        assertTrue(ex.isAuthFailure());
    }

    @Test
    void http403IsAuthFailure() {
        UpstoxApiException ex = new UpstoxApiException(403, null, "Forbidden");
        assertTrue(ex.isAuthFailure());
    }

    @Test
    void upstoxErrorCodeIsAuthFailure() {
        UpstoxApiException ex = new UpstoxApiException(400, "UDAPI1001", "Token issue");
        assertTrue(ex.isAuthFailure());
    }

    @Test
    void http200WithNoErrorCodeIsNotAuthFailure() {
        UpstoxApiException ex = new UpstoxApiException(200, null, "Success");
        assertFalse(ex.isAuthFailure());
    }

    @Test
    void http400WithoutUDAPIIsNotAuthFailure() {
        UpstoxApiException ex = new UpstoxApiException(400, "VE001", "Invalid quantity");
        assertFalse(ex.isAuthFailure());
    }

    @Test
    void httpStatusIsPreserved() {
        UpstoxApiException ex = new UpstoxApiException(429, null, "Rate limited");
        assertEquals(429, ex.httpStatus());
    }

    @Test
    void errorCodeIsPreserved() {
        UpstoxApiException ex = new UpstoxApiException(400, "UDAPI1001", "Token expired");
        assertEquals("UDAPI1001", ex.errorCode());
    }

    @Test
    void authRevokedIsNotRetryable() {
        assertFalse(BrokerErrorCategory.isRetryable(BrokerErrorCategory.AUTH_REVOKED));
    }

    @Test
    void rateLimitedIsRetryable() {
        assertTrue(BrokerErrorCategory.isRetryable(BrokerErrorCategory.RATE_LIMITED));
    }

    @Test
    void serviceDownIsRetryable() {
        assertTrue(BrokerErrorCategory.isRetryable(BrokerErrorCategory.SERVICE_DOWN));
    }
}
