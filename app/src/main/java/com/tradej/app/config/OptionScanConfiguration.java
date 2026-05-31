package com.tradej.app.config;

import com.tradej.app.scanner.OptionScanService;
import com.tradej.broker.api.IBrokerConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OptionScanConfiguration {

    @Bean
    OptionScanService optionScanService(IBrokerConnection brokerConnection) {
        return new OptionScanService(brokerConnection.options());
    }
}
