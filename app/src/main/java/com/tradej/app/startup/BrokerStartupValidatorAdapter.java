package com.tradej.app.startup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import com.tradej.app.config.TradingProperties;
import com.tradej.broker.core.startup.BrokerStartupValidator;

@Component
public class BrokerStartupValidatorAdapter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BrokerStartupValidatorAdapter.class);

    private final TradingProperties properties;
    private final BrokerStartupValidator validator;

    public BrokerStartupValidatorAdapter(TradingProperties properties) {
        this.properties = properties;
        this.validator = new BrokerStartupValidator();
    }

    @Override
    public void run(ApplicationArguments args) {
        String clientId = properties.broker() != null ? properties.broker().clientId() : null;
        String accessToken = properties.broker() != null ? properties.broker().accessToken() : null;
        String csvPath = properties.instruments() != null ? properties.instruments().csvPath() : null;

        validator.validate(
                properties.runtime().mode(),
                new BrokerStartupValidator.BrokerStartupConfig(clientId, accessToken, csvPath)
        );
    }
}
