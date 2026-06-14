package com.tradej.broker.api.spi;

/**
 * Describes a single credential field that a broker requires for authentication.
 * Used by the frontend to dynamically render credential input forms.
 *
 * @param key         the configuration key (e.g., "clientId", "accessToken")
 * @param label       human-readable label (e.g., "Client ID")
 * @param inputType   HTML input type: "text", "password", "file"
 * @param placeholder placeholder text for the input field
 */
public record CredentialField(
        String key,
        String label,
        String inputType,
        String placeholder
) {
    public CredentialField {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key must not be blank");
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label must not be blank");
        if (inputType == null || inputType.isBlank()) inputType = "text";
        if (placeholder == null) placeholder = "";
    }
}
