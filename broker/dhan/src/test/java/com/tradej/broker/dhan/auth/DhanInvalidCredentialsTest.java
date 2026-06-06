package com.tradej.broker.dhan.auth;

import com.tradej.broker.api.resilience.BrokerErrorCategory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class DhanInvalidCredentialsTest {

    @Test
    void authExceptionCarriesDescriptiveMessage() {
        DhanAuthenticationException ex = new DhanAuthenticationException("Invalid token");
        assertTrue(ex.getMessage().contains("Invalid token"));
    }

    @Test
    void authExceptionIsRuntimeException() {
        DhanAuthenticationException ex = new DhanAuthenticationException("Expired");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void authRevokedIsNotRetryable() {
        assertFalse(BrokerErrorCategory.isRetryable(BrokerErrorCategory.AUTH_REVOKED),
                "Auth errors should not be retried — they require re-authentication");
    }

    @Test
    void rateLimitedIsRetryable() {
        assertTrue(BrokerErrorCategory.isRetryable(BrokerErrorCategory.RATE_LIMITED),
                "Rate limit errors should be retried with backoff");
    }

    @Test
    void serviceDownIsRetryable() {
        assertTrue(BrokerErrorCategory.isRetryable(BrokerErrorCategory.SERVICE_DOWN),
                "Service down errors should be retried");
    }

    @Test
    void validationErrorIsNotRetryable() {
        assertFalse(BrokerErrorCategory.isRetryable(BrokerErrorCategory.VALIDATION_ERROR),
                "Validation errors should not be retried — they indicate bad input");
    }

    @Test
    void unknownIsRetryable() {
        assertTrue(BrokerErrorCategory.isRetryable(BrokerErrorCategory.UNKNOWN),
                "Unknown errors are retried by default — they may be transient");
    }
}
