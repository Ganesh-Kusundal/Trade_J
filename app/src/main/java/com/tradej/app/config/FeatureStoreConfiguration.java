package com.tradej.app.config;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.feature.store.AsyncDuckDbWriter;
import com.tradej.feature.store.DuckDbFeatureStore;
import com.tradej.feature.store.InMemoryFeatureStore;
import com.tradej.feature.store.OptionsAwareFeatureStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;

/**
 * Configures feature stores: in-memory for hot-path strategy reads and DuckDB for async persistence.
 */
@Configuration
public class FeatureStoreConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FeatureStoreConfiguration.class);
    private static final String FEATURES_DB = "-features.duckdb";

    @Bean
    OptionsAwareFeatureStore hotPathFeatureStore() {
        return new OptionsAwareFeatureStore(new InMemoryFeatureStore());
    }

    @Bean
    @Primary
    FeatureStore featureStore(OptionsAwareFeatureStore hotPathFeatureStore) {
        return hotPathFeatureStore;
    }

    @Bean(destroyMethod = "close")
    DuckDbFeatureStore duckDbFeatureStore(TradingProperties properties) {
        Path featuresPath = resolveFeaturesPath(properties);
        return new DuckDbFeatureStore(featuresPath);
    }

    /**
     * Async wrapper around {@link DuckDbFeatureStore} that moves blocking JDBC writes
     * off the event dispatch thread onto a dedicated background thread. Subscribed to
     * the EventBus in place of the raw DuckDB store.
     */
    @Bean(destroyMethod = "close")
    AsyncDuckDbWriter asyncDuckDbWriter(DuckDbFeatureStore duckDbFeatureStore) {
        AsyncDuckDbWriter writer = new AsyncDuckDbWriter(duckDbFeatureStore);
        writer.start();
        log.info("AsyncDuckDbWriter bean created and started");
        return writer;
    }

    static Path resolveFeaturesPath(TradingProperties properties) {
        Path basePath = Path.of(properties.storage().duckdbPath());
        String baseName = basePath.getFileName().toString();
        String featuresName = baseName.replaceAll("\\.duckdb$", "") + FEATURES_DB;
        return basePath.getParent() != null
                ? basePath.getParent().resolve(featuresName)
                : Path.of(featuresName);
    }
}
