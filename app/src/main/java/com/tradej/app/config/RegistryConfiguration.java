package com.tradej.app.config;

import com.tradej.core.domain.event.*;
import com.tradej.indicators.spi.IndicatorRegistry;
import com.tradej.indicators.spi.TransformationRegistry;
import com.tradej.scanner.spi.ScannerRegistry;
import com.tradej.strategy.spi.StrategyRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RegistryConfiguration {

    @Bean
    public EventRegistry eventRegistry() {
        return EventRegistry.of(List.of(
                // Market Data
                MarketTickEvent.class,
                DepthUpdateEvent.class,
                CandleClosed.class,
                CandleDeveloping.class,
                OptionChainUpdated.class,
                GreeksComputed.class,
                GammaExposureComputed.class,
                MaxPainComputed.class,
                // Order Lifecycle
                OrderAccepted.class,
                OrderFilled.class,
                OrderRejected.class,
                OrderCancelled.class,
                OrderModified.class,
                OrderPartiallyFilled.class,
                OrderFullyFilled.class,
                // Trading
                SignalGenerated.class,
                SignalPendingExecution.class,
                SignalSuppressed.class,
                TradeOpened.class,
                TradeClosed.class,
                TradeUpdated.class,
                TradeExecutionEvent.class,
                KillSwitchEngaged.class,
                UnifiedKillSwitchEngaged.class,
                UnifiedKillSwitchDisengaged.class,
                UnrealizedPnLUpdated.class,
                PnlUpdatedEvent.class,
                PositionUpdateEvent.class,
                PositionMismatch.class,
                // System
                ReconciliationHaltRequired.class,
                StreamHealthChanged.class,
                EventBusBackpressure.class,
                BrokerAdapterError.class,
                StrategyError.class,
                ScanHitProduced.class,
                ScanResultsPublished.class,
                ReplayTimeChangedEvent.class
        ));
    }

    @Bean
    public StrategyRegistry strategyRegistry() {
        return StrategyRegistry.discover();
    }

    @Bean
    public ScannerRegistry scannerRegistry() {
        return ScannerRegistry.discover();
    }

    @Bean
    public IndicatorRegistry indicatorRegistry() {
        return IndicatorRegistry.discover();
    }

    @Bean
    public TransformationRegistry transformationRegistry() {
        return TransformationRegistry.discover();
    }
}
