package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxPkceUtilTest {

    @Test
    void generatesNonNullPair() {
        var pair = UpstoxPkceUtil.generate();
        assertNotNull(pair.codeVerifier());
        assertNotNull(pair.codeChallenge());
    }

    @Test
    void generatesBase64UrlWithoutPadding() {
        var pair = UpstoxPkceUtil.generate();
        assertFalse(pair.codeVerifier().contains("="), "code_verifier must not contain padding");
        assertFalse(pair.codeVerifier().contains("+"), "code_verifier must be URL-safe");
        assertFalse(pair.codeVerifier().contains("/"), "code_verifier must be URL-safe");
    }

    @Test
    void codeVerifierIsCorrectLength() {
        var pair = UpstoxPkceUtil.generate();
        // 32 bytes → 44 chars without padding in base64
        assertTrue(pair.codeVerifier().length() >= 43 && pair.codeVerifier().length() <= 128,
                "code_verifier length must be between 43 and 128 characters");
    }

    @Test
    void challengeMatchesVerifier() {
        var pair = UpstoxPkceUtil.generate();
        String recomputed = UpstoxPkceUtil.computeChallenge(pair.codeVerifier());
        assertEquals(pair.codeChallenge(), recomputed, "code_challenge must match SHA-256 of code_verifier");
    }

    @Test
    void computeChallengeIsDeterministic() {
        String verifier = "test-verifier-1234567890abcdefghijklmnopqrstuvwxyz";
        String challenge1 = UpstoxPkceUtil.computeChallenge(verifier);
        String challenge2 = UpstoxPkceUtil.computeChallenge(verifier);
        assertEquals(challenge1, challenge2, "computeChallenge must be deterministic");
    }

    @Test
    void differentVerifiersProduceDifferentChallenges() {
        var pair1 = UpstoxPkceUtil.generate();
        var pair2 = UpstoxPkceUtil.generate();
        assertNotEquals(pair1.codeChallenge(), pair2.codeChallenge(),
                "Different verifiers must produce different challenges");
    }
}
