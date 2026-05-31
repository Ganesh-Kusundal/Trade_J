package com.tradej.broker.api.port;

import com.tradej.core.domain.model.ConditionalAlert;
import com.tradej.core.domain.model.ConditionalAlertRequest;

import java.util.List;

public interface ConditionalAlertProvider {
    String placeAlert(ConditionalAlertRequest request);

    ConditionalAlert getAlert(String alertId);

    List<ConditionalAlert> listAlerts();

    boolean deleteAlert(String alertId);
}
