package com.tradej.app.scanner;

import com.tradej.composition.config.ScanProperties;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.ScanCriterionFactory;
import com.tradej.scanner.model.OptionScanSpec;
import com.tradej.scanner.model.PromotionSpec;
import com.tradej.scanner.model.RestScanSpec;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.model.UniverseSpec;

import java.util.List;

public final class ScanProfileMapper {
    private ScanProfileMapper() {
    }

    public static ScanProfile toDomain(ScanProperties.ScanProfileProperties properties) {
        ScanProperties.UniverseProperties universe = properties.universe();
        if (universe == null) {
            throw new IllegalArgumentException("Scan profile " + properties.id() + " missing universe");
        }
        UniverseSpec universeSpec = new UniverseSpec(
                universe.segments(),
                universe.assetClasses(),
                universe.underlyings(),
                universe.indexConstituentsFile(),
                universe.minLotSize()
        );
        ScanProperties.RestProperties rest = properties.rest() == null
                ? new ScanProperties.RestProperties(50, true)
                : properties.rest();
        ScanProperties.PromotionProperties promotion = properties.promotion() == null
                ? new ScanProperties.PromotionProperties(0, null, 0, 30)
                : properties.promotion();
        List<ScanCriterion> criteria = ScanCriterionFactory.fromConfigList(properties.criteria());
        OptionScanSpec optionScan = null;
        if (properties.optionScan() != null) {
            ScanProperties.OptionScanProperties os = properties.optionScan();
            optionScan = OptionScanSpec.fromConfig(
                    os.expiryPolicy(),
                    os.explicitExpiry(),
                    os.sides(),
                    os.minOpenInterest(),
                    os.minVolume(),
                    os.maxSpreadBps(),
                    os.strictSpread(),
                    os.topNPerUnderlying(),
                    os.topNGlobal()
            );
        }
        return new ScanProfile(
                properties.id(),
                properties.mode() == null ? ScanMode.REST_SNAPSHOT : properties.mode(),
                universeSpec,
                new RestScanSpec(rest.batchSize(), rest.fetchOptionChainsOnCoarsePass()),
                new PromotionSpec(
                        promotion.topN(),
                        promotion.feedMode(),
                        promotion.maxConcurrentPromotions(),
                        promotion.promotionTtlMinutes()
                ),
                criteria,
                properties.optionFinePassEnabled(),
                optionScan
        );
    }
}
