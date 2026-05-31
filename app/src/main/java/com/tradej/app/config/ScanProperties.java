package com.tradej.app.config;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import com.tradej.scanner.model.AssetClass;
import com.tradej.scanner.model.ScanMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "trade.scan")
public record ScanProperties(
        @DefaultValue("false") boolean enabled,
        String defaultProfile,
        List<ScanProfileProperties> profiles
) {
    public ScanProperties {
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
    }

    public record ScanProfileProperties(
            String id,
            ScanMode mode,
            String scheduleCron,
            String scheduleZone,
            UniverseProperties universe,
            RestProperties rest,
            PromotionProperties promotion,
            @DefaultValue("true") boolean optionFinePassEnabled,
            List<Map<String, Object>> criteria,
            OptionScanProperties optionScan
    ) {
    }

    public record OptionScanProperties(
            @DefaultValue("NEAREST") String expiryPolicy,
            String explicitExpiry,
            List<String> sides,
            @DefaultValue("1000") long minOpenInterest,
            @DefaultValue("0") long minVolume,
            @DefaultValue("300") double maxSpreadBps,
            @DefaultValue("false") boolean strictSpread,
            @DefaultValue("10") int topNPerUnderlying,
            @DefaultValue("0") int topNGlobal
    ) {
    }

    public record UniverseProperties(
            List<ExchangeSegment> segments,
            List<AssetClass> assetClasses,
            List<String> underlyings,
            String indexConstituentsFile,
            @DefaultValue("0") long minLotSize
    ) {
    }

    public record RestProperties(
            @DefaultValue("50") int batchSize,
            @DefaultValue("true") boolean fetchOptionChainsOnCoarsePass
    ) {
    }

    public record PromotionProperties(
            @DefaultValue("0") int topN,
            FeedMode feedMode,
            @DefaultValue("0") int maxConcurrentPromotions,
            @DefaultValue("30") int promotionTtlMinutes
    ) {
    }
}
