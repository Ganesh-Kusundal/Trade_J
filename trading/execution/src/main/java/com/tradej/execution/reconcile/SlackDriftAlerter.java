package com.tradej.execution.reconcile;

import com.tradej.core.domain.event.PositionMismatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

/**
 * Real {@link DriftAlerter} that POSTs a JSON envelope to a Slack
 * incoming-webhook URL on high-severity drift. The envelope uses the
 * simple Slack {@code {"text": "..."}} format that incoming webhooks
 * accept out of the box — no attachments, no blocks, no Slack SDK
 * dependency.
 *
 * <p>Wired behind {@code @ConditionalOnProperty(name =
 * "trade.drift-alerting.slack-webhook-url")} so the bean only exists
 * when the property is set. The {@link DriftAlertingConfiguration}
 * (in the {@code app} module) is the wiring location.
 *
 * <p>Implementation:
 * <ul>
 *   <li>Built on {@link HttpClient} (JDK 11+ built-in — no new
 *       dependencies).</li>
 *   <li>Envelope shape: {@code {"text": "DRIFT ALERT: <symbol> <side>
 *       <qty> (paper=<X> broker=<Y> delta=<Z> absDelta=<W>)"}}.
 *       {@code side} is {@code SELL} when {@code brokerQty - paperQty > 0}
 *       (broker has more than paper — we need to sell) and {@code BUY}
 *       otherwise. {@code qty} is the absolute delta.</li>
 *   <li>{@code Content-Type: application/json} is set on the request.</li>
 *   <li>A configurable {@code Duration timeout} (default 5 seconds)
 *       is applied to the request via
 *       {@link HttpRequest.Builder#timeout(Duration)}.</li>
 *   <li>All failures (network error, non-2xx response, JSON
 *       serialization) are caught and logged at WARN. {@code alertDrift}
 *       never throws — the handler's try/catch around the alerter
 *       invocation already enforces this contract, and a misbehaving
 *       Slack alerter must never break the reconciliation pass.</li>
 * </ul>
 *
 * <p>Replaces the stub from commit 65a4515 (B2 follow-up).
 */
public final class SlackDriftAlerter implements DriftAlerter {

    private static final Logger log = LoggerFactory.getLogger(SlackDriftAlerter.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final String webhookUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public SlackDriftAlerter(String webhookUrl) {
        this(webhookUrl, DEFAULT_TIMEOUT);
    }

    public SlackDriftAlerter(String webhookUrl, Duration timeout) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("slack webhookUrl must be non-blank");
        }
        this.webhookUrl = webhookUrl;
        this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    @Override
    public void alertDrift(PositionMismatch mismatch, long absDelta) {
        long delta = mismatch.brokerQuantity() - mismatch.paperQuantity();
        String side = delta > 0 ? "SELL" : "BUY";
        String webhookHost = hostOf(webhookUrl);

        String text = "DRIFT ALERT: " + mismatch.symbol() + " " + side + " " + absDelta
                + " (paper=" + mismatch.paperQuantity()
                + " broker=" + mismatch.brokerQuantity()
                + " delta=" + delta
                + " absDelta=" + absDelta + ")";
        String json = "{\"text\":\"" + escapeJson(text) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(json))
                    .build();
            int status = httpClient.send(request, BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) {
                log.warn("DRIFT_ALERT_SLACK non-2xx response [webhook={}] status={} symbol={} side={} qty={} eventId={}",
                        webhookHost, status, mismatch.symbol(), side, absDelta,
                        mismatch.metadata().eventId());
            }
        } catch (Exception e) {
            log.warn("DRIFT_ALERT_SLACK post failed [webhook={}] symbol={} side={} qty={} eventId={} error={}: {}",
                    webhookHost, mismatch.symbol(), side, absDelta,
                    mismatch.metadata().eventId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
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
