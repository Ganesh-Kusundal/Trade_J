package com.tradej.scanner.option;

import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.scan.AssetClass;
import com.tradej.core.domain.scan.ScanHit;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OptionContractHit(
        InstrumentKey instrumentKey,
        String underlying,
        LocalDate expiry,
        long strikePricePaisa,
        OptionType optionType,
        long openInterest,
        long volume,
        double spreadBps,
        long ltpPaisa,
        double liquidityScore,
        List<String> reasons
) {
    public OptionContractHit {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public ScanHit toScanHit() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("expiry", expiry.toString());
        snapshot.put("strikePaisa", strikePricePaisa);
        snapshot.put("optionType", optionType.name());
        snapshot.put("openInterest", openInterest);
        snapshot.put("volume", volume);
        snapshot.put("spreadBps", spreadBps);
        snapshot.put("ltpPaisa", ltpPaisa);
        snapshot.put("spotPricePaisa", 0L);
        return new ScanHit(
                instrumentKey,
                AssetClass.OPTION,
                underlying,
                liquidityScore,
                reasons,
                snapshot,
                false
        );
    }

    public static InstrumentKey syntheticKey(
            String underlying,
            ExchangeSegment segment,
            LocalDate expiry,
            long strikePricePaisa,
            OptionType optionType
    ) {
        String symbol = underlying + expiry + strikePricePaisa + optionType.name();
        return InstrumentKey.of(symbol, segment);
    }
}
