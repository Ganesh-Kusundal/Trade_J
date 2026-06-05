package com.tradej.app.api;

import com.tradej.execution.risk.PositionRiskHandler;
import com.tradej.strategy.portfolio.PortfolioEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioAnalyticsController {

    private final PortfolioEngine portfolioEngine;
    private final PositionRiskHandler positionRiskHandler;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "portfolio-sse");
        t.setDaemon(true);
        return t;
    });

    public PortfolioAnalyticsController(PortfolioEngine portfolioEngine, PositionRiskHandler positionRiskHandler) {
        this.portfolioEngine = portfolioEngine;
        this.positionRiskHandler = positionRiskHandler;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        return buildSnapshot();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        var task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().name("portfolio").data(buildSnapshot()));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }, 0, 2, TimeUnit.SECONDS);
        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> task.cancel(true));
        return emitter;
    }

    private Map<String, Object> buildSnapshot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("netPositions", portfolioEngine.netPositionsSnapshot());
        body.put("allocations", portfolioEngine.allocationsSnapshot().entrySet().stream()
                .map(e -> Map.of(
                        "strategy", e.getKey(),
                        "allocatedCapitalPaisa", e.getValue().allocatedCapitalPaisa(),
                        "usedCapitalPaisa", e.getValue().usedCapitalPaisa()
                ))
                .toList());
        body.put("realizedLossPaisa", positionRiskHandler.getRealizedLossPaisa());
        body.put("unrealizedLossPaisa", positionRiskHandler.getUnrealizedLossPaisa());
        body.put("openTrades", positionRiskHandler.getOpenTrades());
        body.put("killSwitchActive", positionRiskHandler.isKillSwitchActive());
        return body;
    }
}
