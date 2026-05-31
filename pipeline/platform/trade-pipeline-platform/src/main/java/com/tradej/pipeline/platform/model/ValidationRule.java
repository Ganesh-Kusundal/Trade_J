package com.tradej.pipeline.platform.model;

import java.util.List;
import java.util.Objects;

/**
 * Declarative constraint applied to a {@link PropertyDescriptor}.
 *
 * <p>Used by the frontend to render inline validation and by the server
 * to assert constraints before a pipeline definition is accepted.
 */
public record ValidationRule(
        RuleType type,
        String message,
        Object parameter
) {
    public ValidationRule {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public static ValidationRule required(String message) {
        return new ValidationRule(RuleType.REQUIRED, message, null);
    }

    public static ValidationRule min(double minimum, String message) {
        return new ValidationRule(RuleType.MIN, message, minimum);
    }

    public static ValidationRule max(double maximum, String message) {
        return new ValidationRule(RuleType.MAX, message, maximum);
    }

    public static ValidationRule minLength(int length, String message) {
        return new ValidationRule(RuleType.MIN_LENGTH, message, length);
    }

    public static ValidationRule maxLength(int length, String message) {
        return new ValidationRule(RuleType.MAX_LENGTH, message, length);
    }

    public static ValidationRule pattern(String regex, String message) {
        return new ValidationRule(RuleType.PATTERN, message, regex);
    }

    public static ValidationRule oneOf(List<Object> allowed, String message) {
        return new ValidationRule(RuleType.ONE_OF, message, List.copyOf(allowed));
    }

    public static ValidationRule greaterThan(String otherPropertyName, String message) {
        return new ValidationRule(RuleType.GREATER_THAN, message, otherPropertyName);
    }

    public enum RuleType {
        REQUIRED,
        MIN,
        MAX,
        MIN_LENGTH,
        MAX_LENGTH,
        PATTERN,
        ONE_OF,
        GREATER_THAN
    }
}
