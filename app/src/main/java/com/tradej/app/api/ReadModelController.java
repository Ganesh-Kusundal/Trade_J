package com.tradej.app.api;

import com.tradej.app.readmodel.ReadModelStore;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SSE endpoint for operator read models decoupled from the hot path.
 */
@RestController
@RequestMapping("/api/v1")
public class ReadModelController {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final ReadModelStore readModelStore;
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "readmodel-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public ReadModelController(ReadModelStore readModelStore) {
        this.readModelStore = readModelStore;
    }

    @GetMapping("/read-model")
    public Map<String, Object> snapshot() {
        ReadModelStore.ReadModelSnapshot snapshot = readModelStore.snapshot();
        return Map.of(
                "version", snapshot.version(),
                "orders", snapshot.orders(),
                "positions", snapshot.positions(),
                "ticks", snapshot.ticks(),
                "depths", snapshot.depths(),
                "candles", snapshot.candles(),
                "signals", snapshot.signals(),
                "pnl", snapshot.pnl()
        );
    }

    @GetMapping(value = "/stream/read-model", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        AtomicLong lastVersion = new AtomicLong(-1L);

        readModelStore.subscribe(snapshot -> {
            if (snapshot.version() == lastVersion.get()) {
                return;
            }
            lastVersion.set(snapshot.version());
            try {
                emitter.send(SseEmitter.event()
                        .name("read-model")
                        .data(Map.of(
                                "version", snapshot.version(),
                                "orders", snapshot.orders(),
                                "positions", snapshot.positions(),
                                "ticks", snapshot.ticks(),
                                "depths", snapshot.depths(),
                                "candles", snapshot.candles(),
                                "signals", snapshot.signals(),
                                "pnl", snapshot.pnl()
                        )));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });

        var heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }, 15, 15, TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(false));
        emitter.onTimeout(() -> heartbeat.cancel(false));
        emitter.onError(error -> heartbeat.cancel(true));
        return emitter;
    }
}
