package com.tradej.composition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SimpleEventBus;
import com.tradej.core.domain.model.Candle;
import com.tradej.persistence.replay.ReplayResult;
import com.tradej.replay.engine.ReplayController;
import com.tradej.replay.engine.ReplayOrchestrator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class ReplayServiceTest {

    @Test
    void startReturnsHandleWithSessionId() {
        ReplayService service = newService(true);
        ReplayService.ReplaySessionHandle h = service.start("SBIN", "1m", 0L, 100L, 1.0);
        assertThat(h).isNotNull();
        assertThat(h.id()).isNotBlank();
        assertThat(h.symbol()).isEqualTo("SBIN");
    }

    @Test
    void pauseRequiresKnownSession() {
        ReplayService service = newService(true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.pause("nope"));
    }

    @Test
    void resumeRequiresKnownSession() {
        ReplayService service = newService(true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.resume("nope"));
    }

    @Test
    void stopRemovesSession() {
        ReplayService service = newService(true);
        ReplayService.ReplaySessionHandle h = service.start("SBIN", "1m", 0L, 100L, 1.0);
        service.status(h.id()); // exists
        service.stop(h.id());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.pause(h.id()));
    }

    @Test
    void handleControlCommandDispatchesByOp() {
        ReplayService service = newService(true);

        // pause on unknown session -> IllegalArgumentException
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.handleControlCommand("{\"op\":\"pause\",\"sessionId\":\"nope\"}"));

        // start with no symbol -> IllegalArgumentException
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.handleControlCommand("{\"op\":\"start\"}"));

        // start with valid symbol + range
        String startCmd = "{\"op\":\"start\",\"symbol\":\"SBIN\",\"interval\":\"1m\","
                + "\"fromMs\":0,\"toMs\":1000,\"speed\":2.0}";
        ReplayService.ReplayStatus status = service.handleControlCommand(startCmd);
        assertThat(status.state()).isEqualTo("PLAYING");
        assertThat(status.symbol()).isEqualTo("SBIN");
        assertThat(status.speed()).isEqualTo(2.0);
    }
    @Test
    void handleControlCommandUnknownOpThrows() {
        ReplayService service = newService(true);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.handleControlCommand("{\"op\":\"WAT\"}"));
    }

    // ── helpers ──

    private static ReplayService newService(boolean withReplayResult) {
        SimpleEventBus bus = new SimpleEventBus();
        ReplayOrchestrator orchestrator = mock(ReplayOrchestrator.class);
        ReplayController controller = new ReplayController(bus);
        if (withReplayResult) {
            when(orchestrator.replayCandles(anyString(), anyString(), anyLong(), anyLong(), any()))
                    .thenAnswer(inv -> {
                        com.tradej.core.domain.port.EventBus b = inv.getArgument(4);
                        Candle c = new Candle("SBIN", "1m", 0L, 60_000L,
                                75_000L, 75_100L, 74_900L, 75_050L, 100L, true);
                        if (b != null) b.publish(new CandleClosed(EventMetadata.root(), c));
                        return new ReplayResult(1L, 1L, 0L, 0L);
                    });
        }
        return new ReplayService(
                bus, orchestrator, controller,
                Optional.empty(), Optional.empty(),
                new ObjectMapper()
        );
    }
}
