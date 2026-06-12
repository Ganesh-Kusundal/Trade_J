package com.tradej.broker.upstox.http;

/**
 * Raised when an Upstox REST call returns a non-success HTTP status or {@code status=error} JSON body.
 */
public class UpstoxApiException extends RuntimeException {

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

    // ---- Typed subclasses (used by UpstoxErrorClassifier for strong-typed mapping) ----
    // Pre-existing references in UpstoxErrorClassifier.map() — defining them here
    // restores the contract that those references assumed.

    public static class InvalidOrderType extends UpstoxApiException {
        public InvalidOrderType(String message) { super(400, "UDAPI1004", message); }
    }

    public static class MissingValidity extends UpstoxApiException {
        public MissingValidity(String message) { super(400, "UDAPI1007", message); }
    }

    public static class InvalidValidity extends UpstoxApiException {
        public InvalidValidity(String message) { super(400, "UDAPI1055", message); }
    }

    public static class UplinkBusinessRequired extends UpstoxApiException {
        public UplinkBusinessRequired(String message) { super(403, "UDAPI100049", message); }
    }

    public static class OrderNotFound extends UpstoxApiException {
        public OrderNotFound(String message) { super(404, "UDAPI100010", message); }
    }

    public static class CannotCancelFinalized extends UpstoxApiException {
        public CannotCancelFinalized(String message) { super(400, "UDAPI100040", message); }
    }

    public static class CannotModifyFinalized extends UpstoxApiException {
        public CannotModifyFinalized(String message) { super(400, "UDAPI100041", message); }
    }

    public static class MissingOrderId extends UpstoxApiException {
        public MissingOrderId(String message) { super(400, "UDAPI1023", message); }
    }

    public static class MalformedOrderId extends UpstoxApiException {
        public MalformedOrderId(String message) { super(400, "UDAPI1010", message); }
    }

    public static class MissingTriggerPrice extends UpstoxApiException {
        public MissingTriggerPrice(String message) { super(400, "UDAPI1036", message); }
    }

    public static class MissingPrice extends UpstoxApiException {
        public MissingPrice(String message) { super(400, "UDAPI1029", message); }
    }

    public static class UnexpectedPrice extends UpstoxApiException {
        public UnexpectedPrice(String message) { super(400, "UDAPI1030", message); }
    }

    public static class MissingPriceAndTrigger extends UpstoxApiException {
        public MissingPriceAndTrigger(String message) { super(400, "UDAPI1031", message); }
    }

    public static class MissingPriceOrTrigger extends UpstoxApiException {
        public MissingPriceOrTrigger(String message) { super(400, "UDAPI1032", message); }
    }

    public static class DisclosedQuantityTooSmall extends UpstoxApiException {
        public DisclosedQuantityTooSmall(String message) { super(400, "UDAPI1039", message); }
    }

    public static class StaticIpBlocked extends UpstoxApiException {
        public StaticIpBlocked(String message) { super(403, "UDAPI1154", message); }
    }

    public static class InvalidAlgoName extends UpstoxApiException {
        public InvalidAlgoName(String message) { super(400, "UDAPI1156", message); }
    }

    public static class MarketOrderBlocked extends UpstoxApiException {
        public MarketOrderBlocked(String message) { super(403, "UDAPI1158", message); }
    }

    public static class MarketProtectionOutOfRange extends UpstoxApiException {
        public MarketProtectionOutOfRange(String message) { super(400, "UDAPI1159", message); }
    }

    public static class InvalidMarketProtection extends UpstoxApiException {
        public InvalidMarketProtection(String message) { super(400, "UDAPI1160", message); }
    }

    public static class McxTemporarilyDisabled extends UpstoxApiException {
        public McxTemporarilyDisabled(String message) { super(503, "UDAPI1161", message); }
    }
}
