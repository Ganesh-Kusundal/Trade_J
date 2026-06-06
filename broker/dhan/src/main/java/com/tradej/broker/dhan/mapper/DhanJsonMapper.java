package com.tradej.broker.dhan.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps Dhan REST / WebSocket JSON payloads into domain models.
 *
 * <p>Uses {@link DhanJsonResponse} field names from the official v2 API
 * ({@code last_price}, {@code netQty}, {@code orderStatus}, etc.).
 */
public final class DhanJsonMapper {
    private DhanJsonMapper() {
    }

    public static Order toOrder(DhanJsonResponse data, Instrument instrument) {
        ExchangeSegment segment = instrument != null
                ? instrument.exchangeSegment()
                : DhanFieldMapper.segment(data.string("exchangeSegment"));
        return new Order(
                data.string("orderId", "id"),
                data.string("correlationId"),
                instrument != null ? instrument.canonicalSymbol() : data.string("tradingSymbol", "symbol"),
                segment,
                DhanFieldMapper.requiredSide(data.string("transactionType")),
                DhanFieldMapper.productType(data.string("productType")),
                DhanFieldMapper.orderType(data.string("orderType")),
                DhanFieldMapper.requiredOrderStatus(data.string("orderStatus", "status")),
                data.longValue("quantity"),
                data.longValue("filledQty", "filledQuantity"),
                data.decimalPrice("price", "averageTradedPrice", "tradedPrice"),
                data.decimalPrice("triggerPrice"),
                data.longValue("exchangeTime", "updateTime", "createTime"),
                data.string("omsErrorDescription", "remarks", "message")
        );
    }

    public static Order toOrder(DhanJsonResponse response, OrderRequest request, DhanInstrumentDefinition definition) {
        DhanJsonResponse data = response.has("data") ? response.path("data") : response;
        String orderId = data.string("orderId", "id");
        String correlationId = data.string("correlationId");
        String symbol = definition == null ? data.string("tradingSymbol", "symbol") : definition.canonicalSymbol();
        ExchangeSegment segment = definition == null
                ? DhanFieldMapper.segment(data.string("exchangeSegment"))
                : definition.exchangeSegment();
        String sideValue = data.string("transactionType");
        String productTypeValue = data.string("productType");
        String orderTypeValue = data.string("orderType");
        String statusValue = data.string("orderStatus", "status");
        if (request != null) {
            if (sideValue.isBlank()) {
                sideValue = request.side().name();
            }
            if (productTypeValue.isBlank()) {
                productTypeValue = request.productType().name();
            }
            if (orderTypeValue.isBlank()) {
                orderTypeValue = request.orderType().name();
            }
            if (correlationId.isBlank()) {
                correlationId = request.correlationId();
            }
        }
        if (productTypeValue.isBlank()) {
            productTypeValue = ProductType.INTRADAY.name();
        }
        if (orderTypeValue.isBlank()) {
            orderTypeValue = OrderType.MARKET.name();
        }
        if (statusValue.isBlank()) {
            statusValue = OrderStatus.PENDING.name();
        }
        return new Order(
                orderId,
                correlationId,
                symbol,
                segment,
                DhanFieldMapper.side(sideValue),
                DhanFieldMapper.productType(productTypeValue),
                DhanFieldMapper.orderType(orderTypeValue),
                DhanFieldMapper.orderStatus(statusValue),
                data.longValue("quantity"),
                data.longValue("filledQty", "filledQuantity"),
                data.decimalPrice("price"),
                data.decimalPrice("triggerPrice"),
                0L,
                data.string("remarks", "message")
        );
    }

    public static Order toForeverOrder(DhanJsonResponse data) {
        return new Order(
                data.string("orderId", "id"),
                data.string("correlationId"),
                data.string("tradingSymbol", "symbol"),
                DhanFieldMapper.segment(data.string("exchangeSegment")),
                DhanFieldMapper.requiredSide(data.string("transactionType")),
                DhanFieldMapper.productType(data.string("productType")),
                DhanFieldMapper.orderType(data.string("orderType")),
                DhanFieldMapper.requiredOrderStatus(data.string("orderStatus", "status")),
                data.longValue("quantity"),
                0L,
                data.decimalPrice("price"),
                data.decimalPrice("triggerPrice"),
                0L,
                data.string("remarks", "message")
        );
    }

    public static Trade toTrade(DhanJsonResponse data, Instrument instrument) {
        ExchangeSegment segment = instrument != null
                ? instrument.exchangeSegment()
                : DhanFieldMapper.segment(data.string("exchangeSegment"));
        return new Trade(
                data.string("exchangeTradeId", "tradeId", "id"),
                data.string("orderId"),
                instrument != null ? instrument.canonicalSymbol() : data.string("tradingSymbol", "symbol"),
                segment,
                DhanFieldMapper.requiredSide(data.string("transactionType")),
                data.longValue("tradedQuantity", "quantity"),
                data.decimalPrice("tradedPrice", "price"),
                data.longValue("exchangeTime", "updateTime", "createTime")
        );
    }

