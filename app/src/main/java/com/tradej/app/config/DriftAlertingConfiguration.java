package com.tradej.app.config;

import com.tradej.execution.reconcile.DriftAlerter;
import com.tradej.execution.reconcile.LoggingDriftAlerter;
import com.tradej.execution.reconcile.SlackDriftAlerter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Spring wiring for the drift-alerting extension point used by
 * {@link com.tradej.execution.reconcile.LiveBracketOrderCorrectionHandler}
 * (B2 follow-up, 2026-06-13).
 *
 * <p><b>Why a separate config class (not folded into
 * AutoReconciliationConfiguration):</b>
 * <ol>
 *   <li>{@code AutoReconciliationConfiguration} is the wiring for the
 *       scheduled auto-reconciliation runner
 *       ({@code SandboxAutoReconciliationScheduler}); it owns the
 *       periodic tick + the broker-view adapter. The drift alerter is
 *       consumed by the {@code MismatchHandler} wired in
 *       {@code AdminConfiguration}, not by the auto-reconciliation
 *       scheduler — folding the alerter wiring into
 *       {@code AutoReconciliationConfiguration} would create a
 *       misleading "alerter belongs to the auto-recon scheduler"
 *       coupling.</li>
 *   <li>The alerter is a leaf dependency with no scheduled-task surface;
 *       keeping it in its own config class makes the
 *       "what is wired for drift alerting?" answer a single-file
 *       read.</li>
 *   <li>{@code AutoReconciliationConfiguration} is already large
 *       (scheduler bean + broker-view + scheduled-task inner class);
 *       adding alerter wiring would push it past the single-purpose
 *       threshold.</li>
 * </ol>
 *
 * <p><b>Bean shape:</b>
 * <ul>
 *   <li><b>{@code loggingDriftAlerter} — always available</b>
 *       ({@code LoggingDriftAlerter}). Provides the safe default
 *       for any environment where no external alerting channel
 *       (Slack, PagerDuty, etc.) has been configured.</li>
 *   <li><b>{@code slackDriftAlerter} — optional</b>, gated by
 *       {@code @ConditionalOnProperty(name =
 *       "trade.drift-alerting.slack-webhook-url")}. Marked
 *       {@code @Primary} so that when the property is set, Spring's
 *       by-type injection of {@link DriftAlerter} picks the Slack
 *       implementation. The logging bean still exists (so the
 *       "always available" contract is preserved) but is shadowed
 *       for the injection point.</li>
 * </ul>
 *
 * <p><b>Why two beans + {@code @Primary} (not one @Bean method
 * with conditional logic):</b> the prompt explicitly describes two
 * distinct beans (a default + an optional Slack variant). The
 * {@code @Primary} + {@code @ConditionalOnProperty} pattern is the
 * idiomatic Spring way to express "optional override of a default
 * by-type bean" and keeps the always-available fallback explicit.
 *
 * <p><b>Threshold wiring note:</b> the alerter threshold
 * ({@code trade.drift-alerting.threshold-qty}, default 100 qty) is
 * NOT wired here — it is a property of the
 * {@code LiveBracketOrderCorrectionHandler} constructor and is
 * injected in {@link AdminConfiguration} where the
 * {@code MismatchHandler} bean is defined.
 */
@Configuration
public class DriftAlertingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DriftAlertingConfiguration.class);

    /**
     * Default {@link DriftAlerter}. Always available. Logs the
     * structured DRIFT_ALERT payload at WARN. No configuration
     * required.
     */
    @Bean
    public LoggingDriftAlerter loggingDriftAlerter() {
        log.info("DriftAlerter: LoggingDriftAlerter (default — no external channel configured)");
        return new LoggingDriftAlerter();
    }

    /**
     * Optional {@link DriftAlerter} that POSTs to a Slack incoming
     * webhook. Wired only when
     * {@code trade.drift-alerting.slack-webhook-url} is set; marked
     * {@code @Primary} so the Slack impl wins for by-type injection
     * when it is present. The
     * {@link com.tradej.execution.reconcile.SlackDriftAlerter} is a
     * stub in this commit (logs the payload it WOULD send); the real
     * HTTP POST is a follow-up commit.
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "trade.drift-alerting.slack-webhook-url")
    public SlackDriftAlerter slackDriftAlerter(
            @Value("${trade.drift-alerting.slack-webhook-url}") String webhookUrl
    ) {
        log.info("DriftAlerter: SlackDriftAlerter (webhook URL configured — stub, " +
                "real HTTP POST is a follow-up commit)");
        return new SlackDriftAlerter(webhookUrl);
    }
}
