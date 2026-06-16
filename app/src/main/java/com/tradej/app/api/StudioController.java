package com.tradej.app.api;

import com.tradej.strategy.studio.StudioChartService;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.config.DefaultSegments;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/studio")
public class StudioController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final StudioChartService studioChartService;

    public StudioController(StudioChartService studioChartService) {
        this.studioChartService = studioChartService;
    }

    @GetMapping(value = "/startup-candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> startupCandidates(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "3") int topN
    ) {
        return ResponseEntity.ok(studioChartService.startupCandidates(Optional.ofNullable(date), topN));
    }

    @GetMapping(value = "/chart", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> chart(
            @RequestParam String symbol,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) ExchangeSegment exchangeSegment,
            @RequestParam(defaultValue = "5m") String interval,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) String optionUnderlying,
            @RequestParam(required = false) String optionExpiryKind,
            @RequestParam(required = false) Integer optionExpiryCode,
            @RequestParam(required = false) Integer optionStrikeOffset,
            @RequestParam(required = false) String optionType,
            @RequestParam(required = false, defaultValue = "5") Integer optionIntervalMin
    ) {
        Optional<RollingOptionSeriesRequest> overlay = buildOptionOverlay(
                from,
                to,
                optionUnderlying,
                optionExpiryKind,
                optionExpiryCode,
                optionStrikeOffset,
                optionType,
                optionIntervalMin
        );
        return ResponseEntity.ok(
                studioChartService.chartPayload(symbol, exchangeSegment, interval, from, to, overlay)
        );
    }

    private Optional<RollingOptionSeriesRequest> buildOptionOverlay(
            LocalDate from,
            LocalDate to,
            String underlying,
            String expiryKind,
            Integer expiryCode,
            Integer strikeOffset,
            String optionType,
            Integer intervalMin
    ) {
        if (underlying == null || underlying.isBlank()
                || expiryKind == null || expiryKind.isBlank()
                || expiryCode == null
                || strikeOffset == null
                || optionType == null || optionType.isBlank()) {
            return Optional.empty();
        }
        long fromMs = from.atStartOfDay(IST).toInstant().toEpochMilli();
        long toMs = to.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli();
        return Optional.of(new RollingOptionSeriesRequest(
                underlying,
                expiryKind,
                expiryCode,
                strikeOffset,
                OptionType.fromCode(optionType),
                intervalMin == null ? 5 : intervalMin,
                fromMs,
                toMs,
                10_000
        ));
    }
}
