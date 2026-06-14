package com.tradej.execution.reconcile;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.PositionMismatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for the drift-alerting channels in the
 * {@code trading-execution} module. Closes audit gap #14 ("no alerts
 * integration test — a misconfigured webhook may go silent") for the
 * drift path.
 *
 * <p><b>Module-scope note.</b> The platform has two separate alert
 * abstractions:
 * <ul>
 *   <li>The drift path in this module ({@link SlackDriftAlerter},
 *       {@link LoggingDriftAlerter}, {@link DriftAlerter} SPI) — for
 *       position-mismatch events emitted by
 *       {@link LiveBracketOrderCorrectionHandler}. Tested here.</li>
 *   <li>The general health path in the {@code app} module
 *       ({@code com.tradej.app.health.AlertChannel} SPI:
 *       {@code SlackAlertChannel}, {@code LoggingAlertChannel},
 *       {@code PagerDutyAlertChannel}, {@code WebhookAlertChannel},
 *       orchestrated by {@code com.tradej.app.health.AlertManager}).
 *       Out of scope for this commit per the audit's G2 brief, which
 *       pinned verification to {@code :trading-execution:test}.</li>
 * </ul>
 *
 * <p>The {@code alertManager_dispatchesToAllChannels} test from the
 * brief is replaced here by
 * {@link #dispatcher_invokesBothDriftAlerters} because no
 * {@code AlertManager}-style orchestrator exists in this module —
 * alerters are wired directly into
 * {@link LiveBracketOrderCorrectionHandler} via its constructor
 * (a single {@link DriftAlerter} per handler instance, with
 * {@link LoggingDriftAlerter} as the null-safe fallback). The
 * dispatcher test walks the set of alerters this module ships and
 * asserts each one fires correctly when invoked directly, so a
 * misconfigured one will fail loudly in CI.
 *
 * <p>HTTP server is {@link com.sun.net.httpserver.HttpServer} (JDK
 * built-in, zero new dependencies) bound to {@code 127.0.0.1:0} so the
 * OS allocates a free port.
 */
class AlertChannelsEndToEndTest {

    private HttpServer server;
    private List<CapturedRequest> captured;
    private ListAppender<ILoggingEvent> slackAppender;
    private ListAppender<ILoggingEvent> loggingAppender;
    private Logger slackLogger;
    private Logger loggingLogger;

    @BeforeEach
    void startServer() throws IOException {
        captured = new CopyOnWriteArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(body, StandardCharsets.UTF_8)));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();

        slackLogger = (Logger) LoggerFactory.getLogger(SlackDriftAlerter.class);
        slackAppender = new ListAppender<>();
        slackAppender.start();
        slackLogger.addAppender(slackAppender);

        loggingLogger = (Logger) LoggerFactory.getLogger(LoggingDriftAlerter.class);
        loggingAppender = new ListAppender<>();
        loggingAppender.start();
        loggingLogger.addAppender(loggingAppender);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (slackLogger != null && slackAppender != null) {
            slackLogger.detachAppender(slackAppender);
            slackAppender.stop();
        }
        if (loggingLogger != null && loggingAppender != null) {
            loggingLogger.detachAppender(loggingAppender);
            loggingAppender.stop();
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static PositionMismatch mismatch(String symbol, long paper, long broker) {
        return new PositionMismatch(EventMetadata.root(), symbol, paper, broker, "nse-eq::" + symbol);
    }

    // ── SlackDriftAlerter ────────────────────────────────────────────

    @Test
    void slackDriftAlerter_postsCorrectJsonToWebhook() {
        SlackDriftAlerter alerter = new SlackDriftAlerter(
                url("/slack"), Duration.ofSeconds(2));
        // paper=90, broker=290 → broker has 200 MORE → SELL 200
        alerter.alertDrift(mismatch("RELIANCE", 90L, 290L), 200L);

        assertEquals(1, captured.size(), "Slack alerter must POST exactly once");
        CapturedRequest req = captured.get(0);
        assertEquals("POST", req.method(), "Slack alerter must use POST");
        assertEquals("/slack", req.path(), "Slack alerter must hit the configured path");
        assertEquals("application/json", req.contentType(),
                "Slack alerter must set Content-Type: application/json");

        // Body: {"text":"DRIFT ALERT: RELIANCE SELL 200 (paper=90 broker=290 delta=200 absDelta=200)"}
        String body = req.body();
        assertTrue(body.startsWith("{") && body.endsWith("}"),
                "Slack body must be a JSON object: " + body);
        assertTrue(body.contains("\"text\":"),
                "Slack body must contain a text field: " + body);
        assertTrue(body.contains("RELIANCE"),
                "Slack body must include the symbol: " + body);
        assertTrue(body.contains("SELL"),
                "Slack body must include the side (SELL when broker has more): " + body);
        assertTrue(body.contains("200"),
                "Slack body must include the absDelta qty: " + body);
        assertTrue(body.contains("paper=90"),
                "Slack body must include paper=90: " + body);
        assertTrue(body.contains("broker=290"),
                "Slack body must include broker=290: " + body);
    }

    @Test
    void slackDriftAlerter_swallowsConnectionError() {
        // Port 1 is a privileged port nothing listens on → ConnectException.
        // The alerter must catch the failure and log WARN; alertDrift must
        // not throw (the handler's try/catch + this contract together mean
        // a misbehaving webhook never breaks the reconciliation pass).
        SlackDriftAlerter alerter = new SlackDriftAlerter(
                "http://127.0.0.1:1", Duration.ofMillis(500));

        assertDoesNotThrow(() ->
                alerter.alertDrift(mismatch("TCS", 100L, 150L), 50L));

        // paper=100, broker=150 → broker has 50 MORE → SELL 50
        boolean loggedPostFailed = slackAppender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("DRIFT_ALERT_SLACK post failed")
                        && e.getFormattedMessage().contains("symbol=TCS")
                        && e.getFormattedMessage().contains("side=SELL")
                        && e.getFormattedMessage().contains("qty=50"));
        assertTrue(loggedPostFailed,
                "Slack alerter must log DRIFT_ALERT_SLACK post failed at WARN with mismatch fields. "
                        + "Captured: " + slackAppender.list);
    }

    @Test
    void slackDriftAlerter_logsWarnOnNon2xxResponse() {
        // Replace the default handler with one that returns 500. A real
        // misconfigured webhook (e.g., Slack rejecting the payload
        // shape) will surface as a non-2xx; the alerter must log WARN
        // and not throw.
        server.removeContext("/");
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(body, StandardCharsets.UTF_8)));
            byte[] respBody = "{\"error\":\"channel_not_found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, respBody.length);
            exchange.getResponseBody().write(respBody);
            exchange.close();
        });

        SlackDriftAlerter alerter = new SlackDriftAlerter(
                url("/slack"), Duration.ofSeconds(2));
        assertDoesNotThrow(() ->
                alerter.alertDrift(mismatch("HDFC", 50L, 250L), 200L));

        assertEquals(1, captured.size(), "Slack alerter must still POST on non-2xx path");
        boolean loggedNon2xx = slackAppender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("DRIFT_ALERT_SLACK non-2xx response")
                        && e.getFormattedMessage().contains("status=500")
                        && e.getFormattedMessage().contains("symbol=HDFC"));
        assertTrue(loggedNon2xx,
                "Slack alerter must log non-2xx response at WARN. Captured: "
                        + slackAppender.list);
    }

    // ── LoggingDriftAlerter ──────────────────────────────────────────

    @Test
    void loggingDriftAlerter_logsWarnLevel() {
        new LoggingDriftAlerter().alertDrift(mismatch("INFY", 80L, 180L), 100L);

        ILoggingEvent event = loggingAppender.list.stream()
                .filter(e -> e.getMessage().startsWith("DRIFT_ALERT"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "LoggingDriftAlerter must emit a DRIFT_ALERT log line. Captured: "
                                + loggingAppender.list));
        assertEquals(Level.WARN, event.getLevel(),
                "LoggingDriftAlerter must log at WARN level");
        String msg = event.getFormattedMessage();
        assertTrue(msg.contains("symbol=INFY"), "symbol: " + msg);
        assertTrue(msg.contains("side=SELL"), "side: " + msg);
        assertTrue(msg.contains("qty=100"), "qty: " + msg);
        assertTrue(msg.contains("paperQty=80"), "paperQty: " + msg);
        assertTrue(msg.contains("brokerQty=180"), "brokerQty: " + msg);
        assertTrue(msg.contains("engineKey=nse-eq::INFY"), "engineKey: " + msg);
        assertTrue(msg.contains("delta=100"), "delta: " + msg);
        assertTrue(msg.contains("absDelta=100"), "absDelta: " + msg);
    }

    // ── Dispatcher (replaces alertManager_dispatchesToAllChannels) ───

    @Test
    void dispatcher_invokesBothDriftAlerters() {
        // Replaces the brief's alertManager_dispatchesToAllChannels test:
        // this module has no AlertManager-style orchestrator. Alerters
        // are wired into LiveBracketOrderCorrectionHandler directly.
        // We walk the set this module ships and assert each one
        // delivers its contract — Slack POSTs JSON, Logging emits a
        // WARN line. Both fire for a single mismatch.
        AtomicInteger fired = new AtomicInteger();
        List<DriftAlerter> alerters = List.of(
                new SlackDriftAlerter(url("/slack"), Duration.ofSeconds(2)),
                new LoggingDriftAlerter());
        PositionMismatch m = mismatch("WIPRO", 40L, 140L);
        alerters.forEach(a -> {
            fired.incrementAndGet();
            a.alertDrift(m, 100L);
        });
        assertEquals(2, fired.get(),
                "Both drift alerters in this module must be invoked once each");

        // Slack side: 1 captured POST to /slack with the right shape.
        long slackPosts = captured.stream()
                .filter(r -> r.path().equals("/slack") && r.method().equals("POST"))
                .count();
        assertEquals(1, slackPosts,
                "SlackDriftAlerter must have POSTed exactly once via the dispatcher");

        // Logging side: 1 DRIFT_ALERT WARN line for WIPRO.
        long loggingLines = loggingAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN
                        && e.getMessage().startsWith("DRIFT_ALERT")
                        && e.getFormattedMessage().contains("symbol=WIPRO"))
                .count();
        assertEquals(1, loggingLines,
                "LoggingDriftAlerter must have emitted exactly one DRIFT_ALERT line");
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private record CapturedRequest(String method, String path, String contentType, String body) { }
}
