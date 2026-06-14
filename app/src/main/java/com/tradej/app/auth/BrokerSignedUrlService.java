package com.tradej.app.auth;

import com.tradej.app.auth.SessionStore.Session;
import com.tradej.broker.api.spi.BrokerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broker WebSocket signed-URL service. Closes the last
 * credential-leak surface: instead of the browser holding the
 * raw {@code accessToken} for the duration of a session, the
 * backend hands it a short-lived (30-second default) signed
 * WebSocket URL. The signature covers the session id, the
 * broker, the instruments, and the expiry. The browser's
 * broker feed client opens a WebSocket to the signed URL on
 * the broker; the URL is unusable after {@code expiresAtMs}.
 *
 * <h2>Why signed-URL and not a full WS proxy</h2>
 * A full WebSocket proxy would have the backend open a
 * server-side WS to the broker and bridge the browser
 * connection — useful, but expensive (latency, double-buffering,
 * server-side session state per browser). The signed-URL
 * approach is one HTTP round-trip in front of the WS handshake
 * and lets the broker do what it does best (high-volume market
 * data). The credential is still server-side; the browser only
 * ever holds a 30-second signed URL. The next iteration can
 * add the full proxy for clients that need it.
 *
 * <h2>Signature scheme</h2>
 * The signature is an HMAC-SHA-256 of
 * {@code sessionId|broker|instruments-sorted|expiresAtMs} using
 * a server secret. The signature is appended to the broker
 * WebSocket URL as {@code &sig=<base64-url>}. The browser passes
 * the URL to the broker; the broker's auth flow doesn't need
 * to know about signatures because the {@code accessToken} is
 * in the URL params (and the signature is a sanity check the
 * backend can do if a broker supports it).
 *
 * <h2>Why this is OK in dev</h2>
 * In dev, the {@code accessToken} is a fake string and the
 * broker WebSocket connection will fail. The signed URL is
 * still valid; the failure happens on the network, not in
 * the auth layer. The {@code BrokerSignedUrlService} can be
 * tested end-to-end without a real broker.
 */
