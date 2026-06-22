package com.tradej.core.domain.event;

import com.tradej.core.domain.config.TradeDefaults;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("unit")
class EventDeliveryPolicyTest {

    @Test
    void defaultsUsePlatformQueueCapacities() {
        EventDeliveryPolicy policy = EventDeliveryPolicy.defaults();

        assertEquals(TradeDefaults.EVENT_DOWNSTREAM_QUEUE_CAPACITY, policy.downstreamQueueCapacity());
        assertEquals(TradeDefaults.EVENT_DISPATCH_QUEUE_CAPACITY, policy.dispatchQueueCapacity());
        assertEquals(EventDeliveryPolicy.OverflowStrategy.DEAD_LETTER_AND_DROP, policy.overflowStrategy());
    }

    @Test
    void queueCapacitiesMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> new EventDeliveryPolicy(0, 1, EventDeliveryPolicy.OverflowStrategy.DEAD_LETTER_AND_DROP));
        assertThrows(IllegalArgumentException.class,
                () -> new EventDeliveryPolicy(1, 0, EventDeliveryPolicy.OverflowStrategy.DEAD_LETTER_AND_DROP));
    }
}
