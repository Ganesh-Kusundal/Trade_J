package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link DriftAlerter}: logs a structured WARN line with the
 * mismatch payload. Always available — no configuration required.
 *
 * <p>This is the safe default for environments where no external
 * alerting channel (Slack, PagerDuty, etc.) has been configured. The
 * log line is the contract that operators / OMS listeners can
 * subscribe to; it is intentionally human-readable today and is the
 * same structured-payload shape the {@link SlackDriftAlerter} would
 * POST.
 */
public final class LoggingDriftAlerter implements DriftAlerter {

    private static final Logger log = LoggerFactory.getLogger(LoggingDriftAlerter.class);

    @Override
    public void alertDrift(PositionMismatch mismatch, long absDelta) {
        long delta = mismatch.brokerQuantity() - mismatch.paperQuantity();
        log.warn("DRIFT_ALERT symbol={} side={} qty={} paperQty={} brokerQty={} " +
                        "engineKey={} delta={} absDelta={} eventId={}",
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
}
