package com.tradej.app.integration;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import java.util.Optional;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.core.domain.model.Candle;
import com.tradej.persistence.replay.HistoricalRangeService.HistoricalFill;
import com.tradej.persistence.replay.HistoricalRangeService.HistoricalFillEvent;
import com.tradej.persistence.replay.HistoricalRangeService.HistoricalOrder;
import com.tradej.persistence.replay.HistoricalRangeService.RangeStats;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link com.tradej.app.admin.AdminController} historical
 * query endpoints ({@code GET /admin/historical/candles},
 * {@code GET /admin/historical/ticks}, etc.).
 */
class AdminHistoricalQueryTest extends AdminTestBase {

    @SuppressWarnings("unchecked")
    @Test
    void historicalCandlesReturnsData() {
        var candle = new Candle("SBIN", "5m", 1700000000000L, 1700000300000L,
                10000, 10100, 9900, 10050, 5000, true);
        when(historicalRangeService.queryCandles(
                eq("SBIN"), eq("5m"), eq(1700000000000L), eq(1700003600000L), eq(1000))
        ).thenReturn(List.of(candle));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/candles?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("interval")).isEqualTo("5m");
        assertThat(body.get("count")).isEqualTo(1);

        List<Map<String, Object>> candles = (List<Map<String, Object>>) body.get("candles");
        assertThat(candles).hasSize(1);
        assertThat(candles.get(0).get("startTimeMs")).isEqualTo(1700000000000L);
        assertThat(candles.get(0).get("openPaisa")).isEqualTo(10000);
        assertThat(candles.get(0).get("closePaisa")).isEqualTo(10050);
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalTicksReturnsData() {
        var tick = new MarketTickEvent(EventMetadata.root(), 0L, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER, 10050, 100, 5000L, 1700000000500L, Optional.empty(), 0L, 0L);
        when(historicalRangeService.queryTicks(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), eq(5000))
        ).thenReturn(List.of(tick));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/ticks?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("count")).isEqualTo(1);

        List<Map<String, Object>> ticks = (List<Map<String, Object>>) body.get("ticks");
        assertThat(ticks).hasSize(1);
        assertThat(ticks.get(0).get("exchangeTimestampMs")).isEqualTo(1700000000500L);
        assertThat(ticks.get(0).get("ltpPaisa")).isEqualTo(10050);
        assertThat(ticks.get(0).get("interval")).isEqualTo("1s");
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalOrdersReturnsData() {
        var order = new HistoricalOrder("evt-1", "ord-1", "corr-1", "SBIN", "TRADED", 100, 10050);
        when(historicalRangeService.queryOrders(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), eq(500))
        ).thenReturn(List.of(order));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/orders?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("count")).isEqualTo(1);

        List<Map<String, Object>> orders = (List<Map<String, Object>>) body.get("orders");
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).get("orderId")).isEqualTo("ord-1");
        assertThat(orders.get(0).get("symbol")).isEqualTo("SBIN");
        assertThat(orders.get(0).get("status")).isEqualTo("TRADED");
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalOrdersReturnsAllSymbolsWhenSymbolEmpty() {
        var order = new HistoricalOrder("evt-2", "ord-2", "corr-2", "RELIANCE", "TRADED", 200, 25000);
        when(historicalRangeService.queryOrders(
                eq(null), eq(1700000000000L), eq(1700003600000L), eq(500))
        ).thenReturn(List.of(order));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/orders?symbol=&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("*");
        assertThat(body.get("count")).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalFillsReturnsData() {
        var fill = new HistoricalFill("evt-1", "ord-1", "trade-1", "SBIN", 100, 10050);
        when(historicalRangeService.queryFills(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), eq(500))
        ).thenReturn(List.of(fill));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/fills?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("count")).isEqualTo(1);

        List<Map<String, Object>> fills = (List<Map<String, Object>>) body.get("fills");
        assertThat(fills).hasSize(1);
        assertThat(fills.get(0).get("tradeId")).isEqualTo("trade-1");
        assertThat(fills.get(0).get("pricePaisa")).isEqualTo(10050);
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalFillEventsReturnsData() {
        var fillEvent = new HistoricalFillEvent(
                "evt-1", "PARTIALLY_FILLED", "ord-1", "corr-1", "SBIN", 50, 10000, 2);
        when(historicalRangeService.queryFillEvents(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L), eq(500))
        ).thenReturn(List.of(fillEvent));

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/fill-events?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("count")).isEqualTo(1);

        List<Map<String, Object>> events = (List<Map<String, Object>>) body.get("fillEvents");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("eventType")).isEqualTo("PARTIALLY_FILLED");
        assertThat(events.get(0).get("fillCount")).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalStatsReturnsData() {
        var stats = new RangeStats(
                "SBIN", 1700000000000L, 1700003600000L,
                1000, 120, 5, 3, 8,
                1700000000100L, 1700003599000L,
                1700000000000L, 1700003580000L);
        when(historicalRangeService.rangeStats(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L))
        ).thenReturn(stats);

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/stats?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("symbol")).isEqualTo("SBIN");
        assertThat(body.get("tickCount")).isEqualTo(1000);
        assertThat(body.get("candleCount")).isEqualTo(120);
        assertThat(body.get("orderCount")).isEqualTo(5);
        assertThat(body.get("fillCount")).isEqualTo(3);
        assertThat(body.get("fillEventCount")).isEqualTo(8);
        assertThat(body.get("firstTickMs")).isEqualTo(1700000000100L);
        assertThat(body.get("lastCandleMs")).isEqualTo(1700003580000L);
        assertThat(body.get("hasData")).isEqualTo(true);
    }

    @SuppressWarnings("unchecked")
    @Test
    void historicalStatsReturnsNoData() {
        var stats = new RangeStats(
                "SBIN", 1700000000000L, 1700003600000L,
                0, 0, 0, 0, 0, 0, 0, 0, 0);
        when(historicalRangeService.rangeStats(
                eq("SBIN"), eq(1700000000000L), eq(1700003600000L))
        ).thenReturn(stats);

        ResponseEntity<Map> response = rest.getForEntity(
                "/admin/historical/stats?symbol=SBIN&from=1700000000000&to=1700003600000",
                Map.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("hasData")).isEqualTo(false);
    }
}
