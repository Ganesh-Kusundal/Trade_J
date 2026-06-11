package com.tradej.app.api;

import com.tradej.broker.api.spi.BrokerDescriptor;
import com.tradej.broker.api.spi.BrokerRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/brokers")
public class BrokerRegistryController {

    private final BrokerRegistry brokerRegistry;

    public BrokerRegistryController(BrokerRegistry brokerRegistry) {
        this.brokerRegistry = brokerRegistry;
    }

    @GetMapping
    public List<Map<String, Object>> listBrokers() {
        return brokerRegistry.descriptors().stream()
                .map(this::toResponse)
                .toList();
    }

    private Map<String, Object> toResponse(BrokerDescriptor d) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", d.source().name());
        result.put("displayName", d.displayName());
        result.put("supportedSegments", d.supportedSegments());
        result.put("capabilities", d.capabilities());
        result.put("credentialFields", credentialFieldsFor(d.source().name()));
        result.put("version", "1.0.0");
        return result;
    }

    private List<Map<String, String>> credentialFieldsFor(String source) {
        return switch (source) {
            case "DHAN" -> List.of(
                    Map.of("key", "clientId", "label", "Client ID", "type", "text", "placeholder", "Enter Dhan Client ID"),
                    Map.of("key", "accessToken", "label", "Access Token", "type", "password", "placeholder", "Enter Dhan Access Token")
            );
            case "UPSTOX" -> List.of(
                    Map.of("key", "apiKey", "label", "API Key", "type", "text", "placeholder", "Enter Upstox API Key"),
                    Map.of("key", "accessToken", "label", "Access Token", "type", "password", "placeholder", "Enter Upstox Access Token")
            );
            case "ICICI" -> List.of(
                    Map.of("key", "appKey", "label", "App Key", "type", "text", "placeholder", "Enter ICICI App Key"),
                    Map.of("key", "secretKey", "label", "Secret Key", "type", "password", "placeholder", "Enter ICICI Secret Key")
            );
            default -> List.of();
        };
    }
}
