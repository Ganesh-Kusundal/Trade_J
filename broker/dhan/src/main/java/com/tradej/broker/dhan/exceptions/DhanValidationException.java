package com.tradej.broker.dhan.exceptions;

/**
 * Exception thrown when an order request fails DhanHQ safety validation.
 *
 * <p>Contains structured validation details for programmatic handling and
 * user-facing error messages.
 */
public class DhanValidationException extends DhanBrokerException {

    private final ValidationCode code;
    private final String field;
    private final Object rejectedValue;
    private final String suggestion;

    public DhanValidationException(ValidationCode code, String message) {
        this(code, message, null, null, null);
    }

    public DhanValidationException(ValidationCode code, String message, String field, Object rejectedValue) {
        this(code, message, field, rejectedValue, null);
    }

    public DhanValidationException(
            ValidationCode code,
            String message,
            String field,
            Object rejectedValue,
            String suggestion
    ) {
        super(message);
        this.code = code;
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.suggestion = suggestion;
    }

    public ValidationCode code() {
        return code;
    }

    public String field() {
        return field;
    }

    public Object rejectedValue() {
        return rejectedValue;
    }

    public String suggestion() {
        return suggestion;
    }

    /**
     * Standardized validation failure codes.
     */
    public enum ValidationCode {
        /** Product type not allowed for the instrument's exchange segment. */
        INVALID_PRODUCT_TYPE_FOR_SEGMENT,
        /** Order quantity not a multiple of the instrument's lot size. */
        INVALID_LOT_SIZE,
        /** Notional value exceeds the configured maximum (default ₹50,000). */
        NOTIONAL_EXCEEDS_LIMIT,
        /** Market order not allowed without price reference for notional check. */
        MARKET_ORDER_REQUIRES_LTP,
        /** Order type defaulted to LIMIT for safety. */
        ORDER_TYPE_DEFAULTED_TO_LIMIT,
        /** Confirmation token required for live mutating operations. */
        CONFIRMATION_REQUIRED,
        /** Kill switch not supported in sandbox environment. */
        KILL_SWITCH_UNSUPPORTED_IN_SANDBOX,
        /** Instrument not found in catalog. */
        INSTRUMENT_NOT_FOUND
    }

    /**
     * Create a user-friendly message with context.
     */
    public String toDetailedMessage() {
        StringBuilder sb = new StringBuilder(getMessage());
        if (field != null) {
            sb.append(" [field: ").append(field).append("]");
        }
        if (rejectedValue != null) {
            sb.append(" [value: ").append(rejectedValue).append("]");
        }
        if (suggestion != null) {
            sb.append(" - ").append(suggestion);
        }
        return sb.toString();
    }
}