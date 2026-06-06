package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.domain.DhanEdisFormResult;
import com.tradej.broker.dhan.domain.DhanEdisInquiryResult;
import com.tradej.broker.dhan.domain.DhanLedgerEntry;
import com.tradej.broker.dhan.domain.DhanProfileInfo;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("unit")
class DhanPortfolioProviderNonTradingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DhanClientHolder clientHolder;
    private DhanInstrumentResolver instrumentResolver;
    private DhanRetryExecutor executor;
    private DhanAuthenticatedHttpClient httpClient;
    private DhanApiUrlResolver apiUrlResolver;
    private DhanPortfolioProvider provider;

    @BeforeEach
    void setUp() {
        clientHolder = mock(DhanClientHolder.class);
        instrumentResolver = mock(DhanInstrumentResolver.class);
        httpClient = mock(DhanAuthenticatedHttpClient.class);
        apiUrlResolver = mock(DhanApiUrlResolver.class);
        executor = mock(DhanRetryExecutor.class);

        when(executor.execute(any(ApiCategory.class), anyString(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> supplier = invocation.getArgument(2);
                    return supplier.get();
                });

        provider = new DhanPortfolioProvider(
                clientHolder, instrumentResolver, executor, httpClient, apiUrlResolver
        );
    }

    private ObjectNode ledgerEntryNode(String date, String narration, double debit, double credit, double runbal) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("dhanClientId", "2505162156");
        node.put("narration", narration);
        node.put("voucherdate", date);
        node.put("exchange", "NSE");
        node.put("voucherdesc", "Test");
        node.put("vouchernumber", "VCH001");
        node.put("debit", debit);
        node.put("credit", credit);
        node.put("runbal", runbal);
        return node;
    }

    @Nested
    @DisplayName("getLedger")
    class GetLedgerTests {

        @Test
        @DisplayName("returns ledger entries from API response")
        void returnsLedgerEntries() {
            when(apiUrlResolver.ledgerUrl()).thenReturn("https://api.dhan.co/v2/ledger");

            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode data = root.putArray("data");
            data.add(ledgerEntryNode("2025-01-15", "Opening Balance", 0.0, 100000.0, 100000.0));
            data.add(ledgerEntryNode("2025-01-16", "Trading Charges", 250.0, 0.0, 99750.0));
            DhanJsonResponse response = new DhanJsonResponse(root);
            when(httpClient.getJson(anyString())).thenReturn(response);

            List<DhanLedgerEntry> entries = provider.getLedger(
                    LocalDate.of(2025, 1, 15),
                    LocalDate.of(2025, 1, 16)
            );

            assertEquals(2, entries.size());
            assertEquals("Opening Balance", entries.get(0).narration());
            assertEquals("2025-01-15", entries.get(0).voucherDate());
            assertEquals(100_000_00L, entries.get(0).credit()); // 100000.0 * 100
            assertEquals(99_750_00L, entries.get(1).runningBalance()); // 99750.0 * 100
            verify(httpClient, times(1)).getJson(anyString());
        }

        @Test
        @DisplayName("returns empty list when no data")
        void returnsEmptyWhenNoData() {
            when(apiUrlResolver.ledgerUrl()).thenReturn("https://api.dhan.co/v2/ledger");

            ObjectNode root = MAPPER.createObjectNode();
            root.putArray("data");
            DhanJsonResponse response = new DhanJsonResponse(root);
            when(httpClient.getJson(anyString())).thenReturn(response);

            List<DhanLedgerEntry> entries = provider.getLedger(
                    LocalDate.of(2025, 1, 1),
                    LocalDate.of(2025, 1, 2)
            );

            assertTrue(entries.isEmpty());
        }

        @Test
        @DisplayName("throws when fromDate is null")
        void throwsWhenFromDateNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> provider.getLedger(null, LocalDate.of(2025, 1, 15)));
        }

        @Test
        @DisplayName("throws when toDate is null")
        void throwsWhenToDateNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> provider.getLedger(LocalDate.of(2025, 1, 15), null));
        }
    }

    @Nested
    @DisplayName("generateEdisTpin")
    class GenerateEdisTpinTests {

        @Test
        @DisplayName("returns true on success")
        void returnsTrueOnSuccess() {
            when(apiUrlResolver.edisTpinUrl()).thenReturn("https://api.dhan.co/v2/edis/tpin");
            when(httpClient.getJson(anyString())).thenReturn(new DhanJsonResponse(MAPPER.createObjectNode()));

            assertTrue(provider.generateEdisTpin());
            verify(httpClient, times(1)).getJson("https://api.dhan.co/v2/edis/tpin");
        }
    }

    @Nested
    @DisplayName("generateEdisForm")
    class GenerateEdisFormTests {

        @Test
        @DisplayName("returns form result with HTML")
        void returnsFormResult() {
            when(apiUrlResolver.edisFormUrl()).thenReturn("https://api.dhan.co/v2/edis/form");

            ObjectNode data = MAPPER.createObjectNode();
            data.put("dhanClientId", "2505162156");
            data.put("edisFormHtml", "<form>...</form>");
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(httpClient.postJson(anyString(), any())).thenReturn(new DhanJsonResponse(root));

            DhanEdisFormResult result = provider.generateEdisForm(
                    "INE123456789", 10, "NSE", "EQ", true
            );

            assertEquals("2505162156", result.clientId());
            assertEquals("<form>...</form>", result.edisFormHtml());
            verify(httpClient, times(1)).postJson(anyString(), any());
        }
    }

    @Nested
    @DisplayName("edisInquiry")
    class EdisInquiryTests {

        @Test
        @DisplayName("returns inquiry result for ISIN")
        void returnsInquiryResult() {
            when(apiUrlResolver.edisInquiryUrl("INE123456789"))
                    .thenReturn("https://api.dhan.co/v2/edis/inquire/INE123456789");

            ObjectNode data = MAPPER.createObjectNode();
            data.put("clientId", "2505162156");
            data.put("totalQty", 100);
            data.put("aprvdQty", 50);
            data.put("status", "SUCCESS");
            data.put("remarks", "Approved");
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(httpClient.getJson(anyString())).thenReturn(new DhanJsonResponse(root));

            DhanEdisInquiryResult result = provider.edisInquiry("INE123456789");

            assertEquals("2505162156", result.clientId());
            assertEquals("INE123456789", result.isin());
            assertEquals(100, result.totalQty());
            assertEquals(50, result.approvedQty());
            assertEquals("SUCCESS", result.status());
            assertEquals("Approved", result.remarks());
            verify(httpClient, times(1)).getJson("https://api.dhan.co/v2/edis/inquire/INE123456789");
        }
    }

    @Nested
    @DisplayName("getProfile")
    class GetProfileTests {

        @Test
        @DisplayName("returns profile info")
        void returnsProfileInfo() {
            when(apiUrlResolver.profileUrl()).thenReturn("https://api.dhan.co/v2/fundlimit/userprofile");

            ObjectNode data = MAPPER.createObjectNode();
            data.put("dhanClientId", "2505162156");
            data.put("activeSegment", "NSE_EQ,NSE_FNO,BSE_EQ");
            data.put("dataPlan", "Active");
            data.put("dataValidity", "2025-12-31");
            data.put("tokenValidity", "Valid");
            data.put("ddpi", "500000");
            data.put("mtf", "100000");
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(httpClient.getJson(anyString())).thenReturn(new DhanJsonResponse(root));

            DhanProfileInfo profile = provider.getProfile();

            assertEquals("2505162156", profile.clientId());
            assertEquals("NSE_EQ,NSE_FNO,BSE_EQ", profile.activeSegment());
            assertEquals("Active", profile.dataPlan());
            assertEquals("2025-12-31", profile.dataValidity());
            assertEquals("Valid", profile.tokenValidity());
            assertEquals("500000", profile.ddpi());
            assertEquals("100000", profile.mtf());
            verify(httpClient, times(1)).getJson("https://api.dhan.co/v2/fundlimit/userprofile");
        }

        @Test
        @DisplayName("handles minimal profile response with missing fields")
        void handlesMinimalResponse() {
            when(apiUrlResolver.profileUrl()).thenReturn("https://api.dhan.co/v2/fundlimit/userprofile");

            ObjectNode data = MAPPER.createObjectNode();
            data.put("dhanClientId", "2505162156");
            ObjectNode root = MAPPER.createObjectNode();
            root.set("data", data);
            when(httpClient.getJson(anyString())).thenReturn(new DhanJsonResponse(root));

            DhanProfileInfo profile = provider.getProfile();

            assertEquals("2505162156", profile.clientId());
            assertEquals("", profile.activeSegment());
            assertEquals("", profile.dataPlan());
            assertEquals("", profile.dataValidity());
            assertEquals("", profile.tokenValidity());
            assertEquals("", profile.ddpi());
            assertEquals("", profile.mtf());
        }
    }
}
