package com.tradej.research.lab;

import com.tradej.analytics.engine.DuckDbAnalyticsEngine;
import com.tradej.core.domain.model.Candle;
import com.tradej.research.core.ScannerConfig;
import com.tradej.scanner.criterion.ScanCriterion;
import com.tradej.scanner.model.ScanAsset;
import com.tradej.scanner.model.ScanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service to backtest scanner configurations by rolling through historical candle data
 * and evaluating custom criteria pipelines.
 */
@Service
public class ScannerLabService {
    private static final Logger log = LoggerFactory.getLogger(ScannerLabService.class);

    private final DuckDbAnalyticsEngine analyticsEngine;
    private final DuckDbResearchStore researchStore;

    public ScannerLabService(DuckDbAnalyticsEngine analyticsEngine, DuckDbResearchStore researchStore) {
        this.analyticsEngine = analyticsEngine;
        this.researchStore = researchStore;
    }

    /**
     * Runs a historical backtest of a scanner config over a given date range.
     */
    public void backtestScanner(
        UUID sessionId,
        ScannerConfig config,
        List<ScanCriterion> criteria,
        long fromMs,
        long toMs
    ) throws SQLException {
        log.info("Starting historical scanner backtest for session={} config={}", sessionId, config.configHash());

        // 1. Fetch historical candles for the symbols in the session
        for (String symbol : config.criteria()) { // let's assume config criteria contains symbol names, or we query them
            // Fetch candles
            List<Map<String, Object>> rows = analyticsEngine.queryEquityCandles(symbol, fromMs, toMs, 10000);
            if (rows.isEmpty()) {
                continue;
            }

            List<Candle> candles = rows.stream().map(row -> new Candle(
                (String) row.get("symbol"),
                (String) row.get("interval"),
                (Long) row.get("barTimeMs"),
                (Long) row.get("barTimeMs") + 59999, // 1m close
                (Long) row.get("openPaisa"),
                (Long) row.get("highPaisa"),
                (Long) row.get("lowPaisa"),
                (Long) row.get("closePaisa"),
                (Long) row.get("volume"),
                true
            )).collect(Collectors.toList());

            // 2. Evaluate criteria using a rolling window
            com.tradej.core.domain.model.Instrument instrument = new com.tradej.core.domain.model.Instrument(
                symbol,
                symbol,
                com.tradej.core.domain.value.Exchange.NSE,
                com.tradej.core.domain.value.ExchangeSegment.NSE_EQ,
                "EQUITY",
                symbol,
                null,
                0L,
                null,
                1,
                5
            );
            ScanAsset asset = new ScanAsset(instrument, com.tradej.scanner.model.AssetClass.EQUITY, symbol);
            for (int i = 10; i < candles.size(); i++) {
                List<Candle> window = candles.subList(0, i + 1);
                Candle lastCandle = candles.get(i);

                ScanContext context = new ScanContext(
                    asset,
                    null, // quote
                    null, // optionChain
                    window
                );

                List<String> matchedCriteria = new ArrayList<>();
                boolean allMatch = true;

                for (ScanCriterion criterion : criteria) {
                    if (criterion.matches(context)) {
                        matchedCriteria.add(criterion.type());
                    } else {
                        allMatch = false;
                    }
                }

                if (allMatch && !matchedCriteria.isEmpty()) {
                    String hitInfo = String.join(",", matchedCriteria);
                    researchStore.saveScannerHit(
                        sessionId.toString(),
                        config.configHash(),
                        symbol,
                        lastCandle.endTimeMs(),
                        hitInfo
                    );
                }
            }
        }
        log.info("Scanner backtest finished successfully for session={}", sessionId);
    }
}