    public static Position toPosition(DhanJsonResponse data, Instrument instrument) {
        long quantity = data.longValue("netQty", "netQuantity");
        long averagePricePaisa = data.decimalPrice("costPrice", "buyAvg", "averagePrice");
        long unrealizedPnlPaisa = data.decimalPrice("unrealizedProfit");
        long lastPricePaisa = data.decimalPrice("ltp", "lastPrice", "last_price");
        if (lastPricePaisa == 0L && quantity != 0L) {
            lastPricePaisa = averagePricePaisa + (unrealizedPnlPaisa / Math.abs(quantity));
        } else if (lastPricePaisa == 0L) {
            lastPricePaisa = averagePricePaisa;
        }
        return new Position(
                instrument != null ? instrument.canonicalSymbol() : data.string("tradingSymbol"),
                instrument != null ? instrument.exchangeSegment()
                        : DhanFieldMapper.segment(data.string("exchangeSegment")),
                DhanFieldMapper.requiredSide(data.string("positionType")),
                quantity,
                averagePricePaisa,
                lastPricePaisa,
                unrealizedPnlPaisa
        );
    }

    public static Holding toHolding(DhanJsonResponse data, Instrument instrument) {
        ExchangeSegment segment = instrument != null
                ? instrument.exchangeSegment()
                : DhanFieldMapper.equitySegment(data.string("exchange"));
        return new Holding(
                instrument != null ? instrument.canonicalSymbol() : data.string("tradingSymbol"),
                segment,
                data.longValue("totalQty", "totalQuantity"),
                data.longValue("availableQty", "availableQuantity"),
                data.longValue("collateralQty", "collateralQuantity"),
                data.decimalPrice("avgCostPrice", "averageCostPrice")
        );
    }

    public static Balance toBalance(DhanJsonResponse data) {
        return new Balance(
                data.string("dhanClientId"),
                data.decimalPrice("availabelBalance", "availableBalance"),
                data.decimalPrice("collateralAmount"),
                data.decimalPrice("receiveableAmount", "receivableAmount"),
                data.decimalPrice("utilizedAmount"),
                data.decimalPrice("withdrawableBalance")
        );
    }

    public static Quote toQuote(DhanJsonResponse data, Instrument instrument) {
        DhanJsonResponse ohlc = data.has("ohlc") ? data.path("ohlc") : data;
        return new Quote(
                instrument,
                data.decimalPrice("last_price", "lastPrice", "ltp"),
                ohlc.decimalPrice("open"),
                ohlc.decimalPrice("high"),
                ohlc.decimalPrice("low"),
                ohlc.decimalPrice("close"),
                data.longValue("volume"),
                data.longValue("buy_quantity", "totalBuyQuantity", "total_buy_quantity"),
                data.longValue("sell_quantity", "totalSellQuantity", "total_sell_quantity"),
                data.longValue("oi", "openInterest"),
                data.longValue("last_trade_time", "lastTradeTime", "ltt")
        );
    }

    public static MarketDepth toDepth(DhanJsonResponse data, Instrument instrument) {
        if (!data.has("depth")) {
            return new MarketDepth(instrument, List.of(), List.of(), 0,
                    data.longValue("last_trade_time", "lastTradeTime", "ltt"));
        }
        DhanJsonResponse depth = data.path("depth");
        List<DepthLevel> bids = depthLevels(depth.path("buy"));
        List<DepthLevel> asks = depthLevels(depth.path("sell"));
        return new MarketDepth(
                instrument,
                bids,
                asks,
                Math.max(bids.size(), asks.size()),
                data.longValue("last_trade_time", "lastTradeTime", "ltt")
        );
    }

    public static DhanJsonResponse wrap(Object raw) {
        if (raw instanceof JsonNode node) {
            return new DhanJsonResponse(node);
        }
        if (raw instanceof DhanJsonResponse response) {
            return response;
        }
        if (raw == null) {
            return DhanJsonResponse.missing();
        }
        throw new IllegalArgumentException("Unsupported Dhan payload type: " + raw.getClass().getName());
    }

    private static List<DepthLevel> depthLevels(DhanJsonResponse side) {
        if (!side.isArray()) {
            return List.of();
        }
        List<DepthLevel> levels = new ArrayList<>();
        for (DhanJsonResponse entry : side.asList()) {
            levels.add(new DepthLevel(
                    entry.decimalPrice("price"),
                    entry.longValue("quantity"),
                    (int) entry.longValue("orders")
            ));
        }
        return List.copyOf(levels);
    }
}
