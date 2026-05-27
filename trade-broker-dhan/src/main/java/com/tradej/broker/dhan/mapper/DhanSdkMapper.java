package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.DepthLevel;
import com.tradej.core.domain.model.Holding;
import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.MarketDepth;
import com.tradej.core.domain.model.Order;
import com.tradej.core.domain.model.Position;
import com.tradej.core.domain.model.Quote;
import com.tradej.core.domain.model.Trade;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.PriceMath;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DhanSdkMapper {
    private DhanSdkMapper() {
    }

    public static Order toOrder(DhanSdkResponse<?> source, Instrument instrument) {
        ExchangeSegment segment = instrument != null ? instrument.exchangeSegment()
                : segment(firstOptionalString(source, "getExchangeSegment"));
        return new Order(
                source.string("getOrderId"),
                firstOptionalString(source, "getCorrelationId"),
                instrument != null ? instrument.canonicalSymbol() : source.string("getTradingSymbol"),
                segment,
                requiredSide(source.string("getTransactionType")),
                requiredProductType(source.string("getProductType")),
                requiredOrderType(source.string("getOrderType")),
                requiredOrderStatus(source.string("getOrderStatus")),
                source.longValue("getQuantity"),
                firstOptionalLong(source, "getFilledQty").orElse(0L),
                PriceMath.toPaisa(firstOptionalDecimal(source, "getPrice", "getAverageTradedPrice").orElse(BigDecimal.ZERO)),
                PriceMath.toPaisa(firstOptionalDecimal(source, "getTriggerPrice").orElse(BigDecimal.ZERO)),
                firstTimestamp(source, "getExchangeTime", "getUpdateTime", "getCreateTime").orElse(0L),
                firstOptionalString(source, "getOmsErrorDescription")
        );
    }

    public static Trade toTrade(DhanSdkResponse<?> source, Instrument instrument) {
        ExchangeSegment segment = instrument != null ? instrument.exchangeSegment()
                : segment(firstOptionalString(source, "getExchangeSegment"));
        return new Trade(
                source.string("getExchangeTradeId"),
                source.string("getOrderId"),
                instrument != null ? instrument.canonicalSymbol() : source.string("getTradingSymbol"),
                segment,
                requiredSide(source.string("getTransactionType")),
                source.longValue("getTradedQuantity"),
                PriceMath.toPaisa(source.decimal("getTradedPrice")),
                firstTimestamp(source, "getExchangeTime", "getUpdateTime", "getCreateTime").orElse(0L)
        );
    }

    public static Position toPosition(DhanSdkResponse<?> source, Instrument instrument) {
        long quantity = source.longValue("getNetQuantity", "getNetQty");
        long averagePricePaisa = PriceMath.toPaisa(source.decimal("getCostPrice"));
        long unrealizedPnlPaisa = PriceMath.toPaisa(source.decimal("getUnrealizedProfit"));
        long lastPricePaisa = firstOptionalDecimal(source, "getLtp", "getLastPrice")
                .map(PriceMath::toPaisa)
                .orElse(averagePricePaisa);
        if (lastPricePaisa == averagePricePaisa && quantity != 0L) {
            lastPricePaisa = averagePricePaisa + (unrealizedPnlPaisa / Math.abs(quantity));
        }
        return new Position(
                instrument != null ? instrument.canonicalSymbol() : source.string("getTradingSymbol"),
                instrument != null ? instrument.exchangeSegment() : segment(source.string("getExchangeSegment")),
                requiredSide(source.string("getPositionType")),
                quantity,
                averagePricePaisa,
                lastPricePaisa,
                unrealizedPnlPaisa
        );
    }

    public static Balance toBalance(DhanSdkResponse<?> source) {
        return new Balance(
                source.string("getDhanClientId"),
                PriceMath.toPaisa(source.decimal("getAvailableBalance")),
                PriceMath.toPaisa(source.decimal("getCollateralAmount")),
                PriceMath.toPaisa(source.decimal("getReceivableAmount")),
                PriceMath.toPaisa(source.decimal("getUtilizedAmount")),
                PriceMath.toPaisa(source.decimal("getWithdrawableBalance"))
        );
    }

    public static Holding toHolding(DhanSdkResponse<?> source, Instrument instrument) {
        ExchangeSegment segment = instrument != null ? instrument.exchangeSegment() : equitySegment(source.string("getExchange"));
        return new Holding(
                instrument != null ? instrument.canonicalSymbol() : source.string("getTradingSymbol"),
                segment,
                source.longValue("getTotalQuantity"),
                source.longValue("getAvailableQuantity"),
                firstOptionalLong(source, "getCollateralQuantity").orElse(0L),
                PriceMath.toPaisa(source.decimal("getAverageCostPrice"))
        );
    }

    public static Quote toQuote(DhanSdkResponse<?> quoteSource, Instrument instrument) {
        if (quoteSource == null || quoteSource.raw() == null) {
            throw new IllegalStateException("Missing Dhan quote payload for " + instrument.canonicalSymbol());
        }
        return new Quote(
                instrument,
                PriceMath.toPaisa(quoteSource.decimal("getLastPrice")),
                PriceMath.toPaisa(quoteSource.optionalDecimal("getOpen").orElse(BigDecimal.ZERO)),
                PriceMath.toPaisa(quoteSource.optionalDecimal("getHigh").orElse(BigDecimal.ZERO)),
                PriceMath.toPaisa(quoteSource.optionalDecimal("getLow").orElse(BigDecimal.ZERO)),
                PriceMath.toPaisa(quoteSource.optionalDecimal("getClose").orElse(BigDecimal.ZERO)),
                quoteSource.optionalLong("getVolume").orElse(0L),
                quoteSource.optionalLong("getTotalBuyQuantity").orElse(0L),
                quoteSource.optionalLong("getTotalSellQuantity").orElse(0L),
                firstTimestamp(quoteSource, "getLastTradeTime", "getLtt").orElse(0L)
        );
    }

    public static MarketDepth toDepth(DhanSdkResponse<?> quoteSource, Instrument instrument) {
        List<DepthLevel> bids = List.of();
        List<DepthLevel> asks = List.of();
        Optional<Object> depthOpt = quoteSource.invoke("getDepth");
        if (depthOpt.isPresent()) {
            DhanSdkResponse<?> depth = new DhanSdkResponse<>(depthOpt.get());
            bids = levels(depth, "getBuy");
            asks = levels(depth, "getSell");
        }
        return new MarketDepth(instrument, bids, asks, Math.max(bids.size(), asks.size()),
                firstTimestamp(quoteSource, "getLastTradeTime", "getLtt").orElse(System.currentTimeMillis()));
    }

    @SuppressWarnings("unchecked")
    private static List<DepthLevel> levels(DhanSdkResponse<?> depth, String accessor) {
        Object raw = depth.invoke(accessor).orElse(List.of());
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        List<DepthLevel> levels = new ArrayList<>(entries.size());
        for (Object entry : entries) {
            DhanSdkResponse<?> entryResponse = new DhanSdkResponse<>(entry);
            levels.add(new DepthLevel(
                    PriceMath.toPaisa(entryResponse.decimal("getPrice")),
                    entryResponse.longValue("getQuantity"),
                    (int) entryResponse.longValue("getOrders")
            ));
        }
        return levels;
    }

    public static Instrument toInstrument(String symbol, String securityId, ExchangeSegment segment) {
        return new Instrument(symbol, symbol, segment.exchange(), segment, "UNKNOWN", "", null, null, null, 1L, 0L);
    }

    public static ExchangeSegment segment(String value) {
        return ExchangeSegment.fromCode(value == null ? "UNKNOWN" : value);
    }

    public static Side side(String value) {
        if (value == null) {
            return Side.UNKNOWN;
        }
        return switch (value.toUpperCase()) {
            case "BUY", "LONG" -> Side.BUY;
            case "SELL", "SHORT" -> Side.SELL;
            default -> Side.UNKNOWN;
        };
    }

    public static OrderType orderType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Dhan order type");
        }
        return switch (value.toUpperCase()) {
            case "LIMIT" -> OrderType.LIMIT;
            case "STOP_LOSS" -> OrderType.STOP_LOSS;
            case "STOP_LOSS_MARKET" -> OrderType.STOP_LOSS_MARKET;
            case "MARKET" -> OrderType.MARKET;
            default -> throw new IllegalStateException("Unsupported Dhan order type " + value);
        };
    }

    public static ProductType productType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Dhan product type");
        }
        return switch (value.toUpperCase()) {
            case "CNC" -> ProductType.CNC;
            case "MARGIN" -> ProductType.MARGIN;
            case "CARRY_FORWARD" -> ProductType.CARRY_FORWARD;
            case "INTRADAY" -> ProductType.INTRADAY;
            default -> throw new IllegalStateException("Unsupported Dhan product type " + value);
        };
    }

    public static OrderStatus orderStatus(String value) {
        if (value == null || value.isBlank()) {
            return OrderStatus.UNKNOWN;
        }
        return switch (value.toUpperCase()) {
            case "PENDING" -> OrderStatus.PENDING;
            case "OPEN" -> OrderStatus.OPEN;
            case "PART_TRADED" -> OrderStatus.PART_TRADED;
            case "TRADED", "COMPLETE" -> OrderStatus.TRADED;
            case "CANCELLED", "CANCELED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    private static Side requiredSide(String value) {
        Side side = side(value);
        if (side == Side.UNKNOWN) {
            throw new IllegalStateException("Unsupported Dhan side " + value);
        }
        return side;
    }

    private static OrderType requiredOrderType(String value) {
        return orderType(value);
    }

    private static ProductType requiredProductType(String value) {
        return productType(value);
    }

    private static OrderStatus requiredOrderStatus(String value) {
        OrderStatus orderStatus = orderStatus(value);
        if (orderStatus == OrderStatus.UNKNOWN) {
            throw new IllegalStateException("Unsupported Dhan order status " + value);
        }
        return orderStatus;
    }

    private static ExchangeSegment equitySegment(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return ExchangeSegment.UNKNOWN;
        }
        return switch (exchange.trim().toUpperCase()) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            default -> ExchangeSegment.UNKNOWN;
        };
    }

    // ---- DhanSdkResponse-based private helpers ----

    private static String firstRequiredString(DhanSdkResponse<?> source, String... methods) {
        return source.string(methods);
    }

    private static String firstOptionalString(DhanSdkResponse<?> source, String... methods) {
        for (String method : methods) {
            String value = source.optionalString(method);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static long firstLong(DhanSdkResponse<?> source, String... methods) {
        return source.longValue(methods);
    }

    private static Optional<Long> firstOptionalLong(DhanSdkResponse<?> source, String... methods) {
        return source.optionalLong(methods);
    }

    private static BigDecimal firstDecimal(DhanSdkResponse<?> source, String... methods) {
        return source.decimal(methods);
    }

    private static Optional<BigDecimal> firstOptionalDecimal(DhanSdkResponse<?> source, String... methods) {
        return source.optionalDecimal(methods);
    }

    private static Optional<Long> firstTimestamp(DhanSdkResponse<?> source, String... methods) {
        return source.timestampMillis(methods);
    }
}
