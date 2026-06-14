package com.tradej.core.domain.event;

public sealed interface DomainEvent permits
        OrderUpdateEvent,
        BrokerAdapterError,
        CandleClosed,
        CandleDeveloping,
        DepthUpdateEvent,
        EventBusBackpressure,
        GammaExposureComputed,
        GreeksComputed,
        KillSwitchEngaged,
        MarketTickEvent,
        MaxPainComputed,
        OptionChainUpdated,
        PnlUpdatedEvent,
        PositionMismatch,
        PositionUpdateEvent,
        ReconciliationHaltRequired,
        ReplayTimeChangedEvent,
        ScanHitProduced,
        ScanResultsPublished,
        SignalGenerated,
        SignalPendingExecution,
        SignalSuppressed,
        StrategyError,
        StrategyMetricsSnapshot,
        StreamHealthChanged,
        TradeClosed,
        TradeExecutionEvent,
        TradeOpened,
        TradeUpdated,
        UnifiedKillSwitchDisengaged,
        UnifiedKillSwitchEngaged,
        UnrealizedPnLUpdated,
        PoisonPillEvent,
        TestEvent {
    EventMetadata metadata();

    /**
     * Accepts a visitor to process this event in a type-safe, non-branching manner.
     */
    void accept(DomainEventVisitor visitor);

    default String eventId() {
        return metadata().eventId();
    }

    default long timestampMs() {
        return metadata().timestampMs();
    }

    default long timestampMonotonic() {
        return metadata().timestampMonotonic();
    }

    default long sequenceId() {
        return metadata().sequenceId();
    }

    default String correlationId() {
        return metadata().correlationId();
    }

    default EventPriority priority() {
        return EventPriority.NORMAL;
    }

    /**
     * Schema version of this event type. Monotonically increasing integer
     * that starts at 1 for all initial event schemas.
     * <p>
     * Bump this version when the fields of an event record change in a way
     * that affects serialization or deserialization. The version enables
     * backward-compatible read paths for persisted events at older schema
     * versions.
     * <p>
     * Override this method in individual event records when evolving their
     * schema. The version is also captured in {@link EventMetadata#schemaVersion}
     * at event creation time for forensic traceability.
     *
     * @see EventSchemaVersion
     */
    default int schemaVersion() {
        return 1;
    }
}
