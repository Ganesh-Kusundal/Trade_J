package com.tradej.app.api;

import com.tradej.app.api.dto.StrategyMetricsResponse;
import com.tradej.app.api.dto.StrategySignalResponse;
import com.tradej.persistence.duckdb.DuckDbEventStore;
import com.tradej.persistence.duckdb.DuckDbEventStore.TradeLifecycleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("contract")
@ExtendWith(MockitoExtension.class)
class StrategyControllerTest {

    @Mock
    private DuckDbEventStore eventStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StrategyController(eventStore)).build();
    }

    @Test
    void signalsReturnsRowsForValidWindow() throws Exception {
        TradeLifecycleRow opened = new TradeLifecycleRow(
                "TRADE_OPENED", 1_700_000_000_000L, "SBIN", "BUY",
                25L, 60_000L, 0L, 0L, "SIG-1", "");
        TradeLifecycleRow closed = new TradeLifecycleRow(
                "TRADE_CLOSED", 1_700_000_500_000L, "SBIN", "SELL",
                0L, 0L, 60_500L, 12_500L, "SIG-1", "TARGET");
        when(eventStore.queryTradeLifecycle(anyLong(), anyLong(), any()))
                .thenReturn(List.of(opened, closed));

        mockMvc.perform(get("/api/v1/strategy/signals")
                        .param("from", "1700000000000")
                        .param("to", "1700001000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("ENTRY_LONG"))
                .andExpect(jsonPath("$[0].price").value(60000))
                .andExpect(jsonPath("$[0].quantity").value(25))
                .andExpect(jsonPath("$[0].symbol").value("SBIN"))
                .andExpect(jsonPath("$[1].type").value("EXIT_LONG"))
                .andExpect(jsonPath("$[1].pnl").value(12500));
    }

    @Test
    void signalsReturnsEmptyListWhenNoRows() throws Exception {
        when(eventStore.queryTradeLifecycle(anyLong(), anyLong(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/strategy/signals")
                        .param("from", "1700000000000")
                        .param("to", "1700001000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void signalsReturnsBadRequestWhenWindowInverted() throws Exception {
        mockMvc.perform(get("/api/v1/strategy/signals")
                        .param("from", "1700001000000")
                        .param("to", "1700000000000"))
                .andExpect(status().isBadRequest());

        verify(eventStore, never()).queryTradeLifecycle(anyLong(), anyLong(), any());
    }

    @Test
    void signalsReturnsBadRequestWhenFromIsZero() throws Exception {
        mockMvc.perform(get("/api/v1/strategy/signals")
                        .param("from", "0")
                        .param("to", "1700001000000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void metricsAggregatesClosedTradesBySide() throws Exception {
        TradeLifecycleRow c1 = new TradeLifecycleRow(
                "TRADE_CLOSED", 1_700_000_500_000L, "SBIN", "BUY",
                0L, 0L, 60_500L, 12_500L, "SIG-1", "TARGET");
        TradeLifecycleRow c2 = new TradeLifecycleRow(
                "TRADE_CLOSED", 1_700_001_000_000L, "RELIANCE", "SELL",
                0L, 0L, 250_000L, -5_000L, "SIG-2", "STOPLOSS");
        TradeLifecycleRow opened = new TradeLifecycleRow(
                "TRADE_OPENED", 1_700_000_000_000L, "SBIN", "BUY",
                25L, 60_000L, 0L, 0L, "SIG-1", "");
        when(eventStore.queryTradeLifecycle(anyLong(), anyLong(), any()))
                .thenReturn(List.of(c1, c2, opened));

        mockMvc.perform(get("/api/v1/strategy/metrics")
                        .param("from", "1700000000000")
                        .param("to", "1700002000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.strategyName == 'BUY')].totalTrades").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.strategyName == 'BUY')].winRate").value(org.hamcrest.Matchers.contains(1.0)))
                .andExpect(jsonPath("$[?(@.strategyName == 'SELL')].totalTrades").value(org.hamcrest.Matchers.contains(1)))
                .andExpect(jsonPath("$[?(@.strategyName == 'SELL')].winRate").value(org.hamcrest.Matchers.contains(0.0)));
    }

    @Test
    void metricsReturnsEmptyArrayWhenNoTrades() throws Exception {
        when(eventStore.queryTradeLifecycle(anyLong(), anyLong(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/strategy/metrics")
                        .param("from", "1700000000000")
                        .param("to", "1700002000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void metricsReturnsBadRequestWhenWindowInverted() throws Exception {
        mockMvc.perform(get("/api/v1/strategy/metrics")
                        .param("from", "1700002000000")
                        .param("to", "1700000000000"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signalsForwardsSymbolFilter() throws Exception {
        when(eventStore.queryTradeLifecycle(anyLong(), anyLong(), eq("RELIANCE")))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/strategy/signals")
                        .param("symbol", "RELIANCE")
                        .param("from", "1700000000000")
                        .param("to", "1700002000000"))
                .andExpect(status().isOk());

        verify(eventStore).queryTradeLifecycle(anyLong(), anyLong(), eq("RELIANCE"));
    }
}
