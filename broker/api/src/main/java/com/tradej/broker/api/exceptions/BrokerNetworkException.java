package com.tradej.broker.api.exceptions;

public class BrokerNetworkException extends RuntimeException {
    public BrokerNetworkException(String message) {
        super(message);
    }
    public BrokerNetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
