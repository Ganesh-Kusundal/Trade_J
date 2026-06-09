package com.tradej.institutional;

import com.tradej.institutional.model.InstitutionalScanResult;
import com.tradej.core.domain.port.NoOpHistoricalBarRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class NoOpInstitutionalScanEngine extends InstitutionalScanEngine {

    public NoOpInstitutionalScanEngine() {
        super(new NoOpHistoricalBarRepository());
    }

    @Override
    public InstitutionalScanResult runHistoricalScan(LocalDate date, String scanTime) {
        return new InstitutionalScanResult(
                date.toString(),
                scanTime != null ? scanTime : "09:15:00",
                "noop",
                List.of(),
                Map.of()
        );
    }
}
