package com.tradej.app.admin;

import com.tradej.composition.FullComposition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/reconciliation")
public class ReconciliationController {

    private final FullComposition fullComposition;

    public ReconciliationController(FullComposition fullComposition) {
        this.fullComposition = fullComposition;
    }

    @PostMapping("/acknowledge")
    public ResponseEntity<Map<String, Object>> acknowledgeHalt() {
        fullComposition.executionComposition().positionRiskHandler().acknowledgeReconciliationHalt();
        return ResponseEntity.ok(Map.of(
                "acknowledged", true,
                "reconciliationHalt", fullComposition.executionComposition().positionRiskHandler().isReconciliationHaltActive(),
                "killSwitch", fullComposition.executionComposition().positionRiskHandler().isKillSwitchActive()));
    }
}
