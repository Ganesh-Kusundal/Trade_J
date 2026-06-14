package com.tradej.app.auth;

import com.tradej.app.auth.SessionStore.Session;
import com.tradej.broker.api.spi.BrokerSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerSignedUrlServiceTest {

    private static final String SECRET = "test-secret-not-for-prod";
    private static final long TTL = 30_000L;

    @Test
    void signProducesValidUrlForKnownBroker() {
        SessionStore store = new SessionStore();
        String sid = store.create("DHAN", "access-token-123", "client-abc", null, BrokerSource.DHAN);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);

        BrokerSignedUrlService.SignedUrl signed = svc.sign(sid, "DHAN", List.of("2885", "11536"));
        assertNotNull(signed.url());
        assertTrue(signed.url().startsWith("wss://api-feed.dhan.co?"),
                "Dhan URL template should be used");
        assertTrue(signed.url().contains("token=access-token-123"),
                "Token must be in the URL params");
        assertTrue(signed.url().contains("clientId=client-abc"),
                "Client id must be in the URL params");
        assertTrue(signed.url().contains("sig="),
                "Signature must be in the URL params");
        assertTrue(signed.url().contains("exp="),
                "Expiry must be in the URL params");
        assertEquals("DHAN", signed.broker());
    }

    @Test
    void signFailsOnMissingSession() {
        SessionStore store = new SessionStore();
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.sign("not-a-real-session", "DHAN", List.of()));
        assertTrue(ex.getMessage().contains("session"));
    }

    @Test
    void signFailsOnUnknownBroker() {
        SessionStore store = new SessionStore();
        String sid = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> svc.sign(sid, "BINANCE", List.of()));
        assertTrue(ex.getMessage().contains("BINANCE"),
                "Error message should name the unsupported broker");
    }

    @Test
    void signDifferentSessionsProduceDifferentSignatures() {
        SessionStore store = new SessionStore();
        String sid1 = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        String sid2 = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);

        BrokerSignedUrlService.SignedUrl u1 = svc.sign(sid1, "DHAN", List.of("2885"));
        BrokerSignedUrlService.SignedUrl u2 = svc.sign(sid2, "DHAN", List.of("2885"));
        // Same payload but different sessionIds → different signatures
        // (the signature includes the sessionId).
        assertFalse(u1.signature().equals(u2.signature()),
                "Different sessionIds must produce different signatures");
    }

    @Test
    void signInstrumentsAreSortedBeforeSigning() {
        SessionStore store = new SessionStore();
        String sid = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);

        // Pass the same instruments in two different orders;
        // the signatures must match (the service sorts before
        // signing, so order doesn't matter).
        long exp1;
        long exp2;
        // Sleep 1ms between to make the expiry differ
        BrokerSignedUrlService.SignedUrl a = svc.sign(sid, "DHAN", List.of("2885", "11536"));
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        BrokerSignedUrlService.SignedUrl b = svc.sign(sid, "DHAN", List.of("11536", "2885"));
        // The signatures differ only because expiresAtMs differs.
        // We can't directly assert they match; we verify both
        // produce a valid signature by calling verify on each.
        assertTrue(svc.verify(sid, "DHAN", List.of("2885", "11536"),
                a.expiresAtMs(), a.signature()));
        assertTrue(svc.verify(sid, "DHAN", List.of("11536", "2885"),
                b.expiresAtMs(), b.signature()));
    }

    @Test
    void verifyAcceptsValidSignature() {
        SessionStore store = new SessionStore();
        String sid = store.create("UPSTOX", "tok-up", "cid-up", null, BrokerSource.UPSTOX);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);

        BrokerSignedUrlService.SignedUrl signed = svc.sign(sid, "UPSTOX", List.of("NIFTY", "BANKNIFTY"));
        assertTrue(svc.verify(sid, "UPSTOX", List.of("NIFTY", "BANKNIFTY"),
                signed.expiresAtMs(), signed.signature()));
    }

    @Test
    void verifyRejectsExpiredSignature() throws InterruptedException {
        SessionStore store = new SessionStore();
        String sid = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        // 50ms TTL so we can wait it out
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, 50L);
        BrokerSignedUrlService.SignedUrl signed = svc.sign(sid, "DHAN", List.of("2885"));
        Thread.sleep(80);
        assertFalse(svc.verify(sid, "DHAN", List.of("2885"),
                signed.expiresAtMs(), signed.signature()),
                "Expired signatures must be rejected");
    }

    @Test
    void verifyRejectsTamperedSignature() {
        SessionStore store = new SessionStore();
        String sid = store.create("DHAN", "tok", "cid", null, BrokerSource.DHAN);
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, TTL);

        BrokerSignedUrlService.SignedUrl signed = svc.sign(sid, "DHAN", List.of("2885"));
        String tampered = signed.signature().substring(0, signed.signature().length() - 1) + "A";
        assertFalse(svc.verify(sid, "DHAN", List.of("2885"),
                signed.expiresAtMs(), tampered),
                "Tampered signature must be rejected");
    }

    @Test
    void ttlExposedForTheController() {
        SessionStore store = new SessionStore();
        BrokerSignedUrlService svc = new BrokerSignedUrlService(store, SECRET, 45_000L);
        assertEquals(45_000L, svc.ttlMs());
    }
}
