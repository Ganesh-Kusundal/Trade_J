package com.tradej.scanner.universe;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.UniverseSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class UniverseBuilder {
    private static final Logger log = LoggerFactory.getLogger(UniverseBuilder.class);

    private final InstrumentResolver instrumentResolver;
    private final FuturesProvider futuresProvider;

    public UniverseBuilder(InstrumentResolver instrumentResolver, FuturesProvider futuresProvider) {
        this.instrumentResolver = instrumentResolver;
        this.futuresProvider = futuresProvider;
    }

    public List<ScanAsset> build(UniverseSpec spec) {
        if (!instrumentResolver.isLoaded()) {
            throw new IllegalStateException("Instrument catalog is not loaded");
        }
        List<String> underlyings = resolveUnderlyings(spec);
        if (underlyings.isEmpty()) {
            throw new IllegalArgumentException("Universe spec must define underlyings or index-constituents file");
        }
        Set<ScanAsset> assets = new LinkedHashSet<>();
        List<ExchangeSegment> segments = spec.segments().isEmpty()
                ? List.of(ExchangeSegment.NSE_EQ, ExchangeSegment.NSE_FNO)
                : spec.segments();
        for (AssetClass assetClass : spec.assetClasses()) {
            for (String underlying : underlyings) {
                for (ExchangeSegment segment : segments) {
                    addAssetsForClass(assets, assetClass, underlying, segment, spec.minLotSize());
                }
            }
        }
        log.info("Built scan universe: {} assets from {} underlyings", assets.size(), underlyings.size());
        return List.copyOf(assets);
    }

    private List<String> resolveUnderlyings(UniverseSpec spec) {
        Set<String> symbols = new LinkedHashSet<>();
        if (spec.underlyings() != null) {
            for (String underlying : spec.underlyings()) {
                if (underlying != null && !underlying.isBlank()) {
                    symbols.add(underlying.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        if (spec.indexConstituentsFile() != null && !spec.indexConstituentsFile().isBlank()) {
            symbols.addAll(IndexConstituentsLoader.load(Path.of(spec.indexConstituentsFile())));
        }
        return List.copyOf(symbols);
    }

    private void addAssetsForClass(
            Set<ScanAsset> assets,
            AssetClass assetClass,
            String underlying,
            ExchangeSegment segment,
            long minLotSize
    ) {
        switch (assetClass) {
            case EQUITY -> addEquity(assets, underlying, segment, minLotSize);
            case FUTURE -> addFuture(assets, underlying, segment);
            case OPTION -> addOptionUnderlying(assets, underlying, segment);
            default -> throw new IllegalArgumentException("Unsupported asset class: " + assetClass);
        }
    }

    private void addEquity(Set<ScanAsset> assets, String underlying, ExchangeSegment segment, long minLotSize) {
        if (!isEquitySegment(segment)) {
            return;
        }
        InstrumentKey key = new InstrumentKey(underlying, segment);
        try {
            Instrument instrument = instrumentResolver.getBySymbol(key);
            if (instrument == null) {
                instrument = instrumentResolver.requireDefinition(key);
            }
            if (minLotSize > 0 && instrument.lotSize() < minLotSize) {
                return;
            }
            if (instrument.expiry() != null && instrument.expiry().isBefore(LocalDate.now())) {
                return;
            }
            assets.add(new ScanAsset(instrument, AssetClass.EQUITY, underlying));
        } catch (RuntimeException ex) {
            log.debug("Skipping equity {} on {}: {}", underlying, segment, ex.getMessage());
        }
    }

    private void addFuture(Set<ScanAsset> assets, String underlying, ExchangeSegment segment) {
        if (!isFnoSegment(segment)) {
            return;
        }
        try {
            Instrument contract = futuresProvider.getNearestContract(underlying, segment);
            assets.add(new ScanAsset(contract, AssetClass.FUTURE, underlying));
        } catch (RuntimeException ex) {
            log.debug("Skipping future {} on {}: {}", underlying, segment, ex.getMessage());
        }
    }

    private void addOptionUnderlying(Set<ScanAsset> assets, String underlying, ExchangeSegment segment) {
        if (!isFnoSegment(segment)) {
            return;
        }
        InstrumentKey indexKey = new InstrumentKey(underlying, segment);
        try {
            Instrument indexOrUnderlying = instrumentResolver.getBySymbol(indexKey);
            if (indexOrUnderlying == null) {
                indexOrUnderlying = instrumentResolver.requireDefinition(indexKey);
            }
            assets.add(new ScanAsset(indexOrUnderlying, AssetClass.OPTION, underlying));
        } catch (RuntimeException ex) {
            log.debug("Skipping option underlying {} on {}: {}", underlying, segment, ex.getMessage());
        }
    }

    private static boolean isEquitySegment(ExchangeSegment segment) {
        return segment == ExchangeSegment.NSE_EQ || segment == ExchangeSegment.BSE_EQ;
    }

    private static boolean isFnoSegment(ExchangeSegment segment) {
        return segment == ExchangeSegment.NSE_FNO
                || segment == ExchangeSegment.BSE_FNO
                || segment == ExchangeSegment.MCX_COMM;
    }
}
