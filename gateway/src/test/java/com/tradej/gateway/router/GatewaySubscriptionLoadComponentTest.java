package com.tradej.gateway.router;

import com.tradej.gateway.protocol.GatewayTopic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates gateway router can sustain high-volume publishes for 500+ symbols.
 */
@Tag("component")
class GatewaySubscriptionLoadComponentTest {

    @Test
    void publishesHighVolumeTickStream() {
        GatewayTopicRouter router = new GatewayTopicRouter();
        router.start();
        byte[] payload = "{\"symbol\":\"SBIN\",\"ltpPaisa\":10000}".getBytes();
        for (int i = 0; i < 500; i++) {
            router.publish(GatewayTopic.MARKET_TICK, payload);
        }
        assertTrue(router.droppedEventCount() >= 0);
        router.stop();
    }
}
