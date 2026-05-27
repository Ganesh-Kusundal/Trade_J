package com.tradej.broker.dhan.options;

import com.tradej.broker.dhan.exceptions.DhanHttpException;
import com.tradej.broker.dhan.mapper.DhanJsonResponse;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Parses Dhan option-chain REST payloads into typed structures.
 */
public final class DhanOptionChainResponseMapper {
    private DhanOptionChainResponseMapper() {
    }

    public record OptionChainData(long spotPricePaisa, DhanJsonResponse optionChain) {
    }

    public static List<LocalDate> parseExpiries(DhanJsonResponse payload) {
        DhanJsonResponse data = unwrapData(payload);
        if (!data.isArray()) {
            throw new DhanHttpException(describeRateLimit(payload) + "Option expiry list response missing data array");
        }
        List<LocalDate> expiries = new ArrayList<>();
        for (DhanJsonResponse element : data.asList()) {
            String text = element.asText().trim();
            if (text.isBlank()) {
                continue;
            }
            try {
                expiries.add(LocalDate.parse(text));
            } catch (DateTimeParseException ex) {
                throw new DhanHttpException("Invalid option expiry date in response: " + text, ex);
            }
        }
        if (expiries.isEmpty()) {
            throw new DhanHttpException(describeRateLimit(payload) + "Option expiry list returned no expiries");
        }
        expiries.sort(Comparator.naturalOrder());
        return List.copyOf(expiries);
    }

    public static OptionChainData parseChain(DhanJsonResponse payload, String underlying, LocalDate expiry) {
        DhanJsonResponse data = unwrapData(payload);
        DhanJsonResponse optionChain = data.path("oc");
        if (!optionChain.isObject() || optionChain.size() == 0) {
            throw new DhanHttpException(describeRateLimit(payload)
                    + "Option chain response missing strike data for " + underlying + " " + expiry);
        }
        return new OptionChainData(data.decimalPrice("last_price", "lastPrice"), optionChain);
    }

    private static DhanJsonResponse unwrapData(DhanJsonResponse payload) {
        if (payload.has("data")) {
            return payload.path("data");
        }
        return payload;
    }

    private static String describeRateLimit(DhanJsonResponse payload) {
        String body = payload.toString();
        if (body.contains("805") || body.contains("Too many requests")) {
            return "Dhan option-chain rate limit exceeded. ";
        }
        return "";
    }
}
