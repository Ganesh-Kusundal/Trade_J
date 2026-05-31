package com.tradej.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan("com.tradej")
public class TradingApplication {
    private TradingApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(TradingApplication.class, args);
    }
}
