package com.tradej.broker.upstox.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;


import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * PKCE (Proof Key for Code Exchange) utility for OAuth 2.0 authorization code flow.
 * <p>
 * Generates a cryptographically random code_verifier and its SHA-256 code_challenge.
 */
public final class UpstoxPkceUtil {

    private static final int CODE_VERIFIER_BYTE_LENGTH = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private UpstoxPkceUtil() {}

    /**
     * A PKCE pair: code_verifier and its derived code_challenge.
     */
    public record PkcePair(String codeVerifier, String codeChallenge) {}

    /**
     * Generates a new cryptographically random PKCE pair.
     */
    public static PkcePair generate() {
        byte[] verifierBytes = new byte[CODE_VERIFIER_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(verifierBytes);
        String codeVerifier = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(verifierBytes);
        String codeChallenge = computeChallenge(codeVerifier);
        return new PkcePair(codeVerifier, codeChallenge);
    }

    /**
     * Computes the SHA-256 code challenge for the given code verifier.
     */
    public static String computeChallenge(String codeVerifier) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] challengeBytes = md.digest(codeVerifier.getBytes(US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
