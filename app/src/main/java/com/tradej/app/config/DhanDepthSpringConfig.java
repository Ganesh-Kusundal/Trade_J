package com.tradej.app.config;

import com.tradej.broker.api.port.OrderBookSnapshotProvider;
import com.tradej.broker.core.depth.OrderBookEngine;
import com.tradej.broker.dhan.depth.DhanMarketDepthProvider;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the Dhan market depth provider.
 *
 * <p>Kept in {@code app} (not in {@code broker-dhan}) because
 * {@code broker-dhan} is Spring-free. The provider itself is pure Java
 * and the lifecycle hooks are wired here.
 */
@Configuration
public class DhanDepthSpringConfig {

    private final DhanMarketDepthProvider provider;

    public DhanDepthSpringConfig(OrderBookEngine orderBookEngine) {
        this.provider = new DhanMarketDepthProvider(orderBookEngine);
    }

    @Bean
    public DhanMarketDepthProvider dhanMarketDepthProvider() {
        return provider;
    }

    @Bean
    public OrderBookSnapshotProvider dhanOrderBookSnapshotProvider() {
        return new OrderBookSnapshotProvider() {
            @Override
            public Object snapshot(String symbol, com.tradej.core.domain.value.ExchangeSegment segment, int levels) {
                return provider.snapshot(symbol, segment, levels);
            }

            @Override
            public java.util.List<Object> snapshotAll(int levels) {
                return provider.snapshotsAll(levels).stream().map(s -> (Object) s).toList();
            }

            @Override
            public int bookCount() {
                return provider.bookCount();
            }

            @Override
            public java.util.Map<String, com.tradej.core.domain.value.ExchangeSegment> activeBooks() {
                return provider.activeBooks();
            }
        };
    }

    @PostConstruct
    void start() {
        provider.start();
    }

    @PreDestroy
    void stop() {
        provider.stop();
    }
}
