package com.tradej.cli.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.ScanCriterionFactory;
import com.tradej.scanner.model.AssetClass;
import com.tradej.scanner.model.PromotionSpec;
import com.tradej.scanner.model.RestScanSpec;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.model.UniverseSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ScanProfileJsonLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path DEFAULT_PATH = Path.of("config/scan-profiles.json");

    private ScanProfileJsonLoader() {
    }

    public static ScanProfile load(String profileId) throws IOException {
        return load(DEFAULT_PATH, profileId);
    }

    public static ScanProfile load(Path path, String profileId) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(path));
        for (JsonNode node : root.get("profiles")) {
            if (profileId.equals(node.get("id").asText())) {
                return toProfile(node);
            }
        }
        throw new IllegalArgumentException("Unknown scan profile in " + path + ": " + profileId);
    }

    @SuppressWarnings("unchecked")
    private static ScanProfile toProfile(JsonNode node) throws IOException {
        JsonNode universe = node.get("universe");
        List<ExchangeSegment> segments = new ArrayList<>();
        for (JsonNode s : universe.get("segments")) {
            segments.add(ExchangeSegment.valueOf(s.asText()));
        }
        List<AssetClass> assetClasses = new ArrayList<>();
        for (JsonNode ac : universe.get("assetClasses")) {
            assetClasses.add(AssetClass.valueOf(ac.asText()));
        }
        List<String> underlyings = new ArrayList<>();
        for (JsonNode u : universe.get("underlyings")) {
            underlyings.add(u.asText());
        }
        UniverseSpec universeSpec = new UniverseSpec(
                segments,
                assetClasses,
                underlyings,
                universe.has("indexConstituentsFile") ? universe.get("indexConstituentsFile").asText() : null,
                universe.path("minLotSize").asLong(0)
        );
        JsonNode rest = node.get("rest");
        RestScanSpec restSpec = new RestScanSpec(
                rest.path("batchSize").asInt(50),
                rest.path("fetchOptionChainsOnCoarsePass").asBoolean(true)
        );
        JsonNode promotion = node.get("promotion");
        FeedMode feedMode = promotion.has("feedMode")
                ? FeedMode.valueOf(promotion.get("feedMode").asText())
                : FeedMode.QUOTE;
        PromotionSpec promotionSpec = new PromotionSpec(
                promotion.path("topN").asInt(0),
                feedMode,
                promotion.path("maxConcurrentPromotions").asInt(0),
                promotion.path("promotionTtlMinutes").asInt(30)
        );
        List<Map<String, Object>> criteriaMaps = new ArrayList<>();
        if (node.has("criteria") && node.get("criteria").isArray()) {
            for (JsonNode c : node.get("criteria")) {
                criteriaMaps.add(MAPPER.convertValue(c, Map.class));
            }
        }
        List<ScanCriterion> criteria = ScanCriterionFactory.fromConfigList(criteriaMaps);
        OptionScanSpec optionScan = null;
        if (node.has("optionScan") && node.get("optionScan").isObject()) {
            JsonNode os = node.get("optionScan");
            List<String> sides = new ArrayList<>();
            if (os.has("sides") && os.get("sides").isArray()) {
                for (JsonNode s : os.get("sides")) {
                    sides.add(s.asText());
                }
            }
            optionScan = OptionScanSpec.fromConfig(
                    os.path("expiryPolicy").asText("NEAREST"),
                    os.has("explicitExpiry") ? os.get("explicitExpiry").asText() : null,
                    sides,
                    os.path("minOpenInterest").asLong(1000),
                    os.path("minVolume").asLong(0),
                    os.path("maxSpreadBps").asDouble(300),
                    os.path("strictSpread").asBoolean(false),
                    os.path("topNPerUnderlying").asInt(10),
                    os.path("topNGlobal").asInt(0)
            );
        }
        return new ScanProfile(
                node.get("id").asText(),
                ScanMode.valueOf(node.get("mode").asText()),
                universeSpec,
                restSpec,
                promotionSpec,
                criteria,
                node.path("optionFinePassEnabled").asBoolean(true),
                optionScan
        );
    }

    public static List<String> listProfileIds(Path path) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readString(path));
        List<String> ids = new ArrayList<>();
        Iterator<JsonNode> it = root.get("profiles").elements();
        while (it.hasNext()) {
            ids.add(it.next().get("id").asText());
        }
        return ids;
    }
}
