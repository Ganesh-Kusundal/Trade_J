package com.tradej.core.domain.event;

import com.tradej.core.domain.model.Candle;

public record CandleClosed(EventMetadata metadata, Candle candle) implements DomainEvent {
}
