package com.tradej.app.api;

import com.tradej.broker.upstox.expired.BrokerExpiredOptionQueryService;
import com.tradej.core.domain.instrument.ExpiredOptionContractKey;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/market/expired-options")
@ConditionalOnBean(BrokerExpiredOptionQueryService.class)
public class ExpiredOptionsController {

    private final BrokerExpiredOptionQueryService queryService;

    public ExpiredOptionsController(BrokerExpiredOptionQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping(value = "/expiries", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> expiries(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment
    ) {
        var expiries = queryService.listExpiries(symbol, exchangeSegment);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "exchangeSegment", exchangeSegment.name(),
                "count", expiries.size(),
                "expiries", expiries.stream().map(LocalDate::toString).toList()
        ));
    }

    @GetMapping(value = "/contracts", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> contracts(
            @RequestParam String symbol,
            @RequestParam ExchangeSegment exchangeSegment,
            @RequestParam LocalDate expiry
    ) {
        var contracts = queryService.listContracts(symbol, exchangeSegment, expiry);
        return ResponseEntity.ok(Map.of(
                "symbol", symbol,
                "exchangeSegment", exchangeSegment.name(),
                "expiry", expiry.toString(),
                "count", contracts.size(),
                "contracts", contracts.stream().map(this::toContractMap).toList()
        ));
    }

    @GetMapping(value = "/candles", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> candles(
            @RequestParam String expiredInstrumentKey,
            @RequestParam(defaultValue = "5minute") String interval,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        var bars = queryService.fetchCandles(expiredInstrumentKey, interval, from, to);
        return ResponseEntity.ok(Map.of(
                "expiredInstrumentKey", expiredInstrumentKey,
                "interval", interval,
                "from", from.toString(),
                "to", to.toString(),
                "count", bars.size(),
                "candles", bars.stream().map(b -> Map.of(
                        "timestampMs", b.timestampMs(),
                        "openPaisa", b.openPaisa(),
                        "highPaisa", b.highPaisa(),
                        "lowPaisa", b.lowPaisa(),
                        "closePaisa", b.closePaisa(),
                        "volume", b.volume(),
                        "oi", b.oi()
                )).toList()
        ));
    }

    private Map<String, Object> toContractMap(ExpiredOptionContractKey contract) {
        return Map.of(
                "underlying", contract.underlying(),
                "exchangeSegment", contract.segment().name(),
                "expiry", contract.expiry().toString(),
                "strikePaisa", contract.strikePaisa(),
                "optionType", contract.optionType().name(),
                "brokerInstrumentKey", contract.brokerInstrumentKey()
        );
    }
}
