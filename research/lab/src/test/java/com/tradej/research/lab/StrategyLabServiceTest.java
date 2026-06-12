package com.tradej.research.lab;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.Side;
import com.tradej.research.core.RunResult;
import com.tradej.research.core.StrategyConfig;
import com.tradej.strategy.api.GraphStrategyPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

public class StrategyLabServiceTest {

    @Test
    public void testStrategyBacktestExecutionAndPersistence() throws SQLException {
        // 1. Mock the analytics engine to return dummy candles
        DuckDbAnalyticsEngine mockEngine = Mockito.mock(DuckDbAnalyticsEngine.class);
        List<Map<String, Object>> mockCandles = new ArrayList<>();
        
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 20; i++) {
            Map<String, Object> candle = new HashMap<>();
            candle.put("symbol", "SBIN");
            candle.put("interval", "1m");
            candle.put("barTimeMs", baseTime + (i * 60000));
            candle.put("openPaisa", 100000L);
            candle.put("highPaisa", 102000L);
            candle.put("lowPaisa", 99000L);
            candle.put("closePaisa", 101000L);
            candle.put("volume", 5000L);
            mockCandles.add(candle);
        }
        
        when(mockEngine.queryEquityCandles(eq("SBIN"), anyLong(), anyLong(), anyInt()))
            .thenReturn(mockCandles);

        // 2. Setup in-memory DuckDB research store
        DuckDbResearchStore researchStore = new DuckDbResearchStore("jdbc:duckdb::memory:");

        // 3. Define a simple buying strategy
        GraphStrategyPlugin dummyStrategy = new GraphStrategyPlugin() {
            private boolean triggered = false;

            @Override
            public String name() {
                return "SimpleBuy";
            }

            @Override
            public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of();
            }

            @Override
            public Optional<SignalGenerated> onEvent(DomainEvent event) {
                if (!triggered) {
                    triggered = true;
                    // Trigger a BUY signal with stop loss at 995.00 Rs and take profit at 1015.00 Rs
                    return Optional.of(new SignalGenerated(
                        EventMetadata.root(),
                        UUID.randomUUID().toString(),
                        "SBIN",
                        "1m",
                        Side.BUY,
                        100000L, // entry
                        99500L,  // stop loss
                        101500L, // take profit
                        "BuySetup",
                        Map.of()
                    ));
                }
                return Optional.empty();
            }
        };

        // 4. Create StrategyLabService and execute backtest
        StrategyLabService labService = new StrategyLabService(mockEngine, researchStore, new com.tradej.core.domain.service.PositionService());
        StrategyConfig config = StrategyConfig.create("SimpleBuy", "1.0", Map.of("param1", 10));
        UUID sessionId = UUID.randomUUID();

        RunResult result = labService.executeBacktest(
            sessionId,
            config,
            dummyStrategy,
            baseTime,
            baseTime + (30 * 60000)
        );

        // 5. Assertions
        assertNotNull(result);
        assertTrue(result.totalTrades() > 0, "Should have executed at least one trade");
        assertEquals(config.configHash(), result.configHash());

        // 6. Verify data was persisted in in-memory DuckDB
        try (Connection conn = researchStore.getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM run_results")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM trade_log")) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) >= 1);
            }
        }
    }
}
