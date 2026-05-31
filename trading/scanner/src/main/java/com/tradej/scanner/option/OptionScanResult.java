package com.tradej.scanner.option;

import com.tradej.scanner.model.ScanHit;
import com.tradej.scanner.model.ScanResult;
import com.tradej.scanner.model.ScanRun;

import java.time.LocalDate;
import java.util.List;

public record OptionScanResult(
        String underlying,
        LocalDate expiry,
        ScanRun run,
        List<OptionContractHit> contracts,
        int partialFailureCount
) {
    public OptionScanResult {
        contracts = contracts == null ? List.of() : List.copyOf(contracts);
    }

    public ScanResult toScanResult() {
        List<ScanHit> hits = contracts.stream().map(OptionContractHit::toScanHit).toList();
        ScanRun completed = run.completed(hits.size(), partialFailureCount);
        return new ScanResult(completed, hits);
    }
}
