package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;

/**
 * Strategy for emitting a high-severity alert when the live-correction
 * path detects drift above its alerting threshold.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link LoggingDriftAlerter} — the default. Logs at WARN with a
 *       structured payload that operators / OMS listeners can subscribe
 *       to. Always available.</li>
 *   <li>{@link SlackDriftAlerter} — a stub that logs the payload it WOULD
 *       POST to a Slack incoming-webhook URL. The real HTTP POST is a
 *       follow-up commit. Wired behind
 *       {@code @ConditionalOnProperty(name =
 *       "trade.drift-alerting.slack-webhook-url")} so the bean only
 *       exists when the property is set.</li>
 * </ul>
 *
 * <p>Alerters are invoked by
 * {@link LiveBracketOrderCorrectionHandler} on every drift event whose
 * absolute quantity exceeds the configured threshold
 * ({@code trade.drift-alerting.threshold-qty}, default 100 qty). The
 * invocation is wrapped in try/catch by the handler so a failing alerter
 * (e.g., Slack webhook down) does not break the reconciliation pass.
 */
public interface DriftAlerter {
    void alertDrift(PositionMismatch mismatch, long absDelta);
}
