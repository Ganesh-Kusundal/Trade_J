package com.tradej.app.config;

import com.tradej.core.domain.port.FeatureStore;
import com.tradej.feature.store.DuckDbFeatureStore;
import com.tradej.feature.store.InMemoryFeatureStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.file.Path;

/**
 * Configures feature stores: in-memory for hot-path strategy reads and DuckDB for async persistence.
 */
@Configuration
public class FeatureStoreConfiguration {

    private static final String FEATURES_DB = "-features.duckdb";

    @Bean
    InMemoryFeatureStore hotPathFeatureStore() {
        return new InMemoryFeatureStore();
    }

    @Bean
    @Primary
    FeatureStore featureStore(InMemoryFeatureStore hotPathFeatureStore) {
        return hotPathFeatureStore;
    }

    @Bean(destroyMethod = "close")
    DuckDbFeatureStore duckDbFeatureStore(TradingProperties properties) {
        Path featuresPath = resolveFeaturesPath(properties);
        return new DuckDbFeatureStore(featuresPath);
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
