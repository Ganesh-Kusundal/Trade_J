package com.tradej.app.integration;

import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeClosed;
import com.tradej.core.domain.event.TradeOpened;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.risk.PositionRiskHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("component")
class KillSwitchE2EComponentTest {

    @Test
    void consecutiveLossesEngageKillSwitchAndSuppressSignals() {
        PositionRiskHandler risk = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 2, 5_000_000L, 3),
                NetPositionProvider.empty()
        );

        List<com.tradej.core.domain.event.DomainEvent> events = new ArrayList<>();
        var publisher = (java.util.function.Consumer<com.tradej.core.domain.event.DomainEvent>) events::add;

        for (int i = 0; i < 2; i++) {
            risk.onDomainEvent(new TradeOpened(
                    com.tradej.core.domain.event.EventMetadata.root(),
                    "T" + i, "O" + i, "SIG" + i, "SBIN", Side.LONG, 1, 100_00L, 0L, 0L
            ), publisher);
            risk.onDomainEvent(new TradeClosed(
                    com.tradej.core.domain.event.EventMetadata.root(),
                    "T" + i, "SBIN", 100_00L, -50_000L, 1L, "loss"
            ), publisher);
        }

        assertTrue(risk.isKillSwitchActive());

        SignalGenerated signal = new SignalGenerated(
                com.tradej.core.domain.event.EventMetadata.root(),
                UUID.randomUUID().toString(),
                "SBIN",
                "5m",
                Side.BUY,
                100_00L,
                0L,
                0L,
                "test",
                Map.of("quantity", 1L)
        );
        events.clear();
        risk.onDomainEvent(signal, publisher);
        assertTrue(events.stream().anyMatch(SignalSuppressed.class::isInstance));
        assertFalse(events.stream().anyMatch(SignalPendingExecution.class::isInstance));
    }
}
