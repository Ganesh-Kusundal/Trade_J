package com.tradej.broker.upstox.auth;

/**
 * Exception thrown during Upstox OAuth authentication.
 */
public class UpstoxAuthException extends RuntimeException {
    public UpstoxAuthException(String message) {
        super(message);
    }

    public UpstoxAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
