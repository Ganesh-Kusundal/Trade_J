package com.tradej.broker.upstox.adapter;

import com.tradej.broker.api.port.ConditionalAlertProvider;
import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;

import java.util.List;

final class UpstoxUnsupportedAlerts extends UpstoxUnsupportedPort implements ConditionalAlertProvider {
    @Override
    public String placeAlert(ConditionalAlertRequest request) {
        throw unsupported("ConditionalAlertProvider");
    }

    @Override
    public ConditionalAlert getAlert(String alertId) {
        throw unsupported("ConditionalAlertProvider");
    }

    @Override
    public List<ConditionalAlert> listAlerts() {
        throw unsupported("ConditionalAlertProvider");
    }

    @Override
    public boolean deleteAlert(String alertId) {
        throw unsupported("ConditionalAlertProvider");
    }
}
