package com.tradej.app.auth;

import com.tradej.app.auth.SessionStore.Session;
import com.tradej.broker.api.spi.BrokerSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side auth: the frontend posts the broker credential once,
 * the backend returns a session id, and all subsequent calls use
 * the session id (e.g. via the {@code X-Session-Id} header).
 *
 * <p>This replaces the {@code localStorage} flow where the access
 * token was embedded in the SPA. Plaintext credentials in browser
 * storage is a security defect; the session-id flow keeps the
 * credential on the server only.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} — body: {@code LoginRequest};
 *       response: {@code LoginResponse} with the session id.
 *   <li>{@code POST /api/v1/auth/logout} — header: {@code X-Session-Id};
 *       response: 204.
 *   <li>{@code GET /api/v1/auth/whoami} — header: {@code X-Session-Id};
 *       response: {@code WhoAmI} with broker + remaining TTL.
 * </ul>
 *
 * <p>The session id is a 192-bit random token, base64-url-encoded.
 * It is the only thing the browser ever sees after {@code /login}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String SESSION_HEADER = "X-Session-Id";

    private final SessionStore sessions;

    public AuthController(SessionStore sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body) {
        if (body == null || body.broker == null || body.broker.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (body.accessToken == null || body.accessToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        BrokerSource source = body.source != null
                ? BrokerSource.valueOf(body.source)
                : BrokerSource.valueOf(body.broker);
        String id = sessions.create(
                body.broker,
                body.accessToken,
                body.clientId,
                body.sessionToken,
                source
        );
        long now = System.currentTimeMillis();
        return ResponseEntity.ok(new LoginResponse(
                id,
                body.broker,
                source.name(),
                now,
                now + SessionStore.DEFAULT_TTL_MS
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String id = request.getHeader(SESSION_HEADER);
        if (id == null) {
            // Idempotent — no session, nothing to do.
            return ResponseEntity.noContent().build();
        }
        sessions.invalidate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/whoami")
    public ResponseEntity<Map<String, Object>> whoami(HttpServletRequest request) {
        String id = request.getHeader(SESSION_HEADER);
        Optional<Session> s = id == null ? Optional.empty() : sessions.get(id);
        if (s.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Invalid or missing session",
                    "header", SESSION_HEADER
            ));
        }
        Session sess = s.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sess.sessionId());
        body.put("broker", sess.broker());
        body.put("source", sess.source().name());
        body.put("expiresAtMs", sess.expiresAtMs());
        return ResponseEntity.ok(body);
    }

    /**
     * Returns the full {@link BrokerSession} JSON for the WebSocket
     * feed. The server reads the credential from
     * {@link SessionStore} and hands it to the browser in one
     * shot — the browser does not persist the response.
     */
    @GetMapping("/broker-session")
    public ResponseEntity<Map<String, Object>> brokerSession(HttpServletRequest request) {
        String id = request.getHeader(SESSION_HEADER);
        Optional<Session> s = id == null ? Optional.empty() : sessions.get(id);
        if (s.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid or missing session"));
        }
        Session sess = s.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("broker", sess.broker());
        body.put("accessToken", sess.accessToken());
        if (sess.clientId() != null) body.put("clientId", sess.clientId());
        if (sess.sessionToken() != null) body.put("sessionToken", sess.sessionToken());
        return ResponseEntity.ok(body);
    }

    public static class LoginRequest {
        public String broker;
        public String source;
        public String accessToken;
        public String clientId;
        public String sessionToken;
    }

    public record LoginResponse(
            String sessionId,
            String broker,
            String source,
            long issuedAtMs,
            long expiresAtMs
    ) {}
}
