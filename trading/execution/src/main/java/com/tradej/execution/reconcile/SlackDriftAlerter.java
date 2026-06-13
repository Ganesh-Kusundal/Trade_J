package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub {@link DriftAlerter} that would POST a JSON envelope to a Slack
 * incoming-webhook URL on high-severity drift. The real HTTP POST is
 * NOT implemented in this commit — this class logs the payload it WOULD
 * send so the wiring is testable end-to-end.
 *
 * <p>Wired behind {@code @ConditionalOnProperty(name =
 * "trade.drift-alerting.slack-webhook-url")} so the bean only exists
 * when the property is set. The {@link DriftAlertingConfiguration}
 * (in the {@code app} module) is the wiring location.
 *
 * <p>Follow-up commit will replace the {@code log.warn} below with a
 * real {@code HttpClient.send} of a JSON envelope
 * ({@code {"text": "...", "attachments": [...]}}) using
 * {@code java.net.http.HttpClient}.
 *
 * <p><b>What this stub does today:</b>
 * <ol>
 *   <li>Logs the payload it WOULD POST, at WARN level, with the same
 *       structured fields as {@link LoggingDriftAlerter} plus the
 *       webhook URL (truncated to host for log-safety).</li>
 *   <li>Does NOT make any network call. The method always returns
 *       normally — the caller (the handler) will never see an
 *       exception from this stub. A follow-up commit will need to
 *       handle HttpClient errors and the handler's try/catch will
 *       already swallow them, so the contract is preserved.</li>
 * </ol>
 */
public final class SlackDriftAlerter implements DriftAlerter {

    private static final Logger log = LoggerFactory.getLogger(SlackDriftAlerter.class);

    private final String webhookUrl;

    public SlackDriftAlerter(String webhookUrl) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("slack webhookUrl must be non-blank");
        }
        this.webhookUrl = webhookUrl;
    }

    @Override
    public void alertDrift(PositionMismatch mismatch, long absDelta) {
        long delta = mismatch.brokerQuantity() - mismatch.paperQuantity();
        String webhookHost = hostOf(webhookUrl);
        log.warn("DRIFT_ALERT_SLACK_STUB [webhook={}] symbol={} side={} qty={} " +
                        "paperQty={} brokerQty={} engineKey={} delta={} absDelta={} " +
                        "eventId={} (HTTP POST NOT IMPLEMENTED — see follow-up commit)",
                webhookHost,
                mismatch.symbol(),
                delta > 0 ? "SELL" : "BUY",
                absDelta,
                mismatch.paperQuantity(),
                mismatch.brokerQuantity(),
                mismatch.engineKey(),
                delta,
                absDelta,
                mismatch.metadata().eventId());
    }

    private static String hostOf(String url) {
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int hostStart = schemeEnd + 3;
            int hostEnd = url.indexOf('/', hostStart);
            return hostEnd < 0 ? url.substring(hostStart) : url.substring(hostStart, hostEnd);
        } catch (RuntimeException e) {
            return "<unparseable>";
        }
    }
}
