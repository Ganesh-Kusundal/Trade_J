package com.tradej.broker.api.exceptions;

public class BrokerRateLimitException extends RuntimeException {
    public BrokerRateLimitException(String message) {
        super(message);
    }
}
