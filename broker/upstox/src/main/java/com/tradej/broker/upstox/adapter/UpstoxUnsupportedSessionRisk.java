package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.SessionRiskProvider;
import com.tradej.core.domain.model.PnlExitPolicy;
import com.tradej.core.domain.model.PnlExitResult;

final class UpstoxUnsupportedSessionRisk extends UpstoxUnsupportedPort implements SessionRiskProvider {
    @Override
    public PnlExitResult enablePnlExit(PnlExitPolicy policy) {
        throw unsupported("SessionRiskProvider");
    }
}
