package com.tradej.app.api;

import com.tradej.brokergateway.simulation.SimulatedMarketDataProvider;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market")
public class MarketSessionController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @GetMapping("/session")
    public Map<String, Object> getSession(@RequestParam(defaultValue = "NSE") String exchange) {
        String state = SimulatedMarketDataProvider.getMarketState(exchange);
        ZonedDateTime now = ZonedDateTime.now(IST);
        String nextTransition = computeNextTransition(state, exchange, now);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", state);
        result.put("exchange", exchange.toUpperCase());
        result.put("currentTime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        result.put("timezone", "Asia/Kolkata");
        result.put("sessionHours", sessionHours(exchange));
        result.put("nextTransition", nextTransition);
        result.put("dataSource", "SIMULATION");
        return result;
    }

    private String computeNextTransition(String state, String exchange, ZonedDateTime now) {
        String ex = exchange.toUpperCase();
        return switch (state) {
            case "CLOSED" -> "PREOPEN tomorrow 09:00 IST";
            case "PREOPEN" -> "OPEN at 09:15 IST";
            case "OPEN" -> switch (ex) {
                case "MCX" -> "CLOSE at 23:30 IST";
                case "CDS" -> "CLOSE at 17:00 IST";
                default -> "CLOSE at 15:30 IST";
            };
            case "AUCTION" -> "CLOSED at 16:00 IST";
            default -> "UNKNOWN";
        };
    }

    private Map<String, String> sessionHours(String exchange) {
        Map<String, String> hours = new LinkedHashMap<>();
        switch (exchange.toUpperCase()) {
            case "NSE", "BSE", "NFO" -> {
                hours.put("preopen", "09:00-09:15");
                hours.put("regular", "09:15-15:30");
                hours.put("auction", "15:30-16:00");
            }
            case "MCX" -> {
                hours.put("regular", "09:00-23:30");
            }
            case "CDS" -> {
                hours.put("regular", "09:00-17:00");
            }
            default -> {
                hours.put("regular", "24/7");
            }
        }
        return hours;
    }
}
