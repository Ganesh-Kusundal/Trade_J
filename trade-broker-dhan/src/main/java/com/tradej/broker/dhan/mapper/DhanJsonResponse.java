package com.tradej.broker.dhan.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.value.PriceMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Typed wrapper around a {@link JsonNode} response from Dhan HTTP endpoints.
 *
 * <p>Centralises the JSON path traversal and typed field extraction that was
 * previously duplicated as private helpers in {@link com.tradej.broker.dhan.adapter.DhanOptionsAdapter}
 * and inline in {@link com.tradej.broker.dhan.historical.DhanHistoricalDataMapper}.
 *
 * <p>Usage:
 * <pre>{@code
 * DhanJsonResponse response = new DhanJsonResponse(payload);
 * long price = response.decimalPrice("last_price", "lastPrice");
 * long oi = response.longValue("oi", "open_interest");
 * DhanJsonResponse data = response.has("data") ? response.path("data") : response;
 * }</pre>
 */
public final class DhanJsonResponse {

    /** Sentinel for a missing/null node, akin to {@link com.fasterxml.jackson.databind.node.MissingNode}. */
    private static final DhanJsonResponse MISSING = new DhanJsonResponse(null);

    private final JsonNode raw;

    /**
     * Wrap a JSON node.
     *
     * @param raw the JSON node (may be {@code null})
     */
    public DhanJsonResponse(JsonNode raw) {
        this.raw = raw;
    }

    /**
     * Returns a sentinel missing-node wrapper.
     * Used as a default return for {@link #path(String)} when the field does not exist.
     */
    public static DhanJsonResponse missing() {
        return MISSING;
    }

    // ---- Raw access ----

    /** Return the underlying {@link JsonNode}. */
    public JsonNode raw() {
        return raw;
    }

    // ---- Navigation ----

    /**
     * Check if a field exists and is not null/missing.
     *
     * @param field the field name
     * @return {@code true} if the field is present and non-null
     */
    public boolean has(String field) {
        return raw != null && raw.has(field) && !raw.path(field).isNull() && !raw.path(field).isMissingNode();
    }

    /**
     * Navigate to a child field, returning a sentinel missing wrapper if absent.
     *
     * @param field the field name
     * @return a {@link DhanJsonResponse} wrapping the child, or {@link #missing()} if absent
     */
    public DhanJsonResponse path(String field) {
        if (raw == null || !raw.has(field)) {
            return MISSING;
        }
        JsonNode child = raw.path(field);
        return (child == null || child.isNull() || child.isMissingNode()) ? MISSING : new DhanJsonResponse(child);
    }

    /**
     * Navigate to a child field, throwing if absent.
     *
     * @param field the field name
     * @return a {@link DhanJsonResponse} wrapping the child
     * @throws IllegalStateException if the field is missing or null
     */
    public DhanJsonResponse get(String field) {
        if (raw == null || !raw.has(field)) {
            throw new IllegalStateException("Missing required JSON field `" + field + "`");
        }
        JsonNode child = raw.get(field);
        if (child == null || child.isNull() || child.isMissingNode()) {
            throw new IllegalStateException("Required JSON field `" + field + "` is null or missing");
        }
        return new DhanJsonResponse(child);
    }

    // ---- Type checks ----

    /** Whether the wrapped node is a JSON object. */
    public boolean isObject() {
        return raw != null && raw.isObject();
    }

    /** Whether the wrapped node is a JSON array. */
    public boolean isArray() {
        return raw != null && raw.isArray();
    }

    /** Whether the wrapped node is missing (absent from parent). */
    public boolean isMissingNode() {
        return raw == null || raw.isNull() || raw.isMissingNode();
    }

    /** Whether the wrapped node is explicitly null. */
    public boolean isNull() {
        return raw == null || raw.isNull();
    }

    /** Number of elements (array length) or fields (object), or 0. */
    public int size() {
        if (raw == null) {
            return 0;
        }
        if (raw.isArray()) {
            return raw.size();
        }
        return raw.size();
    }

    // ---- Direct value access ----

    /** Return the text value of the wrapped node, or empty string. */
    public String asText() {
        return raw == null ? "" : raw.asText();
    }

