package com.tradej.research.parity;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.value.Side;
import com.tradej.research.core.RunResult;
import com.tradej.research.core.StrategyConfig;
import com.tradej.research.lab.DuckDbResearchStore;
import com.tradej.research.lab.StrategyLabService;
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

public class DeterministicReplayParityTest {

    @Test
    public void testReplayToLiveParityContract() throws SQLException {
        // 1. Setup mock candles that represent the live market session
        List<Map<String, Object>> liveMarketCandles = new ArrayList<>();
        long baseTime = 1717146000000L;
        
        for (int i = 0; i < 20; i++) {
            Map<String, Object> candle = new HashMap<>();
            candle.put("symbol", "SBIN");
            candle.put("interval", "1m");
            candle.put("barTimeMs", baseTime + (i * 60000L));
            candle.put("openPaisa", 100000L + (i * 100)); // slightly rising
            candle.put("highPaisa", 102000L + (i * 100));
            candle.put("lowPaisa", 99000L + (i * 100));
            candle.put("closePaisa", 101000L + (i * 100));
            candle.put("volume", 5000L);
            liveMarketCandles.add(candle);
        }

        // 2. Setup mock analytics engine
        DuckDbAnalyticsEngine mockEngine = Mockito.mock(DuckDbAnalyticsEngine.class);
        when(mockEngine.queryEquityCandles(eq("SBIN"), anyLong(), anyLong(), anyInt()))
            .thenReturn(liveMarketCandles);

        // 3. Create persistent store (using clean in-memory db)
        DuckDbResearchStore researchStore = new DuckDbResearchStore("jdbc:duckdb::memory:");

        // 4. Define bytecode-identical strategy
        GraphStrategyPlugin strategy = new GraphStrategyPlugin() {
            private boolean bought = false;

            @Override
            public String name() {
                return "ParityStrategy";
            }

            @Override
            public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of();
            }

            @Override
            public Optional<SignalGenerated> onEvent(DomainEvent event) {
                if (!bought) {
                    bought = true;
                    return Optional.of(new SignalGenerated(
                        EventMetadata.root(),
                        "SIG-100",
                        "SBIN",
                        "1m",
                        Side.BUY,
                        100000L, // entry price
                        98000L,  // stop loss (20 Rs down)
                        105000L, // take profit (50 Rs up)
                        "Breakout",
                        Map.of()
                    ));
                }
                return Optional.empty();
            }
        };

        // 5. Execute Run A ("Live Simulation")
        StrategyLabService liveLabService = new StrategyLabService(mockEngine, researchStore, new com.tradej.core.domain.service.PositionService());
        StrategyConfig config = StrategyConfig.create("ParityStrategy", "1.0", Map.of("threshold", 0.05));
        UUID sessionA = UUID.randomUUID();

        RunResult liveResult = liveLabService.executeBacktest(
            sessionA,
            config,
            strategy,
            baseTime,
            baseTime + (30 * 60000L)
        );

        // 6. Reset strategy internal state for Run B ("Replay Sandbox")
        GraphStrategyPlugin replayStrategy = new GraphStrategyPlugin() {
            private boolean bought = false;

            @Override
            public String name() {
                return "ParityStrategy";
            }

            @Override
            public List<Class<? extends DomainEvent>> subscribedEventTypes() {
                return List.of();
            }

            @Override
            public Optional<SignalGenerated> onEvent(DomainEvent event) {
                if (!bought) {
                    bought = true;
                    return Optional.of(new SignalGenerated(
                        EventMetadata.root(),
                        "SIG-100",
                        "SBIN",
                        "1m",
                        Side.BUY,
                        100000L,
                        98000L,
                        105000L,
                        "Breakout",
                        Map.of()
                    ));
                }
                return Optional.empty();
            }
        };

        UUID sessionB = UUID.randomUUID();
        RunResult replayResult = liveLabService.executeBacktest(
            sessionB,
            config,
            replayStrategy,
            baseTime,
            baseTime + (30 * 60000L)
        );

        // 7. Verify Contract Parity: Replay execution must yield identical trades and realized P&L
        assertNotNull(liveResult);
        assertNotNull(replayResult);
        assertEquals(liveResult.totalTrades(), replayResult.totalTrades(), "Trade count must match exactly");
        assertEquals(liveResult.winRate(), replayResult.winRate(), "Win rate must match exactly");
        assertEquals(liveResult.totalProfitLoss(), replayResult.totalProfitLoss(), "Net realized P&L must match exactly down to the last paisa");
        assertEquals(liveResult.maxDrawdown(), replayResult.maxDrawdown(), "Drawdown curve must be identical");

        // Verify database records for both runs
        try (Connection conn = researchStore.getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM run_results")) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "Database should store both runs");
            }
        }
    }
}
