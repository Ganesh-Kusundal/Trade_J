package com.tradej.broker.icici.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.icici.mapper.IciciExchangeSegmentMapper;
import com.tradej.broker.icici.rest.BreezePortfolioRestClient;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;

import java.util.ArrayList;
import java.util.List;

public final class IciciPortfolioProvider implements PortfolioProvider {
    private final BreezePortfolioRestClient restClient;

    public IciciPortfolioProvider(BreezePortfolioRestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<Position> getPositions() {
        JsonNode success = restClient.getPortfolioPositions();
        List<Position> positions = new ArrayList<>();
        if (success == null || !success.isArray()) {
            return positions;
        }
        for (JsonNode node : success) {
            long quantity = parseLong(node.path("quantity").asText("0"));
            if (quantity == 0L) {
                continue;
            }
            String stockCode = node.path("stock_code").asText("");
            ExchangeSegment segment = resolveSegmentForStock(stockCode);
            positions.add(new Position(
                    stockCode,
                    segment,
                    quantity >= 0 ? Side.BUY : Side.SELL,
                    Math.abs(quantity),
                    pricePaisa(node.path("average_price").asText("0")),
                    pricePaisa(node.path("current_market_price").asText("0")),
                    pricePaisa(node.path("pnl").asText("0"))
            ));
        }
        return positions;
    }

    @Override
    public List<Holding> getHoldings() {
        JsonNode success = restClient.getDematHoldings();
        List<Holding> holdings = new ArrayList<>();
        if (success == null || !success.isArray()) {
            return holdings;
        }
        for (JsonNode node : success) {
            String stockCode = node.path("stock_code").asText("");
            ExchangeSegment segment = resolveSegmentForStock(stockCode);
            holdings.add(new Holding(
                    stockCode,
                    segment,
                    parseLong(node.path("quantity").asText("0")),
                    parseLong(node.path("demat_avail_quantity").asText("0")),
                    0L,
                    0L
            ));
        }
        return holdings;
    }

    private ExchangeSegment resolveSegmentForStock(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return ExchangeSegment.NSE_EQ;
        }
        return IciciExchangeSegmentMapper.resolveSegmentFromStockCode(stockCode);
    }

    @Override
    public Balance getBalance() {
        JsonNode success = restClient.getFunds();
        if (success == null || success.isMissingNode()) {
            return new Balance("", 0L, 0L, 0L, 0L, 0L);
        }
        return new Balance(
                "",
                pricePaisa(success.path("total_bank_balance").asText("0")),
                0L,
                0L,
                pricePaisa(success.path("allocated_equity").asText("0")),
                pricePaisa(success.path("fund_limit").asText("0"))
        );
    }

    private static long pricePaisa(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Math.round(Double.parseDouble(value) * 100.0);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return (long) Double.parseDouble(value);
    }
}
