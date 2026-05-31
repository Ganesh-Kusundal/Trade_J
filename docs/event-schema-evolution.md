# Event Schema Evolution Strategy

## Overview

Trade-J domain events are Java records that flow through the Disruptor pipeline, persist to DuckDB/Chronicle, serialize over WebSocket, and deserialize for replay. As the system evolves, event records inevitably change — fields are added, renamed, deprecated, or removed. This document defines how to manage those changes safely.

## Versioning Mechanism

Every `DomainEvent` carries a `schemaVersion()` that defaults to `1`:

```java
public interface DomainEvent {
    // ...
    default int schemaVersion() {
        return 1;
    }
}
```

The version is also captured in `EventMetadata.schemaVersion` at event creation time, so persisted events carry their schema version for deserialization.

### Version Constants

`EventSchemaVersion.CURRENT` (`int`) tracks the latest schema version for newly created events. Individual event types may override `schemaVersion()` to return higher values.

## When to Bump the Version

Bump an event type's `schemaVersion()` (override the default) when its Java record definition changes in any of these ways:

| Change | Bump? | Example |
|--------|-------|---------|
| Add a new optional field | **Yes** | `MarketTickEvent` adds `String optionType` |
| Add a new required field | **Yes** | `TickReceived` adds `ExchangeSegment segment` |
| Rename a field | **Yes** | `long exchangeTimestamp` → `long exchangeTimestampMs` |
| Change a field's type | **Yes** | `long price` → `BigDecimal price` |
| Remove a field (deprecate first) | **Yes** | Remove `String interval` from `TickReceived` |
| Add a `default` method to `DomainEvent` | No (version stays 1) | Adding `default int schemaVersion()` |
| Change only internal implementation | No | Changing `CandleClosed` formatting logic |

## Migration Pattern

### 1. Deprecate first (soft phase)

Mark the old field as `@Deprecated(forRemoval = true)` one minor release before removal:

```java
// Schema version 1 → still written by producers
public record TickReceived(
        EventMetadata metadata,
        String symbol,
        @Deprecated(since = "2.0", forRemoval = true)
        String interval,          // ← deprecated, still present
        long ltpPaisa,
        ...
) implements DomainEvent {
    @Override
    public int schemaVersion() { return 2; }
}
```

### 2. Add the new field

New consumers read the new field. Old consumers use the deprecated field as fallback.

### 3. Remove in the next minor release

Delete the deprecated field and bump consumers to use the new field exclusively.

## Serialization & Deserialization

### DuckDB (feature store)

Events stored as rows with a `schema_version` column. On read, check the version:

```java
switch (row.schemaVersion()) {
    case 1 -> // legacy row layout
    case 2 -> // current row layout
    default -> throw new UnknownEventSchemaException(...);
}
```

### Chronicle Queue (audit log)

Events serialized as bytes. A `schemaVersion` header byte precedes the event payload. On deserialization, the reader checks the version and applies the appropriate deserializer.

### WebSocket (gateway → frontend)

The JSON payload includes a `schemaVersion` field:

```json
{
  "type": "MarketTickEvent",
  "schemaVersion": 2,
  "symbol": "NIFTY",
  ...
}
```

The frontend can branch on `schemaVersion` for backward-compatible parsing.

## Testing Strategy

### Contract assertions

`EventContractAssertions` validates required fields. Update these when adding required fields to an event.

### Migration test pattern

Create a test that:
1. Creates an event at version N
2. Serializes it to bytes/JSON
3. Deserializes it as version N+1
4. Asserts backward-compatible field mapping

Example:

```java
@Test
void tickReceivedV2CanDeserializeV1Payload() {
    // Simulate a legacy persisted event
    var v1Payload = serializeV1();
    var deserialized = deserialize(v2Deserializer(), v1Payload);
    assertThat(deserialized.symbol()).isEqualTo("SBIN");
    // V2's new field gets a sensible default
    assertThat(deserialized.segment()).isEqualTo(ExchangeSegment.NSE_EQ);
}
```

### Replay parity

`ParityVerifier` ensures replay at schema version N produces the same results as live processing at version N. When bumping a schema version, verify replay parity before deploying.

## Example: Evolving MarketTickEvent

### Version 1 (current)

```java
public record MarketTickEvent(
        EventMetadata metadata,
        long sequenceId,
        String symbol,
        ExchangeSegment segment,
        FeedMode feedMode,
        long ltpPaisa,
        long lastTradeQuantity,
        long cumulativeVolume,
        long exchangeTimestampEpochMs,
        Optional<MarketDepth> depth
) implements DomainEvent {
}
```

### Version 2 (hypothetical — adding `vwap`)

```java
public record MarketTickEvent(
        EventMetadata metadata,
        long sequenceId,
        String symbol,
        ExchangeSegment segment,
        FeedMode feedMode,
        long ltpPaisa,
        long lastTradeQuantity,
        long cumulativeVolume,
        long exchangeTimestampEpochMs,
        Optional<MarketDepth> depth,
        Optional<Long> vwapPaisa    // ← new field
) implements DomainEvent {
    @Override
    public int schemaVersion() { return 2; }
}
```

### Backward-compatible read

```java
MarketTickEvent deserialize(byte[] raw, int schemaVersion) {
    return switch (schemaVersion) {
        case 1 -> readV1(raw);  // vwap = Optional.empty()
        case 2 -> readV2(raw);
        default -> throw new IllegalArgumentException(...);
    };
}
```

## Summary

| Principle | Guideline |
|-----------|-----------|
| **Immutable past** | Never modify a persisted event's schema version. |
| **Deprecation window** | Mark `@Deprecated(forRemoval = true)`, leave for ≥1 minor release. |
| **Version per type** | Each event type has its own version counter. |
| **Monotonic** | Versions only increase, never reset or reuse. |
| **Consumer first** | Add the new code path before removing the old one. |
| **Parity gate** | Replay parity must pass before deploying a schema version change. |
