package com.tradej.scanner.spi;

import com.tradej.scanner.criterion.MaxOiStrikeCriterion;
import com.tradej.scanner.criterion.PcrRangeCriterion;
import com.tradej.scanner.criterion.PctChangeFromOpenCriterion;
import com.tradej.scanner.criterion.PctChangeFromPrevCloseCriterion;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.criterion.VolumeSpikeCriterion;

import java.util.List;

public final class DefaultScannerProvider implements ScannerProvider {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public String displayName() {
        return "Default Options Scanner";
    }

    @Override
    public List<ScanCriterion> criteria() {
        return List.of(
                new VolumeSpikeCriterion(2.0, 1000),
                new PctChangeFromPrevCloseCriterion(1.0, null),
                new PctChangeFromOpenCriterion(0.5, null),
                new PcrRangeCriterion(0.5, 2.0),
                new MaxOiStrikeCriterion(50000)
        );
    }
}
