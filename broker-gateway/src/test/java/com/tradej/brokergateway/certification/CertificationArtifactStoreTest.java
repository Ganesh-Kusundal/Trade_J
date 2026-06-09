package com.tradej.brokergateway.certification;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tradej.brokergateway.result.BrokerSource;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CertificationArtifactStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void store_andLoad_roundTrips() throws IOException {
        CertificationArtifactStore store = new CertificationArtifactStore(tempDir);
        CertificationArtifact artifact = new CertificationArtifact(
                BrokerSource.DHAN, "ltp", "market-data",
                CertificationStatus.PASS, "ltp=75000", 42L,
                java.time.Instant.now(), null
        );

        store.store(artifact);

        List<CertificationArtifact> loaded = store.loadAll(BrokerSource.DHAN);
        assertEquals(1, loaded.size());
        assertEquals("ltp", loaded.get(0).checkName());
        assertEquals(CertificationStatus.PASS, loaded.get(0).status());
        assertEquals("ltp=75000", loaded.get(0).evidence());
    }

    @Test
    void storeAll_fromReport_storesAllChecks() throws IOException {
        CertificationArtifactStore store = new CertificationArtifactStore(tempDir);
        List<CertificationCheck> checks = List.of(
                CertificationCheck.pass("ltp", "ltp=75000", Duration.ofMillis(42)),
                CertificationCheck.pass("quote", "ltp=75000 open=74500", Duration.ofMillis(55)),
                CertificationCheck.fail("depth", "timeout", Duration.ofMillis(5000))
        );
        CertificationReport report = new CertificationReport(
                BrokerSource.DHAN, checks, CertificationStatus.PARTIAL, Duration.ofMillis(5097));

        store.storeAll(BrokerSource.DHAN, report);

        assertEquals(3, store.countArtifacts(BrokerSource.DHAN));
    }

    @Test
    void loadAll_emptyDir_returnsEmpty() throws IOException {
        CertificationArtifactStore store = new CertificationArtifactStore(tempDir);
        List<CertificationArtifact> loaded = store.loadAll(BrokerSource.UPSTOX);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void categorize_assignsCorrectCategories() {
        assertEquals("market-data", CertificationArtifactStore.categorize("ltp"));
        assertEquals("market-data", CertificationArtifactStore.categorize("quote"));
        assertEquals("market-data", CertificationArtifactStore.categorize("depth"));
        assertEquals("options", CertificationArtifactStore.categorize("option-chain"));
        assertEquals("portfolio", CertificationArtifactStore.categorize("portfolio-balance"));
        assertEquals("orders", CertificationArtifactStore.categorize("order-book"));
        assertEquals("advanced-orders", CertificationArtifactStore.categorize("bracket-orders"));
        assertEquals("margin", CertificationArtifactStore.categorize("margin-estimate"));
        assertEquals("catalog", CertificationArtifactStore.categorize("instrument-catalog"));
        assertEquals("capabilities", CertificationArtifactStore.categorize("session-risk"));
    }

    @Test
    void lastCertifiedAt_findsExistingArtifact() throws IOException {
        CertificationArtifactStore store = new CertificationArtifactStore(tempDir);
        CertificationArtifact artifact = new CertificationArtifact(
                BrokerSource.DHAN, "quote", "market-data",
                CertificationStatus.PASS, "ok", 30L,
                java.time.Instant.parse("2026-06-07T10:00:00Z"), null
        );
        store.store(artifact);

        Optional<java.time.Instant> ts = store.lastCertifiedAt(BrokerSource.DHAN, "quote");
        assertTrue(ts.isPresent());
    }
}
