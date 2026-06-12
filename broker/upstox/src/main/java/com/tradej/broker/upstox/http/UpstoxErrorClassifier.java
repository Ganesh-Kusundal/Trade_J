package com.tradej.broker.upstox.http;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

/**
 * Classifies Upstox V3 error responses into typed exceptions.
 * <p>
 * Upstox V3 returns errors as a JSON body with a numeric {@code errors[].errorCode}
 * field and a human-readable {@code message}. The catalog of documented
 * error codes is documented in the V3 API pages and is what we parse
 * here to give callers structured information instead of a raw
 * {@code RuntimeException}.
 * <p>
 * <h2>Code catalogue</h2>
 * <ul>
 *   <li>{@code UDAPI1004} — Valid order type is required (e.g. place
 *       order without order_type in body)</li>
 *   <li>{@code UDAPI1007} — Validity is required (e.g. DAY/IOC missing)</li>
 *   <li>{@code UDAPI100049} — Access to this API is restricted; use
 *       "Uplink Business" for place/modify/cancel</li>
 *   <li>{@code UDAPI100010} — Order not found</li>
 *   <li>{@code UDAPI100040} — Cancel of already cancelled/rejected/completed
 *       order is not allowed</li>
 *   <li>{@code UDAPI100041} — Modifications of already cancelled/rejected/completed
 *       orders are not allowed</li>
 *   <li>{@code UDAPI1023} — Order id is required (cancel)</li>
 *   <li>{@code UDAPI1010} — Order id accepts only alphanumeric and "-"</li>
 *   <li>{@code UDAPI1036} — Trigger price is required (for SL orders)</li>
 *   <li>{@code UDAPI1029} — Price is required</li>
 *   <li>{@code UDAPI1030} — Price is not required (e.g. for MARKET)</li>
 *   <li>{@code UDAPI1031} — Price and Trigger price both are required</li>
 *   <li>{@code UDAPI1032} — Only Trigger price is required</li>
 *   <li>{@code UDAPI1039} — Disclosed quantity should not be less than 10% of
 *       the quantity</li>
 *   <li>{@code UDAPI1003} — Order id is required (modify)</li>
 *   <li>{@code UDAPI1055} — Validity is invalid</li>
 *   <li>{@code UDAPI1056} — Order type is invalid</li>
 *   <li>{@code UDAPI1008} — Price is required (legacy naming)</li>
 *   <li>{@code UDAPI1154} — Access to this API is blocked due to static IP
 *       restrictions. The caller is not on the whitelisted IP. Surfacing
 *       this as a structured exception lets the health subsystem flag
 *       the broker as misconfigured without crashing the trading thread.</li>
 *   <li>{@code UDAPI1156} — Invalid Algo name passed in headers. The
 *       X-Algo-Name header is set but not SEBI-registered.</li>
 *   <li>{@code UDAPI1158} — Market orders are not allowed. Use the
 *       Market Protection feature (V3) by setting
 *       {@code market_protection} to a value between 1 and 25.</li>
 *   <li>{@code UDAPI1159} — Market Protection cannot be greater than 25%</li>
 *   <li>{@code UDAPI1160} — Invalid market protection</li>
 *   <li>{@code UDAPI1161} — MCX orders via API are temporarily disabled</li>
 * </ul>
 * <p>
 * The class is intentionally broker-local: it is a thin translation layer
 * between documented V3 error codes and a typed exception hierarchy that
 * callers can switch on.
 */
public final class UpstoxErrorClassifier {

    private UpstoxErrorClassifier() {
    }

    /**
     * Inspects the response body for a {@code errors[].errorCode} field
     * and returns the matching {@link UpstoxApiException} subclass. If
     * the body is empty or no error is detected, returns
     * {@code null} (caller should treat as success).
     */
    public static UpstoxApiException classify(JsonNode responseBody) {
        Objects.requireNonNull(responseBody, "responseBody");
        if (!responseBody.isObject()) {
            return null;
        }
        JsonNode errors = responseBody.get("errors");
        if (errors == null || !errors.isArray() || errors.isEmpty()) {
            return null;
        }
        JsonNode first = errors.get(0);
        String code = first.has("errorCode") ? first.get("errorCode").asText() : null;
        if (code == null || code.isBlank()) {
            // No errorCode to classify on; let the caller fall back to a
            // generic UpstoxApiException with the right HTTP status.
            return null;
        }
        return map(code, first.has("message") ? first.get("message").asText() : "");
    }

    private static UpstoxApiException map(String code, String message) {
        return switch (code) {
            case "UDAPI1004", "UDAPI1056" -> new UpstoxApiException.InvalidOrderType(message);
            case "UDAPI1007" -> new UpstoxApiException.MissingValidity(message);
            case "UDAPI1055" -> new UpstoxApiException.InvalidValidity(message);
            case "UDAPI100049" -> new UpstoxApiException.UplinkBusinessRequired(message);
            case "UDAPI100010" -> new UpstoxApiException.OrderNotFound(message);
            case "UDAPI100040" -> new UpstoxApiException.CannotCancelFinalized(message);
            case "UDAPI100041" -> new UpstoxApiException.CannotModifyFinalized(message);
            case "UDAPI1023", "UDAPI1003" -> new UpstoxApiException.MissingOrderId(message);
            case "UDAPI1010" -> new UpstoxApiException.MalformedOrderId(message);
            case "UDAPI1036" -> new UpstoxApiException.MissingTriggerPrice(message);
            case "UDAPI1029", "UDAPI1008" -> new UpstoxApiException.MissingPrice(message);
            case "UDAPI1030" -> new UpstoxApiException.UnexpectedPrice(message);
            case "UDAPI1031" -> new UpstoxApiException.MissingPriceAndTrigger(message);
            case "UDAPI1032" -> new UpstoxApiException.MissingPriceOrTrigger(message);
            case "UDAPI1039" -> new UpstoxApiException.DisclosedQuantityTooSmall(message);
            case "UDAPI1154" -> new UpstoxApiException.StaticIpBlocked(message);
            case "UDAPI1156" -> new UpstoxApiException.InvalidAlgoName(message);
            case "UDAPI1158" -> new UpstoxApiException.MarketOrderBlocked(message);
            case "UDAPI1159" -> new UpstoxApiException.MarketProtectionOutOfRange(message);
            case "UDAPI1160" -> new UpstoxApiException.InvalidMarketProtection(message);
            case "UDAPI1161" -> new UpstoxApiException.McxTemporarilyDisabled(message);
            default -> null; // unrecognised code; let the caller use a generic UpstoxApiException
        };
    }
}
