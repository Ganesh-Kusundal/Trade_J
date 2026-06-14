package com.tradej.app.api;

import com.tradej.app.api.dto.StrategyMetricsResponse;
import com.tradej.strategy.observability.StrategyMetrics;
import com.tradej.strategy.observability.StrategyMetricsRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class StrategyCatalogControllerTest {

    @Test
    void catalogGroupsByStrategyId() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();
        StrategyMetrics a = new StrategyMetrics();
        a.recordOk("S1", "TickEvent");
        a.recordError("S1", "TickEvent");
        a.recordOk("S1", "CandleEvent");
        a.recordOk("S2", "TickEvent");
        r.register("default", a);

        @SuppressWarnings("unchecked")
        ObjectProvider<StrategyMetricsRegistry> provider =
                (ObjectProvider<StrategyMetricsRegistry>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(r);

        StrategyCatalogController controller = new StrategyCatalogController(provider);
        ResponseEntity<List<StrategyMetricsResponse>> resp = controller.catalog();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertEquals(2, resp.getBody().size());
        var first = resp.getBody().get(0);
        assertEquals("S1", first.id());
        assertTrue(first.counters().containsKey("TickEvent|OK"));
        assertTrue(first.counters().containsKey("TickEvent|ERROR"));
    }

    @Test
    void emptyRegistryReturnsEmptyList() {
        StrategyMetricsRegistry r = new StrategyMetricsRegistry();

        @SuppressWarnings("unchecked")
        ObjectProvider<StrategyMetricsRegistry> provider =
                (ObjectProvider<StrategyMetricsRegistry>) mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(r);

        StrategyCatalogController controller = new StrategyCatalogController(provider);
        var resp = controller.catalog();
        assertEquals(0, resp.getBody().size());
    }
}
