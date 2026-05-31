package com.tradej.core.domain.instrument;

import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;

public record RollingOptionSeriesKey(
        String underlying,
        ExchangeSegment exchangeSegment,
        RollingExpiryRoll expiry,
        StrikeOffset strikeOffset,
        OptionType optionType,
        int intervalMinutes
) {
    private static final Set<Integer> ALLOWED_INTERVALS = Set.of(1, 5, 15, 25, 60);

    public RollingOptionSeriesKey {
        underlying = ContractSymbolNormalizer.normalize(underlying);
        if (underlying.isBlank()) {
            throw new IllegalArgumentException("underlying is required");
        }
        if (exchangeSegment == null) {
            throw new IllegalArgumentException("exchangeSegment is required");
        }
        if (expiry == null) {
            throw new IllegalArgumentException("expiry is required");
        }
        if (strikeOffset == null) {
            throw new IllegalArgumentException("strikeOffset is required");
        }
        if (optionType == null || optionType == OptionType.UNKNOWN) {
            throw new IllegalArgumentException("optionType must be CALL or PUT");
        }
        if (!ALLOWED_INTERVALS.contains(intervalMinutes)) {
            throw new IllegalArgumentException("unsupported intervalMinutes " + intervalMinutes);
        }
    }

    public String fingerprint(LocalDate fromDate, LocalDate toDate) {
        String raw = String.join("|",
                underlying,
                exchangeSegment.name(),
                expiry.kind().name(),
                Integer.toString(expiry.code()),
                Integer.toString(strikeOffset.value()),
                optionType.name(),
                Integer.toString(intervalMinutes),
                fromDate.toString(),
                toDate.toString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
