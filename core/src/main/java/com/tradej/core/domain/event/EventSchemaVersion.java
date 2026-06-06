package com.tradej.core.domain.event;

/**
 * Event schema version constants for {@link DomainEvent} evolution.
 * <p>
 * Every event type in the system starts at {@code schemaVersion = 1}.
 * When the fields of an event record change (add, remove, rename, retype),
 * bump <strong>that event's</strong> schema version — NOT this global constant.
 * <p>
 * {@link #CURRENT} is the current schema version for <em>newly created</em>
 * events and is captured in {@link EventMetadata#schemaVersion} at creation
 * time so that persisted events carry their own version for deserialization.
 *
 * <h2>Versioning rules</h2>
 * <ul>
 *   <li>Each event type independently tracks its own schema version.</li>
 *   <li>Start at 1 for all event types added before versioning was introduced.</li>
 *   <li>Bump the version for an event type when its record definition changes.</li>
 *   <li>Never reuse or reset a version number for an event type.</li>
 *   <li>Deprecation of an event field uses {@code @Deprecated(forRemoval = true)}
 *       one minor release before the field is actually removed.</li>
 * </ul>
 *
 * <h2>Migration pattern</h2>
 * <pre>{@code
 * // v1: MarketTickEvent(metadata, seqId, symbol, segment, feedMode, ltpPaisa, ...)
 * // v2: Added exchangeSegment field — override schemaVersion() → 2
 * @Override
 * public int schemaVersion() { return 2; }
 * }</pre>
 *
 * @see DomainEvent#schemaVersion()
 * @see EventMetadata#schemaVersion
 */
public final class EventSchemaVersion {

    private EventSchemaVersion() {
    }

    /**
     * The current schema version for <em>all newly created events</em>.
     * Individual event types may have higher version numbers when they
     * override {@link DomainEvent#schemaVersion()}.
     */
    public static final int CURRENT = 1;
}
