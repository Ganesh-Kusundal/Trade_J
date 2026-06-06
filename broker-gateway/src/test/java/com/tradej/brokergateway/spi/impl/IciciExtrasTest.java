package com.tradej.brokergateway.spi.impl;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.port.FuturesProvider;
import com.tradej.brokergateway.result.BrokerSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IciciExtrasTest {

    @Mock IBrokerConnection connection;
    @Mock FuturesProvider futures;

    @Test
    void sourceReturnsIcici() {
        IciciExtras extras = new IciciExtras(connection);
        assertEquals(BrokerSource.ICICI, extras.source());
    }

    @Test
    void newsReturnsEmpty() {
        IciciExtras extras = new IciciExtras(connection);
        assertTrue(extras.news().isEmpty());
    }

    @Test
    void methodsReturnsFuturesAndOptionGreeks() {
        IciciExtras extras = new IciciExtras(connection);
        Set<String> expected = Set.of("futures", "optionGreeks", "getOptionGreeks");
        assertEquals(expected, extras.methods());
        assertEquals(3, extras.methods().size());
    }

    @Test
    void invokeFuturesReturnsProviderWhenAvailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.of(futures));
        IciciExtras extras = new IciciExtras(connection);
        Optional<Object> result = extras.invoke("futures");
        assertTrue(result.isPresent());
        assertEquals(futures, result.get());
    }

    @Test
    void invokeFuturesReturnsEmptyWhenUnavailable() {
        when(connection.getCapability(FuturesProvider.class)).thenReturn(Optional.empty());
        IciciExtras extras = new IciciExtras(connection);
        assertTrue(extras.invoke("futures").isEmpty());
    }

    @Test
    void invokeUnknownMethodThrowsUnsupportedOperationException() {
        IciciExtras extras = new IciciExtras(connection);
        assertThrows(UnsupportedOperationException.class, () -> extras.invoke("unknown"));
    }
}
