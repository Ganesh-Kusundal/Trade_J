package com.tradej.app.config;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.model.BrokerCapabilities;
import com.tradej.broker.api.model.BrokerTransportCapabilities;
import com.tradej.broker.api.model.MarketSessionPolicy;
import com.tradej.broker.api.model.VenueCapability;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.broker.api.port.InstrumentResolver;
import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.MarketDataProvider;
import com.tradej.broker.api.port.OptionsProvider;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.broker.api.port.OrderQuery;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.broker.api.port.WebSocketMultiplexer;
import com.tradej.broker.core.observability.ObservableMarketDataProvider;
import com.tradej.broker.core.observability.ObservableOrderCommand;
import com.tradej.broker.icici.auth.BreezeTokenProvider;
import com.tradej.broker.icici.config.BreezeConnectionSettings;
import com.tradej.composition.BrokerComposition;
import com.tradej.composition.config.BrokerProfile;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Map;

@Configuration
@ConditionalOnExpression("'${trade.broker-type:}' == 'icici' || '${trade.broker-type:}' == 'gateway'")
public class IciciBrokerConfiguration {

    @Bean
    BreezeConnectionSettings iciciConnectionSettings(TradingProperties properties) {
        TradingProperties.IciciProperties cfg = properties.icici();
        if (cfg == null) {
            throw new IllegalStateException("trade.icici configuration is required when broker-type=icici");
        }
        return BreezeConnectionSettings.withDefaults(
                cfg.appKey(),
                cfg.secretKey(),
                cfg.sessionToken(),
                cfg.authMode(),
                Path.of(cfg.totpSecretFile()),
                Path.of(cfg.usernameFile()),
                Path.of(cfg.passwordFile()),
                Path.of(cfg.apiSessionFile()),
                Path.of(cfg.tokenStateFile()),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );
    }

    @Bean
    BreezeTokenProvider iciciTokenProvider(BreezeConnectionSettings iciciConnectionSettings) {
        return new com.tradej.broker.icici.auth.BreezeTokenManager(iciciConnectionSettings);
    }

    @Bean
    BrokerComposition iciciBrokerComposition(TradingProperties properties) {
        TradingProperties.IciciProperties cfg = properties.icici();
        BrokerProfile.IciciConfig iciciConfig = new BrokerProfile.IciciConfig(
                cfg.appKey(),
                cfg.secretKey(),
                cfg.sessionToken(),
                cfg.authMode(),
                Path.of(cfg.totpSecretFile()),
                Path.of(cfg.usernameFile()),
                Path.of(cfg.passwordFile()),
                Path.of(cfg.apiSessionFile()),
                Path.of(cfg.tokenStateFile()),
                cfg.ordersEnabled(),
                cfg.refreshBufferMinutes(),
                cfg.loginRedirectPort(),
                cfg.loginRedirectPath(),
                cfg.browserHeadless(),
                cfg.browserLoginTimeoutSeconds()
        );
        BrokerProfile profile = new BrokerProfile(BrokerProfile.BrokerType.ICICI, null, null, iciciConfig);
        return BrokerComposition.create(profile);
    }

    @Bean(name = "iciciBrokerConnection")
    IBrokerConnection iciciBrokerConnection(BrokerComposition iciciBrokerComposition) {
        return iciciBrokerComposition.brokerConnection();
    }

    @Bean
    MarketDataProvider iciciMarketDataProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn,
            MeterRegistry meterRegistry) {
        return new ObservableMarketDataProvider("icici", conn.marketData(), meterRegistry);
    }

    @Bean
    OrderCommand iciciOrderCommand(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn,
            MeterRegistry meterRegistry) {
        return new ObservableOrderCommand("icici", conn.orders(), meterRegistry);
    }

    @Bean
    InstrumentResolver iciciInstrumentResolver(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.instruments();
    }

    @Bean
    OrderQuery iciciOrderQuery(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.orderQuery();
    }

    @Bean
    PortfolioProvider iciciPortfolioProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.portfolio();
    }

    @Bean
    MarginProvider iciciMarginProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.margin();
    }

    @Bean
    FuturesProvider iciciFuturesProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.futures();
    }

    @Bean
    OptionsProvider iciciOptionsProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.options();
    }

    @Bean
    WebSocketMultiplexer iciciWebSocketMultiplexer(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.websocket();
    }

    @Bean
    ConditionalAlertProvider iciciConditionalAlertProvider(
            @org.springframework.beans.factory.annotation.Qualifier("iciciBrokerConnection") IBrokerConnection conn) {
        return conn.alerts();
    }

    @Bean
    BrokerCapabilities iciciBrokerCapabilities() {
        MarketSessionPolicy session = new MarketSessionPolicy(LocalTime.of(9, 15), LocalTime.of(15, 30), false);
        return new BrokerCapabilities(Map.of(
                ExchangeSegment.NSE_EQ, VenueCapability.withSyntheticAdvancedOrders(
                        ExchangeSegment.NSE_EQ,
                        EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                        false, false, false, session),
                ExchangeSegment.NSE_FNO, VenueCapability.withSyntheticAdvancedOrders(
                        ExchangeSegment.NSE_FNO,
                        EnumSet.of(FeedMode.TICKER, FeedMode.QUOTE),
                        false, false, true, session)
        ));
    }

    @Bean
    BrokerTransportCapabilities iciciBrokerTransportCapabilities(BreezeConnectionSettings settings) {
        return new BrokerTransportCapabilities(
                true, settings.ordersEnabled(), true, true, false);
    }
}
