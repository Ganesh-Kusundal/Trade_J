package com.tradej.execution.risk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Daily reset of the kill switch and loss counters at market open.
 * <p>
 * Runs before the Indian market opens (9:15 AM IST = 3:45 AM UTC).
 * The {@link PositionRiskHandler#resetDailyLimits()} call clears
 * the realized loss counter, consecutive loss counter, and kill switch,
 * allowing a fresh day of trading.
 */
@Service
public class DailyRiskResetScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyRiskResetScheduler.class);

    private final PositionRiskHandler positionRiskHandler;

    public DailyRiskResetScheduler(PositionRiskHandler positionRiskHandler) {
        this.positionRiskHandler = positionRiskHandler;
    }

    /**
     * Resets daily loss limits and kill switch at 9:00 AM IST (3:30 AM UTC)
     * every weekday, giving a 15-minute buffer before market open at 9:15 AM IST.
     */
    @Scheduled(cron = "${trade.risk.daily-reset-cron:0 30 3 * * MON-FRI}", zone = "UTC")
    void resetDailyLimits() {
        log.info("Resetting daily loss limits and kill switch for new trading day");
        positionRiskHandler.resetDailyLimits();
    }
}
