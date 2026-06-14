package com.tradej.app.auth;

import com.tradej.broker.api.spi.BrokerSource;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session store. The frontend sends broker credentials
 * to {@code POST /api/v1/auth/login}; the backend stores the
 * credentials here keyed by a random session id and returns the
 * session id to the frontend. The frontend then uses the session
 * id (not the credential) for subsequent calls.
 *
 * <p>This is v1: in-memory only, lost on restart. A real deployment
 * would use Redis or a signed JWT. The point of this class is to
 * stop putting plaintext access tokens in {@code localStorage}.
 *
 * <p>Sessions expire after {@link #DEFAULT_TTL_MS} (30 min). A
 * sliding expiration: each successful lookup resets the TTL.
 */
@Component
public class SessionStore {

    public record Session(
            String sessionId,
            String broker,
            String accessToken,
            String clientId,
            String sessionToken,
            BrokerSource source,
            long expiresAtMs
    ) {
        public boolean isExpired(long nowMs) {
            return nowMs >= expiresAtMs;
        }
    }

    public static final long DEFAULT_TTL_MS = 30L * 60L * 1000L;

    private static final SecureRandom RANDOM = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * Create a new session and store it. Returns the session id.
     */
    public String create(String broker, String accessToken, String clientId, String sessionToken, BrokerSource source) {
        String id = newSessionId();
        long now = System.currentTimeMillis();
        sessions.put(id, new Session(id, broker, accessToken, clientId, sessionToken, source, now + DEFAULT_TTL_MS));
        return id;
    }

    /**
     * Look up a session by id, sliding the TTL if still valid. Returns
     * empty if the id is unknown or the session has expired.
     */
    public Optional<Session> get(String sessionId) {
        if (sessionId == null) return Optional.empty();
        Session s = sessions.get(sessionId);
        if (s == null) return Optional.empty();
        long now = System.currentTimeMillis();
        if (s.isExpired(now)) {
            sessions.remove(sessionId, s);
            return Optional.empty();
        }
        // Sliding expiration
        Session refreshed = new Session(s.sessionId, s.broker, s.accessToken, s.clientId, s.sessionToken, s.source, now + DEFAULT_TTL_MS);
        sessions.put(sessionId, refreshed);
        return Optional.of(refreshed);
    }

    /**
     * Explicit logout. Idempotent. Returns true if a session was
     * actually removed.
     */
    public boolean invalidate(String sessionId) {
        if (sessionId == null) return false;
        return sessions.remove(sessionId) != null;
    }

    /** Test-only. */
    int activeSessionCount() {
        return sessions.size();
    }

    private static String newSessionId() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
