package com.tradej.execution.risk;

/**
 * Context passed through the risk check chain.
 * Carries all state needed for risk decisions without coupling to specific handlers.
 *
 * @param symbol the trading symbol being evaluated
 * @param realizedLossPaisa total realized losses for the session
 * @param unrealizedLossPaisa current unrealized MTM losses
 * @param openTradeCount number of currently open trades
 * @param killSwitchActive whether the kill switch is engaged
 * @param reconciliationHaltActive whether reconciliation has halted trading
 * @param maxDailyLossPaisa configured maximum daily loss limit
 * @param maxOpenPositions configured maximum open positions
 */
public record RiskContext(
        String symbol,
        long realizedLossPaisa,
        long unrealizedLossPaisa,
        int openTradeCount,
        boolean killSwitchActive,
        boolean reconciliationHaltActive,
        long maxDailyLossPaisa,
        int maxOpenPositions
) {}
