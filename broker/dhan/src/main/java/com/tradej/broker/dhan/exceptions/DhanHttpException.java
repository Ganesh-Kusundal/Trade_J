package com.tradej.broker.dhan.exceptions;

/**
 * Exception thrown when a Dhan HTTP API call fails.
 *
 * <p>Covers both transport errors (I/O, interruption) and non-200 status
 * responses. Authentication failures raise
 * {@link com.tradej.broker.dhan.auth.DhanAuthenticationException} instead.
 */
public class DhanHttpException extends DhanBrokerException {

    public DhanHttpException(String message) {
        super(message);
    }

    public DhanHttpException(String message, Throwable cause) {
        super(message, cause);
    }
}
