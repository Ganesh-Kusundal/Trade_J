package com.tradej.core.domain.model;

public record PnlExitPolicy(
        long profitThresholdPaisa,
        long lossThresholdPaisa,
        boolean enableKillSwitch
) {
}
