package com.tradej.broker.icici.http;

public final class BreezeHttpException extends RuntimeException {
    private final int httpStatus;

    public BreezeHttpException(int httpStatus, String message, String operation) {
        super("ICICI " + operation + " failed (" + httpStatus + "): " + message);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
