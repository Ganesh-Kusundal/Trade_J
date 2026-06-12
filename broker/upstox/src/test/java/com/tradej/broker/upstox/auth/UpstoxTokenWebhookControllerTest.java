package com.tradej.broker.upstox.auth;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class UpstoxTokenWebhookControllerTest {

    @Test
    void emptyBodyIsRejected() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("");
        assertFalse(result.applied());
        assertEquals("empty body", result.reason());
    }

    @Test
    void nullBodyIsRejected() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle(null);
        assertFalse(result.applied());
    }

    @Test
    void missingAccessTokenIsRejected() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("{\"authorization_expiry\":\"1740729366039\"}");
        assertFalse(result.applied());
        assertEquals("missing access_token", result.reason());
    }

    @Test
    void missingExpiryIsRejected() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("{\"access_token\":\"abc\"}");
        assertFalse(result.applied());
        assertEquals("missing authorization_expiry", result.reason());
    }

    @Test
    void nonNumericExpiryIsRejected() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("{\"access_token\":\"abc\",\"authorization_expiry\":\"not-a-number\"}");
        assertFalse(result.applied());
        assertTrue(result.reason().contains("not a numeric epoch ms"));
    }

    @Test
    void validPayloadIsApplied() {
        var manager = stubManager();
        var ctrl = new UpstoxTokenWebhookController(manager, "upstox-test");
        var result = ctrl.handle("{\"access_token\":\"abc\",\"authorization_expiry\":\"1740729366039\"}");
        assertTrue(result.applied());
        assertEquals(1740729366039L, result.expiresAtMs());
    }

    @Test
    void rejectsMalformedJson() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("{not json");
        assertFalse(result.applied());
        assertTrue(result.reason().startsWith("parse error"));
    }

    @Test
    void rejectsNullManager() {
        assertThrows(NullPointerException.class,
                () -> new UpstoxTokenWebhookController(null));
    }

    @Test
    void rejectedHasZeroExpiry() {
        var ctrl = new UpstoxTokenWebhookController(stubManager());
        var result = ctrl.handle("");
        assertEquals(0L, result.expiresAtMs());
    }

    @Test
    void appliedAndRejectedFactories() {
        assertTrue(UpstoxTokenWebhookController.WebhookResult.applied(100L).applied());
        assertEquals(100L, UpstoxTokenWebhookController.WebhookResult.applied(100L).expiresAtMs());
        assertFalse(UpstoxTokenWebhookController.WebhookResult.rejected("x").applied());
        assertEquals("x", UpstoxTokenWebhookController.WebhookResult.rejected("x").reason());
    }

    private static UpstoxTokenManager stubManager() {
        // The controller only calls upgradeFromWebhook; for valid-payload
        // tests the manager is bypassed via a stub that records the call.
        // The non-trivial manager construction (PKCE, redirect server, etc.)
        // is irrelevant here. We use a Mockito mock.
        return org.mockito.Mockito.mock(UpstoxTokenManager.class);
    }
}
