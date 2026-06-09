package com.tradej.historical.ingest.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Store for corporate actions (splits, bonuses, dividends, rights issues).
 *
 * <p>Used to adjust historical OHLCV data for backward compatibility
 * when computing returns, indicators, and ML features.
 */
public interface CorporateActionStore {

    List<CorporateAction> queryActions(String symbol, LocalDate from, LocalDate to);

    BigDecimal adjustmentFactor(String symbol, LocalDate asOfDate);

    record CorporateAction(
            String symbol,
            LocalDate exDate,
            CorporateActionType type,
            BigDecimal ratio,
            String description
    ) {}

    enum CorporateActionType {
        SPLIT,
        BONUS,
        DIVIDEND,
        RIGHTS,
        MERGER,
        DEMERGER
    }
}
