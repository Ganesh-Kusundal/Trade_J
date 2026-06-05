package com.tradej.execution.risk;

import com.tradej.broker.api.port.MarginProvider;
import com.tradej.broker.api.port.PortfolioProvider;
import com.tradej.core.domain.model.Balance;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;
import com.tradej.core.domain.model.OrderRequest;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OrderType;
import com.tradej.core.domain.value.ProductType;
import com.tradej.core.domain.value.Side;
import com.tradej.core.domain.value.Validity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class MarginEnforcementHandlerTest {

    @Test
    void skipsWhenDisabled() {
        MarginEnforcementHandler handler = new MarginEnforcementHandler(false, null, null, Duration.ofMinutes(5));
        OrderRequest order = sampleOrder();
        assertTrue(handler.checkMargin(order).isEmpty());
    }

    @Test
    void rejectsInsufficientMargin() {
        MarginProvider margin = mock(MarginProvider.class);
        PortfolioProvider portfolio = mock(PortfolioProvider.class);
        when(margin.estimateMargin(any(MarginEstimateRequest.class)))
                .thenReturn(new MarginEstimate(1_000_000L, 0, 0, 0));
        when(portfolio.getBalance()).thenReturn(new Balance("c1", 100_000L, 0, 0, 0, 50_000L));

        MarginEnforcementHandler handler = new MarginEnforcementHandler(true, margin, portfolio, Duration.ofMinutes(5));
        assertTrue(handler.checkMargin(sampleOrder()).filter("insufficient_margin"::equals).isPresent());
    }

    @Test
    void passesWhenMarginAvailable() {
        MarginProvider margin = mock(MarginProvider.class);
        PortfolioProvider portfolio = mock(PortfolioProvider.class);
        when(margin.estimateMargin(any(MarginEstimateRequest.class)))
                .thenReturn(new MarginEstimate(10_000L, 0, 0, 0));
        when(portfolio.getBalance()).thenReturn(new Balance("c1", 1_000_000L, 0, 0, 0, 500_000L));

        MarginEnforcementHandler handler = new MarginEnforcementHandler(true, margin, portfolio, Duration.ofMinutes(5));
        assertFalse(handler.checkMargin(sampleOrder()).isPresent());
    }

    private static OrderRequest sampleOrder() {
        return new OrderRequest(
                "SBIN",
                ExchangeSegment.NSE_EQ,
                Side.BUY,
                10,
                OrderType.LIMIT,
                50000L,
                0L,
                ProductType.INTRADAY,
                Validity.DAY,
                "sig-1");
    }
}
