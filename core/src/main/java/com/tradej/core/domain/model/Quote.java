package com.tradej.core.domain.model;

public record Quote(
        Instrument instrument,
        long ltpPaisa,
        long openPaisa,
        long highPaisa,
        long lowPaisa,
        long closePaisa,
        long volume,
        long totalBuyQuantity,
        long totalSellQuantity,
        long timestampMs
) {
}
