package com.tradej.broker.upstox.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.Side;
import com.tradej.broker.upstox.rest.UpstoxPortfolioRestClient;

import java.util.ArrayList;
import java.util.List;

public final class UpstoxPortfolioProvider implements PortfolioProvider {

    private final UpstoxPortfolioRestClient restClient;

    public UpstoxPortfolioProvider(UpstoxPortfolioRestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<Position> getPositions() {
        JsonNode root = restClient.getPositions();
        List<Position> positions = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode pos : data) {
                long quantity = pos.has("quantity") ? pos.get("quantity").asLong() : 0L;
                positions.add(new Position(
                        pos.has("trading_symbol") ? pos.get("trading_symbol").asText() : "",
                        parseSegment(pos),
                        quantity >= 0 ? Side.BUY : Side.SELL,
                        Math.abs(quantity),
                        pos.has("average_price") ? (long) (pos.get("average_price").asDouble() * 100) : 0L,
                        pos.has("last_price") ? (long) (pos.get("last_price").asDouble() * 100) : 0L,
                        pos.has("unrealised_pnl") ? (long) (pos.get("unrealised_pnl").asDouble() * 100) : 0L
                ));
            }
        }
        return positions;
    }

    @Override
    public List<Holding> getHoldings() {
        JsonNode root = restClient.getHoldings();
        List<Holding> holdings = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode h : data) {
                holdings.add(new Holding(
                        h.has("trading_symbol") ? h.get("trading_symbol").asText() : "",
                        parseSegment(h),
                        h.has("total_quantity") ? h.get("total_quantity").asLong() : 0L,
                        h.has("quantity") ? h.get("quantity").asLong() : 0L,
                        h.has("collateral_quantity") ? h.get("collateral_quantity").asLong() : 0L,
                        h.has("average_price") ? (long) (h.get("average_price").asDouble() * 100) : 0L
                ));
            }
        }
        return holdings;
    }

    @Override
    public Balance getBalance() {
        JsonNode root = restClient.getFundsAndMargin();
        JsonNode data = root.get("data");
        if (data == null) {
            return new Balance("", 0L, 0L, 0L, 0L, 0L);
        }
        return new Balance(
                data.has("client_id") ? data.get("client_id").asText() : "",
                data.has("cash") ? (long) (data.get("cash").asDouble() * 100) : 0L,
                data.has("collateral") ? (long) (data.get("collateral").asDouble() * 100) : 0L,
                0L,
                data.has("used_margin") ? (long) (data.get("used_margin").asDouble() * 100) : 0L,
                data.has("available_margin") ? (long) (data.get("available_margin").asDouble() * 100) : 0L
        );
    }

    private static ExchangeSegment parseSegment(JsonNode node) {
        if (!node.has("exchange")) {
            return ExchangeSegment.NSE_EQ;
        }
        String ex = node.get("exchange").asText().toUpperCase();
        return switch (ex) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            case "NFO" -> ExchangeSegment.NSE_FNO;
            case "BFO" -> ExchangeSegment.BSE_FNO;
            case "MCX" -> ExchangeSegment.MCX_COMM;
            default -> ExchangeSegment.NSE_EQ;
        };
    }
}
