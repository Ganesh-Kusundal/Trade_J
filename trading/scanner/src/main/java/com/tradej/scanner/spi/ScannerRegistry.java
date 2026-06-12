package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.ScanCriterion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for discovering scanner profile plugins via {@link ServiceLoader}.
 */
public final class ScannerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ScannerRegistry.class);

    private final Map<String, ScannerProvider> providers;

    private ScannerRegistry(Map<String, ScannerProvider> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    public static ScannerRegistry discover() {
        Map<String, ScannerProvider> map = new LinkedHashMap<>();
        ServiceLoader.load(ScannerProvider.class).forEach(provider -> {
            if (provider.isEnabled()) {
                if (map.containsKey(provider.name())) {
                    log.warn("Duplicate scanner provider '{}' — keeping first", provider.name());
                } else {
                    map.put(provider.name(), provider);
                    log.info("Registered scanner: {} ({} criteria)", provider.name(), provider.criteria().size());
                }
            }
        });
        log.info("ScannerRegistry initialized with {} providers: {}", map.size(), map.keySet());
        return new ScannerRegistry(map);
    }

    public Optional<ScannerProvider> get(String name) {
        return Optional.ofNullable(providers.get(name));
    }

    public List<ScanCriterion> criteria(String name) {
        ScannerProvider provider = providers.get(name);
        return provider != null ? provider.criteria() : List.of();
    }

    public List<String> names() {
        return List.copyOf(providers.keySet());
    }

    public Map<String, ScannerProvider> all() {
        return providers;
    }

    public int size() {
        return providers.size();
    }
}
