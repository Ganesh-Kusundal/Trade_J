package com.tradej.broker.icici.auth;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Locale;

public final class BreezeTotpGenerator {
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final TimeBasedOneTimePasswordGenerator generator;

    public BreezeTotpGenerator() {
        this.generator = new TimeBasedOneTimePasswordGenerator();
    }

    public String currentCode(String sharedSecret) {
        return currentCode(sharedSecret, 0);
    }

    public String currentCode(String sharedSecret, int windowOffsetSteps) {
        return codeAt(sharedSecret, Instant.now().plusSeconds(windowOffsetSteps * 30L));
    }

    String codeAt(String sharedSecret, Instant instant) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalArgumentException("ICICI TOTP secret is blank");
        }
        byte[] secretBytes = decodeBase32(sharedSecret);
        SecretKeySpec key = new SecretKeySpec(secretBytes, generator.getAlgorithm());
        try {
            int password = generator.generateOneTimePassword(key, instant);
            return String.format(Locale.ROOT, "%06d", password);
        } catch (InvalidKeyException ex) {
            throw new IllegalStateException("Failed to generate ICICI TOTP code", ex);
        }
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace(" ", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ICICI TOTP secret is blank");
        }
        int expectedLength = normalized.length() * 5 / 8;
        byte[] output = new byte[expectedLength];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (char ch : normalized.toCharArray()) {
            if (ch == '=') {
                break;
            }
            int alphabetIndex = BASE32_ALPHABET.indexOf(ch);
            if (alphabetIndex < 0) {
                throw new IllegalArgumentException("Invalid base32 character in ICICI TOTP secret: " + ch);
            }
            buffer = (buffer << 5) | alphabetIndex;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        if (index == output.length) {
            return output;
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(output, 0, trimmed, 0, index);
        return trimmed;
    }
}
