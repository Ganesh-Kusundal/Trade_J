package com.tradej.broker.dhan.mapper;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderStatus;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;

/**
 * Maps Dhan API string constants to domain enums.
 */
public final class DhanFieldMapper {
    private DhanFieldMapper() {
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
        if ("SINGLE".equalsIgnoreCase(value) || "OCO".equalsIgnoreCase(value)) {
            return OrderType.LIMIT;
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
            case "PENDING", "TRANSIT", "CONFIRM" -> OrderStatus.PENDING;
            case "OPEN" -> OrderStatus.OPEN;
            case "PART_TRADED" -> OrderStatus.PART_TRADED;
            case "TRADED", "COMPLETE" -> OrderStatus.TRADED;
            case "CANCELLED", "CANCELED" -> OrderStatus.CANCELLED;
            case "REJECTED" -> OrderStatus.REJECTED;
            case "EXPIRED" -> OrderStatus.CANCELLED;
            default -> OrderStatus.UNKNOWN;
        };
    }

    public static Side requiredSide(String value) {
        Side side = side(value);
        if (side == Side.UNKNOWN) {
            throw new IllegalStateException("Unsupported Dhan side " + value);
        }
        return side;
    }

    public static OrderStatus requiredOrderStatus(String value) {
        OrderStatus status = orderStatus(value);
        if (status == OrderStatus.UNKNOWN) {
            throw new IllegalStateException("Unsupported Dhan order status " + value);
        }
        return status;
    }

    public static ExchangeSegment equitySegment(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return ExchangeSegment.UNKNOWN;
        }
        return switch (exchange.trim().toUpperCase()) {
            case "NSE" -> ExchangeSegment.NSE_EQ;
            case "BSE" -> ExchangeSegment.BSE_EQ;
            default -> ExchangeSegment.UNKNOWN;
        };
    }
}
