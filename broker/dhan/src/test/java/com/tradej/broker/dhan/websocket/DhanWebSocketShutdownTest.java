package com.tradej.broker.dhan.websocket;

import com.tradej.broker.dhan.auth.DhanTokenProvider;
import com.tradej.broker.dhan.client.DhanClientHolder;
import com.tradej.broker.dhan.config.DhanConnectionSettings;
import com.tradej.broker.dhan.adapter.DhanInstrumentResolver;
import com.tradej.core.domain.event.EventMetadataFactory;
import com.tradej.core.domain.time.LiveTradingClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link DhanWebSocketMultiplexer#disconnect()} always shuts down
 * both the reconnect and reconciliation schedulers — even when intermediate failures occur.
 */
@Tag("unit")
class DhanWebSocketShutdownTest {

    @Test
    void disconnectAlwaysShutsBothSchedulers() throws Exception {
        // Minimal stubs — just enough to construct DhanWebSocketMultiplexer in sandbox mode
        DhanConnectionSettings settings = mock(DhanConnectionSettings.class);
        when(settings.isSandbox()).thenReturn(true); // Skip depth client setup

        DhanTokenProvider tokenProvider = mock(DhanTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("test-token");

        DhanClientHolder clientHolder = mock(DhanClientHolder.class);
        when(clientHolder.tokenProvider()).thenReturn(tokenProvider);

        DhanInstrumentResolver resolver = mock(DhanInstrumentResolver.class);

        EventMetadataFactory metadataFactory = new EventMetadataFactory(new LiveTradingClock());

        DhanWebSocketMultiplexer multiplexer = new DhanWebSocketMultiplexer(
                clientHolder, resolver, settings, metadataFactory);

        // disconnect() without connect() — the executors still need to shut down cleanly
        multiplexer.disconnect();

        ScheduledExecutorService reconnectScheduler = getField(multiplexer, "reconnectScheduler");
        ScheduledExecutorService reconciliationScheduler = getField(multiplexer, "reconciliationScheduler");

        assertTrue(reconnectScheduler.isShutdown(),
                "reconnectScheduler must be shut down after disconnect()");
        assertTrue(reconciliationScheduler.isShutdown(),
                "reconciliationScheduler must be shut down after disconnect()");
    }

    @Test
    void doubleDisconnectDoesNotThrow() throws Exception {
        DhanConnectionSettings settings = mock(DhanConnectionSettings.class);
        when(settings.isSandbox()).thenReturn(true);

        DhanTokenProvider tokenProvider = mock(DhanTokenProvider.class);
        when(tokenProvider.getAccessToken()).thenReturn("test-token");

        DhanClientHolder clientHolder = mock(DhanClientHolder.class);
        when(clientHolder.tokenProvider()).thenReturn(tokenProvider);

        DhanInstrumentResolver resolver = mock(DhanInstrumentResolver.class);
        EventMetadataFactory metadataFactory = new EventMetadataFactory(new LiveTradingClock());

        DhanWebSocketMultiplexer multiplexer = new DhanWebSocketMultiplexer(
                clientHolder, resolver, settings, metadataFactory);

        // Calling disconnect twice must not throw RejectedExecutionException or similar
        multiplexer.disconnect();
        multiplexer.disconnect();  // second call — schedulers already shut down
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
