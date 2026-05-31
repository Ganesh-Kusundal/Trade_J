package com.tradej.research.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Pinned scanner configuration with a deterministic SHA-256 hash.
 */
public record ScannerConfig(
    String scannerName,
    String version,
    List<String> criteria,
    String configHash
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ScannerConfig create(String scannerName, String version, List<String> criteria) {
        String hash = calculateHash(scannerName, version, criteria);
        return new ScannerConfig(scannerName, version, criteria, hash);
    }

    private static String calculateHash(String scannerName, String version, List<String> criteria) {
        try {
            List<String> sortedCriteria = criteria.stream().sorted().toList();
            String serialized = scannerName + ":" + version + ":" + OBJECT_MAPPER.writeValueAsString(sortedCriteria);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate SHA-256 scanner hash", e);
        }
    }
}
