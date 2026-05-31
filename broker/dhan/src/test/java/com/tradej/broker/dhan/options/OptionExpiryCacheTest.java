package com.tradej.broker.dhan.options;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
class OptionExpiryCacheTest {
    @Test
    void loadsOnceWithinTtl() {
        OptionExpiryCache cache = new OptionExpiryCache(5L);
        AtomicInteger loads = new AtomicInteger();

        List<LocalDate> first = cache.getOrLoad("NIFTY|IDX_I", () -> {
            loads.incrementAndGet();
            return List.of(LocalDate.of(2026, 6, 2));
        });
        List<LocalDate> second = cache.getOrLoad("NIFTY|IDX_I", () -> {
            loads.incrementAndGet();
            return List.of(LocalDate.of(2026, 6, 9));
        });

        assertEquals(first, second);
        assertEquals(1, loads.get());
    }
}
