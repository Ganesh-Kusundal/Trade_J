package com.tradej.historical.ingest.query;

import com.tradej.core.domain.model.RollingOptionBar;
import com.tradej.historical.ingest.store.DuckDbHistoricalWarehouse;

import java.sql.SQLException;
import java.util.List;

public final class HistoricalWarehouseQuery {

    private final DuckDbHistoricalWarehouse warehouse;

    public HistoricalWarehouseQuery(DuckDbHistoricalWarehouse warehouse) {
        this.warehouse = warehouse;
    }

    public List<RollingOptionBar> queryRollingOptionBars(
            String underlying,
            String expiryKind,
            int expiryCode,
            int strikeOffset,
            String optionType,
            int intervalMin,
            long fromMs,
            long toMs,
            int limit
    ) throws SQLException {
        return warehouse.queryRollingOptionBars(
                underlying, expiryKind, expiryCode, strikeOffset, optionType, intervalMin, fromMs, toMs, limit);
    }
}
