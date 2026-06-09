package com.tradej.execution.risk;

public final class DailyLossRiskCheck implements RiskCheck {
    @Override
    public RiskVerdict check(RiskContext context) {
        long totalLoss = context.realizedLossPaisa() + context.unrealizedLossPaisa();
        if (context.maxDailyLossPaisa() > 0 && totalLoss >= context.maxDailyLossPaisa()) {
            return RiskVerdict.reject("daily_loss",
                    "Combined loss " + totalLoss + " paisa exceeds limit " + context.maxDailyLossPaisa());
        }
        return RiskVerdict.approve("daily_loss");
    }
}
