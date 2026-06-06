package com.tradej.replay.engine;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.ReplayTimeChangedEvent;
import com.tradej.core.domain.model.Candle;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import com.tradej.core.domain.port.EventBus;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public class ReplayControllerTest {

    @Test
    public void testReplayControllerFlow() {
        // 1. Mock event bus
        EventBus mockEventBus = Mockito.mock(EventBus.class);

        // 2. Generate 10 consecutive 1m candles
        List<Candle> candles = new ArrayList<>();
        long baseTime = 1717146000000L; // aligned to a clean minute boundary (e.g. 9:30 AM)
        for (int i = 0; i < 10; i++) {
            candles.add(new Candle(
                "SBIN", "1m",
                baseTime + (i * 60000L),
                baseTime + ((i + 1) * 60000L) - 1,
                100000L, 102000L, 99000L, 101000L, 5000L, true
            ));
        }

        // 3. Create controller and start
        ReplayController controller = new ReplayController(mockEventBus);
        controller.start(candles);

        assertEquals(ReplayController.ReplayState.PAUSED, controller.getState());
        assertEquals(10, controller.getTotalCandles());
        assertEquals(0, controller.getCurrentIndex());

        // Adjust speed
        controller.setSpeed(5.0);
        assertEquals(5.0, controller.getSpeedMultiplier());

        // 4. Step 6 times (which covers 5 minutes and starts the 6th minute - triggering a closed 5m bar!)
        for (int i = 0; i < 6; i++) {
            boolean success = controller.step();
            assertTrue(success);
        }

        assertEquals(6, controller.getCurrentIndex());

        // 5. Verify published events using Mockito Captors
        ArgumentCaptor<com.tradej.core.domain.event.DomainEvent> eventCaptor = ArgumentCaptor.forClass(com.tradej.core.domain.event.DomainEvent.class);
        verify(mockEventBus, atLeastOnce()).publish(eventCaptor.capture());

        List<com.tradej.core.domain.event.DomainEvent> publishedEvents = eventCaptor.getAllValues();
        assertTrue(publishedEvents.size() >= 10, "Should have published multiple events");

        boolean found1m = false;
        boolean found5m = false;
        boolean foundTimeChange = false;

        for (Object event : publishedEvents) {
            if (event instanceof CandleClosed cc) {
                if ("1m".equals(cc.candle().interval())) {
                    found1m = true;
                } else if ("5m".equals(cc.candle().interval())) {
                    found5m = true;
                    assertTrue(cc.candle().closed(), "Aggregated 5m candle should be closed");
                    assertEquals("SBIN", cc.candle().symbol());
                }
            } else if (event instanceof ReplayTimeChangedEvent) {
                foundTimeChange = true;
            }
        }

        assertTrue(found1m, "Should have emitted 1m CandleClosed events");
        assertTrue(found5m, "Should have emitted a closed 5m CandleClosed event after 5 steps");
        assertTrue(foundTimeChange, "Should have emitted ReplayTimeChangedEvent");

        // Clean up
        controller.stop();
        assertEquals(ReplayController.ReplayState.STOPPED, controller.getState());
        assertEquals(0, controller.getTotalCandles());
    }
}
