package com.tradej.core.support;

import com.tradej.core.domain.event.CandleClosed;
import com.tradej.core.domain.event.CandleDeveloping;
import com.tradej.core.domain.event.DomainEvent;
import com.tradej.core.domain.event.MarketTickEvent;
import com.tradej.core.domain.event.OrderAccepted;
import com.tradej.core.domain.event.OrderFilled;
import com.tradej.core.domain.event.SignalGenerated;
import com.tradej.core.domain.event.SignalPendingExecution;
import com.tradej.core.domain.event.SignalSuppressed;
import com.tradej.core.domain.event.TradeOpened;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * Utility for enriching SLF4J MDC (Mapped Diagnostic Context) with fields from
 * {@link DomainEvent} instances. Call {@link #enrich(DomainEvent)} at the start of
 * event processing and {@link #clear()} when processing completes.
 * <p>
 * MDC is thread-local, so enrichment is safe for concurrent pipelines.
 */
public final class MdcHelper {

    private static final String MDC_EVENT_ID = "eventId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_EVENT_TYPE = "eventType";
    private static final String MDC_SYMBOL = "symbol";
    private static final String MDC_STAGE = "stage";

    private MdcHelper() {
    }

    /**
     * Enriches the current thread's MDC with fields extracted from the given event.
     * Call this before processing an event, then {@link #clear()} after.
     */
    public static void enrich(DomainEvent event) {
        if (event == null) {
            return;
        }
        put(MDC_EVENT_ID, event.eventId());
        put(MDC_CORRELATION_ID, event.correlationId());
        put(MDC_EVENT_TYPE, event.getClass().getSimpleName());
        put(MDC_SYMBOL, resolveSymbol(event).orElse(""));
    }

    /**
     * Enriches MDC and additionally sets the {@code stage} field.
     * Useful when a handler wants to identify its processing stage.
     */
    public static void enrich(DomainEvent event, String stage) {
        enrich(event);
        put(MDC_STAGE, stage);
    }

    /**
     * Returns the trading symbol associated with the event, when known.
     * Used for symbol-sharded pipelines and structured logging.
     */
    public static Optional<String> symbolOf(DomainEvent event) {
        return resolveSymbol(event);
    }

    /**
     * Clears all MDC fields set by {@link #enrich(DomainEvent)}.
     */
    public static void clear() {
        MDC.remove(MDC_EVENT_ID);
        MDC.remove(MDC_CORRELATION_ID);
        MDC.remove(MDC_EVENT_TYPE);
        MDC.remove(MDC_SYMBOL);
        MDC.remove(MDC_STAGE);
    }

    // ── Internal helpers ──

    private static void put(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    private static Optional<String> resolveSymbol(DomainEvent event) {
        return switch (event) {
            case MarketTickEvent t -> Optional.of(t.symbol());
            case CandleClosed c -> Optional.of(c.candle().symbol());
            case CandleDeveloping c -> Optional.of(c.candle().symbol());
            case OrderAccepted a -> Optional.of(a.order().symbol());
            case OrderFilled f -> Optional.of(f.order().symbol());
            case SignalGenerated s -> Optional.of(s.symbol());
            case SignalPendingExecution s -> Optional.of(s.orderRequest().symbol());
            case SignalSuppressed s -> Optional.of(s.symbol());
            case TradeOpened t -> Optional.of(t.symbol());
            default -> Optional.empty();
        };
    }
}
