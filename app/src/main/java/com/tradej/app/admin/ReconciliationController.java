package com.tradej.app.admin;

import com.tradej.execution.risk.PositionRiskHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/reconciliation")
public class ReconciliationController {

    private final PositionRiskHandler positionRiskHandler;

    public ReconciliationController(PositionRiskHandler positionRiskHandler) {
        this.positionRiskHandler = positionRiskHandler;
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeHalt() {
        positionRiskHandler.acknowledgeReconciliationHalt();
        return ResponseEntity.ok(Map.of(
                "acknowledged", true,
                "reconciliationHalt", positionRiskHandler.isReconciliationHaltActive(),
                "killSwitch", positionRiskHandler.isKillSwitchActive()));
    }
}
