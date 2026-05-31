package com.tradej.broker.upstox.expired;

import com.fasterxml.jackson.databind.JsonNode;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.model.ExpiredOptionBar;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class UpstoxExpiredOptionMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<LocalDate> mapExpiries(JsonNode root) {
        List<LocalDate> expiries = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data != null && data.isArray()) {
            for (JsonNode node : data) {
                String text = node.asText();
                if (text != null && text.length() >= 10) {
                    expiries.add(LocalDate.parse(text.substring(0, 10), DATE_FMT));
                }
            }
        }
        return expiries.stream().sorted().distinct().toList();
    }

    public List<ExpiredOptionContractKey> mapContracts(
            JsonNode root,
            String underlying,
            ExchangeSegment segment,
            LocalDate expiry
    ) {
        List<ExpiredOptionContractKey> contracts = new ArrayList<>();
        JsonNode data = root.get("data");
        if (data == null || !data.isArray()) {
            return List.of();
        }
        String normalizedUnderlying = ContractSymbolNormalizer.normalize(underlying);
        for (JsonNode node : data) {
            String instrumentKey = textOrNull(node, "instrument_key");
            if (instrumentKey == null || instrumentKey.isBlank()) {
                continue;
            }
            OptionType optionType = parseOptionType(textOrNull(node, "instrument_type"));
            if (optionType == OptionType.UNKNOWN) {
                continue;
            }
            long strikePaisa = node.has("strike_price")
                    ? (long) (node.get("strike_price").asDouble() * 100)
                    : 0L;
            String wireUnderlying = textOrNull(node, "underlying_symbol");
            String contractUnderlying = wireUnderlying != null && !wireUnderlying.isBlank()
                    ? ContractSymbolNormalizer.normalize(wireUnderlying)
                    : normalizedUnderlying;
            contracts.add(new ExpiredOptionContractKey(
                    contractUnderlying,
                    segment,
                    expiry,
                    strikePaisa,
                    optionType,
                    instrumentKey
            ));
        }
        return contracts;
    }

    public List<ExpiredOptionBar> mapCandles(JsonNode root) {
        List<ExpiredOptionBar> bars = new ArrayList<>();
        JsonNode candles = root.path("data").path("candles");
        if (!candles.isArray()) {
            return List.of();
        }
        for (JsonNode candle : candles) {
            if (!candle.isArray() || candle.size() < 7) {
                continue;
            }
            long timestampMs = parseTimestamp(candle.get(0).asText());
            bars.add(new ExpiredOptionBar(
                    timestampMs,
                    toPaisa(candle.get(1)),
                    toPaisa(candle.get(2)),
                    toPaisa(candle.get(3)),
                    toPaisa(candle.get(4)),
                    candle.get(5).asLong(),
                    candle.get(6).asLong()
            ));
        }
        return bars;
    }

    private static OptionType parseOptionType(String instrumentType) {
        if (instrumentType == null) {
            return OptionType.UNKNOWN;
        }
        return switch (instrumentType.toUpperCase()) {
            case "CE" -> OptionType.CALL;
            case "PE" -> OptionType.PUT;
            default -> OptionType.UNKNOWN;
        };
    }

    private static long parseTimestamp(String timestamp) {
        return Instant.parse(timestamp).toEpochMilli();
    }

    private static long toPaisa(JsonNode node) {
        return (long) (node.asDouble() * 100);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