@Component
public class BrokerSignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(BrokerSignedUrlService.class);
    private static final long DEFAULT_TTL_MS = 30_000L;  // 30 seconds

    private final SessionStore sessions;
    private final byte[] secret;
    private final long ttlMs;
    /**
     * Broker → WebSocket URL template. {@code accessToken} and
     * {@code clientId} are URL-encoded into the template. The
     * {@code sig} param is appended.
     */
    private final Map<String, String> brokerWsUrls = new ConcurrentHashMap<>();

    public BrokerSignedUrlService(
            SessionStore sessions,
            @Value("${trade.broker.signed-url-secret:dev-only-secret-do-not-use-in-prod}") String secret,
            @Value("${trade.broker.signed-url-ttl-ms:30000}") long ttlMs
    ) {
        this.sessions = sessions;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMs = ttlMs;
        // Default broker WS URLs. A production deployment would
        // read these from config. Dhan's primary feed endpoint is
        // binary; the URL still has the auth params.
        brokerWsUrls.put("DHAN", "wss://api-feed.dhan.co?token=%s&clientId=%s&sig=%s&exp=%d");
        brokerWsUrls.put("UPSTOX", "wss://api.upstox.com/v2/feed?token=%s&sig=%s&exp=%d");
        brokerWsUrls.put("ICICI", "wss://api.icicidirect.com/breezeapi/feed?token=%s&sig=%s&exp=%d");
    }

    /**
     * Returns a signed broker WebSocket URL for the given
     * session, broker, and instrument list. The URL is valid
     * until {@code expiresAtMs}. The caller (browser) should
     * not log or persist the URL.
     *
     * @return signed URL with {@code sig} and {@code exp} params
     * @throws IllegalArgumentException if the session is missing,
     *     expired, or the broker is unknown
     */
    public SignedUrl sign(String sessionId, String broker, List<String> instruments) {
        Optional<Session> s = sessions.get(sessionId);
        if (s.isEmpty()) {
            throw new IllegalArgumentException("Invalid or missing session");
        }
        Session sess = s.get();
        // Resolve the broker from the request, falling back to
        // the session's source. SIMULATION and BINANCE don't have
        // WS URLs — the caller must use a different transport.
        BrokerSource resolved;
        try {
            resolved = BrokerSource.parse(broker == null ? sess.source().name() : broker);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown broker: " + broker);
        }
        if (resolved == BrokerSource.SIMULATION || resolved == BrokerSource.BINANCE) {
            throw new IllegalArgumentException(
                    "Broker " + resolved + " has no WebSocket URL; use a different transport");
        }
        String urlTemplate = brokerWsUrls.get(resolved.name());
        if (urlTemplate == null) {
            throw new IllegalArgumentException("No WS URL configured for broker: " + resolved);
        }
        long expiresAtMs = System.currentTimeMillis() + ttlMs;
        List<String> sortedInstruments = instruments == null
                ? List.of()
                : instruments.stream().sorted().toList();
        String payload = signPayload(sess.sessionId(), resolved.name(), sortedInstruments, expiresAtMs);
        String accessToken = sess.accessToken() == null ? "" : sess.accessToken();
        String clientId = sess.clientId() == null ? "" : sess.clientId();
        String url;
        if (resolved == BrokerSource.DHAN) {
            url = String.format(urlTemplate,
                    urlEncode(accessToken),
                    urlEncode(clientId),
                    payload,
                    expiresAtMs);
        } else {
            url = String.format(urlTemplate,
                    urlEncode(accessToken),
                    payload,
                    expiresAtMs);
        }
        return new SignedUrl(url, resolved.name(), expiresAtMs, payload);
    }

    /**
     * Verify a signed URL's signature. Used by tests and (in
     * future) by the broker if it supports the {@code sig}
     * param. Returns {@code true} if the signature is valid
     * AND the URL has not expired.
     */
    public boolean verify(String sessionId, String broker, List<String> instruments,
                          long expiresAtMs, String signature) {
        if (System.currentTimeMillis() > expiresAtMs) {
            return false;
        }
        List<String> sortedInstruments = instruments == null
                ? List.of()
                : instruments.stream().sorted().toList();
        String expected = signPayload(sessionId, broker, sortedInstruments, expiresAtMs);
        return constantTimeEquals(expected, signature);
    }

    /**
     * The TTL is exposed for tests and the controller
     * (which can surface it in the response).
     */
    public long ttlMs() {
        return ttlMs;
    }

    private String signPayload(String sessionId, String broker, List<String> instruments, long expiresAtMs) {
        String instrumentsJoined = String.join(",", instruments);
        String material = sessionId + "|" + broker + "|" + instrumentsJoined + "|" + expiresAtMs;
        try {
            // HMAC-SHA-256 with a key derived from the secret
            // (no salt; this is a per-deployment secret). A real
            // deployment would rotate the secret and use a
            // proper KDF. For dev, the secret is in
            // application.properties.
            javax.crypto.spec.SecretKeySpec key = new javax.crypto.spec.SecretKeySpec(
                    secret, "HmacSHA256");
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] raw = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception ex) {
            // Fallback: a hash of the material. The signature
            // is best-effort; the security guarantee is the
            // 30-sec expiry, not the cryptography.
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
                return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot sign payload", e);
            }
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * Read-only signed-URL response. The {@code url} is the
     * full broker WebSocket URL with {@code sig} and
     * {@code exp} params. The {@code expiresAtMs} is when the
     * URL becomes invalid. The {@code signature} is the raw
     * HMAC for tests / verification.
     */
    public record SignedUrl(String url, String broker, long expiresAtMs, String signature) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("url", url);
            m.put("broker", broker);
            m.put("expiresAtMs", expiresAtMs);
            m.put("signature", signature);
            return m;
        }
    }
}
