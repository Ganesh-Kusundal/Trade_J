package com.tradej.app.integration;

import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.port.NetPositionProvider;
import com.tradej.execution.risk.MarginEnforcementHandler;
import com.tradej.execution.risk.PositionRiskHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@Tag("unit")
class MarginEnforcementComponentTest {

    @Test
    void positionRiskHandlerRejectsWhenMarginHandlerReturnsInsufficient() {
        MarginEnforcementHandler margin = spy(
                new MarginEnforcementHandler(true, null, null, Duration.ofMinutes(5)));
        doReturn(Optional.of("insufficient_margin"))
                .when(margin).checkMargin(any(OrderRequest.class));
        PositionRiskHandler handler = new PositionRiskHandler(
                RiskLimits.withOpenPositionQuantity(1_000_000L, 3, 5_000_000L, 3),
                NetPositionProvider.empty(),
                null,
                margin,
                null);
        assertFalse(handler.isKillSwitchActive());
    }
}
