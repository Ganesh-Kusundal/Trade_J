package com.tradej.scanner.criterion;

import com.tradej.scanner.model.ScanContext;

public interface ScanCriterion {
    String type();

    boolean matches(ScanContext context);

    double score(ScanContext context);

    default String reason(ScanContext context) {
        return type();
    }
}
