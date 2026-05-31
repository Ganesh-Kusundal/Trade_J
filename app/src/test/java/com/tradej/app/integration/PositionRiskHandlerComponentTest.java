package com.tradej.app.integration;

import com.tradej.broker.dhan.adapter.InMemoryInstrumentResolver;
import com.tradej.broker.dhan.instrument.DhanInstrumentDefinition;
import com.tradej.core.domain.instrument.ContractSymbolNormalizer;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.EventMetadata;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.model.RiskLimits;
import com.tradej.core.domain.value.Exchange;
import com.tradej.core.domain.value.ExchangeSegment;
import com.tradej.core.domain.value.OptionType;
import com.tradej.core.domain.value.Side;
import com.tradej.execution.risk.PositionRiskHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Tag("component")
class PositionRiskHandlerComponentTest {
    @Test
    void qualifiesSignalIntoExecutableOrderRequest() {
        InMemoryInstrumentResolver resolver = new InMemoryInstrumentResolver();
        resolver.replaceDefinitions(List.of(testDefinition()));

        PositionRiskHandler handler = new PositionRiskHandler(
                resolver,
                new RiskLimits(1_000_000L, 3, 5_000_000L, 3)
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(new SignalGenerated(
                EventMetadata.root(),
                "sig-1",
                "SBIN",
                "5m",
                Side.BUY,
                75_000L,
                74_000L,
                77_000L,
                "breakout",
                Map.of("quantity", 10L)
        ), emitted::add);

        SignalPendingExecution pendingExecution = assertInstanceOf(SignalPendingExecution.class, emitted.get(0));
        assertEquals("SBIN", pendingExecution.orderRequest().symbol());
        assertEquals(ExchangeSegment.NSE_EQ, pendingExecution.orderRequest().exchangeSegment());
        assertEquals("sig-1", pendingExecution.orderRequest().correlationId());
    }

    @Test
    void normalizesCompactOptionAliasToCanonicalOrderSymbol() {
        LocalDate expiry = LocalDate.of(2026, 5, 26);
        long strikePaisa = 3_075_000L;
        String canonical = ContractSymbolNormalizer.canonicalOption("NIFTY", expiry, strikePaisa, OptionType.CALL);

        InMemoryInstrumentResolver resolver = new InMemoryInstrumentResolver();
        resolver.replaceDefinitions(List.of(new DhanInstrumentDefinition(
                "NIFTY-May2026-30750-CE",
                canonical,
                Exchange.NFO,
                ExchangeSegment.NSE_FNO,
                "35038",
                "OPTIDX",
                "NIFTY",
                expiry,
                strikePaisa,
                OptionType.CALL,
                1L,
                50L,
                null
        )));

        PositionRiskHandler handler = new PositionRiskHandler(
                resolver,
                new RiskLimits(1_000_000L, 3, 5_000_000L, 3)
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(new SignalGenerated(
                EventMetadata.root(),
                "sig-opt",
                "NIFTY26MAY30750CE",
                "5m",
                Side.BUY,
                100_00L,
                0L,
                0L,
                "strike",
                Map.of("quantity", 50L)
        ), emitted::add);

        SignalPendingExecution pending = assertInstanceOf(SignalPendingExecution.class, emitted.get(0));
        assertEquals("NIFTY 26 MAY 30750 CALL", pending.orderRequest().symbol());
    }

    @Test
    void suppressesSignalWhenOrderValueBreachesRiskLimit() {
        InMemoryInstrumentResolver resolver = new InMemoryInstrumentResolver();
        resolver.replaceDefinitions(List.of(testDefinition()));

        PositionRiskHandler handler = new PositionRiskHandler(
                resolver,
                new RiskLimits(1_000_000L, 3, 100_000L, 3)
        );

        List<DomainEvent> emitted = new ArrayList<>();
        handler.onDomainEvent(new SignalGenerated(
                EventMetadata.root(),
                "sig-2",
                "SBIN",
                "5m",
                Side.BUY,
                75_000L,
                74_000L,
                77_000L,
                "breakout",
                Map.of("quantity", 10L)
        ), emitted::add);

        SignalSuppressed suppressed = assertInstanceOf(SignalSuppressed.class, emitted.get(0));
        assertEquals("sig-2", suppressed.signalId());
    }

    private DhanInstrumentDefinition testDefinition() {
        return new DhanInstrumentDefinition(
                "SBIN",
                "SBIN",
                Exchange.NSE,
                ExchangeSegment.NSE_EQ,
                "3045",
                "EQUITY",
                "SBIN",
                LocalDate.now().plusMonths(1),
                null,
                null,
                1L,
                5L,
                ""
        );
    }
}
