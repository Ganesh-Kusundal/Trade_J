package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.port.EventBus;
import com.tradej.replay.engine.ReplayController;
import com.tradej.strategy.certification.ReplaySession;
import com.tradej.strategy.certification.StrategyReplayParityReporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

/**
 * Spring wiring for {@link StrategyReplayParityReporter}. Used by
 * {@code StrategyParityController} and the {@code tradej strategies
 * parity} CLI sub-command.
 */
@Configuration
public class StrategyParityConfiguration {

    @Bean
    ReplaySession replaySessionAdapter(ReplayController replayController) {
        // Adapter from replay-engine's ReplayController to the
        // ReplaySession interface declared in trading-strategy. Keeps
        // the cycle out of the trading-strategy module while
        // preserving the reporter's testable seam.
        return new ReplaySession() {
            @Override public void start(List<Candle> candles) { replayController.start(candles); }
            @Override public void play() { replayController.play(); }
            @Override public boolean step() { return replayController.step(); }
            @Override public void stop() { replayController.stop(); }
        };
    }

    @Bean
    StrategyReplayParityReporter strategyReplayParityReporter(
            EventBus eventBus,
            ReplaySession replaySession,
            EventMetadataFactory metadataFactory,
            ObjectMapper objectMapper,
            @Value("${trade.parity.output-dir:runtime-dev/parity}") String outputDir
    ) {
        return new StrategyReplayParityReporter(
                eventBus,
                replaySession,
                metadataFactory,
                objectMapper,
                Path.of(outputDir)
        );
    }
}
