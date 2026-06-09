package com.tradej.execution.risk;

public final class KillSwitchRiskCheck implements RiskCheck {
    @Override
    public RiskVerdict check(RiskContext context) {
        if (context.killSwitchActive()) {
            return RiskVerdict.reject("kill_switch", "Kill switch is engaged");
        }
        if (context.reconciliationHaltActive()) {
            return RiskVerdict.reject("reconciliation_halt", "Reconciliation halt is active");
        }
        return RiskVerdict.approve("kill_switch");
    }
}
