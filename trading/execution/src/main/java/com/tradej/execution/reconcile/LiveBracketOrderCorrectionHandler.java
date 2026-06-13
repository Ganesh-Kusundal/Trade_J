package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production {@link ReconciliationScheduler.MismatchHandler} that handles
 * drift detection in LIVE mode by computing the corrective order intent
 * and logging it for the OMS / execution path to pick up.
 *
 * <p><b>What this handler does today (P3.5 follow-up, 2026-06-12 + B2 2026-06-13):</b>
 * <ul>
 *   <li>Computes the correction direction (BUY or SELL) from the
 *       {@link PositionMismatch} (paper vs. broker qty delta).</li>
 *   <li>Computes the correction quantity (the absolute delta).</li>
 *   <li>For drift above the configured {@code alertThresholdQty}, invokes
 *       the injected {@link DriftAlerter} <b>before</b> the structured
 *       {@code LIVE_BRACKET_CORRECTION_WOULD_PLACE} log line, so the
 *       alert fires before the structured log is written (operators
 *       see the alert first).</li>
 *   <li>Logs a structured "WOULD PLACE" message at WARN level with the
 *       full correction intent (side, qty, symbol, paper qty, broker
 *       qty, engine key).</li>
 *   <li>Does <b>NOT</b> place orders. Order placement requires OMS
 *       integration + risk checks + bracket-order machinery, which is
 *       a separate commit. The structured log line is the contract
 *       that the OMS path can subscribe to.</li>
 * </ul>
 *
 * <p><b>Alerter failure handling:</b> the alerter invocation is wrapped
 * in try/catch. A failing alerter (e.g., Slack webhook down) does NOT
 * break the reconciliation pass — the handler logs the alerter failure
 * at ERROR and proceeds with the structured-log line.
 *
 * <p><b>Why log instead of act:</b> the MismatchHandler is invoked from
 * the reconciliation pass which is on a fixed schedule. Placing orders
 * from this hook would require:
 * <ol>
 *   <li>Risk-check integration (position size limits, exposure limits)</li>
 *   <li>Bracket-order wiring (entry + target + stop-loss as a single
 *       order intent)</li>
 *   <li>Idempotency keys (so a duplicate event doesn't place 2 orders)</li>
 *   <li>Broker-route dispatch via OrderManagementService</li>
 * </ol>
 * None of these are in scope for the P3.5 follow-up. The log line is
 * the extension point: an operator / OMS listener can read the log
 * stream and place the order via the existing OMS path.
 *
 * <p><b>Gating:</b> this handler is wired as a Spring bean behind
 * {@code @ConditionalOnProperty(name = "trade.reconciliation.live-correction",
 * havingValue = "true", matchIfMissing = false)}. The default is OFF.
 * Production deployment should set the property to true after risk
 * checks and bracket-order wiring are complete.
 *
 * <p><b>Thread safety:</b> the handler is stateless; safe to invoke
 * from multiple reconciliation passes concurrently.
 */
public final class LiveBracketOrderCorrectionHandler
        implements ReconciliationScheduler.MismatchHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveBracketOrderCorrectionHandler.class);

    /**
     * Default tolerance: a delta less than this absolute value is treated
     * as "no correction needed" (e.g., a 1-share rounding error from the
     * broker). Configurable per-deployment via a constructor parameter.
     */
    public static final long DEFAULT_TOLERANCE_QTY = 1L;

    /**
     * Default alerter threshold: a delta strictly greater than this
     * absolute value is considered "high severity" and triggers the
     * injected {@link DriftAlerter}. The threshold is separate from
     * {@link #DEFAULT_TOLERANCE_QTY} so small rounding-error drift still
     * produces the structured WOULD PLACE log line but does NOT page
     * the on-call.
     */
    public static final long DEFAULT_ALERT_THRESHOLD_QTY = 100L;

    private final DriftAlerter alerter;
    private final long toleranceQty;
    private final long alertThresholdQty;

    public LiveBracketOrderCorrectionHandler() {
        this(new LoggingDriftAlerter(), DEFAULT_TOLERANCE_QTY, DEFAULT_ALERT_THRESHOLD_QTY);
    }

    public LiveBracketOrderCorrectionHandler(long toleranceQty) {
        this(new LoggingDriftAlerter(), toleranceQty, DEFAULT_ALERT_THRESHOLD_QTY);
    }

    public LiveBracketOrderCorrectionHandler(
            DriftAlerter alerter,
            long toleranceQty,
            long alertThresholdQty
    ) {
        this.alerter = alerter != null ? alerter : new LoggingDriftAlerter();
        this.toleranceQty = Math.max(0L, toleranceQty);
        this.alertThresholdQty = alertThresholdQty;
    }

    public DriftAlerter alerter() {
        return alerter;
    }

    public long toleranceQty() {
        return toleranceQty;
    }

    public long alertThresholdQty() {
        return alertThresholdQty;
    }

    @Override
    public void onMismatch(PositionMismatch mismatch) {
        long delta = mismatch.brokerQuantity() - mismatch.paperQuantity();
        long absDelta = Math.abs(delta);

        if (absDelta <= toleranceQty) {
            log.debug("Live correction: drift within tolerance ({} qty) — no action for {}",
                    absDelta, mismatch.symbol());
            return;
        }

        // The correction intent. Sign convention: positive delta means
        // broker reports MORE than paper (we need to SELL the excess to
        // align with paper); negative delta means broker reports LESS
        // than paper (we need to BUY the missing to align with paper).
        String side = delta > 0 ? "SELL" : "BUY";
        long correctionQty = absDelta;

        // High-severity drift: invoke the alerter BEFORE the structured
        // WOULD PLACE log line, so an operator / on-call sees the alert
        // before the per-event log entry. The alerter is wrapped in
        // try/catch so a failing alerter (e.g., Slack webhook down)
        // doesn't break the reconciliation pass.
        if (absDelta > alertThresholdQty) {
            try {
                alerter.alertDrift(mismatch, absDelta);
            } catch (Exception e) {
                log.error("DriftAlerter.alertDrift failed for symbol={} absDelta={} — " +
                        "reconciliation pass continues",
                        mismatch.symbol(), absDelta, e);
            }
        }

        // Log a structured WOULD PLACE line. The OMS path can subscribe
        // to the WARN-level log stream and pick up the intent. Replace
        // this with a real OrderManagementService.place() call once
        // bracket-order wiring is complete.
        log.warn("LIVE_BRACKET_CORRECTION_WOULD_PLACE symbol={} side={} qty={} " +
                        "paperQty={} brokerQty={} engineKey={} delta={} tolerance={} alertThreshold={}",
                mismatch.symbol(), side, correctionQty,
                mismatch.paperQuantity(), mismatch.brokerQuantity(),
                mismatch.engineKey(), delta, toleranceQty, alertThresholdQty);
    }
}
