package com.tradej.core.domain.port;

import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RollingOptionHistoricalRepository {

    List<RollingOptionBar> queryBars(RollingOptionSeriesRequest request);

    Optional<LocalDate> latestAvailableTradingDay(String underlying, int lookbackDays);

    List<String> availableUnderlyings();
}
