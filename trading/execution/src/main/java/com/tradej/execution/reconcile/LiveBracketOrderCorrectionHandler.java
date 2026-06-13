package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.execution.service.OrderManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production {@link ReconciliationScheduler.MismatchHandler} that handles
 * drift detection in LIVE mode by computing the corrective order intent
 * and either logging it (log-only mode) or placing it via the
 * {@link OrderManagementService} (wired mode).
 *
 * <p><b>What this handler does:</b>
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
 *   <li>When constructed with an {@link OrderManagementService} and a
 *       {@link PositionRiskHandler} (the 5-arg constructor), places
 *       the correcting order via
 *       {@link OrderManagementService#placeOrder(OrderRequest)} after a
 *       pre-trade risk check. Risk rejection and OMS errors are caught
 *       and logged at WARN — the reconciliation pass is never broken
 *       by a failed correction.</li>
 * </ul>
 *
 * <p><b>"Bracket" naming:</b> the class name is historical. For drift
 * correction, a single MARKET order is the natural choice (drift = "we
 * have N more/less than we should" → close the gap with one market
 * order). True bracket orders (entry + target + stop-loss as a single
 * intent) are for new position entries, not corrections. The class
 * name is retained for git history continuity; the actual order placed
 * is a single MARKET MIS order.
 *
 * <p><b>Alerter failure handling:</b> the alerter invocation is wrapped
 * in try/catch. A failing alerter (e.g., Slack webhook down) does NOT
 * break the reconciliation pass — the handler logs the alerter failure
 * at ERROR and proceeds with the structured-log line.
 *
 * <p><b>OMS error handling:</b> {@link OrderManagementService#placeOrder}
 * is wrapped in try/catch. A broker error (timeout, rejected, circuit
 * breaker open) is logged at WARN and the reconciliation pass continues.
 * The {@code IllegalStateException} thrown when the trading circuit
 * breaker is open is treated as a normal failure path.
 *
 * <p><b>Idempotency:</b> the {@link OrderRequest#getCorrelationId()
 * correlationId} is set to {@code "drift-correction-" + engineKey}.
 * The engine key is stable across re-deliveries of the same event, so
 * duplicate events produce duplicate correlation ids. Brokers (Dhan,
 * Upstox, etc.) deduplicate on the correlation id at the OMS layer.
 *
 * <p><b>Gating:</b> this handler is wired as a Spring bean behind
 * {@code @ConditionalOnProperty(name = "trade.reconciliation.live-correction",
 * havingValue = "true", matchIfMissing = false)}. The default is OFF
 * (log-only mode). Production deployment should set the property to
 * true only after risk-check integration is verified.
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
    private final OrderManagementService orderManagementService;
    private final PositionRiskHandler positionRiskHandler;

    public LiveBracketOrderCorrectionHandler() {
        this(new LoggingDriftAlerter(), DEFAULT_TOLERANCE_QTY, DEFAULT_ALERT_THRESHOLD_QTY,
                null, null);
    }

    public LiveBracketOrderCorrectionHandler(long toleranceQty) {
        this(new LoggingDriftAlerter(), toleranceQty, DEFAULT_ALERT_THRESHOLD_QTY,
                null, null);
    }

    public LiveBracketOrderCorrectionHandler(
            DriftAlerter alerter,
            long toleranceQty,
            long alertThresholdQty
    ) {
        this(alerter, toleranceQty, alertThresholdQty, null, null);
    }

    /**
     * Primary constructor: alerter + tolerance + alert threshold + OMS +
     * risk handler. When {@code orderManagementService} and
     * {@code positionRiskHandler} are both non-null, the handler places
     * real drift-correction orders. When either is null, the handler
     * falls back to log-only mode (structured WOULD PLACE log line).
     */
    public LiveBracketOrderCorrectionHandler(
            DriftAlerter alerter,
            long toleranceQty,
            long alertThresholdQty,
            OrderManagementService orderManagementService,
            PositionRiskHandler positionRiskHandler
    ) {
        this.alerter = alerter != null ? alerter : new LoggingDriftAlerter();
        this.toleranceQty = Math.max(0L, toleranceQty);
        this.alertThresholdQty = alertThresholdQty;
        this.orderManagementService = orderManagementService;
        this.positionRiskHandler = positionRiskHandler;
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

    public OrderManagementService orderManagementService() {
        return orderManagementService;
    }

    public PositionRiskHandler positionRiskHandler() {
        return positionRiskHandler;
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
        // to the WARN-level log stream and pick up the intent. When
        // OMS + risk are wired, we ALSO actually place the order below.
        log.warn("LIVE_BRACKET_CORRECTION_WOULD_PLACE symbol={} side={} qty={} " +
                        "paperQty={} brokerQty={} engineKey={} delta={} tolerance={} alertThreshold={}",
                mismatch.symbol(), side, correctionQty,
                mismatch.paperQuantity(), mismatch.brokerQuantity(),
                mismatch.engineKey(), delta, toleranceQty, alertThresholdQty);

        // Real OMS placement path. Only active when both the OMS and
        // the risk handler are wired. The risk check is a gate: a
        // rejection logs WARN and skips placement. The OMS call is
        // wrapped in try/catch so a broker / circuit-breaker failure
        // never breaks the reconciliation pass.
        if (orderManagementService != null && positionRiskHandler != null) {
            Side orderSide = delta > 0 ? Side.SELL : Side.BUY;
            ExchangeSegment segment = resolveSegment(mismatch.engineKey());
            OrderRequest request = new OrderRequest(
                    mismatch.symbol(),
                    segment,
                    orderSide,
                    correctionQty,
                    OrderType.MARKET,
                    0L,
                    0L,
                    ProductType.INTRADAY,
                    Validity.DAY,
                    "drift-correction-" + mismatch.engineKey()
            );

            boolean allowed = positionRiskHandler.canPlaceOrder(
                    mismatch.symbol(), orderSide, correctionQty);
            if (!allowed) {
                log.warn("Drift correction rejected by risk check: symbol={} side={} qty={}",
                        mismatch.symbol(), orderSide, correctionQty);
                return;
            }

            try {
                Order order = orderManagementService.placeOrder(request);
                log.info("Drift correction order placed: orderId={} symbol={} side={} qty={}",
                        order.orderId(), mismatch.symbol(), orderSide, correctionQty);
            } catch (Exception e) {
                log.warn("Drift correction order failed: symbol={} side={} qty={} error={}",
                        mismatch.symbol(), orderSide, correctionQty, e.getMessage());
            }
        }
    }

    /**
     * Resolve the broker {@link ExchangeSegment} from the engine key.
     * Engine keys are formatted as {@code "<segment>::<symbol>"} (e.g.
     * {@code "nse-eq::RELIANCE"}, {@code "nse-fno::NIFTY24JUNFUT"}).
     * Returns {@link ExchangeSegment#UNKNOWN} when the key is malformed
     * or the segment is not recognised — callers (i.e. the OMS) treat
     * UNKNOWN as a non-routable segment.
     */
    private static ExchangeSegment resolveSegment(String engineKey) {
        if (engineKey == null) {
            return ExchangeSegment.UNKNOWN;
        }
        int sep = engineKey.indexOf("::");
        String rawSegment = sep < 0 ? engineKey : engineKey.substring(0, sep);
        // Engine keys use lowercase + dashes (e.g. "nse-eq", "nse-fno");
        // ExchangeSegment enum names use uppercase + underscores. Normalize
        // by uppercasing + replacing dashes with underscores.
        String normalized = rawSegment.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return ExchangeSegment.fromCode(normalized);
    }
}
