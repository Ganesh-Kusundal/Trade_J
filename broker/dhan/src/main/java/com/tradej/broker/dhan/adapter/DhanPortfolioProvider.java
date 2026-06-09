package com.tradej.broker.dhan.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.constants.DhanApiUrlResolver;
import com.tradej.broker.dhan.domain.DhanEdisFormResult;
import com.tradej.broker.dhan.domain.DhanEdisInquiryResult;
import com.tradej.broker.dhan.domain.DhanLedgerEntry;
import com.tradej.broker.dhan.domain.DhanProfileInfo;
import com.tradej.broker.dhan.http.DhanAuthenticatedHttpClient;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.broker.dhan.mapper.DhanJsonMapper;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;
import com.tradej.broker.dhan.rate.ApiCategory;
import com.tradej.broker.dhan.resilience.DhanRetryExecutor;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DhanPortfolioProvider implements PortfolioProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DhanAdapterContext context;
    private final DhanAuthenticatedHttpClient httpClient;
    private final DhanApiUrlResolver apiUrlResolver;

    public DhanPortfolioProvider(
            DhanAdapterContext context,
            DhanAuthenticatedHttpClient httpClient,
            DhanApiUrlResolver apiUrlResolver
    ) {
        this.context = context;
        this.httpClient = httpClient;
        this.apiUrlResolver = apiUrlResolver;
    }

    @Override
    public List<Position> getPositions() {
        return context.execute(ApiCategory.NON_TRADING, "positions", () -> {
            var response = httpClient.getJson(apiUrlResolver.positionsUrl());
            var data = response.has("data") ? response.path("data") : response;
            if (!data.isArray()) {
                return List.<Position>of();
            }
            List<Position> positions = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                DhanInstrumentDefinition definition = context.resolvePayload(item.raw());
                positions.add(DhanJsonMapper.toPosition(item, definition.toInstrument()));
            }
            return List.copyOf(positions);
        });
    }

    @Override
    public List<Holding> getHoldings() {
        return context.execute(ApiCategory.NON_TRADING, "holdings", () -> {
            var response = httpClient.getJson(apiUrlResolver.holdingsUrl());
            var data = response.has("data") ? response.path("data") : response;
            if (!data.isArray()) {
                return List.<Holding>of();
            }
            List<Holding> holdings = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                DhanInstrumentDefinition definition = context.resolvePayload(item.raw());
                holdings.add(DhanJsonMapper.toHolding(item, definition.toInstrument()));
            }
            return List.copyOf(holdings);
        });
    }

    @Override
    public Balance getBalance() {
        return context.execute(ApiCategory.NON_TRADING, "fund-limits", () -> {
            var response = httpClient.getJson(apiUrlResolver.fundLimitUrl());
            var data = response.has("data") ? response.path("data") : response;
            return DhanJsonMapper.toBalance(data);
        });
    }

    /**
     * Fetch ledger report for a date range ({@code GET /ledger?from-date=...&to-date=...}).
     *
     * @param fromDate start date (inclusive)
     * @param toDate   end date (inclusive)
     * @return list of ledger entries
     */
    public List<DhanLedgerEntry> getLedger(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required for ledger report");
        }
        return context.execute(ApiCategory.NON_TRADING, "ledger", () -> {
            String url = apiUrlResolver.ledgerUrl()
                    + "?from-date=" + fromDate
                    + "&to-date=" + toDate;
            var response = httpClient.getJson(url);
            var data = response.has("data") ? response.path("data") : response;
            if (!data.isArray()) {
                return List.<DhanLedgerEntry>of();
            }
            List<DhanLedgerEntry> entries = new ArrayList<>();
            for (DhanJsonResponse item : data.asList()) {
                entries.add(new DhanLedgerEntry(
                        item.string("dhanClientId"),
                        item.string("narration"),
                        item.string("voucherdate"),
                        item.string("exchange"),
                        item.string("voucherdesc"),
                        item.string("vouchernumber"),
                        item.decimalPrice("debit"),
                        item.decimalPrice("credit"),
                        item.decimalPrice("runbal")
                ));
            }
            return List.copyOf(entries);
        });
    }

    /**
     * Generate an eDIS T-PIN sent to the registered mobile number ({@code GET /edis/tpin}).
     *
     * @return true if the T-PIN was sent successfully (HTTP 202)
     */
    public boolean generateEdisTpin() {
        return context.execute(ApiCategory.NON_TRADING, "edis-tpin", () -> {
            httpClient.getJson(apiUrlResolver.edisTpinUrl());
            return true;
        });
    }

    /**
     * Generate an eDIS form for pledging securities ({@code POST /edis/form}).
     *
     * @param isin     ISIN of the security to pledge
     * @param quantity Quantity to pledge
     * @param exchange Exchange (e.g. "NSE", "BSE")
     * @param segment  Segment (e.g. "EQ", "FO")
     * @param bulk     Whether this is a bulk pledge
     * @return eDIS form result with embedded HTML form
     */
    public DhanEdisFormResult generateEdisForm(String isin, long quantity, String exchange, String segment, boolean bulk) {
        return context.execute(ApiCategory.NON_TRADING, "edis-form", () -> {
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put("isin", isin);
            payload.put("qty", quantity);
            payload.put("exchange", exchange);
            payload.put("segment", segment);
            payload.put("bulk", bulk);
            var response = httpClient.postJson(apiUrlResolver.edisFormUrl(), payload);
            var data = response.has("data") ? response.path("data") : response;
            return new DhanEdisFormResult(
                    data.string("dhanClientId"),
                    data.string("edisFormHtml")
            );
        });
    }

    /**
     * Check the status of an eDIS request ({@code GET /edis/inquire/{isin}}).
     *
     * @param isin ISIN of the security, or "ALL" to check all
     * @return eDIS inquiry result
     */
    public DhanEdisInquiryResult edisInquiry(String isin) {
        return context.execute(ApiCategory.NON_TRADING, "edis-inquiry", () -> {
            var response = httpClient.getJson(apiUrlResolver.edisInquiryUrl(isin));
            var data = response.has("data") ? response.path("data") : response;
            return new DhanEdisInquiryResult(
                    data.string("clientId"),
                    isin,
                    data.longValue("totalQty"),
                    data.longValue("aprvdQty"),
                    data.string("status"),
                    data.string("remarks")
            );
        });
    }

    /**
     * Fetch user profile information ({@code GET /fundlimit/userprofile}).
     *
     * @return profile information including token validity, data plan, and segment access
     */
    public DhanProfileInfo getProfile() {
        return context.execute(ApiCategory.NON_TRADING, "profile", () -> {
            var response = httpClient.getJson(apiUrlResolver.profileUrl());
            var data = response.has("data") ? response.path("data") : response;
            return new DhanProfileInfo(
                    data.string("dhanClientId"),
                    data.string("activeSegment"),
                    data.string("dataPlan"),
                    data.string("dataValidity"),
                    data.string("tokenValidity"),
                    data.string("ddpi"),
                    data.string("mtf")
            );
        });
    }
}
