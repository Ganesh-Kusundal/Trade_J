package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MaxPainComputed;
import com.tradej.core.domain.event.OptionChainUpdated;
import com.tradej.core.domain.model.OptionChainSnapshot;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.options.calculator.MaxPainCalculator;
import com.tradej.options.greeks.OptionsAnalyticsCache;
import com.tradej.options.surface.VolatilitySurfaceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.util.List;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "trade.options.analytics-enabled", havingValue = "true", matchIfMissing = false)
public class OptionsAnalyticsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OptionsAnalyticsConfiguration.class);

    @Bean
    OptionsAnalyticsCache optionsAnalyticsCache() {
        return new OptionsAnalyticsCache();
    }

    @Bean
    VolatilitySurfaceBuilder volatilitySurfaceBuilder(OptionsAnalyticsCache cache) {
        return new VolatilitySurfaceBuilder(cache);
    }

    @Bean
    OptionChainPollingService optionChainPollingService(
            IBrokerConnection brokerConnection,
            EventBus eventBus,
            VolatilitySurfaceBuilder surfaceBuilder
    ) {
        return new OptionChainPollingService(brokerConnection, eventBus, surfaceBuilder);
    }

    public static final class OptionChainPollingService {

        private final IBrokerConnection brokerConnection;
        private final EventBus eventBus;
        private final VolatilitySurfaceBuilder surfaceBuilder;

        public OptionChainPollingService(
                IBrokerConnection brokerConnection,
                EventBus eventBus,
                VolatilitySurfaceBuilder surfaceBuilder
        ) {
            this.brokerConnection = brokerConnection;
            this.eventBus = eventBus;
            this.surfaceBuilder = surfaceBuilder;
        }

        @Scheduled(fixedDelayString = "${trade.options.chain-poll-interval-ms:60000}")
        public void pollNiftyChain() {
            brokerConnection.getCapability(OptionsProvider.class).ifPresent(provider -> {
                try {
                    List<LocalDate> expiries = provider.getExpiries("NIFTY", ExchangeSegment.NSE_FNO);
                    if (expiries.isEmpty()) {
                        return;
                    }
                    LocalDate expiry = expiries.getFirst();
                    OptionChainSnapshot chain = provider.getOptionChain("NIFTY", ExchangeSegment.NSE_FNO, expiry);
                    eventBus.publish(new OptionChainUpdated(EventMetadata.root(), chain));
                    surfaceBuilder.build(chain);
                    MaxPainCalculator.MaxPainResult maxPain = MaxPainCalculator.compute(chain);
                    eventBus.publish(new MaxPainComputed(
                            EventMetadata.root(),
                            "NIFTY",
                            expiry,
                            maxPain.strikePaisa(),
                            maxPain.totalPainPaisa()));
                } catch (Exception e) {
                    log.warn("Option chain poll failed: {}", e.getMessage());
                }
            });
        }
    }
}
