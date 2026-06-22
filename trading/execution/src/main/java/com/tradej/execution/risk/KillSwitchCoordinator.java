package com.tradej.execution.risk;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.broker.api.capability.BrokerCapabilityRouter;
import com.tradej.broker.api.port.OrderCommand;
import com.tradej.execution.service.OrderManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Synchronizes platform kill-switch state with broker-side kill switch APIs.
 */
public final class KillSwitchCoordinator {

    private static final Logger log = LoggerFactory.getLogger(KillSwitchCoordinator.class);

    private final IBrokerConnection brokerConnection;
    private final OrderManagementService orderManagementService;
    private volatile boolean brokerKillSwitchEngaged;

    public KillSwitchCoordinator(
            IBrokerConnection brokerConnection,
            OrderManagementService orderManagementService
    ) {
        this.brokerConnection = brokerConnection;
        this.orderManagementService = orderManagementService;
    }

    public void engage(String reason) {
        log.error("Unified kill switch ENGAGE reason={}", reason);
        if (orderManagementService != null) {
            try {
                orderManagementService.activateKillSwitch();
                brokerKillSwitchEngaged = true;
            } catch (Exception e) {
                log.warn("Broker kill switch via OMS failed: {}", e.getMessage());
            }
        } else if (brokerConnection != null) {
            BrokerCapabilityRouter.forConnection(brokerConnection).find(OrderCommand.class).ifPresent(cmd -> {
                try {
                    cmd.setKillSwitch(true);
                    brokerKillSwitchEngaged = true;
                } catch (Exception e) {
                    log.warn("Broker kill switch API failed: {}", e.getMessage());
                }
            });
        }
    }

    public void disengage() {
        log.info("Unified kill switch DISENGAGE");
        if (orderManagementService != null && brokerKillSwitchEngaged) {
            try {
                orderManagementService.deactivateKillSwitch();
            } catch (Exception e) {
                log.warn("Broker kill switch disengage via OMS failed: {}", e.getMessage());
            }
        } else if (brokerConnection != null && brokerKillSwitchEngaged) {
            BrokerCapabilityRouter.forConnection(brokerConnection).find(OrderCommand.class).ifPresent(cmd -> {
                try {
                    cmd.setKillSwitch(false);
                } catch (Exception e) {
                    log.warn("Broker kill switch disengage failed: {}", e.getMessage());
                }
            });
        }
        brokerKillSwitchEngaged = false;
    }

    public boolean isBrokerKillSwitchEngaged() {
        return brokerKillSwitchEngaged;
    }
}
