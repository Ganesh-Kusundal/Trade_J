package com.tradej.app.api;

import com.tradej.app.scanner.OptionScanService;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.scanner.option.OptionContractHit;
import com.tradej.scanner.option.OptionExpiryPolicy;
import com.tradej.scanner.option.OptionScanResult;
import com.tradej.scanner.option.OptionSideFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/options/scan")
@ConditionalOnBean(OptionScanService.class)
public class OptionScanController {

    private final OptionScanService optionScanService;

    public OptionScanController(OptionScanService optionScanService) {
        this.optionScanService = optionScanService;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> scan(
            @RequestParam String underlying,
            @RequestParam(defaultValue = "IDX_I") String segment,
            @RequestParam(required = false) String expiry,
            @RequestParam(required = false) LocalDate expiryDate,
            @RequestParam(defaultValue = "both") String side,
            @RequestParam(defaultValue = "10") int top,
            @RequestParam(defaultValue = "1000") long minOi,
            @RequestParam(defaultValue = "0") long minVolume,
            @RequestParam(defaultValue = "300") double maxSpreadBps,
            @RequestParam(defaultValue = "false") boolean strictSpread
    ) {
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment);
        OptionExpiryPolicy expiryPolicy = resolveExpiryPolicy(expiry, expiryDate);
        LocalDate explicitExpiry = expiryDate != null ? expiryDate : parseExplicitExpiry(expiry);

        OptionScanResult result = optionScanService.scan(
                underlying,
                exchangeSegment,
                expiryPolicy,
                explicitExpiry,
                OptionSideFilter.parse(side),
                minOi,
                minVolume,
                maxSpreadBps,
                strictSpread,
                top
        );
        return ResponseEntity.ok(toBody(result));
    }

    @GetMapping(value = "/expiries", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> expiries(
            @RequestParam String underlying,
            @RequestParam(defaultValue = "IDX_I") String segment
    ) {
        ExchangeSegment exchangeSegment = ExchangeSegment.valueOf(segment);
        List<LocalDate> expiries = optionScanService.expiries(underlying, exchangeSegment);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underlying", underlying);
        body.put("segment", exchangeSegment.name());
        body.put("expiries", expiries.stream().map(LocalDate::toString).toList());
        return ResponseEntity.ok(body);
    }

    private static OptionExpiryPolicy resolveExpiryPolicy(String expiry, LocalDate expiryDate) {
        if (expiryDate != null) {
            return OptionExpiryPolicy.EXPLICIT;
        }
        if (expiry == null || expiry.isBlank()) {
            return OptionExpiryPolicy.NEAREST;
        }
        return OptionExpiryPolicy.parse(expiry);
    }

    private static LocalDate parseExplicitExpiry(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        String normalized = expiry.trim();
        if ("NEAREST".equalsIgnoreCase(normalized)
                || "NEAR".equalsIgnoreCase(normalized)
                || "NEXT".equalsIgnoreCase(normalized)) {
            return null;
        }
        return LocalDate.parse(normalized);
    }

    private Map<String, Object> toBody(OptionScanResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underlying", result.underlying());
        body.put("expiry", result.expiry() == null ? null : result.expiry().toString());
        body.put("runId", result.run().runId());
        body.put("status", result.run().status().name());
        body.put("partialFailureCount", result.partialFailureCount());
        body.put("contracts", result.contracts().stream().map(this::contractBody).toList());
        return body;
    }

    private Map<String, Object> contractBody(OptionContractHit hit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("symbol", hit.instrumentKey().symbol());
        map.put("segment", hit.instrumentKey().exchangeSegment().name());
        map.put("optionType", toApiOptionType(hit.optionType()));
        map.put("strikePaisa", hit.strikePricePaisa());
        map.put("openInterest", hit.openInterest());
        map.put("volume", hit.volume());
        map.put("spreadBps", hit.spreadBps());
        map.put("ltpPaisa", hit.ltpPaisa());
        map.put("score", hit.liquidityScore());
        map.put("reasons", hit.reasons());
        return map;
    }

    private static String toApiOptionType(OptionType optionType) {
        return optionType == OptionType.CALL ? "CE" : "PE";
    }
}
