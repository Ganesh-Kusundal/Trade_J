package com.tradej.scanner.option;

import com.tradej.core.domain.value.OptionType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public enum OptionSideFilter {
    CE,
    PE,
    BOTH;

    public static OptionSideFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return BOTH;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "CE", "CALL" -> CE;
            case "PE", "PUT" -> PE;
            default -> BOTH;
        };
    }

    public static OptionSideFilter fromList(List<String> sides) {
        if (sides == null || sides.isEmpty()) {
            return BOTH;
        }
        Set<OptionSideFilter> parsed = EnumSet.noneOf(OptionSideFilter.class);
        for (String side : sides) {
            OptionSideFilter filter = parse(side);
            if (filter == BOTH) {
                return BOTH;
            }
            parsed.add(filter);
        }
        if (parsed.contains(CE) && parsed.contains(PE)) {
            return BOTH;
        }
        if (parsed.contains(CE)) {
            return CE;
        }
        if (parsed.contains(PE)) {
            return PE;
        }
        return BOTH;
    }

    public boolean includes(OptionType optionType) {
        return switch (this) {
            case BOTH -> optionType == OptionType.CALL || optionType == OptionType.PUT;
            case CE -> optionType == OptionType.CALL;
            case PE -> optionType == OptionType.PUT;
        };
    }
}
