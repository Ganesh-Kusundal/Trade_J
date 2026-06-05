package com.tradej.app.config;

import com.tradej.simulation.MatchingEngine;
import com.tradej.simulation.PnLLedger;
import com.tradej.simulation.SimulatedOrderService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SimulationConfiguration {

    @Bean
    MatchingEngine matchingEngine() {
        return new MatchingEngine();
    }

    @Bean
    PnLLedger pnlLedger() {
        return new PnLLedger();
    }

    @Bean
    SimulatedOrderService simulatedOrderService(MatchingEngine matchingEngine, PnLLedger pnlLedger) {
        return new SimulatedOrderService(matchingEngine, pnlLedger);
    }
}
