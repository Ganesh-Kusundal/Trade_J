package com.tradej.app.config;

import com.tradej.app.health.AlertChannel;
import com.tradej.app.health.AlertManager;
import com.tradej.app.health.LoggingAlertChannel;
import com.tradej.app.health.PagerDutyAlertChannel;
import com.tradej.app.health.SlackAlertChannel;
import com.tradej.app.health.WebhookAlertChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class AlertConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AlertConfiguration.class);

    @Bean
    public AlertManager alertManager(
            @Value("${tradej.alerts.webhook-url:}") String webhookUrl,
            @Value("${tradej.alerts.slack-webhook-url:}") String slackUrl,
            @Value("${tradej.alerts.pagerduty-routing-key:}") String pagerDutyKey,
            @Value("${tradej.alerts.enabled:true}") boolean enabled,
            @Value("${tradej.alerts.cooldown-seconds:300}") int cooldownSeconds
    ) {
        if (!enabled) {
            log.info("Alerting disabled — using no-op AlertManager");
            return new AlertManager(List.of(AlertChannel.noop()), Duration.ofSeconds(cooldownSeconds));
        }

        List<AlertChannel> channels = new ArrayList<>();
        channels.add(new LoggingAlertChannel());

        if (webhookUrl != null && !webhookUrl.isBlank()) {
            channels.add(new WebhookAlertChannel(webhookUrl));
            log.info("Alert channel: webhook");
        }
        if (slackUrl != null && !slackUrl.isBlank()) {
            channels.add(new SlackAlertChannel(slackUrl));
            log.info("Alert channel: Slack");
        }
        if (pagerDutyKey != null && !pagerDutyKey.isBlank()) {
            channels.add(new PagerDutyAlertChannel(pagerDutyKey));
            log.info("Alert channel: PagerDuty");
        }

        if (channels.size() == 1) {
            log.info("Alerting enabled (log only) — no external channels configured");
        } else {
            log.info("Alerting enabled with {} channels", channels.size());
        }

        return new AlertManager(channels, Duration.ofSeconds(cooldownSeconds));
    }
}
