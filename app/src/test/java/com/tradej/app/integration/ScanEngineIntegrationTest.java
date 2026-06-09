package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import com.tradej.scanner.criterion.PctChangeFromOpenCriterion;
import com.tradej.scanner.engine.ScanDependencies;
import com.tradej.scanner.engine.ScanEngine;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.scanner.model.PromotionSpec;
import com.tradej.scanner.model.RestScanSpec;
import com.tradej.scanner.model.ScanMode;
import com.tradej.scanner.model.ScanProfile;
import com.tradej.core.domain.scan.ScanRunStatus;
import com.tradej.scanner.model.UniverseSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("integration")
@Tag("broker-rest")
class ScanEngineIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void runsRestSnapshotScanAgainstIndex() throws Exception {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );
        brokerConnection.loadDailyInstrumentCatalog(Files.createTempDirectory("scan-engine-cache"), false);

        // Scan a small set of liquid equity underlyings on NSE_EQ
        ScanProfile profile = new ScanProfile(
                "integration-test",
                ScanMode.REST_SNAPSHOT,
                new UniverseSpec(
                        List.of(ExchangeSegment.NSE_EQ),
                        List.of(AssetClass.EQUITY),
                        List.of("SBIN", "RELIANCE", "TCS"),
                        null,
                        0L
                ),
                new RestScanSpec(5, false),
                new PromotionSpec(0, null, 0, 0),
                List.of(new PctChangeFromOpenCriterion(-100.0, null)),
                false
        );

        ScanEngine engine = new ScanEngine(new ScanDependencies(
                brokerConnection.instruments(),
                brokerConnection.marketData(),
                brokerConnection.options(),
                brokerConnection.futures()
        ));

        var result = engine.run(profile);
        assertNotNull(result.run());
        assertNotEquals(ScanRunStatus.FAILED, result.run().status());
        assertNotNull(result.hits());
        // Validate hit structure when market data IS available
        if (!result.hits().isEmpty()) {
            assertNotNull(result.hits().getFirst().symbol());
        }
    }
}
