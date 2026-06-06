package com.tradej.app.scanner;

import com.tradej.composition.config.ScanProperties;
import com.tradej.app.config.TradingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
@ConditionalOnBean(ScanService.class)
public class ScanScheduler {
    private static final Logger log = LoggerFactory.getLogger(ScanScheduler.class);

    private final ScanProperties scanProperties;
    private final ScanService scanService;
    private final TradingProperties tradingProperties;

    public ScanScheduler(
            ScanProperties scanProperties,
            ScanService scanService,
            TradingProperties tradingProperties
    ) {
        this.scanProperties = scanProperties;
        this.scanService = scanService;
        this.tradingProperties = tradingProperties;
    }

    @Scheduled(cron = "${trade.scan.scheduler-cron:0 15,30,45 9-15 * * MON-FRI}", zone = "${trade.scan.scheduler-zone:Asia/Kolkata}")
    public void runScheduledScans() {
        if (!scanProperties.enabled()) {
            return;
        }
        if (!inSession()) {
            log.debug("Skipping scheduled scan — outside venue session");
            return;
        }
        String profileId = scanProperties.defaultProfile();
        if (profileId == null || profileId.isBlank()) {
            log.warn("Scheduled scan skipped — trade.scan.default-profile not set");
            return;
        }
        try {
            scanService.runProfile(profileId);
        } catch (Exception ex) {
            log.warn("Scheduled scan failed for profile {}: {}", profileId, ex.getMessage());
        }
    }

    private boolean inSession() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalTime now = ZonedDateTime.now(zone).toLocalTime();
        if (tradingProperties.venues() == null || tradingProperties.venues().isEmpty()) {
            return !now.isBefore(LocalTime.of(9, 15)) && !now.isAfter(LocalTime.of(15, 30));
        }
        return tradingProperties.venues().values().stream().anyMatch(venue -> {
            if (venue.sessionOpen() == null || venue.sessionClose() == null) {
                return true;
            }
            return !now.isBefore(venue.sessionOpen()) && !now.isAfter(venue.sessionClose());
        });
    }
}
