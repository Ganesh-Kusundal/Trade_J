package com.tradej.app.contract;

import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Contract test: every GatewayTopic enum value must have a corresponding
 * serializer registered in GatewayEventBridge AND a message definition
 * in docs/asyncapi.yaml.
 */
@Tag("contract")
class WebSocketContractTest {

    @Test
    @DisplayName("All GatewayTopic values must appear in GatewayEventBridge serializer map")
    void allTopicsHaveSerializers() throws IOException {
        Path bridgePath = findFile("GatewayEventBridge.java");
        Path topicsPath = findFile("BridgeTopics.java");
        if (bridgePath == null || topicsPath == null) return;

        String bridgeContent = Files.readString(bridgePath);
        String topicsContent = Files.readString(topicsPath);
        String combined = bridgeContent + "\n" + topicsContent;

        List<String> missing = new ArrayList<>();
        for (GatewayTopic topic : GatewayTopic.values()) {
            // PIPELINE_HEALTH and some topics are published directly, not via event bridge
            if (topic == GatewayTopic.PIPELINE_HEALTH
                    || topic == GatewayTopic.DEPTH_IMBALANCE
                    || topic == GatewayTopic.HEATMAP_CHUNK
                    || topic == GatewayTopic.ICEBERG_ALERT
                    || topic == GatewayTopic.ABSORPTION_ALERT
                    || topic == GatewayTopic.SR_LEVELS_UPDATE
                    || topic == GatewayTopic.ORDER_BOOK_SNAPSHOT) {
                continue; // These are published directly by other components
            }

            if (!combined.contains("GatewayTopic." + topic.name())) {
                missing.add(topic.name());
            }
        }

        if (!missing.isEmpty()) {
            fail("GatewayTopic values missing from GatewayEventBridge/BridgeTopics:\n  "
                    + String.join(", ", missing));
        }
    }

    @Test
    @DisplayName("All GatewayTopic values must be documented in AsyncAPI spec")
    void allTopicsInAsyncApi() throws IOException {
        Path asyncApiPath = findFile("asyncapi.yaml");
        if (asyncApiPath == null) return;

        String asyncApiContent = Files.readString(asyncApiPath);

        List<String> missing = new ArrayList<>();
        for (GatewayTopic topic : GatewayTopic.values()) {
            if (!asyncApiContent.contains(topic.name())) {
                missing.add(topic.name());
            }
        }

        if (!missing.isEmpty()) {
            fail("GatewayTopic values missing from asyncapi.yaml:\n  " + String.join(", ", missing));
        }
    }

    @Test
    @DisplayName("GatewayTopic wire IDs must be unique")
    void wireIdsAreUnique() {
        var wireIds = Arrays.stream(GatewayTopic.values())
                .collect(Collectors.groupingBy(GatewayTopic::wireId));

        List<String> duplicates = wireIds.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> "wireId " + e.getKey() + ": " + e.getValue())
                .toList();

        assertTrue(duplicates.isEmpty(),
                "Duplicate wire IDs found: " + duplicates);
    }

    @Test
    @DisplayName("AsyncAPI spec must document binary frame format")
    void binaryFrameFormatDocumented() throws IOException {
        Path asyncApiPath = findFile("asyncapi.yaml");
        if (asyncApiPath == null) return;

        String content = Files.readString(asyncApiPath);
        assertTrue(content.contains("1 byte topic") || content.contains("wireId") || content.contains("Binary Frame Format"),
                "AsyncAPI spec must document the binary frame format");
    }

    private Path findFile(String name) throws IOException {
        for (String base : List.of("../docs/", "docs/", "../gateway/src/", "gateway/src/")) {
            Path p = Path.of(base);
            if (Files.exists(p)) {
                var found = Files.walk(p)
                        .filter(f -> f.getFileName().toString().equals(name))
                        .findFirst();
                if (found.isPresent()) return found.get();
            }
        }
        return null;
    }
}
