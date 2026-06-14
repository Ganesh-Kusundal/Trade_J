package com.tradej.app.api;

import com.tradej.core.domain.port.FeatureRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/features")
public class FeatureController {

    private final FeatureRegistry featureRegistry;

    public FeatureController(FeatureRegistry featureRegistry) {
        this.featureRegistry = featureRegistry;
    }

    @GetMapping
    public List<Map<String, Object>> listFeatures() {
        return featureRegistry.allFeatures().stream()
                .sorted()
                .map(name -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", name);
                    entry.put("enabled", featureRegistry.isEnabled(name));
                    return entry;
                })
                .toList();
    }
}
