package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.MarginProvider;
import com.tradej.core.domain.model.MarginEstimate;
import com.tradej.core.domain.model.MarginEstimateRequest;

public final class UpstoxUnsupportedMarginProvider implements MarginProvider {

    @Override
    public MarginEstimate estimateMargin(MarginEstimateRequest request) {
        throw UpstoxUnsupportedPort.unsupported("MarginProvider");
    }
}
