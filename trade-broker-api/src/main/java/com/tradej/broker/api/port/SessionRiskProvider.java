package com.tradej.broker.api.port;

import com.tradej.core.domain.model.PnlExitPolicy;
import com.tradej.core.domain.model.PnlExitResult;

public interface SessionRiskProvider {
    PnlExitResult enablePnlExit(PnlExitPolicy policy);
}
