package com.tradej.app.e2e;

import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.scan.ScanHit;
import com.tradej.core.domain.scan.ScanResult;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.VolumeSpikeCriterion;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.scanner.model.PromotionSpec;
import com.tradej.scanner.model.RestScanSpec;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.scanner.model.UniverseSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Tag("runtime-e2e")
@ExtendWith(MockitoExtension.class)
class ScannerPipelineEndToEndTest {

    private ScanEngine scanEngine;

    @Mock
    private MarketDataProvider mockMarketData;

    @Mock
    private InstrumentResolver mockInstrumentResolver;

    @Mock
    private OptionsProvider mockOptionsProvider;

    @Mock
    private FuturesProvider mockFuturesProvider;

    private static final ExchangeSegment SEGMENT = ExchangeSegment.NSE_EQ;

    @BeforeEach
    void setUp() {
        when(mockInstrumentResolver.isLoaded()).thenReturn(true);

        ScanDependencies dependencies = new ScanDependencies(
                mockInstrumentResolver, mockMarketData, mockOptionsProvider, mockFuturesProvider
        );
        scanEngine = new ScanEngine(dependencies);
    }

    @Test
    void scanWithVolumeSpikeCriterionMatchesHighVolumeInstrument() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        Instrument infy = createInstrument("INFY", SEGMENT);

        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("INFY", SEGMENT))).thenReturn(infy);

        Quote relianceQuote = createQuote(reliance, 245000L, 50000L);
        Quote infyQuote = createQuote(infy, 150000L, 5000L);

        when(mockMarketData.getOhlcBatch(any())).thenReturn(
                Map.of(reliance.key(), relianceQuote, infy.key(), infyQuote)
        );

        ScanProfile profile = new ScanProfile(
                "volume-spike-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE", "INFY"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(2.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).isNotEmpty();
        assertThat(result.hits()).anyMatch(h -> h.symbol().equals("RELIANCE"));
        assertThat(result.hits()).noneMatch(h -> h.symbol().equals("INFY"));
    }

    @Test
    void scanWithMultipleCriteriaRequiresAllToMatch() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 50000L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        List<ScanCriterion> criteria = List.of(
                new VolumeSpikeCriterion(1.0, 10000L),
                new VolumeSpikeCriterion(1.0, 100000L)
        );

        ScanProfile profile = new ScanProfile(
                "multi-criteria-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                criteria,
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).isEmpty();
    }

    @Test
    void scanWithNoMatchingInstrumentsReturnsEmptyResult() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 500L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        ScanProfile profile = new ScanProfile(
                "no-match-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(10.0, 100000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).isEmpty();
        assertThat(result.run().hitCount()).isZero();
    }

    @Test
    void scanResultIncludesCorrectRunMetadata() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 50000L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        ScanProfile profile = new ScanProfile(
                "metadata-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.run().profileId()).isEqualTo("metadata-test");
        assertThat(result.run().universeSize()).isEqualTo(1);
        assertThat(result.run().hitCount()).isEqualTo(1);
        assertThat(result.run().startedAtMs()).isGreaterThan(0L);
        assertThat(result.run().finishedAtMs()).isGreaterThanOrEqualTo(result.run().startedAtMs());
    }

    @Test
    void scanHitContainsCorrectSnapshotFields() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 50000L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        ScanProfile profile = new ScanProfile(
                "snapshot-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).hasSize(1);
        ScanHit hit = result.hits().getFirst();
        assertThat(hit.symbol()).isEqualTo("RELIANCE");
        assertThat(hit.assetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(hit.snapshotFields()).containsEntry("ltpPaisa", 245000L);
        assertThat(hit.snapshotFields()).containsEntry("volume", 50000L);
    }

    @Test
    void scanHitScoreReflectsVolumeSpike() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 75000L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        ScanProfile profile = new ScanProfile(
                "score-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().getFirst().score()).isEqualTo(75000.0);
    }

    @Test
    void scanHitReasonContainsCriterionType() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);

        Quote quote = createQuote(reliance, 245000L, 50000L);
        when(mockMarketData.getOhlcBatch(any())).thenReturn(Map.of(reliance.key(), quote));

        ScanProfile profile = new ScanProfile(
                "reason-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY), List.of("RELIANCE"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.hits().getFirst().reasons()).isNotEmpty();
        assertThat(result.hits().getFirst().reasons().getFirst()).contains("volume-spike");
    }

    @Test
    void scanWithTopNPromotionLimitsResults() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        Instrument infy = createInstrument("INFY", SEGMENT);
        Instrument tcs = createInstrument("TCS", SEGMENT);

        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("INFY", SEGMENT))).thenReturn(infy);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("TCS", SEGMENT))).thenReturn(tcs);

        Quote rQuote = createQuote(reliance, 245000L, 100000L);
        Quote iQuote = createQuote(infy, 150000L, 80000L);
        Quote tQuote = createQuote(tcs, 350000L, 60000L);

        when(mockMarketData.getOhlcBatch(any())).thenReturn(
                Map.of(reliance.key(), rQuote, infy.key(), iQuote, tcs.key(), tQuote)
        );

        ScanProfile profile = new ScanProfile(
                "topn-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY),
                        List.of("RELIANCE", "INFY", "TCS"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(2, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).hasSize(2);
    }

    @Test
    void scanResultHitsAreSortedByScoreDesc() {
        Instrument reliance = createInstrument("RELIANCE", SEGMENT);
        Instrument infy = createInstrument("INFY", SEGMENT);
        Instrument tcs = createInstrument("TCS", SEGMENT);

        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("RELIANCE", SEGMENT))).thenReturn(reliance);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("INFY", SEGMENT))).thenReturn(infy);
        when(mockInstrumentResolver.getBySymbol(InstrumentKey.of("TCS", SEGMENT))).thenReturn(tcs);

        Quote rQuote = createQuote(reliance, 245000L, 50000L);
        Quote iQuote = createQuote(infy, 150000L, 90000L);
        Quote tQuote = createQuote(tcs, 350000L, 70000L);

        when(mockMarketData.getOhlcBatch(any())).thenReturn(
                Map.of(reliance.key(), rQuote, infy.key(), iQuote, tcs.key(), tQuote)
        );

        ScanProfile profile = new ScanProfile(
                "sorted-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(List.of(SEGMENT), List.of(AssetClass.EQUITY),
                        List.of("RELIANCE", "INFY", "TCS"), null, 1),
                new RestScanSpec(50, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new VolumeSpikeCriterion(1.0, 10000L)),
                false
        );

        ScanResult result = scanEngine.run(profile);

        assertThat(result.hits()).hasSize(3);
        assertThat(result.hits().get(0).score()).isGreaterThanOrEqualTo(result.hits().get(1).score());
        assertThat(result.hits().get(1).score()).isGreaterThanOrEqualTo(result.hits().get(2).score());
    }

    private Instrument createInstrument(String symbol, ExchangeSegment segment) {
        return new Instrument(
                symbol, symbol, segment.exchange(), segment,
                "EQ", null, null, null, null, 1L, 5L
        );
    }

    private Quote createQuote(Instrument instrument, long ltpPaisa, long volume) {
        return new Quote(
                instrument, ltpPaisa, ltpPaisa - 1000L, ltpPaisa + 1000L,
                ltpPaisa - 2000L, ltpPaisa - 500L, volume,
                volume / 2, volume / 2, 0L, System.currentTimeMillis()
        );
    }
}
