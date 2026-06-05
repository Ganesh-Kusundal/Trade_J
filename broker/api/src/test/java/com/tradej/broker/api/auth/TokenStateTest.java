package com.tradej.broker.api.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenStateTest {

    @Test
    void testRefreshRecommendedNormalToken() {
        long now = System.currentTimeMillis();
        long issuedAt = now - 23 * 3600_000L; // Issued 23 hours ago
        long expiry = now + 15 * 60_000L; // Expires in 15 mins
        long buffer = 10 * 60_000L; // 10 mins buffer
        
        TokenState state = new TokenState("token", null, expiry, issuedAt, TokenSource.TOTP);
        assertTrue(state.valid());
        assertFalse(state.refreshRecommended(buffer)); // 15 mins > 10 mins buffer
    }
    
    @Test
    void testRefreshRecommendedNormalTokenNearExpiry() {
        long now = System.currentTimeMillis();
        long issuedAt = now - 23 * 3600_000L; // Issued 23 hours ago (long lifespan)
        long expiry = now + 5 * 60_000L; // Expires in 5 mins
        long buffer = 10 * 60_000L; // 10 mins buffer
        
        TokenState state = new TokenState("token", null, expiry, issuedAt, TokenSource.TOTP);
        assertTrue(state.valid());
        assertTrue(state.refreshRecommended(buffer)); // 5 mins < 10 mins buffer
    }
    
    @Test
    void testRefreshRecommendedShortLivedToken() {
        long now = System.currentTimeMillis();
        long issuedAt = now - 1_000L; // Issued 1 sec ago
        long expiry = now + 5 * 60_000L; // Expires in 5 mins
        long buffer = 10 * 60_000L; // 10 mins buffer
        
        // Lifespan is ~5 mins. Buffer is 10 mins.
        // Even though remaining time (5m) < buffer (10m), it's a short-lived token.
        // It should NOT recommend refresh until it actually expires.
        TokenState state = new TokenState("token", null, expiry, issuedAt, TokenSource.TOTP);
        assertTrue(state.valid());
        assertFalse(state.refreshRecommended(buffer)); 
    }
    
    @Test
    void testRefreshRecommendedShortLivedTokenExpired() {
        long now = System.currentTimeMillis();
        long issuedAt = now - 6 * 60_000L; // Issued 6 mins ago
        long expiry = now - 1_000L; // Expired 1 sec ago
        long buffer = 10 * 60_000L; // 10 mins buffer
        
        // Lifespan is ~6 mins. Buffer is 10 mins.
        // It's a short-lived token, but it is now expired.
        // It should recommend refresh.
        TokenState state = new TokenState("token", null, expiry, issuedAt, TokenSource.TOTP);
        assertFalse(state.valid());
        assertTrue(state.refreshRecommended(buffer)); 
    }
}
