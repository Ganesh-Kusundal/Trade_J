package com.tradej.broker.core.util;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Reflection utilities for accessing broker SDK objects reflectively.
 * <p>
 * Originally extracted from {@code trade-broker-dhan} for reuse across
 * broker adapters.
 */
public final class ReflectionSupport {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter[] DATE_TIME_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private ReflectionSupport() {
    }

    public static Optional<Object> invoke(Object target, String methodName) {
        Optional<Object> value = optionalInvoke(target, methodName);
        if (value.isEmpty()) {
            throw new IllegalStateException("Missing expected method/value " + methodName + " on "
                    + (target == null ? "null" : target.getClass().getName()));
        }
        return value;
    }

    public static Optional<Object> optionalInvoke(Object target, String methodName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return Optional.ofNullable(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    public static String string(Object target, String methodName) {
        return invoke(target, methodName)
                .map(Object::toString)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Missing required string field via " + methodName));
    }

    public static String optionalString(Object target, String methodName) {
        return optionalInvoke(target, methodName).map(Object::toString).orElse("");
    }

    public static long longValue(Object target, String methodName) {
        return invoke(target, methodName)
                .map(value -> {
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                    return new BigDecimal(value.toString()).longValue();
                })
                .orElseThrow(() -> new IllegalStateException("Missing required numeric field via " + methodName));
    }

    public static BigDecimal decimal(Object target, String methodName) {
        return invoke(target, methodName)
                .map(value -> {
                    if (value instanceof BigDecimal bigDecimal) {
                        return bigDecimal;
                    }
                    return new BigDecimal(value.toString());
                })
                .orElseThrow(() -> new IllegalStateException("Missing required decimal field via " + methodName));
    }

    public static BigDecimal requiredDecimal(Object target, String methodName) {
        BigDecimal decimal = decimal(target, methodName);
        if (decimal == null) {
            throw new IllegalStateException("Missing required decimal field via " + methodName);
        }
        return decimal;
    }

    public static Optional<BigDecimal> optionalDecimal(Object target, String methodName) {
        return optionalInvoke(target, methodName).map(value -> {
            if (value instanceof BigDecimal bigDecimal) {
                return bigDecimal;
            }
            return new BigDecimal(value.toString());
        });
    }

    public static Optional<Long> optionalLong(Object target, String methodName) {
        return optionalInvoke(target, methodName).map(value -> {
            if (value instanceof Number number) {
                return number.longValue();
            }
            return new BigDecimal(value.toString()).longValue();
        });
    }

    public static Optional<Long> optionalTimestampMillis(Object target, String methodName) {
        return optionalInvoke(target, methodName).map(ReflectionSupport::toTimestampMillis);
    }

    public static long timestampMillis(Object target, String methodName) {
        return optionalTimestampMillis(target, methodName)
                .orElseThrow(() -> new IllegalStateException("Missing required timestamp field via " + methodName));
    }

    public static long toTimestampMillis(Object value) {
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
            }
            try {
                return LocalDateTime.parse(text, formatter).atZone(INDIA).toInstant().toEpochMilli();
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalStateException("Unsupported timestamp format: " + text);
    }
}
