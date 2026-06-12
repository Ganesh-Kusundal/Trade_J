package com.tradej.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.composition.ReplayService;
import com.tradej.core.domain.port.EventBus;
import com.tradej.replay.engine.CandleReplaySession;
import com.tradej.replay.engine.ReplayController;
import com.tradej.replay.engine.ReplayOrchestrator;
import com.tradej.replay.engine.TickReplaySession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Spring wiring for the {@link ReplayService} façade. Exists in the app
 * module because the bean lives on the Spring path; the class itself
 * is Spring-free and is also constructed directly by the CLI path.
 */
@Configuration
public class ReplayServiceConfiguration {

    @Bean
    ReplayController replayController(EventBus eventBus) {
        return new ReplayController(eventBus);
    }

    @Bean
    ReplayService replayService(
            EventBus eventBus,
            ReplayOrchestrator orchestrator,
            ReplayController controller,
            ObjectProvider<CandleReplaySession> candleSession,
            ObjectProvider<TickReplaySession> tickSession,
            ObjectMapper objectMapper
    ) {
        return new ReplayService(
                eventBus,
                orchestrator,
                controller,
                Optional.ofNullable(candleSession.getIfAvailable()),
                Optional.ofNullable(tickSession.getIfAvailable()),
                objectMapper
        );
    }
}
