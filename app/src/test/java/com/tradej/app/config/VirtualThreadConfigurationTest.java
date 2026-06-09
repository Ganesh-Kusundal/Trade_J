package com.tradej.app.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class VirtualThreadConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VirtualThreadConfiguration.class))
            .withBean(TradingProperties.class, () -> new TradingProperties(
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null
            ));

    @Test
    void virtualThreadExecutorBeanIsCreated() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("virtualThreadExecutor"));
            ExecutorService executor = context.getBean("virtualThreadExecutor", ExecutorService.class);
            assertNotNull(executor);
        });
    }

    @Test
    void beansNotCreatedWhenDisabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VirtualThreadConfiguration.class))
                .withPropertyValues("trade.virtual-threads.enabled=false")
                .withBean(TradingProperties.class, () -> new TradingProperties(
                        null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null,
                        null, null, null
                ))
                .run(context -> {
                    assertFalse(context.containsBean("virtualThreadExecutor"));
                });
    }

    @Test
    void beansCreatedByDefaultWhenPropertyMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(VirtualThreadConfiguration.class))
                .withBean(TradingProperties.class, () -> new TradingProperties(
                        null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null,
                        null, null, null
                ))
                .run(context -> {
                    assertTrue(context.containsBean("virtualThreadExecutor"));
                });
    }

    @Test
    void virtualThreadPropertiesDefaultsAreCorrect() {
        var defaults = TradingProperties.VirtualThreadProperties.defaults();
        assertTrue(defaults.enabled());
        assertTrue(defaults.maxConcurrency() > 0);
    }

    @Test
    void virtualThreadPropertiesNegativeConcurrencyFallsBackToProcessors() {
        var props = new TradingProperties.VirtualThreadProperties(true, -1);
        assertEquals(Runtime.getRuntime().availableProcessors(), props.maxConcurrency());
    }

    @Test
    void virtualThreadPropertiesZeroConcurrencyFallsBackToProcessors() {
        var props = new TradingProperties.VirtualThreadProperties(true, 0);
        assertEquals(Runtime.getRuntime().availableProcessors(), props.maxConcurrency());
    }
}
