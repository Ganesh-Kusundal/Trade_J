package com.tradej.execution.risk;

public final class PositionLimitRiskCheck implements RiskCheck {
    @Override
    public RiskVerdict check(RiskContext context) {
        if (context.maxOpenPositions() > 0 && context.openTradeCount() >= context.maxOpenPositions()) {
            return RiskVerdict.reject("position_limit",
                    "Open trades " + context.openTradeCount() + " >= limit " + context.maxOpenPositions());
        }
        return RiskVerdict.approve("position_limit");
    }
}
