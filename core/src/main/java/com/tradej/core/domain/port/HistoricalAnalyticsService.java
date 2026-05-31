package com.tradej.core.domain.port;

import com.tradej.core.domain.model.AnalyticsCatalogSnapshot;
import com.tradej.core.domain.model.AnalyticsQueryResult;
import com.tradej.core.domain.model.Candle;
import com.tradej.core.domain.model.CandleHistoryRequest;
import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.core.domain.model.RollingOptionSeriesRequest;
import com.tradej.core.domain.model.UniverseEntry;

import java.util.List;

public interface HistoricalAnalyticsService {

    List<Candle> queryEquityCandles(CandleHistoryRequest request);

    List<RollingOptionBar> queryOptionBars(RollingOptionSeriesRequest request);

    List<UniverseEntry> queryEquityUniverse();

    AnalyticsCatalogSnapshot catalog();

    AnalyticsQueryResult executeReadOnlySql(String sql, int rowLimit);
}
