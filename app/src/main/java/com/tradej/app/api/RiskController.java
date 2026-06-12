package com.tradej.app.api;

import com.tradej.broker.api.IBrokerConnection;
import com.tradej.execution.risk.KillSwitchCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint that arms the platform-wide kill switch. Called by the
 * UI's "STOP ALL TRADING" button and by the {@code tradej killswitch}
 * CLI sub-command.
 */
@RestController
@RequestMapping("/api/v1/risk")
@ConditionalOnBean({KillSwitchCoordinator.class, IBrokerConnection.class})
public class RiskController {

    private final KillSwitchCoordinator killSwitch;

    public RiskController(KillSwitchCoordinator killSwitch) {
        this.killSwitch = killSwitch;
    }

    @PostMapping("/kill-switch")
    public ResponseEntity<Map<String, Object>> arm() {
        killSwitch.engage("ui-button");
        return ResponseEntity.ok(Map.of(
                "status", "ARMED",
                "armedAtMs", System.currentTimeMillis()
        ));
    }

    @PostMapping("/kill-switch/disarm")
    public ResponseEntity<Map<String, Object>> disarm() {
        killSwitch.disengage();
        return ResponseEntity.ok(Map.of("status", "DISARMED"));
    }
}
