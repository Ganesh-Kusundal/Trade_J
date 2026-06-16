package com.tradej.app.api;

import com.tradej.app.service.OptionsAnalyticsApplicationService;
import com.tradej.core.domain.config.DefaultSegments;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/options")
public class OptionsAnalyticsController {

    private final OptionsAnalyticsApplicationService optionsService;

    public OptionsAnalyticsController(OptionsAnalyticsApplicationService optionsService) {
        this.optionsService = optionsService;
    }

    @GetMapping("/volatility-surface")
    public ResponseEntity<Map<String, Object>> volatilitySurface(
            @RequestParam String underlying,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_FNO_SEGMENT) ExchangeSegment segment,
            @RequestParam(required = false) LocalDate expiry
    ) {
        Map<String, Object> result = optionsService.getVolatilitySurface(underlying, segment, expiry);
        if (result.containsKey("error")) {
            return ResponseEntity.status(503).body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/chain")
    public ResponseEntity<Map<String, Object>> optionChain(
            @RequestParam String underlying,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_FNO_SEGMENT) ExchangeSegment segment,
            @RequestParam(required = false) LocalDate expiry,
            @RequestParam(required = false) Integer depth
    ) {
        Map<String, Object> result = optionsService.getOptionChain(underlying, segment, expiry, depth);
        if (result.containsKey("error")) {
            int status = "No expiries for ".concat(underlying).equals(result.get("error")) ? 404 : 503;
            return ResponseEntity.status(status).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
