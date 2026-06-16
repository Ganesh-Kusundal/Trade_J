package com.tradej.app.api;

import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.config.DefaultSegments;
import com.tradej.core.domain.value.ExchangeSegment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/replay")
public class ReplayStudioController {

    private final CandleReplaySession replaySession;
    private final HistoricalBarRepository barRepository;

    public ReplayStudioController(CandleReplaySession replaySession, HistoricalBarRepository barRepository) {
        this.replaySession = replaySession;
        this.barRepository = barRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<CandleReplaySession.ReplayStatus> start(
            @RequestParam String symbol,
            @RequestParam(defaultValue = DefaultSegments.DEFAULT_EQUITY_SEGMENT) ExchangeSegment exchange,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        List<Candle> candles = barRepository.queryCandles(
                InstrumentKey.of(symbol, exchange),
                interval,
                LocalDate.parse(from),
                LocalDate.parse(to)
        );
        if (candles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        replaySession.start(candles);
        return ResponseEntity.ok(replaySession.status());
    }

    @PostMapping("/play")
    public ResponseEntity<CandleReplaySession.ReplayStatus> play() {
        replaySession.play();
        return ResponseEntity.ok(replaySession.status());
    }

    @PostMapping("/pause")
    public ResponseEntity<CandleReplaySession.ReplayStatus> pause() {
        replaySession.pause();
        return ResponseEntity.ok(replaySession.status());
    }

    @PostMapping("/step")
    public ResponseEntity<CandleReplaySession.ReplayStatus> step() {
        replaySession.step();
        return ResponseEntity.ok(replaySession.status());
    }

    @PostMapping("/stop")
    public ResponseEntity<CandleReplaySession.ReplayStatus> stop() {
        replaySession.stop();
        return ResponseEntity.ok(replaySession.status());
    }

    @PostMapping("/speed")
    public ResponseEntity<CandleReplaySession.ReplayStatus> speed(@RequestParam double multiplier) {
        replaySession.setSpeed(multiplier);
        return ResponseEntity.ok(replaySession.status());
    }

    @GetMapping("/status")
    public ResponseEntity<CandleReplaySession.ReplayStatus> status() {
        return ResponseEntity.ok(replaySession.status());
    }
}
