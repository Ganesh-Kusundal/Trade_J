package com.tradej.scanner.model;

import com.tradej.core.domain.model.Instrument;
import com.tradej.core.domain.model.InstrumentKey;

public record ScanAsset(
        Instrument instrument,
        AssetClass assetClass,
        String underlying
) {
    public InstrumentKey key() {
        return instrument.key();
    }
}
