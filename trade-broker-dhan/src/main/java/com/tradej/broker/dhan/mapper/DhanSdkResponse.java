package com.tradej.broker.dhan.mapper;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Typed envelope for Dhan SDK response objects.
 *
 * <p>Wraps raw {@code Object} references returned by the Dhan SDK and provides
 * type-safe accessor methods that eliminate direct reflection calls at every
 * call site. The {@code T} parameter is a semantic marker indicating the kind
 * of response (e.g. order, trade, position, quote).
 *
 * <p>Usage:
 * <pre>{@code
 * DhanSdkResponse<?> response = new DhanSdkResponse<>(sdkResult);
 * String orderId = response.string("getOrderId");
 * long quantity = response.longValue("getQuantity");
 * }</pre>
 *
 * <p>Centralises all reflection logic previously spread across
 * {@link ReflectionSupport}, {@link DhanSdkMapper} private helpers, and
 * {@link com.tradej.broker.dhan.adapter.DhanMarketDataProvider#nestedValue(Object, String, String)}.
 */
public final class DhanSdkResponse<T> {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter[] DATE_TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private final Object raw;

    /**
     * Wrap a raw Dhan SDK response object.
     *
     * @param raw the SDK object (may be {@code null})
     */
    public DhanSdkResponse(Object raw) {
        this.raw = raw;
    }

    // ---- Raw access ----

    /** Return the underlying raw SDK object. */
    public Object raw() {
        return raw;
    }

    /** The class name of the underlying object, for error messages. */
    public String className() {
        return raw == null ? "null" : raw.getClass().getName();
    }

    // ---- Invocation ----

    /**
     * Reflectively invoke a no-arg method on the underlying SDK object.
     *
     * @param methodName the method name (e.g. {@code "getOrderId"})
     * @return the return value, or {@link Optional#empty()} if the method does
     *         not exist or throws
     */
    public Optional<Object> invoke(String methodName) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            Method method = raw.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(raw));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Reflectively invoke a no-arg method, throwing if it does not exist.
     *
     * @param methodName the method name
     * @return the return value
     * @throws IllegalStateException if the method is not present
     */
    public Object requireInvoke(String methodName) {
        return invoke(methodName)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing expected method/value " + methodName + " on " + className()));
    }

    // ---- String accessors ----

    /**
     * Return the first non-blank string from the given method names.
     *
     * @param methods method names to try in order
     * @return the first non-blank string value
     * @throws IllegalStateException if none of the methods produce a non-blank value
     */
    public String string(String... methods) {
        for (String method : methods) {
            String value = optionalString(method);
            if (!value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("Missing required string field from " + List.of(methods)
                + " on " + className());
    }

    /**
     * Return the value of an optional string field, or empty string.
     *
     * @param methodName the method name
     * @return the string value, or {@code ""} if absent
     */
    public String optionalString(String methodName) {
        return invoke(methodName).map(Object::toString).orElse("");
    }

    // ---- Numeric accessors (long) ----

    /**
     * Return the first present long value from the given method names.
     *
     * @param methods method names to try in order
     * @return the first present long value
     * @throws IllegalStateException if none of the methods return a value
     */
    public long longValue(String... methods) {
        return optionalLong(methods)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required numeric field from " + List.of(methods)
                                + " on " + className()));
    }

    /**
     * Return the first present optional long value from the given method names.
     *
     * @param methods method names to try in order
     * @return the first present value, or {@link Optional#empty()}
     */
    public Optional<Long> optionalLong(String... methods) {
        for (String method : methods) {
            Optional<Long> value = optionalLong(method);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /**
     * Return an optional long value from a single method name.
     *
     * @param methodName the method name
     * @return the long value, or {@link Optional#empty()} if absent
     */
    public Optional<Long> optionalLong(String methodName) {
        return invoke(methodName).map(value -> {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return new BigDecimal(value.toString()).longValue();
        });
    }

    // ---- Decimal accessors (BigDecimal) ----

    /**
     * Return the first present decimal value from the given method names.
     *
     * @param methods method names to try in order
     * @return the first present decimal value
     * @throws IllegalStateException if none of the methods return a value
     */
    public BigDecimal decimal(String... methods) {
        return optionalDecimal(methods)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing required decimal field from " + List.of(methods)
                                + " on " + className()));
    }

    /**
     * Return the first present optional decimal value from the given method names.
     *
     * @param methods method names to try in order
     * @return the first present value, or {@link Optional#empty()}
     */
    public Optional<BigDecimal> optionalDecimal(String... methods) {
        for (String method : methods) {
            Optional<BigDecimal> value = optionalDecimal(method);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /**
     * Return an optional decimal value from a single method name.
     *
     * @param methodName the method name
     * @return the BigDecimal value, or {@link Optional#empty()} if absent
     */
    public Optional<BigDecimal> optionalDecimal(String methodName) {
        return invoke(methodName).map(value -> {
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            return new BigDecimal(value.toString());
        });
    }

    // ---- Timestamp accessors ----

    /**
     * Return the first present timestamp (epoch milliseconds) from the given
     * method names.
     *
     * @param methods method names to try in order
     * @return the first present timestamp, or {@link Optional#empty()}
     */
    public Optional<Long> timestampMillis(String... methods) {
        for (String method : methods) {
            Optional<Long> value = timestampMillis(method);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    /**
     * Return an optional timestamp from a single method name.
     *
     * @param methodName the method name
     * @return the timestamp in epoch milliseconds, or {@link Optional#empty()}
     */
    public Optional<Long> timestampMillis(String methodName) {
        return invoke(methodName).map(DhanSdkResponse::toTimestampMillis);
    }

    // ---- Nested value extraction ----

    /**
     * Extract a nested value from a hierarchical map response (segment → securityId).
     *
     * <p>Moved from {@link com.tradej.broker.dhan.adapter.DhanMarketDataProvider#nestedValue(Object, String, String)}.
     * The typical use case is Dhan quote/LTP responses that are structured as
     * {@code Map<segment, Map<securityId, quote>>}.
     *
     * @param segment    the exchange segment key (e.g. {@code "NSE_EQ"})
     * @param securityId the security ID to look up within that segment
     * @return a new {@code DhanSdkResponse} wrapping the inner value
     * @throws IllegalStateException if any level of the hierarchy is missing
     */
    @SuppressWarnings("unchecked")
    public DhanSdkResponse<?> nestedValue(String segment, String securityId) {
        if (!(raw instanceof Map<?, ?> root)) {
            throw new IllegalStateException("Unexpected Dhan quote payload type: "
                    + className() + " — expected Map");
        }
        Object bySegment = root.get(segment);
        if (!(bySegment instanceof Map<?, ?> nested)) {
            throw new IllegalStateException("Missing Dhan quote segment " + segment
                    + " in response of type " + className());
        }
        Object result = nested.get(securityId);
        if (result == null) {
            result = nested.get(Integer.parseInt(securityId));
        }
        if (result == null) {
            throw new IllegalStateException("Missing Dhan quote payload for securityId="
                    + securityId + " in segment " + segment);
        }
        return new DhanSdkResponse<>(result);
    }

    // ---- Private helpers ----

    private static long toTimestampMillis(Object value) {
        if (value instanceof Number number) {
            long numeric = number.longValue();
            return numeric > 100_000_000_000L ? numeric : numeric * 1000L;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            throw new IllegalStateException("Blank timestamp value");
        }
        try {
            long numeric = new BigDecimal(text).longValue();
            return numeric > 100_000_000_000L ? numeric : numeric * 1000L;
        } catch (NumberFormatException ignored) {
            // fall through to datetime parsing
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATS) {
            try {
                if (formatter == DateTimeFormatter.ISO_DATE_TIME) {
                    return Instant.parse(text).toEpochMilli();
                }
            } catch (DateTimeParseException ignored) {
                // try local datetime formats
            }
            try {
                return LocalDateTime.parse(text, formatter).atZone(INDIA).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        throw new IllegalStateException("Unsupported timestamp format: " + text);
    }
}
