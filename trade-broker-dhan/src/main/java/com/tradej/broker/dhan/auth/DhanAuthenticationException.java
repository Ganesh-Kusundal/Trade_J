package com.tradej.broker.dhan.auth;

import com.tradej.broker.dhan.exceptions.DhanBrokerException;

public class DhanAuthenticationException extends DhanBrokerException {
    public DhanAuthenticationException(String message) {
        super(message);
    }

    public DhanAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
