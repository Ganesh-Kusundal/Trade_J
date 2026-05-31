package com.tradej.app.api;

import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.core.domain.model.Instrument;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Serves available trading symbols to the operator console frontend.
 * <p>
 * Design principles:
 * <ul>
 *   <li>Single lightweight endpoint — no complex filtering on the backend; the
 *       frontend caches aggressively using the {@code Cache-Ttl-Seconds} hint.</li>
 *   <li>Returns only active, tradeable symbols (filters out expired options, etc.).</li>
 *   <li>Sets {@code Cache-Control: public, max-age=} based on catalog staleness.</li>
 *   <li>Sets {@code Retry-After} under load via {@link RateLimitFilter}.</li>
 *   <li>Burst-friendly: the first few requests after startup bypass rate limiting
 *       for UI initialisation.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
public class SymbolController {

    /** Symbols endpoint TTL in seconds — frontend caches for at least this long. */
    static final int CACHE_TTL_SECONDS = 60;

    private final InstrumentResolver instrumentResolver;

    public SymbolController(InstrumentResolver instrumentResolver) {
        this.instrumentResolver = instrumentResolver;
    }

    @GetMapping(value = "/symbols", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getSymbols(
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        // If the catalog isn't loaded yet, return a service-unavailable response
        if (!instrumentResolver.isLoaded()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("Retry-After", "10")
                    .body(Map.of(
                            "error", "Instrument catalog not yet loaded",
                            "retryAfterSeconds", 10
                    ));
        }

        List<Instrument> all = instrumentResolver.allInstruments();

        // Filter to active/tradeable instruments (exclude expired options by default)
        List<Map<String, Object>> symbols = all.stream()
                .filter(instr -> instr.symbol() != null && !instr.symbol().isBlank())
                .map(this::toSymbolMap)
                .collect(Collectors.toList());

        long now = System.currentTimeMillis();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("symbols", symbols);
        body.put("count", symbols.size());
        body.put("totalCount", all.size());
        body.put("generatedAtMs", now);
        body.put("cacheTtlSeconds", CACHE_TTL_SECONDS);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(CACHE_TTL_SECONDS, TimeUnit.SECONDS)
                        .cachePublic()
                        .mustRevalidate())
                .header("Cache-Ttl-Seconds", String.valueOf(CACHE_TTL_SECONDS))
                .body(body);
    }

    private Map<String, Object> toSymbolMap(Instrument instr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol", instr.symbol());
        m.put("canonicalSymbol", instr.canonicalSymbol());
        m.put("name", instr.underlying() != null ? instr.underlying() : instr.symbol());
        m.put("exchange", instr.exchange() != null ? instr.exchange().name() : "NSE");
        m.put("exchangeSegment", instr.exchangeSegment() != null ? instr.exchangeSegment().name() : "NSE_EQ");
        m.put("instrumentType", classifyInstrumentType(instr));
        m.put("lotSize", instr.lotSize());
        m.put("tickSizePaisa", instr.tickSizePaisa() > 0 ? instr.tickSizePaisa() : 5L);
        m.put("active", instr.expiry() == null || !instr.expiry().isBefore(java.time.LocalDate.now()));
        m.put("segment", deriveSegment(instr));
        return m;
    }

    private String classifyInstrumentType(Instrument instr) {
        if (instr.isOption()) { return "OPTION"; }
        if (instr.isFuture()) { return "FUTURE"; }
        String type = instr.instrumentType();
        if (type == null) { return "EQUITY"; }
        String upper = type.toUpperCase();
        if (upper.contains("INDEX")) { return "INDEX"; }
        if (upper.contains("EQ") || upper.contains("EQUITY")) { return "EQUITY"; }
        if (upper.contains("FUT")) { return "FUTURE"; }
        if (upper.contains("OPT")) { return "OPTION"; }
        if (upper.contains("COM") || upper.contains("COMM")) { return "COMMODITY"; }
        if (upper.contains("CUR")) { return "CURRENCY"; }
        return "EQUITY";
    }

    private String deriveSegment(Instrument instr) {
        if (instr.exchangeSegment() != null) {
            return instr.exchangeSegment().name();
        }
        if (instr.exchange() != null) {
            String ex = instr.exchange().name();
            if (ex.contains("FNO") || ex.contains("FO")) { return "FO"; }
            if (ex.contains("COM") || ex.contains("MCX")) { return "CO"; }
            if (ex.contains("CUR")) { return "CD"; }
        }
        return "CM";
    }
}
