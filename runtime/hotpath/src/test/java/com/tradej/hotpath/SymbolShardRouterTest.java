package com.tradej.hotpath;

import com.tradej.core.routing.SymbolShardRouter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class SymbolShardRouterTest {

    @Test
    void returnsZeroForSingleShard() {
        assertEquals(0, SymbolShardRouter.shardFor("SBIN", 1));
    }

    @Test
    void isDeterministic() {
        int shard = SymbolShardRouter.shardFor("RELIANCE", 8);
        assertEquals(shard, SymbolShardRouter.shardFor("RELIANCE", 8));
        assertTrue(shard >= 0 && shard < 8);
    }
}
