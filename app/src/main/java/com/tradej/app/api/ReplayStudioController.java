package com.tradej.app.api;

import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.InstrumentKey;
import com.tradej.core.domain.port.HistoricalBarRepository;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.replay.engine.ReplayController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Studio surface for driving a single in-process replay session.
 * Backed by {@link ReplayController} (the new event-symmetric replay
 * engine that replaces the legacy {@code CandleReplaySession}).
 */
@RestController
@RequestMapping("/api/v1/replay")
public class ReplayStudioController {

    private final ReplayController replay;
    private final HistoricalBarRepository barRepository;

    public ReplayStudioController(ReplayController replay, HistoricalBarRepository barRepository) {
        this.replay = replay;
        this.barRepository = barRepository;
    }

    @PostMapping("/start")
    public ResponseEntity<ReplayController.ReplayState> start(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "NSE_EQ") String exchange,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "1m") String interval
    ) {
        ExchangeSegment segment = ExchangeSegment.valueOf(exchange);
        List<Candle> candles = barRepository.queryCandles(
                InstrumentKey.of(symbol, segment),
                interval,
                LocalDate.parse(from),
                LocalDate.parse(to)
        );
        if (candles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        replay.start(candles);
        return ResponseEntity.ok(replay.getState());
    }

    @PostMapping("/play")
    public ResponseEntity<ReplayController.ReplayState> play() {
        replay.play();
        return ResponseEntity.ok(replay.getState());
    }

    @PostMapping("/pause")
    public ResponseEntity<ReplayController.ReplayState> pause() {
        replay.pause();
        return ResponseEntity.ok(replay.getState());
    }

    @PostMapping("/step")
    public ResponseEntity<ReplayController.ReplayState> step() {
        replay.step();
        return ResponseEntity.ok(replay.getState());
    }

    @PostMapping("/stop")
    public ResponseEntity<ReplayController.ReplayState> stop() {
        replay.stop();
        return ResponseEntity.ok(replay.getState());
    }

    @PostMapping("/speed")
    public ResponseEntity<ReplayController.ReplayState> speed(@RequestParam double multiplier) {
        replay.setSpeed(multiplier);
        return ResponseEntity.ok(replay.getState());
    }

    @GetMapping("/status")
    public ResponseEntity<ReplayController.ReplayState> status() {
        return ResponseEntity.ok(replay.getState());
    }
}
