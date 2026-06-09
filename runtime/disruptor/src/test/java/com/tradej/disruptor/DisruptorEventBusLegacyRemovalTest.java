package com.tradej.disruptor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that NoOpEventBus has been removed and the primary event bus
 * is a real, working implementation.
 */
@Tag("unit")
class DisruptorEventBusLegacyRemovalTest {

    @Test
    void noOpEventBusMustNotExistInAppModule() {
        try {
            Class.forName("com.tradej.app.config.NoOpEventBus");
            fail("NoOpEventBus class should not exist — it has been replaced by SimpleEventBus");
        } catch (ClassNotFoundException expected) {
            // expected — NoOpEventBus was removed
        }
    }

    @Test
    void disruptorEventBusHasConfigConstructor() {
        Constructor<?>[] constructors = DisruptorEventBus.class.getDeclaredConstructors();
        boolean hasConfigConstructor = Arrays.stream(constructors)
                .anyMatch(c -> {
                    Class<?>[] params = c.getParameterTypes();
                    return params.length == 1
                            && params[0].getName().contains("DisruptorPipelineConfig");
                });
        assertTrue(hasConfigConstructor,
                "DisruptorEventBus must have a constructor accepting DisruptorPipelineConfig");
    }

    @Test
    void simpleEventBusExistsAndImplementsEventBus() {
        try {
            Class<?> clazz = Class.forName("com.tradej.core.domain.event.SimpleEventBus");
            assertTrue(
                    com.tradej.core.domain.port.EventBus.class.isAssignableFrom(clazz),
                    "SimpleEventBus must implement EventBus interface"
            );
        } catch (ClassNotFoundException e) {
            fail("SimpleEventBus must exist in core module");
        }
    }
}
