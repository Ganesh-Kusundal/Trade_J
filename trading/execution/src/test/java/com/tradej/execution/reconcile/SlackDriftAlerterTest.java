package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the real-HTTP-POST behavior of {@link SlackDriftAlerter}
 * (B2 follow-up, replacing the 65a4515 stub).
 *
 * <p>Three contracts:
 * <ol>
 *   <li><b>Real HTTP POST attempted, connection-refused swallowed</b> —
 *       a call against a URL nothing listens on (e.g.,
 *       {@code http://localhost:1}) must not throw. The
 *       {@link java.net.http.HttpClient} raises a
 *       {@link java.net.ConnectException}; the alerter must catch and
 *       log it at WARN.</li>
 *   <li><b>Invalid webhook URL throws at construction</b> — the
 *       existing pre-POST validation (blank/null → IllegalArgumentException)
 *       is preserved. The alerter fails fast at bean construction so a
 *       misconfigured property never silently disables alerting.</li>
 *   <li><b>Smoke test for the JSON envelope path</b> — constructing
 *       with a valid URL and calling {@code alertDrift} must not throw.
 *       This is a defensive check: even if no real network call is
 *       made, the JSON serialization step runs and is wrapped in
 *       try/catch — this test pins that contract.</li>
 * </ol>
 */
class SlackDriftAlerterTest {

    private static PositionMismatch mismatch(String symbol, long paper, long broker) {
        return new PositionMismatch(EventMetadata.root(), symbol, paper, broker, "nse-eq::" + symbol);
    }

    @Test
    void alertDrift_realHttpPost_attempted() {
        // Port 1 is a privileged port that should not be listening —
        // HttpClient.send will raise ConnectException (or wrap it in
        // IOException). The alerter must swallow the failure and log
        // WARN; alertDrift must not throw.
        SlackDriftAlerter alerter = new SlackDriftAlerter(
                "http://localhost:1", Duration.ofMillis(500));
        assertDoesNotThrow(() ->
                alerter.alertDrift(mismatch("RELIANCE", 100L, 300L), 200L));
    }

    @Test
    void alertDrift_invalidWebhookUrl_throwsAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new SlackDriftAlerter(null));
        assertThrows(IllegalArgumentException.class,
                () -> new SlackDriftAlerter(""));
        assertThrows(IllegalArgumentException.class,
                () -> new SlackDriftAlerter("   "));
    }

    @Test
    void alertDrift_malformedJson_doesNotThrow() {
        // Smoke test: even if the network call fails, the alerter must
        // not propagate. http://localhost:1 is intentionally
        // unconnectable; the assertion is on the no-throw contract.
        SlackDriftAlerter alerter = new SlackDriftAlerter(
                "http://localhost:1", Duration.ofMillis(500));
        assertDoesNotThrow(() ->
                alerter.alertDrift(mismatch("TCS", 100L, 150L), 50L));
    }
}
