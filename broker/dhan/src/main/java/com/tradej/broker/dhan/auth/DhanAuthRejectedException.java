package com.tradej.broker.dhan.auth;

import com.tradej.broker.dhan.exceptions.DhanBrokerException;

/**
 * Dhan authentication endpoint returned HTTP 200 with a business-level rejection
 * (for example TOTP mint rate limiting).
 */
public final class DhanAuthRejectedException extends DhanBrokerException {

    private final boolean rateLimited;

    public DhanAuthRejectedException(String message, boolean rateLimited) {
        super(message);
        this.rateLimited = rateLimited;
    }

    public boolean rateLimited() {
        return rateLimited;
    }
}
