package com.tradej.execution.risk;

import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.UnrealizedPnLUpdated;
import com.tradej.core.domain.port.EventBus;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.FeedMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MarkToMarketRiskMonitorTest {

    private NetPositionProvider netPositionProvider;
    private MarkToMarketRiskMonitor monitor;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        netPositionProvider = mock(NetPositionProvider.class);
        eventBus = mock(EventBus.class);
        // Set publish interval to 0 to trigger on every tick in tests
        monitor = new MarkToMarketRiskMonitor(true, netPositionProvider, 100_000L, 0L);
        monitor.setEventBus(eventBus);
        // Mock getNetPosition to return non-zero for SBIN (used in onMarketTick)
        when(netPositionProvider.getNetPosition("SBIN")).thenReturn(100L);
    }

    @Test
    void calculateUnrealizedPnLCorrectlyUsingWeightedAverage() {
        // Given: A position with an average price of 100 (10000 paisa)
        NetPositionProvider.Position sbinPos = new NetPositionProvider.Position("SBIN", 100, 100_00L);
        when(netPositionProvider.getPositions()).thenReturn(Map.of("SBIN", sbinPos));
        when(netPositionProvider.getNetPosition("SBIN")).thenReturn(100L);

        // When: A tick arrives with price 110 (11000 paisa)
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 1, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                110_00L, 1, 1000, System.currentTimeMillis(), Optional.empty(), 0, 0
        );

        monitor.onMarketTick(tick);

        // Then: EventBus should publish UnrealizedPnLUpdated with +1000 PnL (10 * 100)
        // (110 - 100) * 100 = 1000
        ArgumentCaptor<UnrealizedPnLUpdated> captor = ArgumentCaptor.forClass(UnrealizedPnLUpdated.class);
        verify(eventBus).publish(captor.capture());
        
        UnrealizedPnLUpdated event = captor.getValue();
        assertEquals(1000_00L, event.unrealizedPnlPaisa()); // (110.00 - 100.00) * 100 = 1000.00
    }

    @Test
    void calculateNegativeUnrealizedPnLForShorts() {
        // Given: A short position (-100 qty) with average price 100 (10000 paisa)
        NetPositionProvider.Position sbinPos = new NetPositionProvider.Position("SBIN", -100, 100_00L);
        when(netPositionProvider.getPositions()).thenReturn(Map.of("SBIN", sbinPos));
        when(netPositionProvider.getNetPosition("SBIN")).thenReturn(-100L);

        // When: Price goes up to 110 (Loss for short)
        MarketTickEvent tick = new MarketTickEvent(
                EventMetadata.root(), 1, "SBIN", ExchangeSegment.NSE_EQ, FeedMode.TICKER,
                110_00L, 1, 1000, System.currentTimeMillis(), Optional.empty(), 0, 0
        );

        monitor.onMarketTick(tick);

        // Then: (110 - 100) * -100 = -1000
        ArgumentCaptor<UnrealizedPnLUpdated> captor = ArgumentCaptor.forClass(UnrealizedPnLUpdated.class);
        verify(eventBus).publish(captor.capture());
        
        UnrealizedPnLUpdated event = captor.getValue();
        assertEquals(-1000_00L, event.unrealizedPnlPaisa());
    }
}
