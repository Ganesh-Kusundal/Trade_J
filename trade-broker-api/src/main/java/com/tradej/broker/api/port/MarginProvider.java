package com.tradej.broker.api.port;

import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;

public interface MarginProvider {
    MarginEstimate estimateMargin(MarginEstimateRequest request);
}
