package com.tradej.broker.upstox.http;

/**
 * Raised when an Upstox REST call returns a non-success HTTP status or {@code status=error} JSON body.
 */
public final class UpstoxApiException extends RuntimeException {

    private final int httpStatus;
    private final String errorCode;

    public UpstoxApiException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean isAuthFailure() {
        return httpStatus == 401 || httpStatus == 403
                || (errorCode != null && errorCode.startsWith("UDAPI"));
    }
}
