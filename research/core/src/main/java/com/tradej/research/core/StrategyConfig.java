package com.tradej.research.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pinned strategy configuration with parameters and a deterministic SHA-256 hash.
 */
public record StrategyConfig(
    String strategyName,
    String version,
    Map<String, Object> parameters,
    String configHash
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static StrategyConfig create(String strategyName, String version, Map<String, Object> parameters) {
        String hash = calculateHash(strategyName, version, parameters);
        return new StrategyConfig(strategyName, version, parameters, hash);
    }

    private static String calculateHash(String strategyName, String version, Map<String, Object> parameters) {
        try {
            TreeMap<String, Object> sortedParams = new TreeMap<>(parameters);
            String serialized = strategyName + ":" + version + ":" + OBJECT_MAPPER.writeValueAsString(sortedParams);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 strategy hash", e);
        }
    }
}
