package com.tradej.disruptor;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.TickReceived;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class ShardedDisruptorEventBusTest {

    @Test
    void routesSameSymbolToSameShard() {
        TickReceived tick1 = tick("SBIN");
        TickReceived tick2 = tick("SBIN");
        assertEquals(
                ShardedDisruptorEventBus.shardIndexFor(tick1, 4),
                ShardedDisruptorEventBus.shardIndexFor(tick2, 4)
        );
    }

    @Test
    void distributesSymbolsAcrossShards() {
        boolean[] seen = new boolean[8];
        for (String symbol : List.of("SBIN", "RELIANCE", "TCS", "INFY", "HDFC", "ICICI", "AXIS", "WIPRO", "LT")) {
            int shard = ShardedDisruptorEventBus.shardIndexFor(tick(symbol), 8);
            seen[shard] = true;
        }
        int used = 0;
        for (boolean shardUsed : seen) {
            if (shardUsed) {
                used++;
            }
        }
        assertTrue(used >= 2, "expected symbols to spread across multiple shards");
    }

    @Test
    void unknownSymbolRoutesToShardZero() {
        assertEquals(0, ShardedDisruptorEventBus.shardIndexFor(
                new com.tradej.core.domain.event.StrategyError(
                        EventMetadata.root(), "plugin", "", "test"
                ),
                4
        ));
    }

    private static TickReceived tick(String symbol) {
        return new TickReceived(
                EventMetadata.root(),
                symbol,
                "1s",
                100_000L,
                1L,
                1L,
                System.currentTimeMillis(),
                null
        );
    }
}
