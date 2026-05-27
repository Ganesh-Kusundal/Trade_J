package com.tradej.broker.dhan.exceptions;

/**
 * Base exception for all Dhan broker operations.
 *
 * <p>All Dhan-specific exceptions (authentication, HTTP, data mapping, etc.)
 * extend this class so callers can catch a single type when they want to
 * handle any Dhan broker error uniformly.
 */
public class DhanBrokerException extends RuntimeException {

    public DhanBrokerException(String message) {
        super(message);
    }

    public DhanBrokerException(String message, Throwable cause) {
        super(message, cause);
    }
}
