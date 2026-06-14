package com.tradej.app.config;

import com.tradej.core.domain.port.FeatureRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class DefaultFeatureRegistry implements FeatureRegistry {

    private static final Set<String> ALL_FEATURES = Set.of(
            "live-trading",
            "paper-trading",
            "replay",
            "scanner",
            "options-analytics",
            "pipeline-studio",
            "backtest",
            "news-feed",
            "mcp-server",
            "ml-strategies",
            "institutional-scanner"
    );

    private final Set<String> enabled;

    public DefaultFeatureRegistry(
            @Value("${tradej.features.enabled:live-trading,scanner,options-analytics,pipeline-studio,replay}")
            String enabledCsv
    ) {
        this.enabled = new LinkedHashSet<>();
        for (String f : enabledCsv.split(",")) {
            String trimmed = f.trim();
            if (!trimmed.isEmpty()) {
                enabled.add(trimmed);
            }
        }
    }

    @Override
    public boolean isEnabled(String featureName) {
        return enabled.contains(featureName);
    }

    @Override
    public Set<String> enabledFeatures() {
        return Set.copyOf(enabled);
    }

    @Override
    public Set<String> allFeatures() {
        return ALL_FEATURES;
    }
}
