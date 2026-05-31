package com.tradej.app.integration;

import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.runtime.RuntimeMode;
import com.tradej.persistence.replay.ReplayResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.tradej.app.admin.AdminController} historical
 * replay endpoints ({@code POST /admin/historical/replay/*} and
 * {@code POST /admin/chronicle/replay}).
 * <p>
 * All tests mock {@link com.tradej.core.domain.runtime.RuntimeModeHolder#mode()}
 * to return {@link RuntimeMode#REPLAY} (or {@link RuntimeMode#LIVE} for the
 * rejection test).
 */
class AdminReplayTest extends AdminTestBase {

    @SuppressWarnings("unchecked")
    @Test
    void replayTicksReturnsResult() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayTicks(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), any(EventBus.class))
        ).thenReturn(new ReplayResult(100, 100, 0));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/ticks?symbol=SBIN&from=1700000000000&to=1700003600000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mode")).isEqualTo("ticks");
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("from")).isEqualTo(1700000000000L);
        assertThat(body.get("to")).isEqualTo(1700003600000L);
        assertThat(body.get("totalRead")).isEqualTo(100);
        assertThat(body.get("replayed")).isEqualTo(100);
        assertThat(body.get("failed")).isEqualTo(0);
        assertThat(body.get("complete")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayTicksRejectedInLiveMode() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.LIVE);

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/ticks?symbol=SBIN&from=1000&to=2000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(409);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Historical replay is blocked in LIVE mode");
        assertThat(body.get("hint")).isEqualTo("Set trade.runtime.mode=REPLAY before invoking replay endpoints");
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayCandlesReturnsResult() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayCandles(
                eq("SBIN"), eq("5m"), eq(1700000000000L), eq(1700003600000L), any(EventBus.class))
        ).thenReturn(new ReplayResult(50, 48, 2));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/candles?symbol=SBIN&from=1700000000000&to=1700003600000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mode")).isEqualTo("candles");
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("interval")).isEqualTo("5m");
        assertThat(body.get("totalRead")).isEqualTo(50);
        assertThat(body.get("replayed")).isEqualTo(48);
        assertThat(body.get("failed")).isEqualTo(2);
        assertThat(body.get("complete")).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayOrdersReturnsResult() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayOrders(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), any(EventBus.class))
        ).thenReturn(new ReplayResult(20, 20, 0));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/orders?symbol=SBIN&from=1700000000000&to=1700003600000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mode")).isEqualTo("orders");
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("totalRead")).isEqualTo(20);
        assertThat(body.get("replayed")).isEqualTo(20);
        assertThat(body.get("failed")).isEqualTo(0);
        assertThat(body.get("complete")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayFillEventsReturnsResult() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayFillEvents(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), any(EventBus.class))
        ).thenReturn(new ReplayResult(30, 28, 2));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/fills?symbol=SBIN&from=1700000000000&to=1700003600000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mode")).isEqualTo("fill-events");
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("totalRead")).isEqualTo(30);
        assertThat(body.get("replayed")).isEqualTo(28);
        assertThat(body.get("failed")).isEqualTo(2);
        assertThat(body.get("complete")).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayFillEventsReturnsAllSymbolsWhenSymbolEmpty() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayFillEvents(
                eq(null), eq(1700000000000L), eq(1700003600000L), any(EventBus.class))
        ).thenReturn(new ReplayResult(10, 10, 0));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/historical/replay/fills?symbol=&from=1700000000000&to=1700003600000",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("mode")).isEqualTo("fill-events");
        assertThat(body.get("symbol")).isEqualTo("*");
        assertThat(body.get("totalRead")).isEqualTo(10);
        assertThat(body.get("complete")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayChronicleReturnsResult() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);
        when(replayOrchestrator.replayChronicle(any())).thenReturn(new ReplayResult(50, 50, 0));

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/chronicle/replay?eventType=com.tradej.core.domain.event.TickReceived",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("eventType")).isEqualTo("com.tradej.core.domain.event.TickReceived");
        assertThat(body.get("totalRead")).isEqualTo(50);
        assertThat(body.get("replayed")).isEqualTo(50);
        assertThat(body.get("failed")).isEqualTo(0);
        assertThat(body.get("complete")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayChronicleRejectsUnknownEventType() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/chronicle/replay?eventType=com.example.NonExistent",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Unknown event type: com.example.NonExistent");
    }

    @SuppressWarnings("unchecked")
    @Test
    void replayChronicleRejectsNonDomainEventType() {
        when(runtimeModeHolder.mode()).thenReturn(RuntimeMode.REPLAY);

        ResponseEntity<Map> response = rest.postForEntity(
                "/admin/chronicle/replay?eventType=java.lang.String",
                null, Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Type must implement DomainEvent: java.lang.String");
    }
}
