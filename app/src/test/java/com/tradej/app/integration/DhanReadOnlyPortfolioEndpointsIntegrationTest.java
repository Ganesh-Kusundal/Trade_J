package com.tradej.app.integration;

import com.tradej.broker.dhan.DhanBrokerConnection;
import com.tradej.broker.dhan.adapter.DhanPortfolioProvider;
import com.tradej.broker.dhan.domain.DhanEdisInquiryResult;
import com.tradej.broker.dhan.domain.DhanLedgerEntry;
import com.tradej.broker.dhan.domain.DhanProfileInfo;
import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.execution.service.CaffeineIdempotencyCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
@Tag("broker-rest")
class DhanReadOnlyPortfolioEndpointsIntegrationTest {
    private DhanBrokerConnection brokerConnection;

    @AfterEach
    void tearDown() {
        if (brokerConnection != null) {
            brokerConnection.disconnect();
        }
    }

    @Test
    void readsProfileLedgerAndEdisInquiryWithoutSideEffects() {
        brokerConnection = new DhanBrokerConnection(
                LiveDhanTestSupport.connectionSettingsOrSkip(),
                com.tradej.broker.dhan.constants.DhanProtocolConstants.defaultRateLimiter(),
                new CaffeineIdempotencyCache()
        );

        DhanPortfolioProvider provider = brokerConnection.getCapability(DhanPortfolioProvider.class)
                .orElseThrow(() -> new IllegalStateException("Dhan portfolio provider capability not registered"));

        DhanProfileInfo profile = provider.getProfile();
        assertEquals(LiveDhanTestSupport.value("DHAN_CLIENT_ID", "dhan.clientId"), profile.clientId());
        assertNotNull(profile.tokenValidity());

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusDays(7);
        List<DhanLedgerEntry> ledger = provider.getLedger(fromDate, toDate);
        assertNotNull(ledger);
        for (DhanLedgerEntry entry : ledger) {
            assertTrue(entry.debit() >= 0L);
            assertTrue(entry.credit() >= 0L);
        }

        try {
            DhanEdisInquiryResult inquiry = provider.edisInquiry("ALL");
            assertNotNull(inquiry);
            assertTrue(inquiry.totalQty() >= 0L);
            assertTrue(inquiry.approvedQty() >= 0L);
        } catch (DhanHttpException ex) {
            Assumptions.assumeTrue(false, "Skipping eDIS inquiry: endpoint unavailable for this account: " + ex.getMessage());
        }
    }
}