    /** Return the long value of the wrapped node, or 0. */
    public long asLong() {
        return raw == null ? 0L : raw.asLong();
    }

    /** Return the double value of the wrapped node, or 0.0. */
    public double asDouble() {
        return raw == null ? 0.0 : raw.asDouble();
    }

    // ---- Typed field access (tries multiple field names) ----

    /**
     * Return the first present decimal price (in paisa) from the given field names.
     *
     * @param fieldNames field names to try in order
     * @return the price in paisa, or {@code 0L} if none of the fields are present
     */
    public long decimalPrice(String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (raw != null && raw.has(fieldName)) {
                JsonNode value = raw.get(fieldName);
                if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText().isBlank()) {
                    return PriceMath.toPaisa(value.asText());
                }
            }
        }
        return 0L;
    }

    /**
     * Return the first present long value from the given field names.
     *
     * @param fieldNames field names to try in order
     * @return the long value, or {@code 0L} if none of the fields are present
     */
    public long longValue(String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (raw != null && raw.has(fieldName)) {
                JsonNode value = raw.get(fieldName);
                if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText().isBlank()) {
                    return value.asLong();
                }
            }
        }
        return 0L;
    }

    /**
     * Return the first present double value from the given field names.
     *
     * @param fieldNames field names to try in order
     * @return the double value, or {@code null} if none of the fields are present
     */
    public Double doubleValue(String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (raw != null && raw.has(fieldName)) {
                JsonNode value = raw.get(fieldName);
                if (value != null && !value.isNull() && !value.isMissingNode() && !value.asText().isBlank()) {
                    return value.asDouble();
                }
            }
        }
        return null;
    }

    /**
     * Return the first non-blank text value from the given field names.
     *
     * @param fieldNames field names to try in order
     * @return the text value, or {@code ""} if none found
     */
    public String string(String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (raw != null && raw.has(fieldName)) {
                JsonNode value = raw.get(fieldName);
                if (value != null && !value.isNull() && !value.isMissingNode()) {
                    String text = value.asText();
                    if (!text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "";
    }

    // ---- Array element access ----

    /**
     * Return the element at the given index (for array nodes).
     *
     * @param index the index
     * @return a {@link DhanJsonResponse} wrapping the element
     * @throws NoSuchElementException if index is out of bounds or node is not an array
     */
    public DhanJsonResponse get(int index) {
        if (raw == null || !raw.isArray() || index < 0 || index >= raw.size()) {
            throw new NoSuchElementException("Index " + index + " out of bounds for array of size "
                    + (raw == null ? 0 : raw.size()));
        }
        return new DhanJsonResponse(raw.get(index));
    }

    // ---- Iteration ----

    /**
     * Iterate over the fields of a JSON object node.
     *
     * @return an iterator over field-name → {@link DhanJsonResponse} entries
     * @throws IllegalStateException if the node is not an object
     */
    public Iterator<Map.Entry<String, DhanJsonResponse>> fields() {
        if (raw == null || !raw.isObject()) {
            throw new IllegalStateException("Cannot iterate fields on a non-object JSON node");
        }
        Iterator<Map.Entry<String, JsonNode>> source = raw.fields();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public Map.Entry<String, DhanJsonResponse> next() {
                Map.Entry<String, JsonNode> entry = source.next();
                return Map.entry(entry.getKey(), new DhanJsonResponse(entry.getValue()));
            }
        };
    }

    /**
     * Convert an array node to a list of wrapped elements.
     *
     * @return list of {@link DhanJsonResponse} elements, or empty list if not an array
     */
    public List<DhanJsonResponse> asList() {
        if (raw == null || !raw.isArray()) {
            return List.of();
        }
        List<DhanJsonResponse> result = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            result.add(new DhanJsonResponse(raw.get(i)));
        }
        return result;
    }

    @Override
    public String toString() {
        if (raw == null) {
            return "DhanJsonResponse{null}";
        }
        return "DhanJsonResponse{" + raw + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DhanJsonResponse that)) return false;
        return raw != null && raw.equals(that.raw);
    }

    @Override
    public int hashCode() {
        return raw == null ? 0 : raw.hashCode();
    }
}
